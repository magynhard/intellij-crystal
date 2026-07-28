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
    private val typeCache = mutableMapOf<String, List<CrystalNamedElement>>()
    private val methodCache = mutableMapOf<String, List<CrystalMethodDefinition>>()
    private val classMethodCache = mutableMapOf<String, List<CrystalMethodDefinition>>()

    fun resolve(element: PsiElement): CrystalTypeResolution {
        val target = promote(element)
        memo[target]?.let { return it }
        if (!resolving.add(target)) return CrystalTypeResolution.Unknown
        val result = resolveUncached(target)
        resolving.remove(target)
        memo[target] = result
        return result
    }

    fun resolveVariable(name: String, position: PsiElement): CrystalTypeResolution =
        resolveVariableValue(name, position)

    fun resolveCall(
        receiverTypeNames: List<String>,
        methodName: String,
        isStatic: Boolean,
        callContext: PsiElement
    ): CrystalTypeResolution {
        val results = mutableListOf<CrystalTypeResolution>()
        for (typeName in receiverTypeNames) {
            val identity = resolveTypeIdentity(typeName, callContext) ?: return CrystalTypeResolution.Unknown
            val candidates = findMethodsInHierarchy(identity, methodName, isStatic, callContext)
            if (candidates.size != 1) return CrystalTypeResolution.Unknown
            results.add(resolveMethodReturn(candidates.single()))
        }
        return mergeKnown(results)
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
            is CrystalVariableReference -> resolveVariableValue(element.text, element)
            is CrystalInstanceVarAccess -> resolveVariableValue(element.text, element)
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
        statement.beginStatement != null -> resolveStatementList(statement.beginStatement!!.statementList)
        statement.returnStatement != null -> resolve(statement.returnStatement!!)
        else -> knownType("Nil")
    }

    private fun resolveExpression(expression: CrystalExpression): CrystalTypeResolution {
        val children = significantChildren(expression)
        val question = children.indexOfFirst { it.node.elementType == CrystalTypes.QUESTION }
        if (question >= 0) {
            val colon = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
            if (colon <= question) return CrystalTypeResolution.Unknown
            val trueExpression = children.getOrNull(question + 1) ?: return CrystalTypeResolution.Unknown
            val falseExpression = children.getOrNull(colon + 1) ?: return CrystalTypeResolution.Unknown
            return mergeKnown(listOf(resolve(trueExpression), resolve(falseExpression)))
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
            for (component in postfixComponents(dotAccess)) {
                val call = component ?: return CrystalTypeResolution.Unknown
                val name = methodName(call) ?: return CrystalTypeResolution.Unknown
                receiver = when (receiver) {
                    is ReceiverState.TypeObject -> {
                        if (name == "new") {
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
        val branches = mutableListOf(resolveStatementList(statement.statementList))
        branches.addAll(statement.elsifClauseList.map { resolveStatementList(it.statementList) })
        branches.add(statement.elseClause?.statementList?.let(::resolveStatementList) ?: knownType("Nil"))
        return mergeKnown(branches)
    }

    private fun resolveUnless(statement: CrystalUnlessStatement): CrystalTypeResolution = mergeKnown(
        listOf(
            resolveStatementList(statement.statementList),
            statement.elseClause?.statementList?.let(::resolveStatementList) ?: knownType("Nil")
        )
    )

    private fun resolveCase(statement: CrystalCaseStatement): CrystalTypeResolution {
        val clauses = significantChildren(statement)
            .filter { it is CrystalWhenClause || it is CrystalInClause }
            .map {
                when (it) {
                    is CrystalWhenClause -> resolveStatementList(it.statementList)
                    is CrystalInClause -> resolveStatementList(it.statementList)
                    else -> CrystalTypeResolution.Unknown
                }
            }
            .toMutableList()
        clauses.add(statement.elseClause?.statementList?.let(::resolveStatementList) ?: knownType("Nil"))
        return mergeKnown(clauses)
    }

    private fun resolveStatementList(statementList: CrystalStatementList?): CrystalTypeResolution {
        val statements = statementList?.statementList.orEmpty()
        return statements.lastOrNull()?.let(::resolve) ?: knownType("Nil")
    }

    private fun resolveVariableValue(name: String, position: PsiElement): CrystalTypeResolution {
        val containingAssignment = PsiTreeUtil.getParentOfType(position, CrystalAssignment::class.java, false)
        if (containingAssignment != null && assignmentName(containingAssignment) == name) {
            return resolve(containingAssignment.assignment ?: containingAssignment.expression
                ?: return CrystalTypeResolution.Unknown)
        }
        if (position is PsiFile) {
            var child = position.lastChild
            while (child != null) {
                when (val evidence = flowEvidence(child, name)) {
                    FlowEvidence.Missing -> Unit
                    is FlowEvidence.Found -> return evidence.result
                }
                child = child.prevSibling
            }
            return CrystalTypeResolution.Unknown
        }
        var current: PsiElement = position
        val file = position.containingFile ?: return CrystalTypeResolution.Unknown
        while (current !== file) {
            var sibling = current.prevSibling
            while (sibling != null) {
                when (val evidence = flowEvidence(sibling, name)) {
                    FlowEvidence.Missing -> Unit
                    is FlowEvidence.Found -> return evidence.result
                }
                sibling = sibling.prevSibling
            }
            val parent = current.parent ?: break
            if (parent is CrystalBlock) {
                parameterResult(parent.parameterList?.parameterList.orEmpty(), name)?.let { return it }
            }
            if (parent is CrystalMethodDefinition || parent is CrystalMacroDefinition) {
                val parameters = when (parent) {
                    is CrystalMethodDefinition -> parent.parameterList?.parameterList.orEmpty()
                    is CrystalMacroDefinition -> parent.parameterList?.parameterList.orEmpty()
                    else -> emptyList()
                }
                parameterResult(parameters, name)?.let { return it }
                if (!name.startsWith("@")) return CrystalTypeResolution.Unknown
            }
            if (parent is CrystalClassDefinition || parent is CrystalModuleDefinition ||
                parent is CrystalStructDefinition || parent is CrystalEnumDefinition) {
                if (!name.startsWith("@")) return CrystalTypeResolution.Unknown
            }
            current = parent
        }
        return CrystalTypeResolution.Unknown
    }

    private fun parameterResult(parameters: List<CrystalParameter>, name: String): CrystalTypeResolution? {
        val parameter = parameters.firstOrNull { (it as? PsiNameIdentifierOwner)?.name == name } ?: return null
        return parameter.typeReference?.text?.let(::parseTypeSet) ?: CrystalTypeResolution.Unknown
    }

    private fun flowEvidence(element: PsiElement, name: String): FlowEvidence {
        if (isScopeBoundary(element)) return FlowEvidence.Missing
        val assignment = when (element) {
            is CrystalAssignment -> element
            is CrystalStatement -> element.assignment
            else -> null
        }
        if (assignment != null) {
            var currentAssignment: CrystalAssignment? = assignment
            while (currentAssignment != null) {
                if (assignmentName(currentAssignment) == name) {
                    return FlowEvidence.Found(resolve(currentAssignment.assignment ?: currentAssignment.expression
                        ?: return FlowEvidence.Found(CrystalTypeResolution.Unknown)))
                }
                currentAssignment = currentAssignment.assignment
            }
        }
        val ifStatement = (element as? CrystalStatement)?.ifStatement ?: element as? CrystalIfStatement
        if (ifStatement != null) return branchFlow(
            listOf(ifStatement.statementList) + ifStatement.elsifClauseList.map { it.statementList } +
                listOf(ifStatement.elseClause?.statementList),
            name
        )
        val unlessStatement = (element as? CrystalStatement)?.unlessStatement ?: element as? CrystalUnlessStatement
        if (unlessStatement != null) return branchFlow(
            listOf(unlessStatement.statementList, unlessStatement.elseClause?.statementList),
            name
        )
        val expression = (element as? CrystalStatement)?.expressionStatement?.expressionList?.firstOrNull()
        val caseStatement = expression?.let { PsiTreeUtil.findChildOfType(it, CrystalCaseStatement::class.java) }
        if (caseStatement != null) {
            val lists = significantChildren(caseStatement).mapNotNull {
                when (it) {
                    is CrystalWhenClause -> it.statementList
                    is CrystalInClause -> it.statementList
                    else -> null
                }
            } + listOf(caseStatement.elseClause?.statementList)
            return branchFlow(lists, name)
        }
        if (element is CrystalBlock && containsAssignment(element, name)) {
            return FlowEvidence.Found(CrystalTypeResolution.Unknown)
        }
        return FlowEvidence.Missing
    }

    private fun branchFlow(statementLists: List<CrystalStatementList?>, name: String): FlowEvidence {
        val branchResults = statementLists.map { list ->
            val evidence = list?.statementList.orEmpty().asReversed()
                .asSequence().map { flowEvidence(it, name) }
                .firstOrNull { it is FlowEvidence.Found } ?: FlowEvidence.Missing
            evidence
        }
        if (branchResults.all { it is FlowEvidence.Missing }) return FlowEvidence.Missing
        if (branchResults.any { it is FlowEvidence.Missing }) {
            return FlowEvidence.Found(CrystalTypeResolution.Unknown)
        }
        return FlowEvidence.Found(mergeKnown(branchResults.filterIsInstance<FlowEvidence.Found>().map { it.result }))
    }

    private fun containsAssignment(element: PsiElement, name: String): Boolean {
        if (isScopeBoundary(element) && element !is CrystalBlock) return false
        if (element is CrystalAssignment && assignmentName(element) == name) return true
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
                val candidates = findMethodsInHierarchy(
                    identity,
                    name,
                    enclosingMethod?.let(CrystalPsiUtils::isSelfMethod) == true,
                    call
                )
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
        if (!resolvingMethods.add(method)) return CrystalTypeResolution.Unknown
        val body = method.methodBody
        if (body == null) {
            resolvingMethods.remove(method)
            return CrystalTypeResolution.Unknown
        }
        val explicitReturns = mutableListOf<CrystalTypeResolution>()
        collectReturns(body, explicitReturns)
        val implicit = resolveStatementList(body.statementList)
        val result = mergeKnown(explicitReturns + implicit)
        resolvingMethods.remove(method)
        return result
    }

    private fun collectReturns(element: PsiElement, results: MutableList<CrystalTypeResolution>) {
        if (element is CrystalMethodDefinition || element is CrystalMacroDefinition || isTypeBoundary(element)) return
        if (element is CrystalReturnStatement) {
            results.add(element.expression?.let(::resolve) ?: knownType("Nil"))
            return
        }
        element.children.forEach { collectReturns(it, results) }
    }

    private fun findMethodsInHierarchy(
        identity: TypeIdentity,
        name: String,
        isStatic: Boolean,
        callContext: PsiElement,
        visited: MutableSet<String> = mutableSetOf()
    ): List<CrystalMethodDefinition> {
        if (!visited.add(identity.qualifiedName)) return emptyList()
        val direct = classMethods(identity.simpleName).filter { method ->
            method.name == name && CrystalPsiUtils.isSelfMethod(method) == isStatic &&
                CrystalPsiUtils.getEnclosingType(method)?.let(CrystalPsiUtils::buildQualifiedName) == identity.qualifiedName
        }
        if (direct.isNotEmpty()) return direct
        val declaration = types(identity.simpleName).singleOrNull {
            CrystalPsiUtils.buildQualifiedName(it) == identity.qualifiedName
        } ?: return emptyList()
        val superclass = when (declaration) {
            is CrystalClassDefinition -> declaration.superclassClause?.typeReference?.text
            is CrystalStructDefinition -> declaration.superclassClause?.typeReference?.text
            else -> null
        } ?: return emptyList()
        val parent = resolveTypeIdentity(superclass, callContext) ?: return emptyList()
        return findMethodsInHierarchy(parent, name, isStatic, callContext, visited)
    }

    private fun resolveTypeIdentity(typeName: String, element: PsiElement): TypeIdentity? {
        val normalized = typeName.substringBefore('(').removePrefix("::").trim()
        val simpleName = normalized.substringAfterLast("::")
        if (simpleName.isEmpty()) return null
        val allowed = if (normalized.contains("::") || typeName.startsWith("::")) {
            setOf(normalized)
        } else {
            CrystalPsiUtils.buildLexicalQualifiedNameCandidates(simpleName, element)
        }
        val identities = types(simpleName).mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { it in allowed }.distinct()
        return identities.singleOrNull()?.let { TypeIdentity(simpleName, it) }
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
            CrystalTypes.MATCH_OP, CrystalTypes.AND_AND, CrystalTypes.OR_OR -> knownType("Bool")
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
        return knownType(name, isUnsuffixedNumericLiteral = normalized.all(Char::isDigit))
    }

    private fun resolveFloat(text: String): CrystalTypeResolution {
        val normalized = text.lowercase().replace("_", "")
        val name = if (normalized.endsWith("f32")) "Float32" else "Float64"
        return knownType(name, isUnsuffixedNumericLiteral = !normalized.endsWith("f32") && !normalized.endsWith("f64"))
    }

    private fun postfixComponents(access: CrystalDotCallAccess): List<PsiElement?> {
        val components = mutableListOf<PsiElement?>(access)
        val argument = access.bareArgumentList?.bareArgumentList?.singleOrNull() ?: return components
        val children = significantChildren(argument)
        if (children.firstOrNull() !is CrystalImplicitObjectCall) return components
        for (child in children) {
            when (child) {
                is CrystalImplicitObjectCall -> components.add(child)
                is CrystalDotCallAccess -> components.addAll(postfixComponents(child))
                else -> components.add(null)
            }
        }
        return components
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

    private fun classMethods(name: String): List<CrystalMethodDefinition> = classMethodCache.getOrPut(name) {
        CrystalIndexService.findMethodsByClass(name, context.project, GlobalSearchScope.allScope(context.project)).toList()
    }

    private sealed interface ReceiverState {
        data class TypeObject(val identity: TypeIdentity) : ReceiverState
        data class Values(val result: CrystalTypeResolution) : ReceiverState
    }

    private sealed interface FlowEvidence {
        data object Missing : FlowEvidence
        data class Found(val result: CrystalTypeResolution) : FlowEvidence
    }

    private data class TypeIdentity(val simpleName: String, val qualifiedName: String)
}
