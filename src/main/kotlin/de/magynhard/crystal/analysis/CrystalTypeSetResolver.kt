package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

internal object CrystalTypeSetResolver {
    fun resolve(element: PsiElement): CrystalTypeResolution =
        CrystalTypeResolutionSession(element).resolve(element)

    fun session(context: PsiElement): CrystalTypeResolutionSession =
        CrystalTypeResolutionSession(context)

    internal fun sessionConstructionCount(): Long = CrystalTypeResolutionSession.constructionCount()

    internal fun resetSessionConstructionCount() = CrystalTypeResolutionSession.resetConstructionCount()
}

internal class CrystalTypeResolutionSession(private val context: PsiElement) {
    init {
        CONSTRUCTION_COUNT.incrementAndGet()
    }

    private val effectiveSources = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context)
    private val memo = IdentityHashMap<PsiElement, CrystalTypeResolution>()
    private val activeHeredocBodies = IdentityHashMap<PsiElement, CrystalHeredocLiteral>()
    private val resolving = java.util.Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
    private val resolvingMethods = java.util.Collections.newSetFromMap(IdentityHashMap<CrystalMethodDefinition, Boolean>())
    private val methodReturnMemo = IdentityHashMap<CrystalMethodDefinition, CrystalTypeResolution>()
    private val typeCache = mutableMapOf<String, List<CrystalNamedElement>>()
    private val methodCache = mutableMapOf<String, List<CrystalMethodDefinition>>()
    private val methodsByTypeCache = mutableMapOf<String, List<CrystalMethodDefinition>>()
    private val hierarchy = CrystalMethodHierarchy(context, ::types, ::classMethods)

    companion object {
        private val CONSTRUCTION_COUNT = AtomicLong()

        internal fun constructionCount(): Long = CONSTRUCTION_COUNT.get()

        internal fun resetConstructionCount() = CONSTRUCTION_COUNT.set(0)
    }

    fun resolve(element: PsiElement): CrystalTypeResolution {
        val target = promote(element)
        memo[target]?.let { return it }
        if (!resolving.add(target)) return CrystalTypeResolution.Unknown
        val result = resolveUncached(target)
        resolving.remove(target)
        memo[target] = result
        return result
    }

    fun resolveExpressionPrefix(expression: CrystalExpression, beforeOffset: Int): CrystalTypeResolution =
        resolveExpressionChildren(
            significantChildren(expression).filter { it.textRange.endOffset <= beforeOffset },
            expression
        )

    fun resolveVariable(name: String, position: PsiElement): CrystalTypeResolution =
        resolveVariableWithProvenance(name, position).resolution

    fun resolveVariableWithProvenance(name: String, position: PsiElement): CrystalVariableResolution =
        resolveVariableValue(name, position).toPublic()

    fun resolveCall(
        receiverTypeNames: List<String>,
        methodName: String,
        isStatic: Boolean,
        callContext: PsiElement
    ): CrystalTypeResolution {
        val results = mutableListOf<CrystalTypeResolution>()
        for (typeName in receiverTypeNames) {
            val identity = resolveTypeIdentity(typeName, callContext) ?: return CrystalTypeResolution.Unknown
            val collection = hierarchy.collectNamedMethods(
                identity.toShared(),
                if (isStatic) CrystalReceiverMode.STATIC else CrystalReceiverMode.INSTANCE,
                methodName
            )
            if (!collection.complete || collection.methods.size != 1) return CrystalTypeResolution.Unknown
            results.add(resolveMethodReturn(collection.methods.single()))
        }
        return mergeKnown(results)
    }

    fun resolveType(typeName: String, element: PsiElement): CrystalTypeIdentity? =
        resolveTypeIdentity(typeName, element)?.toShared()

    fun isConstructible(typeName: String, element: PsiElement): Boolean =
        resolveConstructor(typeName, element).let {
            it is CrystalConstructorResolution.Methods || it is CrystalConstructorResolution.Implicit ||
                it is CrystalConstructorResolution.Record
        }

    fun collectMethods(
        receiverType: CrystalTypeIdentity,
        mode: CrystalReceiverMode
    ): CrystalAllMethodCollection = hierarchy.collectMethods(receiverType, mode)

    fun collectNamedMethods(
        receiverType: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        methodName: String,
        actualSelf: Boolean? = null,
        includeModuleEdges: Boolean = true
    ): CrystalMethodCollection = hierarchy.collectNamedMethods(
        receiverType,
        mode,
        methodName,
        actualSelf,
        includeModuleEdges
    )

    fun resolveConstructor(typeName: String, element: PsiElement): CrystalConstructorResolution {
        val root = normalizeExactTypeRoot(typeName) ?: typeName.removePrefix("::")
        val simpleName = root.substringAfterLast("::")
        val fallbackIdentity = CrystalTypeIdentity(simpleName, root)
        // Nonphysical injected PSI has no load context and must not use the same-file record shortcut.
        val records = if (effectiveSources.contains(element)) {
            element.containingFile?.let { CrystalPsiUtils.findRecordDefinitions(simpleName, it) }
                .orEmpty().groupBy { it.qualifiedName }
        } else {
            emptyMap()
        }
        for (candidate in typeIdentityCandidates(root, typeName.startsWith("::"), element)) {
            val recordCandidates = records[candidate].orEmpty()
            if (recordCandidates.any { CrystalPsiUtils.isInsideMacroControlRegion(it.call) } ||
                recordCandidates.size > 1) {
                return CrystalConstructorResolution.Incomplete(CrystalTypeIdentity(simpleName, candidate))
            }
            recordCandidates.singleOrNull()?.let {
                return CrystalConstructorResolution.Record(
                    CrystalTypeIdentity(simpleName, candidate),
                    it.call
                )
            }
            val identities = exactTypeIdentities(simpleName, candidate)
            if (identities.size > 1) return CrystalConstructorResolution.Incomplete(fallbackIdentity)
            identities.singleOrNull()?.let { return resolveConstructor(it) }
        }
        return CrystalConstructorResolution.Unavailable(fallbackIdentity)
    }

    fun resolveConstructor(identity: CrystalTypeIdentity): CrystalConstructorResolution {
        val declarations = hierarchy.findExactTypeDeclarations(identity)
        if (declarations.isEmpty() || declarations.any(CrystalPsiUtils::isInsideMacroControlRegion)) {
            return CrystalConstructorResolution.Incomplete(identity)
        }
        if (declarations.any { it.node.findChildByType(CrystalTypes.ABSTRACT) != null }) {
            return CrystalConstructorResolution.Abstract(identity)
        }
        if (declarations.any { it !is CrystalClassDefinition && it !is CrystalStructDefinition }) {
            return CrystalConstructorResolution.Unavailable(identity)
        }
        val selfNew = hierarchy.collectNamedMethods(
            identity,
            CrystalReceiverMode.STATIC,
            "new",
            actualSelf = true,
            includeModuleEdges = false
        )
        if (!selfNew.complete) return CrystalConstructorResolution.Incomplete(identity)
        val initializers = hierarchy.collectNamedMethods(
            identity,
            CrystalReceiverMode.INSTANCE,
            "initialize",
            actualSelf = false
        )
        if (!initializers.complete) return CrystalConstructorResolution.Incomplete(identity)
        val explicitSignatures = selfNew.methods
            .flatMapTo(linkedSetOf(), hierarchy::constructorDispatchSignatures)
        val forwardedInitializers = initializers.methods.filter { initializer ->
            hierarchy.constructorDispatchSignatures(initializer).none(explicitSignatures::contains)
        }
        val methods = selfNew.methods + forwardedInitializers
        return if (methods.isEmpty()) {
            CrystalConstructorResolution.Implicit(identity)
        } else {
            CrystalConstructorResolution.Methods(identity, methods)
        }
    }

    private fun resolveUncached(element: PsiElement): CrystalTypeResolution {
        if (element is CrystalBareArgument) return firstExpressionChild(element)?.let(::resolve)
            ?: CrystalTypeResolution.Unknown
        if (element is CrystalStatement) return resolveStatement(element)
        if (element is CrystalExpressionStatement) {
            return element.expressionList.firstOrNull()?.let(::resolve) ?: knownType("Nil")
        }
        if (element is CrystalAssignment) return resolve(element.assignment ?: element.expression
            ?: return CrystalTypeResolution.Unknown)
        if (element is CrystalIndexedAssignment) return resolveIndexedAssignmentValue(element)
        if (element is CrystalReturnStatement) return resolveAbruptValues(element.valueElements())

        when (element.node?.elementType) {
            CrystalTypes.INTEGER_LITERAL -> return resolveInteger(element.text)
            CrystalTypes.FLOAT_LITERAL -> return resolveFloat(element.text)
            CrystalTypes.STRING_LITERAL -> return knownType("String")
            // v12 heredoc header marker: the in-list argument expression is the
            // raw opener token; the string body lives in the trailing bodies node.
            CrystalTypes.HEREDOC_START -> return knownType("String")
            CrystalTypes.CHAR_LITERAL -> return knownType("Char")
            CrystalTypes.SYMBOL_LITERAL -> return knownType("Symbol")
            CrystalTypes.TRUE, CrystalTypes.FALSE -> return knownType("Bool")
            CrystalTypes.NIL -> return knownType("Nil")
        }

        return when (element) {
            is CrystalStringExpression, is CrystalHeredocLiteral, is CrystalCommandExpression -> knownType("String")
            is CrystalRegexExpression -> knownType("Regex")
            is CrystalSymbolStringExpression -> knownType("Symbol")
            is CrystalSizeofExpression, is CrystalInstanceSizeofExpression, is CrystalOffsetofExpression -> knownType("Int32")
            is CrystalArrayLiteral -> resolveArray(element)
            is CrystalHashLiteral -> resolveHash(element)
            is CrystalTupleLiteral -> resolveTuple(element)
            is CrystalPercentLiteral -> resolvePercentLiteral(element)
            is CrystalIfStatement -> resolveIf(element)
            is CrystalUnlessStatement -> resolveUnless(element)
            is CrystalCaseStatement -> resolveCase(element)
            is CrystalBeginStatement -> resolveBegin(element)
            is CrystalVariableReference -> resolveVariableValue(element.text, element).result.let { variable ->
                if (variable is CrystalTypeResolution.Unknown) resolveUnqualifiedCall(element.text, element) else variable
            }
            is CrystalInstanceVarAccess, is CrystalClassVarAccess -> resolveVariableValue(element.text, element).result
            is CrystalMethodCallExpression -> resolveUnqualifiedCall(methodName(element), element)
            is CrystalBareMethodCallExpression -> resolveUnqualifiedCall(methodName(element), element)
            is CrystalGroupedExpression -> {
                if (element.expressionList.size != 1 || element.node.findChildByType(CrystalTypes.ASSIGN) != null) {
                    CrystalTypeResolution.Unknown
                } else {
                    resolve(element.expressionList.single())
                }
            }
            is CrystalExpression -> resolveExpression(element)
            else -> CrystalTypeResolution.Unknown
        }
    }

    private fun resolveStatement(statement: CrystalStatement): CrystalTypeResolution = when {
        statement.assignment != null -> resolve(statement.assignment!!)
        statement.indexedAssignment != null -> resolveIndexedAssignmentValue(statement.indexedAssignment!!)
        statement.expressionStatement != null -> resolve(statement.expressionStatement!!)
        statement.ifStatement != null -> resolve(statement.ifStatement!!)
        statement.unlessStatement != null -> resolve(statement.unlessStatement!!)
        statement.beginStatement != null -> resolveBegin(statement.beginStatement!!)
        statement.returnStatement != null -> resolve(statement.returnStatement!!)
        else -> knownType("Nil")
    }

    private fun resolveIndexedAssignmentValue(assignment: CrystalIndexedAssignment): CrystalTypeResolution {
        val evaluation = assignment.evaluationComponents()
        if (evaluation.compound) return CrystalTypeResolution.Unknown
        val value = evaluation.rhs?.let(::resolve) ?: return CrystalTypeResolution.Unknown
        val postfix = assignment.postfixModifier ?: return value
        return if (postfix.node.findChildByType(CrystalTypes.RESCUE) != null) {
            mergeKnown(listOf(value, resolve(postfix.conditionElement())))
        } else {
            mergeKnown(listOf(value, knownType("Nil")))
        }
    }

    private fun resolveExpression(expression: CrystalExpression): CrystalTypeResolution {
        return resolveExpressionChildren(significantChildren(expression), expression)
    }

    private fun resolveExpressionChildren(
        children: List<PsiElement>,
        expression: CrystalExpression
    ): CrystalTypeResolution {
        val question = children.indexOfFirst { it.node.elementType == CrystalTypes.QUESTION }
        if (question >= 0) {
            val colon = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
            if (colon <= question) return CrystalTypeResolution.Unknown
            val trueExpression = children.getOrNull(question + 1) ?: return CrystalTypeResolution.Unknown
            val falseExpression = children.getOrNull(colon + 1) ?: return CrystalTypeResolution.Unknown
            return mergeKnown(listOf(resolve(trueExpression), resolve(falseExpression)))
        }
        if (children.any {
                it.node.elementType == CrystalTypes.DOTDOT || it.node.elementType == CrystalTypes.DOTDOTDOT
            }) {
            return knownType("Range")
        }
        if (children.any { it is CrystalDotCallAccess }) return resolvePostfix(children, expression)
        resolveOperator(children)?.let { return it }
        return children.firstOrNull()?.let(::resolve) ?: knownType("Nil")
    }

    private fun resolvePostfix(children: List<PsiElement>, callContext: PsiElement): CrystalTypeResolution {
        val firstAccess = children.indexOfFirst { it is CrystalDotCallAccess }
        if (firstAccess < 0) return CrystalTypeResolution.Unknown
        val baseElements = children.take(firstAccess)
        var receiver: ReceiverState = exactTypeRoot(baseElements)?.let { root ->
            val identity = resolveTypeIdentity(root, callContext) ?: return CrystalTypeResolution.Unknown
            ReceiverState.TypeObject(identity)
        } ?: ReceiverState.Values(resolve(baseElements.singleOrNull() ?: return CrystalTypeResolution.Unknown))

        for (access in children.drop(firstAccess)) {
            val dotAccess = access as? CrystalDotCallAccess ?: return CrystalTypeResolution.Unknown
            for (component in CrystalPostfixChain.components(dotAccess)) {
                val call = component ?: return CrystalTypeResolution.Unknown
                val name = methodName(call) ?: return CrystalTypeResolution.Unknown
                receiver = when (receiver) {
                    is ReceiverState.TypeObject -> {
                        if (name == "new") {
                            when (resolveConstructor(receiver.identity.toShared())) {
                                is CrystalConstructorResolution.Methods,
                                is CrystalConstructorResolution.Implicit,
                                is CrystalConstructorResolution.Record -> Unit
                                else -> return CrystalTypeResolution.Unknown
                            }
                            ReceiverState.Values(knownType(receiver.identity.qualifiedName))
                        } else {
                            ReceiverState.Values(
                                resolveCall(listOf(receiver.identity.qualifiedName), name, true, dotAccess)
                            )
                        }
                    }
                    is ReceiverState.Values -> {
                        val known = receiver.result as? CrystalTypeResolution.Known
                            ?: return CrystalTypeResolution.Unknown
                        ReceiverState.Values(resolveCall(known.types.map { it.name }, name, false, call))
                    }
                }
                if (receiver.result is CrystalTypeResolution.Unknown) {
                    return CrystalTypeResolution.Unknown
                }
            }
        }
        return (receiver as? ReceiverState.Values)?.result ?: CrystalTypeResolution.Unknown
    }

    private fun exactTypeRoot(elements: List<PsiElement>): String? {
        if (elements.isEmpty()) return null
        val callArgs = elements.lastOrNull() as? CrystalCallArgs
        val path = if (callArgs == null) elements else elements.dropLast(1)
        val root = path.lastOrNull()?.let(CrystalReceiverExpression::extractExactConstantTypeRoot) ?: return null
        if (callArgs != null) {
            val arguments = CrystalPsiCallArguments.getArguments(callArgs)
            if (arguments.isEmpty() || arguments.any {
                    it.expression?.let(CrystalReceiverExpression::extractExactConstantTypeRoot) == null
                }) return null
        }
        return root
    }

    private fun resolveIf(statement: CrystalIfStatement): CrystalTypeResolution {
        val execution = analyzeIf(statement)
        return execution.value ?: CrystalTypeResolution.Unknown
    }

    private fun resolveUnless(statement: CrystalUnlessStatement): CrystalTypeResolution =
        analyzeUnless(statement).value ?: CrystalTypeResolution.Unknown

    private fun resolveCase(statement: CrystalCaseStatement): CrystalTypeResolution {
        return analyzeCase(statement).value ?: CrystalTypeResolution.Unknown
    }

    private fun resolveBegin(statement: CrystalBeginStatement): CrystalTypeResolution =
        analyzeProtectedBody(
            statement.statementList,
            statement.rescueClauseList,
            statement.elseClause,
            statement.ensureClause
        ).value ?: CrystalTypeResolution.Unknown

    private fun resolveStatementList(statementList: CrystalStatementList?): CrystalTypeResolution {
        val statements = statementList?.statementList.orEmpty()
        return statements.lastOrNull()?.let(::resolve) ?: knownType("Nil")
    }

    private fun resolveVariableValue(name: String, position: PsiElement): VariableState {
        val containingAssignment = PsiTreeUtil.getParentOfType(position, CrystalAssignment::class.java, false)
        if (containingAssignment != null && assignmentName(containingAssignment) == name) {
            return VariableState.Bound(
                resolve(containingAssignment.assignment ?: containingAssignment.expression
                    ?: return VariableState.Unknown),
                CrystalVariableProvenance.ASSIGNMENT
            )
        }
        val boundary = lexicalBoundary(position, name) ?: return VariableState.Unknown
        var state: VariableState = VariableState.Unbound
        if (position === boundary) {
            for (child in boundary.children) {
                val flow = flowElement(child, name, state)
                if (!flow.fallsThrough) return VariableState.Unknown
                state = flow.state
            }
            return state
        }
        val path = generateSequence(position) { it.parent }.takeWhile { it !== boundary }
            .toList().asReversed()
        var container = boundary
        for (next in path) {
            state = introduceParameter(container, name, state)
            val branchState = protectedBranchIncoming(container, next, name, state)
            if (branchState != null) {
                state = branchState
                container = next
                continue
            }
            for (child in container.children) {
                if (child === next) break
                val flow = flowElement(child, name, state)
                if (!flow.fallsThrough) return VariableState.Unknown
                state = flow.state
            }
            container = next
        }
        return state
    }

    private fun parameterResult(parameters: List<CrystalParameter>, name: String): CrystalTypeResolution? {
        val parameter = parameters.firstOrNull {
            name in it.localBindingNames() || it.parameterNameInfo().storageName == name
        } ?: return null
        return parameter.typeReference?.text?.let(::parseTypeSet) ?: CrystalTypeResolution.Unknown
    }

    private fun lexicalBoundary(position: PsiElement, name: String): PsiElement? {
        if (position is PsiFile) return position
        if (name.startsWith("@")) {
            return generateSequence(position.parent) { it.parent }.firstOrNull(::isTypeBoundary)
        }
        return generateSequence(position.parent) { it.parent }.firstOrNull {
            it is CrystalMethodDefinition || it is CrystalMacroDefinition || it is PsiFile
        }
    }

    private fun introduceParameter(container: PsiElement, name: String, incoming: VariableState): VariableState {
        val parameters = when (container) {
            is CrystalMethodDefinition -> container.parameterList?.parameterList.orEmpty()
            is CrystalMacroDefinition -> container.parameterList?.parameterList.orEmpty()
            is CrystalBlock -> container.parameterList?.parameterList.orEmpty()
            else -> emptyList()
        }
        return parameterResult(parameters, name)?.let {
            VariableState.Bound(it, CrystalVariableProvenance.ANNOTATION)
        } ?: incoming
    }

    private fun protectedBranchIncoming(
        container: PsiElement,
        next: PsiElement,
        name: String,
        incoming: VariableState
    ): VariableState? {
        if (next !is CrystalRescueClause) return null
        val body = when (container) {
            is CrystalBeginStatement -> container.statementList
            is CrystalMethodBody -> container.statementList
            else -> return null
        }
        val flow = flowStatementList(body, name, incoming)
        return exceptionalIncoming(flow, incoming)
    }

    private fun flowElement(element: PsiElement, name: String, incoming: VariableState): VariableFlow {
        if (isScopeBoundary(element)) return VariableFlow.falling(incoming)
        activeHeredocBodies[element]?.let { return flowElement(it, name, incoming) }
        val abruptStatement = when (element) {
            is CrystalStatement -> element.returnStatement ?: element.breakStatement ?: element.nextStatement
            is CrystalAbruptStatement -> element
            else -> null
        }
        if (abruptStatement != null) {
            val kind = when (abruptStatement) {
                is CrystalReturnStatement -> AbruptKind.RETURN
                is CrystalBreakStatement -> AbruptKind.BREAK
                else -> AbruptKind.NEXT
            }
            val postfix = when (abruptStatement) {
                is CrystalReturnStatement -> abruptStatement.postfixModifier
                is CrystalBreakStatement -> abruptStatement.postfixModifier
                is CrystalNextStatement -> abruptStatement.postfixModifier
                else -> null
            }
            if (postfix?.node?.findChildByType(CrystalTypes.RESCUE) != null) {
                val abrupt = flowAbruptValues(abruptStatement, name, incoming, kind)
                if (abrupt.exceptionalStates.isEmpty()) return abrupt
                val rescue = flowElement(postfix.conditionElement(), name, exceptionalIncoming(abrupt, incoming))
                val resumed = if (rescue.fallsThrough) {
                    rescue.copy(
                        state = VariableState.Unknown,
                        fallsThrough = false,
                        abruptExits = rescue.abruptExits + VariableExit(kind, rescue.state),
                    )
                } else {
                    rescue
                }
                return mergeFlows(
                    listOf(abrupt.copy(exceptionalStates = emptyList()), resumed),
                )
            }
            val condition = postfix?.let { flowElement(it.conditionElement(), name, incoming) }
            val base = condition?.state ?: incoming
            if (condition != null && !condition.fallsThrough) return condition
            val abrupt = flowAbruptValues(abruptStatement, name, base, kind)
            if (condition == null) return abrupt
            return VariableFlow(
                base,
                fallsThrough = true,
                exceptionalStates = condition.exceptionalStates + abrupt.exceptionalStates,
                abruptExits = condition.abruptExits + abrupt.abruptExits,
            )
        }
        val indexedAssignment = (element as? CrystalStatement)?.indexedAssignment
            ?: element as? CrystalIndexedAssignment
        if (indexedAssignment != null) {
            return flowIndexedAssignment(indexedAssignment, name, incoming)
        }
        val property = (element as? CrystalStatement)?.propertyDeclaration
            ?: element as? CrystalPropertyDeclaration
        if (property != null) {
            val propertyName = property.instanceVarAccess?.text ?: property.classVarAccess?.text
            if (propertyName == name) return VariableFlow.falling(
                VariableState.Bound(parseTypeSet(property.typeReference.text), CrystalVariableProvenance.ANNOTATION)
            )
        }
        val assignment = when (element) {
            is CrystalAssignment -> element
            is CrystalStatement -> element.assignment
            else -> null
        }
        if (assignment != null) {
            val postfix = assignment.postfixModifier
            fun flowAssignmentBody(base: VariableState): VariableFlow {
                var currentAssignment: CrystalAssignment? = assignment
                while (currentAssignment != null) {
                    if (assignmentName(currentAssignment) == name) {
                        val rhs = currentAssignment.assignment ?: currentAssignment.expression
                            ?: return VariableFlow.falling(VariableState.Unknown)
                        val rhsFlow = flowElement(rhs, name, base)
                        val assigned = VariableState.Bound(resolve(rhs), CrystalVariableProvenance.ASSIGNMENT)
                        val exceptional = rhsFlow.exceptionalStates +
                            if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                        if (!rhsFlow.fallsThrough) return rhsFlow
                        return VariableFlow(assigned, true, exceptional, rhsFlow.abruptExits)
                    }
                    currentAssignment = currentAssignment.assignment
                }
                return flowElement(
                    assignment.assignment ?: assignment.expression ?: return VariableFlow.falling(base),
                    name,
                    base,
                )
            }
            if (postfix == null) return flowAssignmentBody(incoming)
            if (postfix.node.findChildByType(CrystalTypes.RESCUE) != null) {
                val body = flowAssignmentBody(incoming)
                if (body.exceptionalStates.isEmpty()) return body
                val rescue = flowElement(postfix.conditionElement(), name, exceptionalIncoming(body, incoming))
                return mergeFlows(listOf(body.copy(exceptionalStates = emptyList()), rescue))
            }
            val condition = flowElement(postfix.conditionElement(), name, incoming)
            if (!condition.fallsThrough) return condition
            val body = flowAssignmentBody(condition.state)
            return VariableFlow(
                if (body.fallsThrough) mergeStates(listOf(condition.state, body.state)) else condition.state,
                fallsThrough = true,
                exceptionalStates = condition.exceptionalStates + body.exceptionalStates,
                abruptExits = condition.abruptExits + body.abruptExits,
            )
        }
        val ifStatement = (element as? CrystalStatement)?.ifStatement ?: element as? CrystalIfStatement
        if (ifStatement != null) {
            val branches = listOf(ifStatement.statementList) + ifStatement.elsifClauseList.map { it.statementList }
            val branchFlows = branches.map { flowStatementList(it, name, incoming) }
            return ifStatement.elseClause?.statementList?.let {
                mergeFlows(branchFlows + flowStatementList(it, name, incoming))
            } ?: mergeFlows(listOf(VariableFlow.falling(incoming)) + branchFlows)
        }
        val unlessStatement = (element as? CrystalStatement)?.unlessStatement ?: element as? CrystalUnlessStatement
        if (unlessStatement != null) {
            val bodyFlow = flowStatementList(unlessStatement.statementList, name, incoming)
            return unlessStatement.elseClause?.statementList?.let {
                mergeFlows(listOf(bodyFlow, flowStatementList(it, name, incoming)))
            } ?: mergeFlows(listOf(VariableFlow.falling(incoming), bodyFlow))
        }
        val begin = (element as? CrystalStatement)?.beginStatement ?: element as? CrystalBeginStatement
        if (begin != null) return flowProtectedBody(begin, name, incoming)
        val loopBody = when (element) {
            is CrystalStatement -> element.whileStatement?.statementList
                ?: element.untilStatement?.statementList
                ?: element.forStatement?.statementList
            is CrystalWhileStatement -> element.statementList
            is CrystalUntilStatement -> element.statementList
            is CrystalForStatement -> element.statementList
            else -> null
        }
        if (loopBody != null) {
            val observed = mutableListOf(incoming)
            val bodyFlow = flowStatementList(loopBody, name, incoming, observed)
            observed.addAll(bodyFlow.abruptExits.filter { it.kind == AbruptKind.BREAK }.map { it.state })
            observed.addAll(bodyFlow.abruptExits.filter { it.kind == AbruptKind.NEXT }.map { it.state })
            return VariableFlow(
                mergeStates(observed),
                fallsThrough = true,
                exceptionalStates = bodyFlow.exceptionalStates,
                abruptExits = bodyFlow.abruptExits.filter { it.kind == AbruptKind.RETURN },
            )
        }
        val expressionStatement = (element as? CrystalStatement)?.expressionStatement
            ?: element as? CrystalExpressionStatement
        if (expressionStatement != null) {
            var flow = VariableFlow.falling(incoming)
            for (expression in expressionStatement.expressionList) {
                if (!flow.fallsThrough) break
                val next = flowExpression(expression, name, flow.state)
                flow = next.copy(
                    exceptionalStates = flow.exceptionalStates + next.exceptionalStates,
                    abruptExits = flow.abruptExits + next.abruptExits,
                )
            }
            return flow
        }
        val expression = element as? CrystalExpression
        val caseStatement = expression?.let { PsiTreeUtil.findChildOfType(it, CrystalCaseStatement::class.java) }
        if (caseStatement != null) {
            val lists = significantChildren(caseStatement).mapNotNull {
                when (it) {
                    is CrystalWhenClause -> it.statementList
                    is CrystalInClause -> it.statementList
                    else -> null
                }
            }
            return mergeFlows(lists.map { flowStatementList(it, name, incoming) } +
                (caseStatement.elseClause?.statementList?.let { listOf(flowStatementList(it, name, incoming)) }
                    ?: listOf(VariableFlow.falling(incoming))))
        }
        if (element is CrystalBlock &&
            (containsAssignment(element, name) || containsExpressionAbrupt(element))
        ) {
            val body = element.statementList
            return mergeFlows(listOf(VariableFlow.falling(incoming), flowStatementList(body, name, incoming)))
        }
        if (element is CrystalExpression || element is CrystalGroupedExpression || element is CrystalBareArgument) {
            return flowExpression(element, name, incoming)
        }
        if (containsAssignment(element, name) || containsExpressionAbrupt(element)) {
            return flowNestedEvaluation(element, name, incoming)
        }
        return VariableFlow(incoming, true, if (mayRaise(element)) listOf(incoming) else emptyList())
    }

    private fun flowAbruptValues(
        statement: CrystalAbruptStatement,
        name: String,
        incoming: VariableState,
        kind: AbruptKind,
    ): VariableFlow {
        val bindings = statement.heredocBodyBindings()
        bindings.forEach { (header, body) -> activeHeredocBodies[header] = body }
        try {
            var state = incoming
            val exceptions = mutableListOf<VariableState>()
            val exits = mutableListOf<VariableExit>()
            for (value in statement.valueElements()) {
                val flow = flowElement(value, name, state)
                exceptions.addAll(flow.exceptionalStates)
                exits.addAll(flow.abruptExits)
                state = flow.state
                if (!flow.fallsThrough) return flow.copy(
                    exceptionalStates = exceptions,
                    abruptExits = exits,
                )
            }
            exits.add(VariableExit(kind, state))
            return VariableFlow(
                VariableState.Unknown,
                fallsThrough = false,
                exceptionalStates = exceptions,
                abruptExits = exits,
            )
        } finally {
            bindings.forEach { (header) -> activeHeredocBodies.remove(header) }
        }
    }

    private fun flowIndexedAssignment(
        assignment: CrystalIndexedAssignment,
        name: String,
        incoming: VariableState,
    ): VariableFlow {
        val postfix = assignment.postfixModifier
        val evaluation = assignment.evaluationComponents()
        fun flowAssignment(base: VariableState): VariableFlow {
            val rhs = evaluation.rhs
            var target = VariableFlow.falling(base)
            for (child in evaluation.targetElements) {
                if (!target.fallsThrough) break
                val next = flowElement(child, name, target.state)
                target = VariableFlow(
                    next.state,
                    next.fallsThrough,
                    target.exceptionalStates + next.exceptionalStates,
                    target.abruptExits + next.abruptExits,
                )
            }
            if (!target.fallsThrough) return target
            val getterExceptions = target.exceptionalStates +
                if (evaluation.compound) listOf(target.state) else emptyList()
            if (rhs == null) return target.copy(exceptionalStates = getterExceptions)
            val value = flowElement(rhs, name, target.state)
            val normal = if (evaluation.shortCircuit) {
                if (value.fallsThrough) mergeStates(listOf(target.state, value.state)) else target.state
            } else {
                value.state
            }
            val setterExceptions = if (value.fallsThrough) listOf(value.state) else emptyList()
            return VariableFlow(
                normal,
                value.fallsThrough || evaluation.shortCircuit,
                getterExceptions + value.exceptionalStates + setterExceptions,
                target.abruptExits + value.abruptExits,
            )
        }
        if (postfix?.node?.findChildByType(CrystalTypes.RESCUE) != null) {
            val body = flowAssignment(incoming)
            if (body.exceptionalStates.isEmpty()) return body
            val rescue = flowElement(postfix.conditionElement(), name, exceptionalIncoming(body, incoming))
            return mergeFlows(listOf(body.copy(exceptionalStates = emptyList()), rescue))
        }
        val condition = postfix?.let { flowElement(it.conditionElement(), name, incoming) }
        val base = condition?.state ?: incoming
        if (condition != null && !condition.fallsThrough) return condition
        val flow = flowAssignment(base)
        if (postfix == null) return flow
        return VariableFlow(
            if (flow.fallsThrough) mergeStates(listOf(base, flow.state)) else base,
            fallsThrough = true,
            exceptionalStates = condition?.exceptionalStates.orEmpty() + flow.exceptionalStates,
            abruptExits = condition?.abruptExits.orEmpty() + flow.abruptExits,
        )
    }

    private fun flowStatementList(
        statementList: CrystalStatementList?,
        name: String,
        incoming: VariableState,
        observed: MutableList<VariableState>? = null
    ): VariableFlow {
        var state = incoming
        var fallsThrough = true
        val exceptional = mutableListOf<VariableState>()
        val exits = mutableListOf<VariableExit>()
        for (statement in statementList?.statementList.orEmpty()) {
            if (!fallsThrough) break
            val flow = flowElement(statement, name, state)
            exceptional.addAll(flow.exceptionalStates)
            exits.addAll(flow.abruptExits)
            fallsThrough = flow.fallsThrough
            state = flow.state
            if (fallsThrough && state != incoming) observed?.add(state)
        }
        return VariableFlow(state, fallsThrough, exceptional, exits)
    }

    private fun flowProtectedBody(
        statement: CrystalBeginStatement,
        name: String,
        incoming: VariableState
    ): VariableFlow {
        val normal = flowStatementList(statement.statementList, name, incoming)
        val normalResult = if (normal.fallsThrough && statement.elseClause != null) {
            val elseFlow = flowStatementList(statement.elseClause!!.statementList, name, normal.state)
            elseFlow.copy(
                exceptionalStates = normal.exceptionalStates + elseFlow.exceptionalStates,
                abruptExits = normal.abruptExits + elseFlow.abruptExits,
            )
        } else {
            normal
        }
        val rescueIncoming = exceptionalIncoming(normal, incoming)
        val rescues = statement.rescueClauseList.map { flowStatementList(it.statementList, name, rescueIncoming) }
        val protected = mergeFlows(rescues + normalResult)
        val ensureBody = statement.ensureClause?.statementList ?: return protected
        return applyEnsure(protected, ensureBody, name)
    }

    private fun applyEnsure(
        flow: VariableFlow,
        ensureBody: CrystalStatementList,
        name: String,
    ): VariableFlow {
        val normal = if (flow.fallsThrough) flowStatementList(ensureBody, name, flow.state) else null
        val exits = mutableListOf<VariableExit>()
        val exceptional = mutableListOf<VariableState>()

        for (exit in flow.abruptExits) {
            val ensured = flowStatementList(ensureBody, name, exit.state)
            exceptional.addAll(ensured.exceptionalStates)
            exits.addAll(ensured.abruptExits)
            if (ensured.fallsThrough) exits.add(VariableExit(exit.kind, ensured.state))
        }
        for (exceptionalState in flow.exceptionalStates) {
            val ensured = flowStatementList(ensureBody, name, exceptionalState)
            exceptional.addAll(ensured.exceptionalStates)
            exits.addAll(ensured.abruptExits)
            if (ensured.fallsThrough) exceptional.add(ensured.state)
        }
        normal?.let {
            exceptional.addAll(it.exceptionalStates)
            exits.addAll(it.abruptExits)
        }
        return VariableFlow(
            normal?.state ?: VariableState.Unknown,
            normal?.fallsThrough == true,
            exceptional,
            exits,
        )
    }

    private fun flowExpression(element: PsiElement, name: String, incoming: VariableState): VariableFlow {
        val caseStatement = PsiTreeUtil.findChildOfType(element, CrystalCaseStatement::class.java)
        if (caseStatement != null) {
            val branches = significantChildren(caseStatement).mapNotNull {
                when (it) {
                    is CrystalWhenClause -> flowStatementList(it.statementList, name, incoming)
                    is CrystalInClause -> flowStatementList(it.statementList, name, incoming)
                    else -> null
                }
            }
            return mergeFlows(branches +
                (caseStatement.elseClause?.statementList?.let { listOf(flowStatementList(it, name, incoming)) }
                    ?: listOf(VariableFlow.falling(incoming))))
        }
        val children = significantChildren(element)
        val firstType = children.firstOrNull()?.node?.elementType
        if (firstType == CrystalTypes.RETURN || firstType == CrystalTypes.BREAK ||
            firstType == CrystalTypes.NEXT
        ) return flowExpressionSegment(children, name, incoming)
        val question = children.indexOfFirst { it.node.elementType == CrystalTypes.QUESTION }
        if (question >= 0) {
            val colon = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
            if (colon <= question) return VariableFlow.falling(VariableState.Unknown)
            val condition = flowExpressionParts(children.subList(0, question), name, incoming)
            if (!condition.fallsThrough) return condition
            val branches = listOf(
                flowExpressionSegment(children.subList(question + 1, colon), name, condition.state),
                flowExpressionSegment(children.subList(colon + 1, children.size), name, condition.state)
            )
            return mergeFlows(branches.sortedBy { it.state != condition.state }).withPriorEffects(condition)
        }
        val logical = children.indexOfFirst {
            it.node.elementType == CrystalTypes.AND_AND || it.node.elementType == CrystalTypes.OR_OR
        }
        if (logical >= 0) return flowLogicalExpression(children, name, incoming)
        val assignment = PsiTreeUtil.getChildOfType(element, CrystalAssignment::class.java)
        if (assignment != null) return flowElement(assignment, name, incoming)
        val assignIndex = children.indexOfFirst { it.node.elementType == CrystalTypes.ASSIGN }
        if (assignIndex > 0 && assignIndex < children.lastIndex) {
            val assignedName = children.take(assignIndex).lastOrNull()?.text
            if (assignedName == name) {
                val rhs = children[assignIndex + 1]
                val rhsFlow = flowElement(rhs, name, incoming)
                if (!rhsFlow.fallsThrough) return rhsFlow
                val exceptional = rhsFlow.exceptionalStates +
                    if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                return VariableFlow(
                    VariableState.Bound(resolve(rhs), CrystalVariableProvenance.ASSIGNMENT),
                    true,
                    exceptional,
                    rhsFlow.abruptExits,
                )
            }
        }
        if (element is CrystalGroupedExpression && element.node.findChildByType(CrystalTypes.ASSIGN) != null) {
            val expressions = element.expressionList
            if (expressions.size >= 2 && expressions.first().text == name) {
                val rhs = expressions.last()
                val rhsFlow = flowElement(rhs, name, incoming)
                if (!rhsFlow.fallsThrough) return rhsFlow
                val exceptional = rhsFlow.exceptionalStates +
                    if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                return VariableFlow(
                    VariableState.Bound(resolve(rhs), CrystalVariableProvenance.ASSIGNMENT),
                    true,
                    exceptional,
                    rhsFlow.abruptExits,
                )
            }
        }
        var state = incoming
        val exceptional = mutableListOf<VariableState>()
        val exits = mutableListOf<VariableExit>()
        var sawAssignment = false
        for (child in children) {
            if (child is CrystalExpression || child is CrystalGroupedExpression ||
                containsAssignment(child, name) || containsActiveHeredoc(child) ||
                containsExpressionAbrupt(child)
            ) {
                val next = flowElement(child, name, state)
                exceptional.addAll(next.exceptionalStates)
                exits.addAll(next.abruptExits)
                if (!next.fallsThrough) return next.copy(
                    exceptionalStates = exceptional,
                    abruptExits = exits,
                )
                if (next.state != state) sawAssignment = true
                state = next.state
            }
        }
        if (isPotentiallyRaisingOperation(element)) {
            exceptional.add(state)
        } else if (!sawAssignment && mayRaise(element)) {
            exceptional.add(incoming)
        }
        return VariableFlow(if (sawAssignment) state else incoming, true, exceptional, exits)
    }

    private fun flowExpressionParts(
        elements: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): VariableFlow = if (elements.any {
            it.node.elementType == CrystalTypes.AND_AND || it.node.elementType == CrystalTypes.OR_OR
        }) {
        flowLogicalExpression(elements, name, incoming)
    } else {
        flowExpressionSegment(elements, name, incoming)
    }

    private fun flowLogicalExpression(
        children: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): VariableFlow = flowOrExpression(children, name, incoming).flow

    private fun flowOrExpression(
        children: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): LogicalVariableFlow {
        val groups = splitLogicalSegments(children, CrystalTypes.OR_OR)
        var result = flowAndExpression(groups.first(), name, incoming)
        for (group in groups.drop(1)) {
            if (!result.flow.fallsThrough) return result
            result = combineLogical(
                result,
                flowAndExpression(group, name, result.flow.state),
                andOperator = false,
            )
        }
        return result
    }

    private fun flowAndExpression(
        children: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): LogicalVariableFlow {
        val segments = splitLogicalSegments(children, CrystalTypes.AND_AND)
        var result = flowLogicalOperand(segments.first(), name, incoming)
        for (segment in segments.drop(1)) {
            if (!result.flow.fallsThrough) return result
            result = combineLogical(
                result,
                flowLogicalOperand(segment, name, result.flow.state),
                andOperator = true,
            )
        }
        return result
    }

    private fun splitLogicalSegments(
        children: List<PsiElement>,
        operator: com.intellij.psi.tree.IElementType,
    ): List<List<PsiElement>> {
        val segments = mutableListOf<MutableList<PsiElement>>(mutableListOf())
        for (child in children) {
            if (child.node.elementType == operator) {
                segments.add(mutableListOf())
            } else {
                segments.last().add(child)
            }
        }
        return segments
    }

    private fun flowLogicalOperand(
        elements: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): LogicalVariableFlow {
        val flow = flowExpressionSegment(elements, name, incoming)
        val valueTruthiness = elements.singleOrNull()?.let(::truthiness) ?: Truthiness.MIXED
        return LogicalVariableFlow(flow, valueTruthiness)
    }

    private fun combineLogical(
        left: LogicalVariableFlow,
        right: LogicalVariableFlow,
        andOperator: Boolean,
    ): LogicalVariableFlow {
        val continuedRight = right.flow.withPriorEffects(left.flow)
        return when (left.truthiness) {
            Truthiness.ALWAYS_TRUTHY -> if (andOperator) {
                right.copy(flow = continuedRight)
            } else {
                left
            }
            Truthiness.ALWAYS_FALSY -> if (andOperator) {
                left
            } else {
                right.copy(flow = continuedRight)
            }
            Truthiness.MIXED -> {
                val flow = mergeFlows(listOf(left.flow, continuedRight))
                val skippedTruthiness = if (andOperator) Truthiness.ALWAYS_FALSY else Truthiness.ALWAYS_TRUTHY
                val resultTruthiness = if (right.flow.fallsThrough) {
                    mergeTruthiness(skippedTruthiness, right.truthiness)
                } else {
                    skippedTruthiness
                }
                LogicalVariableFlow(flow, resultTruthiness)
            }
        }
    }

    private fun mergeTruthiness(left: Truthiness, right: Truthiness): Truthiness =
        if (left == right) left else Truthiness.MIXED

    private fun flowExpressionSegment(
        elements: List<PsiElement>,
        name: String,
        incoming: VariableState,
    ): VariableFlow {
        val firstType = elements.firstOrNull()?.node?.elementType
        if (firstType == CrystalTypes.RETURN || firstType == CrystalTypes.BREAK || firstType == CrystalTypes.NEXT) {
            val kind = when (firstType) {
                CrystalTypes.RETURN -> AbruptKind.RETURN
                CrystalTypes.BREAK -> AbruptKind.BREAK
                else -> AbruptKind.NEXT
            }
            var state = incoming
            val exceptional = mutableListOf<VariableState>()
            val exits = mutableListOf<VariableExit>()
            for (value in elements.drop(1)) {
                if (value !is CrystalAssignment && value !is CrystalExpression) continue
                val flow = flowElement(value, name, state)
                exceptional.addAll(flow.exceptionalStates)
                exits.addAll(flow.abruptExits)
                if (!flow.fallsThrough) return flow.copy(
                    exceptionalStates = exceptional,
                    abruptExits = exits,
                )
                state = flow.state
            }
            exits.add(VariableExit(kind, state))
            return VariableFlow(VariableState.Unknown, false, exceptional, exits)
        }

        var state = incoming
        val exceptional = mutableListOf<VariableState>()
        val exits = mutableListOf<VariableExit>()
        for (element in elements) {
            if (element !is CrystalAssignment && element !is CrystalExpression &&
                element !is CrystalGroupedExpression && !containsAssignment(element, name) &&
                !containsActiveHeredoc(element) && !containsExpressionAbrupt(element)
            ) continue
            val flow = flowElement(element, name, state)
            exceptional.addAll(flow.exceptionalStates)
            exits.addAll(flow.abruptExits)
            if (!flow.fallsThrough) return flow.copy(
                exceptionalStates = exceptional,
                abruptExits = exits,
            )
            state = flow.state
        }
        return VariableFlow(state, true, exceptional, exits)
    }

    private fun flowNestedEvaluation(
        element: PsiElement,
        name: String,
        incoming: VariableState
    ): VariableFlow {
        if (element is CrystalBlock) return flowElement(element, name, incoming)
        if (element is CrystalExpression || element is CrystalGroupedExpression || element is CrystalBareArgument) {
            return flowExpression(element, name, incoming)
        }
        var state = incoming
        val exceptional = mutableListOf<VariableState>()
        val exits = mutableListOf<VariableExit>()
        for (child in significantChildren(element)) {
            val relevant = containsAssignment(child, name) || containsActiveHeredoc(child) ||
                containsExpressionAbrupt(child)
            if (!relevant) {
                if (mayRaise(child)) exceptional.add(state)
                continue
            }
            val next = if (child is CrystalAssignment) {
                flowElement(child, name, state)
            } else {
                flowNestedEvaluation(child, name, state)
            }
            exceptional.addAll(next.exceptionalStates)
            exits.addAll(next.abruptExits)
            if (!next.fallsThrough) return next.copy(
                exceptionalStates = exceptional,
                abruptExits = exits,
            )
            state = next.state
        }
        if (isPotentiallyRaisingOperation(element)) exceptional.add(state)
        return VariableFlow(state, true, exceptional, exits)
    }

    private fun containsActiveHeredoc(element: PsiElement): Boolean =
        activeHeredocBodies.keys.any { marker -> element === marker || PsiTreeUtil.isAncestor(element, marker, true) }

    private fun containsExpressionAbrupt(element: PsiElement): Boolean {
        fun contains(current: PsiElement): Boolean {
            if (current !== element &&
                (isScopeBoundary(current) || current is CrystalProcLiteral || current is CrystalBlock)
            ) return false
            return current.node.getChildren(null).any { child ->
                child.elementType == CrystalTypes.RETURN || child.elementType == CrystalTypes.BREAK ||
                    child.elementType == CrystalTypes.NEXT || contains(child.psi)
            }
        }
        return contains(element)
    }

    private fun mergeStates(states: List<VariableState>): VariableState {
        if (states.all { it is VariableState.Unbound }) return VariableState.Unbound
        if (states.any { it is VariableState.Unbound || it is VariableState.Unknown }) return VariableState.Unknown
        val bound = states.filterIsInstance<VariableState.Bound>()
        val provenance = bound.map { it.provenance }.distinct().singleOrNull() ?: CrystalVariableProvenance.MIXED
        return VariableState.Bound(mergeKnown(bound.map { it.value }), provenance)
    }

    private fun mergeFlows(flows: List<VariableFlow>): VariableFlow {
        val falling = flows.filter { it.fallsThrough }
        return VariableFlow(
            if (falling.isEmpty()) VariableState.Unknown else mergeStates(falling.map { it.state }),
            falling.isNotEmpty(),
            flows.flatMap { it.exceptionalStates },
            flows.flatMap { it.abruptExits },
        )
    }

    private fun exceptionalIncoming(flow: VariableFlow, fallback: VariableState): VariableState =
        flow.exceptionalStates.takeIf { it.isNotEmpty() }?.let(::mergeStates) ?: fallback

    private fun mayRaise(element: PsiElement): Boolean {
        if (element.node.elementType in setOf(
                CrystalTypes.INTEGER_LITERAL,
                CrystalTypes.FLOAT_LITERAL,
                CrystalTypes.STRING_LITERAL,
                CrystalTypes.CHAR_LITERAL,
                CrystalTypes.SYMBOL_LITERAL,
                CrystalTypes.TRUE,
                CrystalTypes.FALSE,
                CrystalTypes.NIL
            )) return false
        if (element is CrystalStringExpression || element is CrystalSymbolStringExpression ||
            element is CrystalHeredocLiteral) return false
        if (element is CrystalArrayLiteral) return element.expressionList?.expressionList.orEmpty().any(::mayRaise)
        if (element is CrystalTupleLiteral) return element.expressionList?.expressionList.orEmpty().any(::mayRaise)
        if (element is CrystalHashLiteral) return element.hashEntryList?.hashEntryList.orEmpty()
            .flatMap { it.expressionList }.any(::mayRaise)
        if (element is CrystalMethodCallExpression || element is CrystalBareMethodCallExpression ||
            element is CrystalDotCallAccess || element is CrystalCommandExpression) return true
        if (PsiTreeUtil.findChildOfType(element, CrystalMethodCallExpression::class.java) != null ||
            PsiTreeUtil.findChildOfType(element, CrystalBareMethodCallExpression::class.java) != null ||
            PsiTreeUtil.findChildOfType(element, CrystalDotCallAccess::class.java) != null) return true
        val variable = element as? CrystalVariableReference
            ?: PsiTreeUtil.findChildOfType(element, CrystalVariableReference::class.java)
        if (variable != null) return resolveVariableValue(variable.text, variable) !is VariableState.Bound
        val children = significantChildren(element)
        if (children.size == 1) return mayRaise(children.single())
        return children.any(::mayRaise)
    }

    private fun isPotentiallyRaisingOperation(element: PsiElement): Boolean =
        element is CrystalMethodCallExpression || element is CrystalBareMethodCallExpression ||
            element is CrystalDotCallAccess || element is CrystalCommandExpression

    private fun containsAssignment(element: PsiElement, name: String): Boolean {
        if (isScopeBoundary(element) && element !is CrystalBlock) return false
        if (element is CrystalAssignment && assignmentName(element) == name) return true
        val children = significantChildren(element)
        val assignIndex = children.indexOfFirst { it.node.elementType == CrystalTypes.ASSIGN }
        if (assignIndex > 0 && children.take(assignIndex).lastOrNull()?.text == name) return true
        return element.children.any { containsAssignment(it, name) }
    }

    private fun assignmentName(assignment: CrystalAssignment): String? =
        (assignment as? PsiNameIdentifierOwner)?.name
            ?: assignment.instanceVarAccess?.text
            ?: assignment.classVarAccess?.text

    private fun resolveUnqualifiedCall(name: String?, call: PsiElement): CrystalTypeResolution {
        name ?: return CrystalTypeResolution.Unknown
        val enclosingMethod = PsiTreeUtil.getParentOfType(call, CrystalMethodDefinition::class.java)
        val enclosingType = CrystalPsiUtils.getEnclosingType(call)
        if (enclosingType != null) {
            val identity = CrystalPsiUtils.buildQualifiedName(enclosingType)
                ?.let { TypeIdentity(it.substringAfterLast("::"), it) }
            if (identity != null) {
                val collection = hierarchy.collectNamedMethods(
                    identity.toShared(),
                    if (enclosingMethod?.let(CrystalPsiUtils::isSelfMethod) == true) {
                        CrystalReceiverMode.STATIC
                    } else {
                        CrystalReceiverMode.INSTANCE
                    },
                    name
                )
                if (!collection.complete) return CrystalTypeResolution.Unknown
                val candidates = collection.methods
                if (candidates.size > 1) return CrystalTypeResolution.Unknown
                if (candidates.size == 1) return resolveMethodReturn(candidates.single())
            }
        }
        val topLevel = methods(name).filter {
            CrystalPsiUtils.getEnclosingType(it) == null && !CrystalPsiUtils.isSelfMethod(it)
        }
        return if (topLevel.size == 1) resolveMethodReturn(topLevel.single()) else CrystalTypeResolution.Unknown
    }

    fun resolveMethodReturn(method: CrystalMethodDefinition): CrystalTypeResolution {
        method.typeReference?.text?.let { return parseTypeSet(it) }
        methodReturnMemo[method]?.let { return it }
        if (!resolvingMethods.add(method)) return CrystalTypeResolution.Unknown
        val body = method.methodBody
        if (body == null) {
            resolvingMethods.remove(method)
            return CrystalTypeResolution.Unknown
        }
        val execution = analyzeProtectedBody(
            body.statementList,
            body.rescueClauseList,
            body.elseClause,
            body.ensureClause
        )
        val results = execution.returns + if (execution.fallsThrough) listOfNotNull(execution.value) else emptyList()
        val result = mergeKnown(results)
        resolvingMethods.remove(method)
        methodReturnMemo[method] = result
        return result
    }

    private fun analyzeStatementList(statementList: CrystalStatementList?): ExecutionResult {
        val returns = mutableListOf<CrystalTypeResolution>()
        var fallsThrough = true
        var value: CrystalTypeResolution? = knownType("Nil")
        for (statement in statementList?.statementList.orEmpty()) {
            if (!fallsThrough) break
            val execution = analyzeStatement(statement)
            returns.addAll(execution.returns)
            fallsThrough = execution.fallsThrough
            value = if (fallsThrough) execution.value else null
        }
        return ExecutionResult(returns, value, fallsThrough)
    }

    private fun analyzeStatement(statement: CrystalStatement): ExecutionResult {
        statement.returnStatement?.let { returnStatement ->
            val result = resolveAbruptValues(returnStatement.valueElements())
            val postfix = returnStatement.postfixModifier
            return if (postfix == null) {
                ExecutionResult(listOf(result), null, false)
            } else if (postfix.node.findChildByType(CrystalTypes.RESCUE) != null &&
                returnStatement.valueElements().any(::mayRaise)
            ) {
                ExecutionResult(listOf(result, resolve(postfix.conditionElement())), null, false)
            } else if (postfix.node.findChildByType(CrystalTypes.RESCUE) != null) {
                ExecutionResult(listOf(result), null, false)
            } else {
                ExecutionResult(listOf(result), knownType("Nil"), true)
            }
        }
        statement.ifStatement?.let { return analyzeIf(it) }
        statement.unlessStatement?.let { return analyzeUnless(it) }
        statement.beginStatement?.let { begin ->
            return analyzeProtectedBody(begin.statementList, begin.rescueClauseList, begin.elseClause, begin.ensureClause)
        }
        val case = statement.expressionStatement?.expressionList?.firstOrNull()?.let {
            PsiTreeUtil.findChildOfType(it, CrystalCaseStatement::class.java)
        }
        if (case != null) return analyzeCase(case)
        return ExecutionResult(emptyList(), resolve(statement), true)
    }

    private fun analyzeIf(statement: CrystalIfStatement): ExecutionResult = mergeExecutions(
        listOf(analyzeStatementList(statement.statementList)) +
            statement.elsifClauseList.map { analyzeStatementList(it.statementList) } +
            (statement.elseClause?.statementList?.let { listOf(analyzeStatementList(it)) }
                ?: listOf(ExecutionResult(emptyList(), knownType("Nil"), true)))
    )

    private fun analyzeUnless(statement: CrystalUnlessStatement): ExecutionResult = mergeExecutions(
        listOf(analyzeStatementList(statement.statementList)) +
            (statement.elseClause?.statementList?.let { listOf(analyzeStatementList(it)) }
                ?: listOf(ExecutionResult(emptyList(), knownType("Nil"), true)))
    )

    private fun analyzeCase(statement: CrystalCaseStatement): ExecutionResult {
        val branches = significantChildren(statement).mapNotNull {
            when (it) {
                is CrystalWhenClause -> analyzeStatementList(it.statementList)
                is CrystalInClause -> analyzeStatementList(it.statementList)
                else -> null
            }
        }
        return mergeExecutions(
            branches + (statement.elseClause?.statementList?.let { listOf(analyzeStatementList(it)) }
                ?: listOf(ExecutionResult(emptyList(), knownType("Nil"), true)))
        )
    }

    private fun analyzeProtectedBody(
        body: CrystalStatementList?,
        rescues: List<CrystalRescueClause>,
        elseClause: CrystalElseClause?,
        ensureClause: CrystalEnsureClause?
    ): ExecutionResult {
        val normal = analyzeStatementList(body)
        val rescueResults = rescues.map { analyzeStatementList(it.statementList) }
        val elseResult = if (elseClause != null && normal.fallsThrough) {
            analyzeStatementList(elseClause.statementList)
        } else {
            null
        }
        val fallingValues = buildList {
            if (elseResult == null && normal.fallsThrough) add(normal.value ?: CrystalTypeResolution.Unknown)
            rescueResults.filter { it.fallsThrough }.forEach { add(it.value ?: CrystalTypeResolution.Unknown) }
            if (elseResult?.fallsThrough == true) add(elseResult.value ?: CrystalTypeResolution.Unknown)
        }
        val protected = ExecutionResult(
            normal.returns + rescueResults.flatMap { it.returns } + elseResult?.returns.orEmpty(),
            fallingValues.takeIf { it.isNotEmpty() }?.let(::mergeKnown),
            fallingValues.isNotEmpty()
        )
        val ensure = ensureClause?.statementList?.let(::analyzeStatementList) ?: return protected
        return if (ensure.fallsThrough) {
            ExecutionResult(protected.returns + ensure.returns, protected.value, protected.fallsThrough)
        } else {
            ExecutionResult(ensure.returns, null, false)
        }
    }

    private fun mergeExecutions(executions: List<ExecutionResult>): ExecutionResult {
        val falling = executions.filter { it.fallsThrough }
        val value = if (falling.isEmpty()) null else mergeKnown(falling.map {
            it.value ?: CrystalTypeResolution.Unknown
        })
        return ExecutionResult(executions.flatMap { it.returns }, value, falling.isNotEmpty())
    }

    private fun resolveTypeIdentity(typeName: String, element: PsiElement): TypeIdentity? {
        val normalized = typeName.substringBefore('(').removePrefix("::").trim()
        val simpleName = normalized.substringAfterLast("::")
        if (simpleName.isEmpty()) return null
        for (candidate in typeIdentityCandidates(normalized, typeName.startsWith("::"), element)) {
            val identities = exactTypeIdentities(simpleName, candidate)
            if (identities.size > 1) return null
            identities.singleOrNull()?.let { return TypeIdentity(it.simpleName, it.qualifiedName) }
        }
        return null
    }

    private fun exactTypeIdentities(simpleName: String, qualifiedName: String): List<CrystalTypeIdentity> =
        types(simpleName).mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { it == qualifiedName }
            .map { CrystalTypeIdentity(simpleName, it) }
            .distinct()

    private fun typeIdentityCandidates(
        normalized: String,
        absolute: Boolean,
        element: PsiElement
    ): List<String> {
        if (absolute || normalized.contains("::")) return listOf(normalized)
        val enclosing = CrystalPsiUtils.getEnclosingType(element)?.let(CrystalPsiUtils::buildQualifiedName)
            ?.split("::").orEmpty()
        return buildList {
            for (size in enclosing.size downTo 1) {
                add(enclosing.take(size).joinToString("::") + "::$normalized")
            }
            add(normalized)
        }
    }

    private fun resolveArray(array: CrystalArrayLiteral): CrystalTypeResolution {
        array.typeReference?.text?.let { return knownType("Array(${it.trim()})") }
        val expressions = array.expressionList?.expressionList.orEmpty()
        if (expressions.isEmpty()) return CrystalTypeResolution.Unknown
        val elementTypes = mergeKnown(expressions.map(::resolve)) as? CrystalTypeResolution.Known
            ?: return CrystalTypeResolution.Unknown
        return knownType("Array(${elementTypes.types.joinToString(" | ") { it.name }})")
    }

    private fun resolveHash(hash: CrystalHashLiteral): CrystalTypeResolution {
        val entries = hash.hashEntryList?.hashEntryList.orEmpty()
        if (entries.isEmpty()) return CrystalTypeResolution.Unknown

        // `{a: 1, b: "x"}` (colon syntax, identifier keys) is a NAMED TUPLE literal;
        // only `=>` builds a Hash. Mixed forms are not valid crystal.
        val allColonNamed = entries.all { entry ->
            entry.node.findChildByType(CrystalTypes.COLON) != null &&
                entry.node.findChildByType(CrystalTypes.DOUBLE_ARROW) == null
        }
        if (allColonNamed && entries.isNotEmpty()) {
            var allIdentifiers = true
            val parts = mutableListOf<String>()
            for (entry in entries) {
                val exprs = entry.expressionList
                val nameExpr = exprs.getOrNull(0)
                val nameText = nameExpr?.text?.trim().orEmpty()
                if (!Regex("[a-z_][A-Za-z0-9_]*").matches(nameText)) {
                    allIdentifiers = false
                    break
                }
                val valueType = exprs.lastOrNull()?.let(::resolve)
                    as? CrystalTypeResolution.Known
                    ?: return CrystalTypeResolution.Unknown
                parts.add("$nameText: ${valueType.types.joinToString(" | ") { it.name }}")
            }
            if (allIdentifiers && parts.isNotEmpty()) {
                return knownType("NamedTuple(${parts.joinToString(", ")})")
            }
        }

        val keys = entries.map { entry ->
            if (entry.node.findChildByType(CrystalTypes.COLON) != null) {
                knownType("Symbol")
            } else {
                entry.expressionList.getOrNull(0)?.let(::resolve) ?: CrystalTypeResolution.Unknown
            }
        }
        val values = entries.map { entry ->
            if (entry.node.findChildByType(CrystalTypes.COLON) != null) {
                entry.expressionList.lastOrNull()?.let(::resolve) ?: CrystalTypeResolution.Unknown
            } else {
                entry.expressionList.getOrNull(1)?.let(::resolve) ?: CrystalTypeResolution.Unknown
            }
        }
        val keyTypes = mergeKnown(keys) as? CrystalTypeResolution.Known ?: return CrystalTypeResolution.Unknown
        val valueTypes = mergeKnown(values) as? CrystalTypeResolution.Known ?: return CrystalTypeResolution.Unknown
        return knownType(
            "Hash(${keyTypes.types.joinToString(" | ") { it.name }}, ${valueTypes.types.joinToString(" | ") { it.name }})"
        )
    }

    private fun resolveTuple(tuple: CrystalTupleLiteral): CrystalTypeResolution {
        return resolveTupleValues(tuple.expressionList?.expressionList.orEmpty())
    }

    private fun resolveAbruptValues(values: List<PsiElement>): CrystalTypeResolution = when (values.size) {
        0 -> knownType("Nil")
        1 -> resolve(values.single())
        else -> resolveTupleValues(values)
    }

    private fun resolveTupleValues(values: List<PsiElement>): CrystalTypeResolution {
        val results = values.map(::resolve)
        if (results.any { it is CrystalTypeResolution.Unknown }) return CrystalTypeResolution.Unknown
        val names = results.map { (it as CrystalTypeResolution.Known).types.joinToString(" | ") { type -> type.name } }
        return knownType("Tuple(${names.joinToString(", ")})")
    }

    /**
     * Percent literals are fully typed at compile time, even when empty:
     * %w/%W → Array(String), %i/%I → Array(Symbol), %r → Regex,
     * %x and plain %/%q/%Q → String. The BEGIN token discriminates the
     * array forms; regex vs string content is distinguished by content token.
     */
    private fun resolvePercentLiteral(literal: CrystalPercentLiteral): CrystalTypeResolution {
        val node = literal.node
        return when {
            node.findChildByType(CrystalTypes.PERCENT_WORD_ARRAY_BEGIN) != null -> knownType("Array(String)")
            node.findChildByType(CrystalTypes.PERCENT_SYMBOL_BEGIN) != null -> knownType("Array(Symbol)")
            node.findChildByType(CrystalTypes.REGEX_LITERAL) != null -> knownType("Regex")
            else -> knownType("String")
        }
    }

    private fun resolveOperator(children: List<PsiElement>): CrystalTypeResolution? {
        if (children.size < 3) return null
        return when (children[1].node.elementType) {
            CrystalTypes.EQ, CrystalTypes.NEQ, CrystalTypes.LT, CrystalTypes.LTE,
            CrystalTypes.GT, CrystalTypes.GTE, CrystalTypes.CASE_EQ -> knownType("Bool")
            CrystalTypes.SPACESHIP, CrystalTypes.MATCH_OP,
            CrystalTypes.BANG_TILDE -> CrystalTypeResolution.Unknown
            CrystalTypes.AND_AND -> resolveLogical(children[0], children[2], andOperator = true)
            CrystalTypes.OR_OR -> resolveLogical(children[0], children[2], andOperator = false)
            CrystalTypes.PLUS, CrystalTypes.MINUS, CrystalTypes.STAR, CrystalTypes.SLASH,
            CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT, CrystalTypes.DOUBLE_STAR -> {
                val left = resolve(children[0])
                val right = resolve(children[2])
                val leftKnown = left as? CrystalTypeResolution.Known
                val rightKnown = right as? CrystalTypeResolution.Known
                if (leftKnown?.types?.map { it.name } == rightKnown?.types?.map { it.name }) {
                    mergeKnown(listOf(left, right))
                } else {
                    CrystalTypeResolution.Unknown
                }
            }
            else -> null
        }
    }

    private fun resolveLogical(leftElement: PsiElement, rightElement: PsiElement, andOperator: Boolean): CrystalTypeResolution {
        val left = resolve(leftElement) as? CrystalTypeResolution.Known ?: return CrystalTypeResolution.Unknown
        val right by lazy { resolve(rightElement) }
        return when (truthiness(leftElement)) {
            Truthiness.ALWAYS_TRUTHY -> if (andOperator) right else left
            Truthiness.ALWAYS_FALSY -> if (andOperator) left else right
            Truthiness.MIXED -> {
                val returnedLeft = left.types.filter { type ->
                    if (andOperator) type.name == "Nil" || type.name == "Bool"
                    else type.name != "Nil"
                }
                val rightReachable = left.types.any { it.name == "Bool" || if (andOperator) it.name != "Nil" else it.name == "Nil" }
                mergeKnown(listOfNotNull(
                    returnedLeft.takeIf { it.isNotEmpty() }?.let(CrystalTypeResolution::Known),
                    right.takeIf { rightReachable }
                ))
            }
        }
    }

    private fun truthiness(element: PsiElement): Truthiness {
        val normalized = CrystalReceiverExpression.normalize(element)
        return when (normalized.node.elementType) {
            CrystalTypes.TRUE -> Truthiness.ALWAYS_TRUTHY
            CrystalTypes.FALSE, CrystalTypes.NIL -> Truthiness.ALWAYS_FALSY
            else -> {
                val known = resolve(normalized) as? CrystalTypeResolution.Known ?: return Truthiness.MIXED
                val canBeFalsy = known.types.any { it.name == "Nil" || it.name == "Bool" }
                val canBeTruthy = known.types.any { it.name != "Nil" }
                when {
                    canBeFalsy && canBeTruthy -> Truthiness.MIXED
                    canBeFalsy -> Truthiness.ALWAYS_FALSY
                    else -> Truthiness.ALWAYS_TRUTHY
                }
            }
        }
    }

    private fun resolveInteger(text: String): CrystalTypeResolution {
        val normalized = text.lowercase().replace("_", "")
        val name = when {
            normalized.endsWith("i8") -> "Int8"
            normalized.endsWith("i16") -> "Int16"
            normalized.endsWith("i32") -> "Int32"
            normalized.endsWith("i64") -> "Int64"
            normalized.endsWith("i128") -> "Int128"
            normalized.endsWith("u8") -> "UInt8"
            normalized.endsWith("u16") -> "UInt16"
            normalized.endsWith("u32") -> "UInt32"
            normalized.endsWith("u64") -> "UInt64"
            normalized.endsWith("u128") -> "UInt128"
            else -> "Int32"
        }
        val hasSuffix = INTEGER_SUFFIXES.any(normalized::endsWith)
        return knownType(name, isUnsuffixedNumericLiteral = !hasSuffix)
    }

    private fun resolveFloat(text: String): CrystalTypeResolution {
        val normalized = text.lowercase().replace("_", "")
        val name = if (normalized.endsWith("f32")) "Float32" else "Float64"
        return knownType(name, isUnsuffixedNumericLiteral = !normalized.endsWith("f32") && !normalized.endsWith("f64"))
    }

    private fun firstExpressionChild(element: PsiElement): PsiElement? {
        val children = significantChildren(element)
        val colonIndex = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
        val valueChildren = if (colonIndex > 0) children.drop(colonIndex + 1) else children
        return valueChildren.firstOrNull {
            it.node.elementType !in setOf(CrystalTypes.IDENTIFIER, CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR)
        }
    }

    private fun methodName(element: PsiElement): String? = element.node.getChildren(null).firstOrNull {
        it.elementType == CrystalTypes.IDENTIFIER || it.elementType == CrystalTypes.CONSTANT
    }?.text

    private fun promote(element: PsiElement): PsiElement = when {
        element.parent is CrystalVariableReference || element.parent is CrystalInstanceVarAccess ||
            element.parent is CrystalClassVarAccess -> element.parent
        else -> element
    }

    private fun significantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot {
            it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE
        }

    private fun isScopeBoundary(element: PsiElement): Boolean =
        element is CrystalMethodDefinition || element is CrystalMacroDefinition || isTypeBoundary(element) || element is PsiFile

    private fun isTypeBoundary(element: PsiElement): Boolean =
        CrystalPsiUtils.isTypeDefinition(element)

    private fun types(name: String): List<CrystalNamedElement> = typeCache.getOrPut(name) {
        CrystalIndexService.findTypes(name, context.project, GlobalSearchScope.allScope(context.project))
            .filter(effectiveSources::contains)
            .toList()
    }

    private fun methods(name: String): List<CrystalMethodDefinition> = methodCache.getOrPut(name) {
        CrystalIndexService.findMethods(name, context.project, GlobalSearchScope.allScope(context.project))
            .filter(effectiveSources::contains)
            .toList()
    }

    private fun classMethods(name: String): List<CrystalMethodDefinition> = methodsByTypeCache.getOrPut(name) {
        CrystalIndexService.findMethodsByClass(name, context.project, GlobalSearchScope.allScope(context.project))
            .filter(effectiveSources::contains)
            .toList()
    }

    private sealed interface ReceiverState {
        data class TypeObject(val identity: TypeIdentity) : ReceiverState
        data class Values(val result: CrystalTypeResolution) : ReceiverState
    }

    private sealed interface VariableState {
        val result: CrystalTypeResolution
        val provenance: CrystalVariableProvenance

        data object Unbound : VariableState {
            override val result: CrystalTypeResolution = CrystalTypeResolution.Unknown
            override val provenance: CrystalVariableProvenance = CrystalVariableProvenance.UNKNOWN
        }

        data object Unknown : VariableState {
            override val result: CrystalTypeResolution = CrystalTypeResolution.Unknown
            override val provenance: CrystalVariableProvenance = CrystalVariableProvenance.UNKNOWN
        }

        data class Bound(
            val value: CrystalTypeResolution,
            override val provenance: CrystalVariableProvenance
        ) : VariableState {
            override val result: CrystalTypeResolution = value
        }
    }

    private fun VariableState.toPublic(): CrystalVariableResolution =
        CrystalVariableResolution(result, provenance)

    private data class VariableFlow(
        val state: VariableState,
        val fallsThrough: Boolean,
        val exceptionalStates: List<VariableState>,
        val abruptExits: List<VariableExit> = emptyList(),
    ) {
        fun withPriorEffects(prior: VariableFlow): VariableFlow =
            copy(
                exceptionalStates = prior.exceptionalStates + exceptionalStates,
                abruptExits = prior.abruptExits + abruptExits,
            )

        companion object {
            fun falling(state: VariableState): VariableFlow = VariableFlow(state, true, emptyList())
        }
    }

    private data class VariableExit(val kind: AbruptKind, val state: VariableState)

    private data class LogicalVariableFlow(val flow: VariableFlow, val truthiness: Truthiness)

    private enum class AbruptKind { RETURN, BREAK, NEXT }

    private data class ExecutionResult(
        val returns: List<CrystalTypeResolution>,
        val value: CrystalTypeResolution?,
        val fallsThrough: Boolean
    )

    private enum class Truthiness { ALWAYS_TRUTHY, ALWAYS_FALSY, MIXED }

    private data class TypeIdentity(val simpleName: String, val qualifiedName: String)

    private fun TypeIdentity.toShared(): CrystalTypeIdentity = CrystalTypeIdentity(simpleName, qualifiedName)
}

private val INTEGER_SUFFIXES = listOf(
    "i8", "i16", "i32", "i64", "i128",
    "u8", "u16", "u32", "u64", "u128"
)
