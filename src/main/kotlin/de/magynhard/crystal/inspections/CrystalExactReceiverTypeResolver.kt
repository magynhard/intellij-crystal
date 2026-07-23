package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

internal data class ExactReceiverType(
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
        val file = receiver.containingFile ?: return ResolutionEvidence.NONE
        var current: PsiElement? = receiver

        while (current != null && current !== file) {
            when (current) {
                is CrystalBlock -> {
                    val parameter = findParameterEvidence(
                        current.parameterList?.parameterList.orEmpty(),
                        name,
                        call
                    )
                    if (parameter.found) return parameter
                }
                is CrystalMethodDefinition -> {
                    return findParameterEvidence(current.parameterList?.parameterList.orEmpty(), name, call)
                }
                is CrystalMacroDefinition -> return ResolutionEvidence.NONE
                is CrystalClassDefinition,
                is CrystalModuleDefinition,
                is CrystalStructDefinition,
                is CrystalEnumDefinition -> return ResolutionEvidence.NONE
            }

            var sibling = current.prevSibling
            while (sibling != null) {
                val assignments = mutableListOf<CrystalAssignment>()
                collectLocalAssignments(sibling, name, assignments)
                if (assignments.isNotEmpty()) {
                    val nearest = assignments.maxBy(CrystalAssignment::getTextOffset)
                    val conditional = assignments.size > 1 || nearest.postfixModifier != null ||
                        isConditionallyNested(nearest, sibling)
                    val type = if (conditional) null else resolveAssignment(nearest, call)
                    return ResolutionEvidence(true, type)
                }
                sibling = sibling.prevSibling
            }
            current = current.parent
        }

        return ResolutionEvidence.NONE
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

    private fun collectLocalAssignments(
        element: PsiElement,
        name: String,
        result: MutableList<CrystalAssignment>
    ) {
        if (element is PsiFile || element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition) {
            return
        }
        if (element is CrystalAssignment && (element as PsiNameIdentifierOwner).name == name) {
            result.add(element)
        }
        element.children.forEach { collectLocalAssignments(it, name, result) }
    }

    private fun isConditionallyNested(assignment: CrystalAssignment, searchedRoot: PsiElement): Boolean {
        var current: PsiElement? = assignment.parent
        while (current != null && current !== searchedRoot) {
            when (current.node.elementType) {
                CrystalTypes.IF_STATEMENT,
                CrystalTypes.UNLESS_STATEMENT,
                CrystalTypes.WHILE_STATEMENT,
                CrystalTypes.UNTIL_STATEMENT,
                CrystalTypes.CASE_STATEMENT,
                CrystalTypes.SELECT_STATEMENT,
                CrystalTypes.BLOCK -> return true
            }
            current = current.parent
        }
        return false
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
        val constructorCalls = mutableListOf<CrystalDotCallAccess>()
        collectDotCalls(expression, constructorCalls)
        val constructor = constructorCalls.singleOrNull() ?: return null
        if (CrystalCallExtractor.extractMethodName(constructor) != "new") return null

        val constructorReceiver = previousSignificantSibling(constructor) ?: return null
        val typeName = when (constructorReceiver) {
            is CrystalNamespaceAccess -> CrystalPsiUtils.buildNamespacePath(constructorReceiver)
            is CrystalVariableReference -> constructorReceiver.node.findChildByType(CrystalTypes.CONSTANT)?.text
            else -> constructorReceiver.takeIf { it.node.elementType == CrystalTypes.CONSTANT }?.text
        } ?: return null
        return resolveTypeIdentity(typeName, call)
    }

    private fun collectDotCalls(element: PsiElement, result: MutableList<CrystalDotCallAccess>) {
        if (element is CrystalDotCallAccess) result.add(element)
        element.children.forEach { collectDotCalls(it, result) }
    }

    private fun resolveExactTypeReference(
        typeReference: CrystalTypeReference,
        call: CrystalDotCallAccess
    ): ExactReceiverType? {
        val typePath = typeReference.typePathList.singleOrNull() ?: return null
        val normalizedReference = typeReference.text.filterNot(Char::isWhitespace)
        val normalizedPath = typePath.text.filterNot(Char::isWhitespace)
        if (normalizedReference != normalizedPath) return null
        return resolveTypeIdentity(normalizedPath, call)
    }

    private fun resolveTypeIdentity(typeName: String, context: PsiElement): ExactReceiverType? {
        val normalizedName = typeName.removePrefix("::")
        val simpleName = normalizedName.substringAfterLast("::")
        if (simpleName.isEmpty()) return null

        val possibleIdentities = mutableSetOf(simpleName)
        var enclosingType = CrystalPsiUtils.getEnclosingType(context)
        while (enclosingType != null) {
            CrystalPsiUtils.buildQualifiedName(enclosingType)
                ?.let { possibleIdentities.add("$it::$simpleName") }
            enclosingType = enclosingType.parent?.let(CrystalPsiUtils::getEnclosingType)
        }
        val hasExplicitIdentity = typeName.startsWith("::") || normalizedName.contains("::")

        val identities = CrystalIndexService.findTypes(
            simpleName,
            context.project,
            GlobalSearchScope.projectScope(context.project)
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

    private fun normalizeReceiver(receiver: PsiElement): PsiElement {
        return when {
            receiver is CrystalVariableReference || receiver is CrystalInstanceVarAccess ||
                receiver is CrystalClassVarAccess -> receiver
            receiver.parent is CrystalVariableReference || receiver.parent is CrystalInstanceVarAccess ||
                receiver.parent is CrystalClassVarAccess -> receiver.parent
            else -> receiver
        }
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
        }
    }
}
