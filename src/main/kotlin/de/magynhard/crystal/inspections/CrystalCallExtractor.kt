package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*

data class DotCallInfo(
    val receiverName: String,
    val methodName: String,
    val methodNameElement: PsiElement?
)

object CrystalCallExtractor {

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
