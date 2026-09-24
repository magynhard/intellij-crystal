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
            // v17: empty tight brackets after an expression parse as a zero-arity
            // `[]` call (`Int64[]` / `foo[]`) instead of an empty array literal, so
            // persisted indexes must rebuild.
            // v18: macro-generated type names (`struct {{num.id}}`) parse as real
            // type definitions and contribute interpolation-compound index keys.
            // v19: explicitly qualified method receivers (`def Time::Location.new`)
            // parse with the receiver path in the header; method stubs carry the
            // explicit receiver as the owner, constant receivers classify as
            // self (static) methods, and method names resolve to the target
            // after the receiver DOT — all three change index keys.
            // v20: constant declarations (file top level, type and lib bodies,
            // visibility modifiers) carry stubs and populate the constant and
            // constant-by-owner indexes; statement-context assignments stay
            // unstubbed.
            override fun getStubVersion(): Int = 20
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
