package de.magynhard.crystal.analysis

import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.psi.CrystalTypes

internal data class CrystalDirectRequires(
    val paths: List<String>,
    val fingerprint: String,
)

internal object CrystalRequireCollector {
    fun collect(file: PsiFile): CrystalDirectRequires {
        val paths = PsiTreeUtil.findChildrenOfType(file, CrystalRequireStatement::class.java)
            .mapNotNull { statement ->
                if (!CrystalRequireSemantics.isValidTopLevel(statement)) return@mapNotNull null
                val stringExpression = statement.stringExpression ?: return@mapNotNull null
                if (stringExpression.expressionList.isNotEmpty()) return@mapNotNull null

                val text = stringExpression.text
                if (text.length < 2 || text.first() != '"' || text.last() != '"') return@mapNotNull null
                val quoteCount = stringExpression.node
                    .getChildren(TokenSet.create(CrystalTypes.STRING_LITERAL))
                    .count { it.text == "\"" }
                if (quoteCount != 2) return@mapNotNull null
                CrystalStringLiteralDecoder.decode(text.substring(1, text.lastIndex))
            }
        val fingerprint = paths.joinToString("|") { "${it.length}:$it" }
        return CrystalDirectRequires(paths, fingerprint)
    }
}
