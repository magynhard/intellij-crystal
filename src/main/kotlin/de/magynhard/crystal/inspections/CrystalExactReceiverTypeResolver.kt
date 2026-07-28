package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

data class ExactReceiverType(
    val simpleName: String,
    val qualifiedName: String
)

internal object CrystalExactReceiverTypeResolver {

    fun resolve(receiver: PsiElement, call: CrystalDotCallAccess): ExactReceiverType? {
        val receiverElement = normalizeReceiver(receiver)
        return when (receiverElement) {
            is CrystalVariableReference -> resolveLocal(receiverElement, call)
            is CrystalInstanceVarAccess -> resolveInstanceVariable(receiverElement, call)
            is CrystalClassVarAccess -> null
            else -> null
        }
    }

    private fun resolveLocal(receiver: CrystalVariableReference, call: CrystalDotCallAccess): ExactReceiverType? {
        val nameNode = receiver.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        val evidence = findLocalEvidence(receiver, nameNode.text, call)
        return if (evidence.found) evidence.type else null
    }

    private fun findLocalEvidence(
        receiver: PsiElement,
        name: String,
        call: CrystalDotCallAccess
    ): ResolutionEvidence {
        val assignment = findLinearAssignmentEvidence(receiver, name, call)
        if (assignment.found) return assignment
        return findEnclosingParameterEvidence(receiver, name, call)
    }

    private fun findParameterEvidence(
        parameters: List<CrystalParameter>,
        name: String,
        call: CrystalDotCallAccess
    ): ResolutionEvidence {
        val parameter = parameters.firstOrNull {
            (it as? PsiNameIdentifierOwner)?.name == name
        } ?: return ResolutionEvidence.NONE
        val type = parameter.typeReference?.let { resolveExactTypeReference(it, call) }
        return ResolutionEvidence(true, type)
    }

    private fun findLinearAssignmentEvidence(
        receiver: PsiElement,
        name: String,
        call: CrystalDotCallAccess
    ): ResolutionEvidence {
        var statement = findEnclosingStatement(receiver) ?: return ResolutionEvidence.NONE

        while (true) {
            val container = statement.parent ?: return ResolutionEvidence.NONE
            if (!isLinearSequence(container)) {
                val nonlinearRoot = findNonlinearRoot(container)
                if (nonlinearRoot != null && containsAssignment(nonlinearRoot, nonlinearRoot, name)) {
                    return ResolutionEvidence.UNKNOWN
                }
                return if (hasPrecedingAssignmentOutside(nonlinearRoot ?: container, name)) {
                    ResolutionEvidence.UNKNOWN
                } else {
                    ResolutionEvidence.NONE
                }
            }

            val preceding = container.children.filterIsInstance<CrystalStatement>()
                .takeWhile { it !== statement }
            val evidence = scanLinearStatements(preceding, name, call)
            if (evidence.found) return evidence

            val begin = container.parent as? CrystalBeginStatement ?: return ResolutionEvidence.NONE
            if (!isPlainBegin(begin)) return ResolutionEvidence.NONE
            statement = findEnclosingStatement(begin) ?: return ResolutionEvidence.NONE
        }
    }

    private fun scanLinearStatements(
        statements: List<CrystalStatement>,
        name: String,
        call: CrystalDotCallAccess
    ): ResolutionEvidence {
        var nearest = ResolutionEvidence.NONE
        for (statement in statements) {
            val directAssignment = statement.assignment
            if (directAssignment != null && assignmentName(directAssignment) == name) {
                if (directAssignment.postfixModifier != null) return ResolutionEvidence.UNKNOWN
                nearest = ResolutionEvidence(true, resolveAssignment(directAssignment, call))
                continue
            }

            val begin = statement.beginStatement
            if (begin != null && containsAssignment(begin, begin, name)) {
                if (!isPlainBegin(begin)) return ResolutionEvidence.UNKNOWN
                val nested = scanLinearStatements(
                    begin.statementList?.statementList.orEmpty(),
                    name,
                    call
                )
                if (nested.found) {
                    if (nested.type == null) return ResolutionEvidence.UNKNOWN
                    nearest = nested
                }
                continue
            }

            if (containsAssignment(statement, statement, name)) return ResolutionEvidence.UNKNOWN
        }
        return nearest
    }

    private fun findEnclosingParameterEvidence(
        receiver: PsiElement,
        name: String,
        call: CrystalDotCallAccess
    ): ResolutionEvidence {
        var current: PsiElement? = receiver.parent
        while (current != null && current !is PsiFile) {
            when (current) {
                is CrystalBlock -> {
                    val parameter = findParameterEvidence(
                        current.parameterList?.parameterList.orEmpty(),
                        name,
                        call
                    )
                    if (parameter.found) return parameter
                }
                is CrystalMethodDefinition ->
                    return findParameterEvidence(current.parameterList?.parameterList.orEmpty(), name, call)
                is CrystalMacroDefinition,
                is CrystalClassDefinition,
                is CrystalModuleDefinition,
                is CrystalStructDefinition,
                is CrystalEnumDefinition -> return ResolutionEvidence.NONE
            }
            current = current.parent
        }
        return ResolutionEvidence.NONE
    }

    private fun isLinearSequence(container: PsiElement): Boolean {
        if (container is PsiFile) return true
        val statementList = container as? CrystalStatementList ?: return false
        return when (val owner = statementList.parent) {
            is CrystalBeginStatement -> isPlainBegin(owner) && owner.statementList === statementList
            is CrystalMethodBody -> owner.rescueClauseList.isEmpty() &&
                owner.elseClause == null && owner.ensureClause == null
            else -> false
        }
    }

    private fun isPlainBegin(begin: CrystalBeginStatement): Boolean =
        begin.rescueClauseList.isEmpty() && begin.elseClause == null && begin.ensureClause == null

    private fun findNonlinearRoot(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is CrystalIfStatement || current is CrystalUnlessStatement ||
                current is CrystalCaseStatement || current is CrystalSelectStatement ||
                current is CrystalWhileStatement || current is CrystalUntilStatement ||
                current is CrystalForStatement || current is CrystalBlock ||
                current is CrystalBeginStatement && !isPlainBegin(current) ||
                current is CrystalMethodBody && (current.rescueClauseList.isNotEmpty() ||
                    current.elseClause != null || current.ensureClause != null)) {
                return current
            }
            if (current is CrystalMethodDefinition || isTypeBoundary(current)) return null
            current = current.parent
        }
        return null
    }

    private fun hasPrecedingAssignmentOutside(element: PsiElement, name: String): Boolean {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            val statement = findEnclosingStatement(current)
            if (statement != null) {
                var sibling = statement.prevSibling
                while (sibling != null) {
                    if (containsAssignment(sibling, sibling, name)) return true
                    sibling = sibling.prevSibling
                }
                current = statement.parent
            } else {
                if (current is CrystalMethodDefinition || isTypeBoundary(current)) return false
                current = current.parent
            }
        }
        return false
    }

    private fun containsAssignment(element: PsiElement, root: PsiElement, name: String): Boolean {
        if (element !== root && (element is PsiFile || element is CrystalMethodDefinition ||
                element is CrystalMacroDefinition || isTypeBoundary(element))) {
            return false
        }
        if (element is CrystalAssignment && assignmentName(element) == name) return true
        return element.children.any { containsAssignment(it, root, name) }
    }

    private fun assignmentName(assignment: CrystalAssignment): String? =
        (assignment as PsiNameIdentifierOwner).name

    private fun findEnclosingStatement(element: PsiElement): CrystalStatement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is CrystalStatement) return current
            if (current is CrystalMethodDefinition || current is CrystalMacroDefinition ||
                isTypeBoundary(current)) return null
            current = current.parent
        }
        return null
    }

    private fun resolveInstanceVariable(
        receiver: CrystalInstanceVarAccess,
        call: CrystalDotCallAccess
    ): ExactReceiverType? {
        val name = receiver.name ?: return null
        val enclosingType = CrystalPsiUtils.getEnclosingType(call) ?: return null
        val declarations = mutableListOf<CrystalTypeReference>()
        val assignments = mutableListOf<CrystalAssignment>()
        collectInstanceVariableEvidence(enclosingType, enclosingType, name, declarations, assignments)

        if (declarations.isNotEmpty()) {
            return resolveConsistentTypes(declarations.map { resolveExactTypeReference(it, call) })
        }
        if (assignments.isEmpty()) return null
        return resolveConsistentTypes(assignments.map { resolveAssignment(it, call) })
    }

    private fun collectInstanceVariableEvidence(
        element: PsiElement,
        rootType: PsiElement,
        name: String,
        declarations: MutableList<CrystalTypeReference>,
        assignments: MutableList<CrystalAssignment>
    ) {
        if (element !== rootType && isTypeBoundary(element)) return

        when (element) {
            is CrystalPropertyDeclaration -> {
                if (element.instanceVarAccess?.name == name) declarations.add(element.typeReference)
            }
            is CrystalParameter -> {
                if (element.instanceVarAccess?.name == name && element.typeReference != null) {
                    declarations.add(element.typeReference!!)
                }
            }
            is CrystalAssignment -> {
                if (element.instanceVarAccess?.name == name) assignments.add(element)
            }
        }
        element.children.forEach {
            collectInstanceVariableEvidence(it, rootType, name, declarations, assignments)
        }
    }

    private fun resolveAssignment(
        assignment: CrystalAssignment,
        call: CrystalDotCallAccess
    ): ExactReceiverType? {
        if (assignment.node.findChildByType(CrystalTypes.ASSIGN) == null) return null
        val expression = assignment.expression ?: return null
        val constructorRoot = unwrapTransparentExpression(expression) as? CrystalExpression ?: return null
        val significantChildren = directSignificantChildren(constructorRoot)
        val constructor = significantChildren.lastOrNull() as? CrystalDotCallAccess ?: return null
        if (significantChildren.count { it is CrystalDotCallAccess } != 1) return null
        if (CrystalCallExtractor.extractMethodName(constructor) != "new") return null
        val typeName = exactConstructorReceiverName(significantChildren.dropLast(1)) ?: return null
        return resolveTypeIdentity(typeName, call)
    }

    private fun exactConstructorReceiverName(elements: List<PsiElement>): String? {
        if (elements.size == 1) {
            return extractExactConstantTypeRoot(elements.single())
        }

        return exactConstantPath(elements)
    }

    private fun exactConstantPath(elements: List<PsiElement>): String? {
        val receiverElements = elements.dropWhile { it.node.elementType == CrystalTypes.DOUBLE_COLON }
        val leadingDoubleColonCount = elements.size - receiverElements.size
        if (leadingDoubleColonCount > 1) return null
        val rootElement = receiverElements.firstOrNull() ?: return null
        if (rootElement is CrystalNamespaceAccess) {
            if (leadingDoubleColonCount != 0 || !isAbsoluteNamespaceRoot(rootElement) ||
                receiverElements.drop(1).any { it !is CrystalNamespaceAccess }) {
                return null
            }
            return "::${CrystalPsiUtils.buildNamespacePath(receiverElements.last() as CrystalNamespaceAccess)}"
        }
        val root = rootElement as? CrystalVariableReference ?: return null
        if (root.node.findChildByType(CrystalTypes.CONSTANT) == null) return null
        val namespaces = receiverElements.drop(1)
        if (namespaces.any { it !is CrystalNamespaceAccess }) return null
        val path = if (namespaces.isEmpty()) {
            root.text
        } else {
            CrystalPsiUtils.buildNamespacePath(namespaces.last() as CrystalNamespaceAccess)
        }
        return if (leadingDoubleColonCount == 1) "::$path" else path
    }

    private fun isAbsoluteNamespaceRoot(namespace: CrystalNamespaceAccess): Boolean {
        val children = directSignificantChildren(namespace)
        return children.size == 2 &&
            children[0].node.elementType == CrystalTypes.DOUBLE_COLON &&
            children[1].node.elementType == CrystalTypes.CONSTANT
    }

    internal fun extractExactConstantTypeRoot(receiver: PsiElement): String? {
        val normalized = unwrapTransparentExpression(promoteVariableAccess(receiver))
        return when (normalized) {
            is CrystalVariableReference -> exactConstantPath(listOf(normalized))
            is CrystalExpression -> exactConstantPath(directSignificantChildren(normalized))
            is CrystalMethodCallExpression -> exactGenericTypeRoot(normalized)
            else -> null
        }
    }

    private fun exactGenericTypeRoot(receiver: CrystalMethodCallExpression): String? {
        val call = receiver
        if (call.block != null || call.bareArgumentList != null) return null
        val callArgs = call.callArgs ?: return null
        val children = directSignificantChildren(call)
        val root = children.firstOrNull()?.takeIf { it.node.elementType == CrystalTypes.CONSTANT }
            ?: return null
        if (children.size != 2 || children[1] !== callArgs) return null

        val typeArguments = callArgs.argumentList?.argumentList.orEmpty()
        if (typeArguments.isEmpty() || typeArguments.any { argument ->
                val expression = argument.expression ?: return@any true
                if (directSignificantChildren(argument) != listOf(expression)) return@any true
                extractExactConstantTypeRoot(expression) == null
            }) {
            return null
        }
        return root.text
    }

    private fun unwrapTransparentExpression(expression: PsiElement): PsiElement {
        var current: PsiElement = expression
        while (true) {
            current = when (current) {
                is CrystalExpression -> {
                    val children = directSignificantChildren(current)
                    val wrapper = children.singleOrNull()
                    if (wrapper is CrystalExpression || wrapper is CrystalGroupedExpression ||
                        wrapper is CrystalMethodCallExpression || wrapper is CrystalVariableReference ||
                        wrapper is CrystalInstanceVarAccess || wrapper is CrystalClassVarAccess) {
                        wrapper
                    } else {
                        return current
                    }
                }
                is CrystalGroupedExpression -> {
                    if (current.expressionList.size != 1 ||
                        current.node.findChildByType(CrystalTypes.ASSIGN) != null ||
                        current.node.findChildByType(CrystalTypes.COMMA) != null) {
                        return current
                    }
                    current.expressionList.single()
                }
                else -> return current
            }
        }
    }

    private fun resolveExactTypeReference(
        typeReference: CrystalTypeReference,
        call: CrystalDotCallAccess
    ): ExactReceiverType? {
        val exactReference = unwrapParenthesizedTypeReference(typeReference) ?: return null
        val typePath = exactReference.typePathList.singleOrNull() ?: return null
        if (exactReference.typeArgumentsList.size > 1 || exactReference.expressionList.isNotEmpty() ||
            exactReference.typeReferenceList.isNotEmpty()) return null
        val significantChildren = directSignificantChildren(exactReference)
        if (significantChildren.any { it !is CrystalTypePath && it !is CrystalTypeArguments }) return null
        if (significantChildren.count { it is CrystalTypePath } != 1) return null
        return resolveTypeIdentity(typePath.text.filterNot(Char::isWhitespace), call)
    }

    private fun unwrapParenthesizedTypeReference(typeReference: CrystalTypeReference): CrystalTypeReference? {
        var current = typeReference
        while (current.typePathList.isEmpty()) {
            val nested = current.typeReferenceList.singleOrNull() ?: return null
            val normalized = current.text.filterNot(Char::isWhitespace)
            val nestedText = nested.text.filterNot(Char::isWhitespace)
            if (normalized != "($nestedText)") return null
            current = nested
        }
        return current
    }

    private fun resolveTypeIdentity(typeName: String, context: PsiElement): ExactReceiverType? {
        val normalizedName = typeName.removePrefix("::")
        val simpleName = normalizedName.substringAfterLast("::")
        if (simpleName.isEmpty()) return null

        val possibleIdentities = CrystalPsiUtils.buildLexicalQualifiedNameCandidates(simpleName, context)
        val hasExplicitIdentity = typeName.startsWith("::") || normalizedName.contains("::")

        val identities = CrystalIndexService.findTypes(
            simpleName,
            context.project,
            GlobalSearchScope.allScope(context.project)
        ).mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { if (hasExplicitIdentity) it == normalizedName else it in possibleIdentities }
            .distinct()

        val qualifiedName = identities.singleOrNull() ?: return null
        return ExactReceiverType(simpleName, qualifiedName)
    }

    private fun resolveConsistentTypes(types: List<ExactReceiverType?>): ExactReceiverType? {
        if (types.any { it == null }) return null
        return types.filterNotNull().distinct().singleOrNull()
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling is PsiWhiteSpace || sibling?.node?.elementType == CrystalTypes.NEWLINE) {
            sibling = sibling.prevSibling
        }
        return sibling
    }

    private fun isTrivia(element: PsiElement): Boolean =
        element is PsiWhiteSpace || element.node.elementType == CrystalTypes.NEWLINE

    private fun directSignificantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot(::isTrivia)

    internal fun normalizeReceiver(receiver: PsiElement): PsiElement {
        return unwrapTransparentExpression(promoteVariableAccess(receiver))
    }

    private fun promoteVariableAccess(receiver: PsiElement): PsiElement =
        when {
            receiver is CrystalVariableReference || receiver is CrystalInstanceVarAccess ||
                receiver is CrystalClassVarAccess -> receiver
            receiver.parent is CrystalVariableReference || receiver.parent is CrystalInstanceVarAccess ||
                receiver.parent is CrystalClassVarAccess -> receiver.parent
            else -> receiver
        }

    private fun isTypeBoundary(element: PsiElement): Boolean =
        element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition

    private data class ResolutionEvidence(
        val found: Boolean,
        val type: ExactReceiverType?
    ) {
        companion object {
            val NONE = ResolutionEvidence(false, null)
            val UNKNOWN = ResolutionEvidence(true, null)
        }
    }
}
