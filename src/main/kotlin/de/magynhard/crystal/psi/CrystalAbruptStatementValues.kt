package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

interface CrystalAbruptStatement : PsiElement {
    val heredocBodies: CrystalHeredocBodies?
}

internal fun CrystalAbruptStatement.valueElements(): List<PsiElement> = children.filter {
    it is CrystalAssignment || it is CrystalExpression
}

internal fun CrystalAbruptStatement.heredocBodyBindings(): List<Pair<PsiElement, CrystalHeredocLiteral>> {
    val headers = valueElements().flatMap { value ->
        PsiTreeUtil.collectElements(value) {
            it.node.elementType == CrystalTypes.HEREDOC_START && it.text.startsWith("<<-")
        }.asList()
    }
    return headers.zip(heredocBodies?.heredocLiteralList.orEmpty())
}
