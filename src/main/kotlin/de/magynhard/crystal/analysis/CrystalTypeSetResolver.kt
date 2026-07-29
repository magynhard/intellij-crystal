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

internal object CrystalTypeSetResolver {
    fun resolve(element: PsiElement): CrystalTypeResolution =
        CrystalTypeResolutionSession(element).resolve(element)

    fun session(context: PsiElement): CrystalTypeResolutionSession =
        CrystalTypeResolutionSession(context)
}

internal class CrystalTypeResolutionSession(private val context: PsiElement) {
    private val memo = IdentityHashMap<PsiElement, CrystalTypeResolution>()
    private val resolving = java.util.Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
    private val resolvingMethods = java.util.Collections.newSetFromMap(IdentityHashMap<CrystalMethodDefinition, Boolean>())
    private val methodReturnMemo = IdentityHashMap<CrystalMethodDefinition, CrystalTypeResolution>()
    private val typeCache = mutableMapOf<String, List<CrystalNamedElement>>()
    private val methodCache = mutableMapOf<String, List<CrystalMethodDefinition>>()
    private val methodsByTypeCache = mutableMapOf<String, List<CrystalMethodDefinition>>()
    private val hierarchy = CrystalMethodHierarchy(context, ::types, ::classMethods)

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
        val records = element.containingFile?.let { CrystalPsiUtils.findRecordDefinitions(simpleName, it) }
            .orEmpty().groupBy { it.qualifiedName }
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
        if (selfNew.methods.isNotEmpty()) return CrystalConstructorResolution.Methods(identity, selfNew.methods)
        val initializers = hierarchy.collectNamedMethods(
            identity,
            CrystalReceiverMode.INSTANCE,
            "initialize",
            actualSelf = false
        )
        if (!initializers.complete) return CrystalConstructorResolution.Incomplete(identity)
        return if (initializers.methods.isEmpty()) {
            CrystalConstructorResolution.Implicit(identity)
        } else {
            CrystalConstructorResolution.Methods(identity, initializers.methods)
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
        if (element is CrystalReturnStatement) return element.expression?.let(::resolve) ?: knownType("Nil")

        when (element.node?.elementType) {
            CrystalTypes.INTEGER_LITERAL -> return resolveInteger(element.text)
            CrystalTypes.FLOAT_LITERAL -> return resolveFloat(element.text)
            CrystalTypes.STRING_LITERAL -> return knownType("String")
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
            is CrystalIfStatement -> resolveIf(element)
            is CrystalUnlessStatement -> resolveUnless(element)
            is CrystalCaseStatement -> resolveCase(element)
            is CrystalBeginStatement -> resolveBegin(element)
            is CrystalVariableReference -> resolveVariableValue(element.text, element).result.let { variable ->
                if (variable is CrystalTypeResolution.Unknown) resolveUnqualifiedCall(element.text, element) else variable
            }
            is CrystalInstanceVarAccess -> resolveVariableValue(element.text, element).result
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
        statement.expressionStatement != null -> resolve(statement.expressionStatement!!)
        statement.ifStatement != null -> resolve(statement.ifStatement!!)
        statement.unlessStatement != null -> resolve(statement.unlessStatement!!)
        statement.beginStatement != null -> resolveBegin(statement.beginStatement!!)
        statement.returnStatement != null -> resolve(statement.returnStatement!!)
        else -> knownType("Nil")
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
            val arguments = callArgs.argumentList?.argumentList.orEmpty()
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
        val parameter = parameters.firstOrNull { (it as? PsiNameIdentifierOwner)?.name == name } ?: return null
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
        val returnStatement = (element as? CrystalStatement)?.returnStatement ?: element as? CrystalReturnStatement
        if (returnStatement != null && returnStatement.postfixModifier == null) {
            val exceptions = returnStatement.expression?.takeIf(::mayRaise)?.let { listOf(incoming) }.orEmpty()
            return VariableFlow(incoming, fallsThrough = false, exceptionalStates = exceptions)
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
            var currentAssignment: CrystalAssignment? = assignment
            while (currentAssignment != null) {
                if (assignmentName(currentAssignment) == name) {
                    val rhs = currentAssignment.assignment ?: currentAssignment.expression
                        ?: return VariableFlow.falling(VariableState.Unknown)
                    val rhsResult = resolve(rhs)
                    val assigned = VariableState.Bound(rhsResult, CrystalVariableProvenance.ASSIGNMENT)
                    val exceptional = if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                    if (currentAssignment.postfixModifier == null) {
                        return VariableFlow(assigned, true, exceptional)
                    }
                    return VariableFlow(mergeStates(listOf(incoming, assigned)), true, exceptional)
                }
                currentAssignment = currentAssignment.assignment
            }
            return flowElement(
                assignment.assignment ?: assignment.expression ?: return VariableFlow.falling(incoming),
                name,
                incoming
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
            return VariableFlow(
                mergeStates(observed),
                fallsThrough = true,
                exceptionalStates = bodyFlow.exceptionalStates
            )
        }
        val expressionStatement = (element as? CrystalStatement)?.expressionStatement
            ?: element as? CrystalExpressionStatement
        if (expressionStatement != null) {
            var flow = VariableFlow.falling(incoming)
            for (expression in expressionStatement.expressionList) {
                if (!flow.fallsThrough) break
                val next = flowExpression(expression, name, flow.state)
                flow = VariableFlow(next.state, next.fallsThrough, flow.exceptionalStates + next.exceptionalStates)
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
        if (element is CrystalBlock && containsAssignment(element, name)) {
            val body = element.statementList
            return mergeFlows(listOf(VariableFlow.falling(incoming), flowStatementList(body, name, incoming)))
        }
        if (element is CrystalExpression || element is CrystalGroupedExpression || element is CrystalBareArgument) {
            return flowExpression(element, name, incoming)
        }
        if (containsAssignment(element, name)) return flowNestedEvaluation(element, name, incoming)
        return VariableFlow(incoming, true, if (mayRaise(element)) listOf(incoming) else emptyList())
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
        for (statement in statementList?.statementList.orEmpty()) {
            if (!fallsThrough) break
            val flow = flowElement(statement, name, state)
            exceptional.addAll(flow.exceptionalStates)
            fallsThrough = flow.fallsThrough
            state = flow.state
            if (fallsThrough && state != incoming) observed?.add(state)
        }
        return VariableFlow(state, fallsThrough, exceptional)
    }

    private fun flowProtectedBody(
        statement: CrystalBeginStatement,
        name: String,
        incoming: VariableState
    ): VariableFlow {
        val normal = flowStatementList(statement.statementList, name, incoming)
        val normalResult = if (normal.fallsThrough && statement.elseClause != null) {
            val elseFlow = flowStatementList(statement.elseClause!!.statementList, name, normal.state)
            VariableFlow(elseFlow.state, elseFlow.fallsThrough, normal.exceptionalStates + elseFlow.exceptionalStates)
        } else {
            normal
        }
        val rescueIncoming = exceptionalIncoming(normal, incoming)
        val rescues = statement.rescueClauseList.map { flowStatementList(it.statementList, name, rescueIncoming) }
        val protected = mergeFlows(rescues + normalResult)
        if (!protected.fallsThrough) return protected
        val ensure = statement.ensureClause?.statementList?.let {
            flowStatementList(it, name, protected.state)
        } ?: return protected
        return VariableFlow(
            ensure.state,
            ensure.fallsThrough,
            protected.exceptionalStates + ensure.exceptionalStates
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
        val assignment = PsiTreeUtil.getChildOfType(element, CrystalAssignment::class.java)
        if (assignment != null) return flowElement(assignment, name, incoming)
        val children = significantChildren(element)
        val assignIndex = children.indexOfFirst { it.node.elementType == CrystalTypes.ASSIGN }
        if (assignIndex > 0 && assignIndex < children.lastIndex) {
            val assignedName = children.take(assignIndex).lastOrNull()?.text
            if (assignedName == name) {
                val rhs = children[assignIndex + 1]
                return VariableFlow(
                    VariableState.Bound(resolve(rhs), CrystalVariableProvenance.ASSIGNMENT),
                    true,
                    if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                )
            }
        }
        if (element is CrystalGroupedExpression && element.node.findChildByType(CrystalTypes.ASSIGN) != null) {
            val expressions = element.expressionList
            if (expressions.size >= 2 && expressions.first().text == name) {
                val rhs = expressions.last()
                return VariableFlow(
                    VariableState.Bound(resolve(rhs), CrystalVariableProvenance.ASSIGNMENT),
                    true,
                    if (mayRaise(rhs)) listOf(VariableState.Unknown) else emptyList()
                )
            }
        }
        val question = children.indexOfFirst { it.node.elementType == CrystalTypes.QUESTION }
        if (question >= 0) {
            val colon = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
            if (colon <= question) return VariableFlow.falling(VariableState.Unknown)
            val branches = listOf(
                flowElement(children[question + 1], name, incoming),
                flowElement(children[colon + 1], name, incoming)
            )
            return mergeFlows(branches.sortedBy { it.state != incoming })
        }
        val operatorIndex = children.indexOfFirst {
            it.node.elementType == CrystalTypes.AND_AND || it.node.elementType == CrystalTypes.OR_OR
        }
        if (operatorIndex > 0 && operatorIndex < children.lastIndex) {
            val left = children[operatorIndex - 1]
            val leftFlow = flowElement(left, name, incoming)
            if (!leftFlow.fallsThrough) return leftFlow
            val rightFlow = flowElement(children[operatorIndex + 1], name, leftFlow.state)
            return when (children[operatorIndex].node.elementType) {
                CrystalTypes.AND_AND -> when (truthiness(left)) {
                    Truthiness.ALWAYS_TRUTHY -> rightFlow.withPriorExceptions(leftFlow)
                    Truthiness.ALWAYS_FALSY -> leftFlow
                    Truthiness.MIXED -> mergeFlows(listOf(leftFlow, rightFlow.withPriorExceptions(leftFlow)))
                }
                else -> when (truthiness(left)) {
                    Truthiness.ALWAYS_TRUTHY -> leftFlow
                    Truthiness.ALWAYS_FALSY -> rightFlow.withPriorExceptions(leftFlow)
                    Truthiness.MIXED -> mergeFlows(listOf(leftFlow, rightFlow.withPriorExceptions(leftFlow)))
                }
            }
        }
        var state = incoming
        val exceptional = mutableListOf<VariableState>()
        var sawAssignment = false
        for (child in children) {
            if (child is CrystalExpression || child is CrystalGroupedExpression || containsAssignment(child, name)) {
                val next = flowElement(child, name, state)
                exceptional.addAll(next.exceptionalStates)
                if (!next.fallsThrough) return VariableFlow(next.state, false, exceptional)
                if (next.state != state) sawAssignment = true
                state = next.state
            }
        }
        if (isPotentiallyRaisingOperation(element)) {
            exceptional.add(state)
        } else if (!sawAssignment && mayRaise(element)) {
            exceptional.add(incoming)
        }
        return VariableFlow(if (sawAssignment) state else incoming, true, exceptional)
    }

    private fun flowNestedEvaluation(
        element: PsiElement,
        name: String,
        incoming: VariableState
    ): VariableFlow {
        if (element is CrystalExpression || element is CrystalGroupedExpression || element is CrystalBareArgument) {
            return flowExpression(element, name, incoming)
        }
        var state = incoming
        val exceptional = mutableListOf<VariableState>()
        for (child in significantChildren(element)) {
            if (!containsAssignment(child, name)) continue
            val next = if (child is CrystalAssignment) {
                flowElement(child, name, state)
            } else {
                flowNestedEvaluation(child, name, state)
            }
            exceptional.addAll(next.exceptionalStates)
            if (!next.fallsThrough) return VariableFlow(next.state, false, exceptional)
            state = next.state
        }
        if (isPotentiallyRaisingOperation(element)) exceptional.add(state)
        return VariableFlow(state, true, exceptional)
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
            flows.flatMap { it.exceptionalStates }
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
        if (element is CrystalTupleLiteral) return element.expressionList.expressionList.any(::mayRaise)
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
            val result = returnStatement.expression?.let(::resolve) ?: knownType("Nil")
            return if (returnStatement.postfixModifier == null) {
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
        val expressions = tuple.expressionList.expressionList
        val results = expressions.map(::resolve)
        if (results.any { it is CrystalTypeResolution.Unknown }) return CrystalTypeResolution.Unknown
        val names = results.map { (it as CrystalTypeResolution.Known).types.joinToString(" | ") { type -> type.name } }
        return knownType("Tuple(${names.joinToString(", ")})")
    }

    private fun resolveOperator(children: List<PsiElement>): CrystalTypeResolution? {
        if (children.size < 3) return null
        return when (children[1].node.elementType) {
            CrystalTypes.EQ, CrystalTypes.NEQ, CrystalTypes.LT, CrystalTypes.LTE,
            CrystalTypes.GT, CrystalTypes.GTE, CrystalTypes.SPACESHIP, CrystalTypes.CASE_EQ,
            CrystalTypes.MATCH_OP -> knownType("Bool")
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

    private fun firstExpressionChild(element: PsiElement): PsiElement? = significantChildren(element).firstOrNull {
        it.node.elementType !in setOf(CrystalTypes.IDENTIFIER, CrystalTypes.COLON, CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR)
    }

    private fun methodName(element: PsiElement): String? = element.node.getChildren(null).firstOrNull {
        it.elementType == CrystalTypes.IDENTIFIER || it.elementType == CrystalTypes.CONSTANT
    }?.text

    private fun promote(element: PsiElement): PsiElement = when {
        element.parent is CrystalVariableReference || element.parent is CrystalInstanceVarAccess -> element.parent
        else -> element
    }

    private fun significantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot {
            it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE
        }

    private fun isScopeBoundary(element: PsiElement): Boolean =
        element is CrystalMethodDefinition || element is CrystalMacroDefinition || isTypeBoundary(element) || element is PsiFile

    private fun isTypeBoundary(element: PsiElement): Boolean =
        element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition

    private fun types(name: String): List<CrystalNamedElement> = typeCache.getOrPut(name) {
        CrystalIndexService.findTypes(name, context.project, GlobalSearchScope.allScope(context.project)).toList()
    }

    private fun methods(name: String): List<CrystalMethodDefinition> = methodCache.getOrPut(name) {
        CrystalIndexService.findMethods(name, context.project, GlobalSearchScope.allScope(context.project)).toList()
    }

    private fun classMethods(name: String): List<CrystalMethodDefinition> = methodsByTypeCache.getOrPut(name) {
        CrystalIndexService.findMethodsByClass(name, context.project, GlobalSearchScope.allScope(context.project)).toList()
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
        val exceptionalStates: List<VariableState>
    ) {
        fun withPriorExceptions(prior: VariableFlow): VariableFlow =
            copy(exceptionalStates = prior.exceptionalStates + exceptionalStates)

        companion object {
            fun falling(state: VariableState): VariableFlow = VariableFlow(state, true, emptyList())
        }
    }

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
