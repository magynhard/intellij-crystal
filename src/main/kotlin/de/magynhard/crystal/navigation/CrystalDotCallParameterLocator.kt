package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalDotCallParameterLocator {

    internal fun extractIdentifierFromCallExpression(expression: PsiElement): String? {
        var child = expression.firstChild
        var foundDot = false
        var lastNameBeforeDot: String? = null
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

    internal fun findMethodNameFromSiblings(argsHolder: PsiElement): String? {
        var sibling = argsHolder.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling == null) return null

        val type = sibling.node?.elementType
        if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
            var beforeName = sibling.prevSibling
            while (beforeName is PsiWhiteSpace) beforeName = beforeName.prevSibling
            if (beforeName?.node?.elementType == CrystalTypes.DOT) return sibling.text
            return sibling.text
        }
        return null
    }

    internal fun findReceiverNameFromSiblings(argsHolder: PsiElement): String? {
        val anchor = argsHolder as? CrystalParameterInfoHandler.CrystalParameterInfoAnchor
        if (anchor != null) {
            var beforeDot = anchor.nameToken.prevSibling
            while (beforeDot is PsiWhiteSpace) beforeDot = beforeDot.prevSibling
            if (beforeDot?.node?.elementType != CrystalTypes.DOT) return null

            var receiver = beforeDot.prevSibling
            while (receiver is PsiWhiteSpace) receiver = receiver.prevSibling
            if (receiver == null) {
                val dotParent = beforeDot.parent
                if (dotParent is CrystalDotCallAccess) {
                    var prev = dotParent.prevSibling
                    while (prev is PsiWhiteSpace) prev = prev.prevSibling
                    receiver = prev
                }
            }
            if (receiver == null) return null

            if (receiver.node?.elementType == CrystalTypes.CONSTANT) return receiver.text
            val constantChild = receiver.node?.findChildByType(CrystalTypes.CONSTANT)
            if (constantChild != null) return constantChild.text
            return null
        }

        var sibling = argsHolder.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling == null) return null

        val methodNameType = sibling.node?.elementType
        if (methodNameType != CrystalTypes.IDENTIFIER && methodNameType != CrystalTypes.CONSTANT) return null

        var beforeName = sibling.prevSibling
        while (beforeName is PsiWhiteSpace) beforeName = beforeName.prevSibling
        if (beforeName?.node?.elementType != CrystalTypes.DOT) return null

        var receiver = beforeName.prevSibling
        while (receiver is PsiWhiteSpace) receiver = receiver.prevSibling
        if (receiver == null) {
            val dotParent = beforeName.parent
            if (dotParent is CrystalDotCallAccess) {
                var prev = dotParent.prevSibling
                while (prev is PsiWhiteSpace) prev = prev.prevSibling
                receiver = prev
            }
        }
        if (receiver == null) return null

        if (receiver.node?.elementType == CrystalTypes.CONSTANT) return receiver.text
        val constantChild = receiver.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) return constantChild.text
        return null
    }

    internal fun findClassNameBeforeNew(argsHolder: PsiElement): String? {
        val newToken = when {
            argsHolder is CrystalParameterInfoHandler.CrystalParameterInfoAnchor -> argsHolder.nameToken
            argsHolder is CrystalCallArgs || argsHolder is CrystalBareArgumentList -> {
                var sibling: PsiElement? = argsHolder.prevSibling
                while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
                if (sibling?.node?.elementType == CrystalTypes.IDENTIFIER && sibling.text == "new") sibling else null
            }
            else -> null
        } ?: return null

        var prev = PsiTreeUtil.prevLeaf(newToken)
        while (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        if (prev?.node?.elementType != CrystalTypes.DOT) return null

        prev = PsiTreeUtil.prevLeaf(prev)
        while (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        if (prev?.node?.elementType == CrystalTypes.CONSTANT) return prev.text
        val constantChild = prev?.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) return constantChild.text
        return null
    }
}
