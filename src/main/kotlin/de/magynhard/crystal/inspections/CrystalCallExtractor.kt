package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.*

data class DotCallInfo(
    val receiverName: String,
    val methodName: String,
    val methodNameElement: PsiElement?
)

data class DotCallDescriptor(
    val access: CrystalDotCallAccess,
    val receiver: PsiElement,
    val receiverText: String,
    val methodNameElement: PsiElement,
    val methodName: String,
    val argumentHolder: PsiElement
)

object CrystalCallExtractor {

    fun extractDotCall(access: CrystalDotCallAccess): DotCallDescriptor? {
        val methodNameElement = access.node.getChildren(null).asSequence()
            .dropWhile { it.elementType != CrystalTypes.DOT }
            .drop(1)
            .map { it.psi }
            .firstOrNull { element ->
                val type = element.node.elementType
                type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT ||
                    CrystalTokenTypes.KEYWORDS.contains(type) ||
                    CrystalTokenTypes.OPERATORS.contains(type) ||
                    element is CrystalMacroInterpolation
            }
            ?: return null
        val receiver = previousSignificantSibling(access) ?: access.node.getChildren(null)
            .asSequence()
            .takeWhile { it.elementType != CrystalTypes.DOT }
            .map { it.psi }
            .filterNot { it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE }
            .lastOrNull()
            ?: return null
        val receiverText = when (receiver) {
            is CrystalNamespaceAccess -> qualifiedReceiverText(receiver)
            else -> receiver.text
        }
        if (receiverText.isEmpty()) return null
        return DotCallDescriptor(
            access,
            receiver,
            receiverText,
            methodNameElement,
            methodNameElement.text,
            access.callArgs ?: access.bareArgumentList ?: access
        )
    }

    private fun qualifiedReceiverText(namespaceAccess: CrystalNamespaceAccess): String {
        val path = CrystalPsiUtils.buildNamespacePath(namespaceAccess)
        var current: PsiElement = namespaceAccess
        while (true) {
            when (val previous = previousSignificantSibling(current)) {
                is CrystalNamespaceAccess -> current = previous
                is CrystalVariableReference -> return path
                else -> return "::$path"
            }
        }
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling is PsiWhiteSpace || sibling?.node?.elementType == CrystalTypes.NEWLINE) {
            sibling = sibling.prevSibling
        }
        return sibling
    }

    fun extractMethodName(callExpr: PsiElement): String? {
        var child = callExpr.firstChild
        var lastNameBeforeDot: String? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child.text
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameBeforeDot = child.text
            }
            child = child.nextSibling
        }
        return lastNameBeforeDot
    }

    fun findMethodNameElement(callExpr: PsiElement): PsiElement? {
        var child = callExpr.firstChild
        var lastNameElement: PsiElement? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameElement = child
            }
            child = child.nextSibling
        }
        return lastNameElement
    }

    fun findClassNameBeforeNew(callExpr: PsiElement): String? {
        if (callExpr is CrystalDotCallAccess) {
            if (extractMethodName(callExpr) != "new") return null
            var receiver = callExpr.prevSibling
            while (receiver is com.intellij.psi.PsiWhiteSpace) receiver = receiver.prevSibling
            val receiverType = receiver?.node?.elementType
            val receiverConstant = receiver?.node?.findChildByType(CrystalTypes.CONSTANT)
            if (receiverType == CrystalTypes.CONSTANT || receiverConstant != null) {
                return receiver.text
            }
            return null
        }

        var child = callExpr.firstChild
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && type == CrystalTypes.IDENTIFIER && child.text == "new") {
                var beforeDot = child.prevSibling
                while (beforeDot is com.intellij.psi.PsiWhiteSpace) beforeDot = beforeDot.prevSibling
                if (beforeDot?.node?.elementType == CrystalTypes.CONSTANT) {
                    return beforeDot.text
                }
            }
            child = child.nextSibling
        }
        return null
    }

    fun detectDotCall(argsElement: PsiElement): DotCallInfo? {
        val parent = argsElement.parent
        if (parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression) return null
        var sibling = argsElement.prevSibling
        while (sibling is com.intellij.psi.PsiWhiteSpace) sibling = sibling.prevSibling
        val methodNameNode = sibling ?: return null
        val methodType = methodNameNode.node?.elementType
        if (methodType != CrystalTypes.IDENTIFIER && methodType != CrystalTypes.CONSTANT) return null
        val methodNameElement = methodNameNode
        val methodName = methodNameNode.text
        sibling = methodNameNode.prevSibling
        while (sibling is com.intellij.psi.PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling?.node?.elementType != CrystalTypes.DOT) return null
        sibling = sibling.prevSibling
        while (sibling is com.intellij.psi.PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling == null) {
            val dotCallAccess = methodNameNode.parent
            if (dotCallAccess is CrystalDotCallAccess) {
                sibling = dotCallAccess.prevSibling
                while (sibling is com.intellij.psi.PsiWhiteSpace) sibling = sibling.prevSibling
            }
        }
        val receiverName = sibling?.text ?: return null
        return DotCallInfo(receiverName, methodName, methodNameElement)
    }
}
