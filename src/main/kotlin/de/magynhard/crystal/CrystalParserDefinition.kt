package de.magynhard.crystal

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.lexer.CrystalTokenTypes

class CrystalParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType(CrystalLanguage)
    }

    override fun createLexer(project: Project?): Lexer = CrystalLexerAdapter()

    override fun createParser(project: Project?): PsiParser {
        // Minimal parser - just wraps all tokens under root
        return PsiParser { root, builder ->
            val marker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            marker.done(root)
            builder.treeBuilt
        }
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = CrystalTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = CrystalTokenTypes.STRINGS

    override fun createElement(node: ASTNode?): PsiElement =
        throw UnsupportedOperationException("Not yet implemented")

    override fun createFile(viewProvider: FileViewProvider): PsiFile = CrystalFile(viewProvider)
}
