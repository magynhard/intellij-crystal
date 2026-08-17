package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.psi.*

data class ExactReceiverType(
    val simpleName: String,
    val qualifiedName: String
)

internal object CrystalExactReceiverTypeResolver {

    fun resolve(
        receiver: PsiElement,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? {
        val receiverElement = CrystalReceiverExpression.normalize(receiver)
        return when (receiverElement) {
            is CrystalVariableReference -> resolveLocal(receiverElement, call, session)
            is CrystalInstanceVarAccess -> resolveInstanceVariable(receiverElement, call, session)
            is CrystalClassVarAccess -> null
            else -> null
        }
    }

    private fun resolveLocal(
        receiver: CrystalVariableReference,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? {
        val nameNode = receiver.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        val evidence = findLocalEvidence(receiver, nameNode.text, call, session)
        return if (evidence.found) evidence.type else null
    }

    private fun findLocalEvidence(
        receiver: PsiElement,
        name: String,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ResolutionEvidence {
        val assignment = findLinearAssignmentEvidence(receiver, name, call, session)
        if (assignment.found) return assignment
        return findEnclosingParameterEvidence(receiver, name, call, session)
    }

    private fun findParameterEvidence(
        parameters: List<CrystalParameter>,
        name: String,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ResolutionEvidence {
        val parameter = parameters.firstOrNull {
            (it as? PsiNameIdentifierOwner)?.name == name
        } ?: return ResolutionEvidence.NONE
        val type = parameter.typeReference?.let { resolveExactTypeReference(it, call, session) }
        return ResolutionEvidence(true, type)
    }

    private fun findLinearAssignmentEvidence(
        receiver: PsiElement,
        name: String,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
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
            val evidence = scanLinearStatements(preceding, name, call, session)
            if (evidence.found) return evidence

            val begin = container.parent as? CrystalBeginStatement ?: return ResolutionEvidence.NONE
            if (!isPlainBegin(begin)) return ResolutionEvidence.NONE
            statement = findEnclosingStatement(begin) ?: return ResolutionEvidence.NONE
        }
    }

    private fun scanLinearStatements(
        statements: List<CrystalStatement>,
        name: String,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ResolutionEvidence {
        var nearest = ResolutionEvidence.NONE
        for (statement in statements) {
            val directAssignment = statement.assignment
            if (directAssignment != null && assignmentName(directAssignment) == name) {
                if (directAssignment.postfixModifier != null) return ResolutionEvidence.UNKNOWN
                nearest = ResolutionEvidence(true, resolveAssignment(directAssignment, call, session))
                continue
            }

            val begin = statement.beginStatement
            if (begin != null && containsAssignment(begin, begin, name)) {
                if (!isPlainBegin(begin)) return ResolutionEvidence.UNKNOWN
                val nested = scanLinearStatements(
                    begin.statementList?.statementList.orEmpty(),
                    name,
                    call,
                    session,
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
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ResolutionEvidence {
        var current: PsiElement? = receiver.parent
        while (current != null && current !is PsiFile) {
            when (current) {
                is CrystalBlock -> {
                    val parameter = findParameterEvidence(
                        current.parameterList?.parameterList.orEmpty(),
                        name,
                        call,
                        session,
                    )
                    if (parameter.found) return parameter
                }
                is CrystalMethodDefinition ->
                    return findParameterEvidence(current.parameterList?.parameterList.orEmpty(), name, call, session)
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
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? {
        val name = receiver.name ?: return null
        val enclosingType = CrystalPsiUtils.getEnclosingType(call) ?: return null
        val declarations = mutableListOf<CrystalTypeReference>()
        val assignments = mutableListOf<CrystalAssignment>()
        collectInstanceVariableEvidence(enclosingType, enclosingType, name, declarations, assignments)

        if (declarations.isNotEmpty()) {
            return resolveConsistentTypes(declarations.map { resolveExactTypeReference(it, call, session) })
        }
        if (assignments.isEmpty()) return null
        return resolveConsistentTypes(assignments.map { resolveAssignment(it, call, session) })
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
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? {
        if (assignment.node.findChildByType(CrystalTypes.ASSIGN) == null) return null
        val expression = assignment.expression ?: return null
        val constructorRoot = CrystalReceiverExpression.unwrapTransparent(expression) as? CrystalExpression ?: return null
        val significantChildren = directSignificantChildren(constructorRoot)
        val constructor = significantChildren.lastOrNull() as? CrystalDotCallAccess ?: return null
        if (significantChildren.count { it is CrystalDotCallAccess } != 1) return null
        if (CrystalCallExtractor.extractMethodName(constructor) != "new") return null
        val typeName = CrystalReceiverExpression.extractExactConstantTypeRoot(significantChildren.dropLast(1))
            ?: return null
        return resolveTypeIdentity(typeName, call, session)
    }

    private fun resolveExactTypeReference(
        typeReference: CrystalTypeReference,
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? {
        val exactReference = unwrapParenthesizedTypeReference(typeReference) ?: return null
        val typePath = exactReference.typePathList.singleOrNull() ?: return null
        if (exactReference.typeArgumentsList.size > 1 || exactReference.expressionList.isNotEmpty() ||
            exactReference.typeReferenceList.isNotEmpty()) return null
        val significantChildren = directSignificantChildren(exactReference)
        if (significantChildren.any { it !is CrystalTypePath && it !is CrystalTypeArguments }) return null
        if (significantChildren.count { it is CrystalTypePath } != 1) return null
        return resolveTypeIdentity(typePath.text.filterNot(Char::isWhitespace), call, session)
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

    private fun resolveTypeIdentity(
        typeName: String,
        context: PsiElement,
        session: CrystalTypeResolutionSession,
    ): ExactReceiverType? = session.resolveType(typeName, context)?.let {
        ExactReceiverType(it.simpleName, it.qualifiedName)
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
