package de.magynhard.crystal

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.parser.CrystalParser
import de.magynhard.crystal.psi.CrystalTypes

class CrystalParserDefinition : ParserDefinition {

    companion object {
        val FILE = object : IStubFileElementType<PsiFileStub<CrystalFile>>(CrystalLanguage) {
            override fun getExternalId(): String = "crystal.FILE"
            override fun getStubVersion(): Int = 2
        }
    }

    override fun createLexer(project: Project?): Lexer = CrystalLexerAdapter()

    override fun createParser(project: Project?): PsiParser = CrystalParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(CrystalTokenTypes.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = CrystalTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = CrystalTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = CrystalTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = CrystalFile(viewProvider)
}
