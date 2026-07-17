package de.magynhard.crystal.stubs

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalTypes

class CrystalStubBuilder : StubBuilder {

    private companion object {
        val LOG = Logger.getInstance(CrystalStubBuilder::class.java)

        // Stdlib root per project. Stdlib stubs are stored under an internal
        // SyntheticLibrary scope that no GlobalSearchScope can query (verified), so
        // indexing them is pure wasted CPU — and during first project open it starves
        // other plugins' indexing (e.g. the Database plugin's .groovy schema parse).
        // We skip building stubs for stdlib files; stdlib Go to Definition is served
        // by a bounded VFS scan cache (CrystalReference) that does not need stubs.
        val stdlibRoots = java.util.concurrent.ConcurrentHashMap<Project, String?>()

        fun stdlibRootFor(project: Project): String? {
            stdlibRoots[project]?.let { return it }
            val root = runCatching { CrystalStdlibResolver.resolveStdlibPath(project)?.path }.getOrNull()
            stdlibRoots[project] = root
            return root
        }
    }

    private fun isStdlibFile(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return false
        val root = stdlibRootFor(file.project) ?: return false
        return vf.path.startsWith(root)
    }


        val STUB_KEYWORDS = setOf(
            CrystalTypes.CLASS, CrystalTypes.MODULE, CrystalTypes.STRUCT,
            CrystalTypes.ENUM, CrystalTypes.DEF, CrystalTypes.MACRO
        )

        val BLOCK_KEYWORDS = setOf(
            CrystalTypes.LIB, CrystalTypes.CASE, CrystalTypes.BEGIN, CrystalTypes.SELECT
        )

        val CONDITIONAL_KEYWORDS = setOf(
            CrystalTypes.IF, CrystalTypes.UNLESS, CrystalTypes.WHILE, CrystalTypes.UNTIL, CrystalTypes.FOR
        )

    override fun buildStubTree(file: PsiFile): StubElement<*> {
        if (file.fileType !is de.magynhard.crystal.CrystalFileType) {
            return PsiFileStubImpl(file)
        }
        if (isStdlibFile(file)) {
            return PsiFileStubImpl(file)
        }
        val content = file.viewProvider.contents
        val lexer = CrystalLexerAdapter()
        lexer.start(content)

        val fileStub = PsiFileStubImpl(file)
        val stack = ArrayDeque<StubElement<*>>()
        stack.addLast(fileStub)

        var atStatementStart = true

        // Defensive guard against a non-advancing lexer token hanging the index pass:
        // every token consumes at least one character, so the loop can run at most
        // content.length times. If that is exceeded we abort rather than freeze indexing.
        val maxIterations = content.length + 1
        var iterations = 0
        val startTime = System.nanoTime()
        // Hard wall-clock budget per file. A single pathological file must never be
        // able to freeze the entire project indexing pass.
        val timeoutNs = 20_000_000_000L // 20 seconds

        while (lexer.tokenType != null) {
            if (iterations++ > maxIterations) {
                LOG.warn("CRYSTAL: stub builder exceeded expected token count for ${file.name} — possible non-advancing lexer token; aborting stub build for this file")
                break
            }
            if (System.nanoTime() - startTime > timeoutNs) {
                LOG.warn("CRYSTAL: stub build for ${file.name} exceeded ${timeoutNs / 1_000_000}ms budget; aborting stub build for this file")
                break
            }
            val tt: IElementType = lexer.tokenType ?: break

            when {
                tt == CrystalTokenTypes.WHITE_SPACE || tt == CrystalTypes.LINE_COMMENT -> {
                    lexer.advance()
                }
                tt == CrystalTypes.NEWLINE -> {
                    atStatementStart = true
                    lexer.advance()
                }
                tt in STUB_KEYWORDS -> {
                    lexer.advance()

                    val name: String?
                    when (tt) {
                        CrystalTypes.DEF -> name = readDefName(lexer, content)
                        CrystalTypes.MACRO -> name = readName(lexer, content)
                        else -> {
                            val (n, _) = readTypeName(lexer, content)
                            name = n
                        }
                    }

                    val stub = createStub(tt, name, stack.last())
                    stack.addLast(stub)
                    atStatementStart = false
                }
                tt in BLOCK_KEYWORDS -> {
                    lexer.advance()
                    stack.addLast(stack.last())
                    atStatementStart = false
                }
                tt in CONDITIONAL_KEYWORDS -> {
                    if (atStatementStart) {
                        lexer.advance()
                        stack.addLast(stack.last())
                    } else {
                        lexer.advance()
                    }
                    atStatementStart = false
                }
                tt == CrystalTypes.DO -> {
                    lexer.advance()
                    stack.addLast(stack.last())
                    atStatementStart = false
                }
                tt == CrystalTypes.END -> {
                    if (stack.size > 1) stack.removeLast()
                    atStatementStart = true
                    lexer.advance()
                }
                else -> {
                    lexer.advance()
                    atStatementStart = false
                }
            }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        if (elapsedMs > 1000) {
            LOG.warn("CRYSTAL: slow stub build for ${file.name}: ${elapsedMs}ms (iterations=$iterations)")
        }

        return fileStub
    }

    override fun skipChildProcessingWhenBuildingStubs(parent: ASTNode, child: ASTNode): Boolean {
        return true
    }

    private fun readName(lexer: CrystalLexerAdapter, content: CharSequence): String? {
        skipWs(lexer)
        val tt = lexer.tokenType
        if (tt == CrystalTypes.IDENTIFIER || tt == CrystalTypes.CONSTANT) {
            val name = content.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
            return name
        }
        return null
    }

    private fun readTypeName(lexer: CrystalLexerAdapter, content: CharSequence): Pair<String?, String?> {
        skipWs(lexer)
        if (lexer.tokenType != CrystalTypes.CONSTANT) return Pair(null, null)
        val first = content.substring(lexer.tokenStart, lexer.tokenEnd)
        lexer.advance()
        skipWs(lexer)
        if (lexer.tokenType == CrystalTypes.DOUBLE_COLON) {
            lexer.advance()
            skipWs(lexer)
            if (lexer.tokenType == CrystalTypes.CONSTANT) {
                val second = content.substring(lexer.tokenStart, lexer.tokenEnd)
                lexer.advance()
                return Pair(second, first)
            }
            return Pair(first, null)
        }
        return Pair(first, null)
    }

    private fun readDefName(lexer: CrystalLexerAdapter, content: CharSequence): String? {
        skipWs(lexer)
        val firstType = lexer.tokenType ?: return null

        fun suffix(): String {
            skipWs(lexer)
            val st = lexer.tokenType
            if (st == CrystalTypes.EQ || st == CrystalTypes.QUESTION) {
                val s = content.substring(lexer.tokenStart, lexer.tokenEnd)
                lexer.advance()
                return s
            }
            return ""
        }

        return when (firstType) {
            CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT -> {
                val name = content.substring(lexer.tokenStart, lexer.tokenEnd)
                lexer.advance()
                name + suffix()
            }
            CrystalTypes.SELF -> {
                lexer.advance()
                skipWs(lexer)
                if (lexer.tokenType == CrystalTypes.DOT) {
                    lexer.advance()
                    skipWs(lexer)
                    if (lexer.tokenType == CrystalTypes.IDENTIFIER || lexer.tokenType == CrystalTypes.CONSTANT) {
                        val name = content.substring(lexer.tokenStart, lexer.tokenEnd)
                        lexer.advance()
                        name + suffix()
                    } else null
                } else null
            }
            CrystalTypes.PLUS, CrystalTypes.MINUS, CrystalTypes.STAR,
            CrystalTypes.SLASH, CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT,
            CrystalTypes.AMPERSAND, CrystalTypes.PIPE, CrystalTypes.CARET,
            CrystalTypes.TILDE, CrystalTypes.DOUBLE_STAR,
            CrystalTypes.WRAP_PLUS, CrystalTypes.WRAP_MINUS,
            CrystalTypes.WRAP_STAR, CrystalTypes.WRAP_DOUBLE_STAR,
            CrystalTypes.LSHIFT, CrystalTypes.RSHIFT,
            CrystalTypes.EQ, CrystalTypes.NEQ, CrystalTypes.LT, CrystalTypes.GT,
            CrystalTypes.LTE, CrystalTypes.GTE, CrystalTypes.SPACESHIP,
            CrystalTypes.CASE_EQ, CrystalTypes.MATCH_OP, CrystalTypes.BANG,
            CrystalTypes.DOT, CrystalTypes.DOTDOT, CrystalTypes.DOTDOTDOT,
            CrystalTypes.ARROW, CrystalTypes.LBRACKET, CrystalTypes.RBRACKET -> {
                val name = content.substring(lexer.tokenStart, lexer.tokenEnd)
                lexer.advance()
                name
            }
            else -> null
        }
    }

    private fun skipWs(lexer: CrystalLexerAdapter) {
        while (lexer.tokenType != null &&
            (lexer.tokenType == CrystalTokenTypes.WHITE_SPACE ||
             lexer.tokenType == CrystalTypes.NEWLINE ||
             lexer.tokenType == CrystalTypes.LINE_COMMENT)
        ) {
            lexer.advance()
        }
    }

    private fun createStub(
        keywordType: IElementType,
        name: String?,
        parent: StubElement<*>
    ): StubElement<*> {
        if (name == null) return parent

        return when (keywordType) {
            CrystalTypes.CLASS -> CrystalClassDefinitionStub(
                parent, CrystalClassDefinitionElementType("CLASS_DEFINITION"), name, null
            )
            CrystalTypes.MODULE -> CrystalModuleDefinitionStub(
                parent, CrystalModuleDefinitionElementType("MODULE_DEFINITION"), name, null
            )
            CrystalTypes.STRUCT -> CrystalStructDefinitionStub(
                parent, CrystalStructDefinitionElementType("STRUCT_DEFINITION"), name, null
            )
            CrystalTypes.ENUM -> CrystalEnumDefinitionStub(
                parent, CrystalEnumDefinitionElementType("ENUM_DEFINITION"), name, null
            )
            CrystalTypes.DEF -> CrystalMethodDefinitionStub(
                parent, CrystalMethodDefinitionElementType("METHOD_DEFINITION"), name
            )
            CrystalTypes.MACRO -> CrystalMacroDefinitionStub(
                parent, CrystalMacroDefinitionElementType("MACRO_DEFINITION"), name
            )
            else -> parent
        }
    }
}
