package de.magynhard.crystal.ecr

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType
import de.magynhard.crystal.ecr.lexer.EmbeddedCrystalLexerFactory
import de.magynhard.crystal.ecr.parser.EmbeddedCrystalParser

class EmbeddedCrystalParserDefinition : ParserDefinition {
    companion object {
        val FILE = object : IFileElementType("ECR_FILE", EmbeddedCrystalLanguage) {}
    }

    override fun createLexer(project: Project?): Lexer = EmbeddedCrystalLexerFactory.create()
    override fun createParser(project: Project?): com.intellij.lang.PsiParser = EmbeddedCrystalParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens() =
        com.intellij.psi.tree.TokenSet.create(EmbeddedCrystalTokenTypes.WHITE_SPACE)
    override fun getCommentTokens() = com.intellij.psi.tree.TokenSet.EMPTY
    override fun getStringLiteralElements() = com.intellij.psi.tree.TokenSet.EMPTY
    override fun createElement(node: ASTNode): PsiElement =
        de.magynhard.crystal.ecr.EmbeddedCrystalTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider): com.intellij.psi.PsiFile =
        EmbeddedCrystalFile(viewProvider)
}
