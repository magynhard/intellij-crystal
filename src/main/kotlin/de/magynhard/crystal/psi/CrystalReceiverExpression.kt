package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

object CrystalReceiverExpression {

    fun normalize(receiver: PsiElement): PsiElement =
        unwrapTransparent(promoteVariableAccess(receiver))

    fun extractExactConstantTypeRoot(receiver: PsiElement): String? {
        val normalized = normalize(receiver)
        return when (normalized) {
            is CrystalVariableReference -> exactConstantPath(listOf(normalized))
            is CrystalNamespaceAccess -> exactNamespacePath(normalized)
            is CrystalExpression -> exactConstantPath(directSignificantChildren(normalized))
            is CrystalMethodCallExpression -> exactGenericTypeRoot(normalized)
            else -> null
        }
    }

    internal fun extractExactConstantTypeRoot(receiverElements: List<PsiElement>): String? =
        if (receiverElements.size == 1) {
            extractExactConstantTypeRoot(receiverElements.single())
        } else {
            exactConstantPath(receiverElements)
        }

    fun unwrapTransparent(receiver: PsiElement): PsiElement {
        var current = receiver
        while (true) {
            current = when (current) {
                is CrystalExpression -> {
                    val wrapper = directSignificantChildren(current).singleOrNull()
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

    private fun exactNamespacePath(namespace: CrystalNamespaceAccess): String {
        val path = CrystalPsiUtils.buildNamespacePath(namespace)
        var current: PsiElement = namespace
        while (true) {
            when (val previous = previousSignificantSibling(current)) {
                is CrystalNamespaceAccess -> current = previous
                is CrystalVariableReference -> return path
                else -> return "::$path"
            }
        }
    }

    private fun exactGenericTypeRoot(receiver: CrystalMethodCallExpression): String? {
        if (receiver.block != null || receiver.bareArgumentList != null) return null
        val callArgs = receiver.callArgs ?: return null
        val children = directSignificantChildren(receiver)
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

    private fun promoteVariableAccess(receiver: PsiElement): PsiElement =
        when {
            receiver is CrystalVariableReference || receiver is CrystalInstanceVarAccess ||
                receiver is CrystalClassVarAccess -> receiver
            receiver.parent is CrystalVariableReference || receiver.parent is CrystalInstanceVarAccess ||
                receiver.parent is CrystalClassVarAccess -> receiver.parent
            else -> receiver
        }

    private fun directSignificantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot(::isTrivia)

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling != null && isTrivia(sibling)) sibling = sibling.prevSibling
        return sibling
    }

    private fun isTrivia(element: PsiElement): Boolean =
        element is PsiWhiteSpace || element.node.elementType == CrystalTypes.NEWLINE
}
