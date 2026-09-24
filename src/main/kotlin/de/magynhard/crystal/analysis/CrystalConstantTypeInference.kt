package de.magynhard.crystal.analysis

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.psi.CrystalExpression
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.CrystalVariableReference
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Type inference for constant declarations.
 *
 * Unlike locals, constants need no flow analysis: Crystal assigns them once,
 * so the type is the type of the right-hand side expression. Conditionally
 * defined constants (one declaration per branch) union their branch types.
 * Bare `CONSTANT`-to-`CONSTANT` chains (`X = KODORRA`) recurse with a cycle
 * guard. Anything uninferable (macro-generated, unresolved calls) yields no
 * names — callers render the honest `Unknown` placeholder instead of guessing.
 */
object CrystalConstantTypeInference {

    private const val MAX_CHAIN_DEPTH = 8

    /**
     * Inferred type names for the constant [name] visible from [context]:
     * same-file declarations first (document order), then require-visible
     * index declarations. Returns null when nothing is inferable.
     */
    fun inferType(name: String, context: PsiElement): List<String>? {
        val types = inferFromDeclarations(visibleDeclarations(name, context), context, emptySet())
        return types.takeIf { it.isNotEmpty() }
    }

    /**
     * Inferred type names for a single [declaration]. Used when the hover
     * target is already resolved; sibling branch declarations union in.
     */
    fun inferType(declaration: CrystalConstantAssignment, context: PsiElement): List<String>? {
        val name = declaration.stub?.name ?: declaration.name ?: return null
        return inferType(name, context)
    }

    /**
     * Literal right-hand-side text for value display (`KODORRA = 123` renders
     * `= 123`), or null unless [declaration] is the only visible declaration
     * and its right-hand side is a single literal.
     */
    fun literalValue(declaration: CrystalConstantAssignment, context: PsiElement): String? {
        val name = declaration.stub?.name ?: declaration.name ?: return null
        if (visibleDeclarations(name, context).size != 1) return null
        val rhs = declaration.expression ?: return null
        return literalValueText(rhs)
    }

    private fun inferFromDeclarations(
        declarations: List<CrystalConstantAssignment>,
        context: PsiElement,
        visited: Set<String>,
    ): List<String> {
        val types = mutableListOf<String>()
        for (declaration in declarations) {
            for (name in rhsTypeNames(declaration, context, visited)) {
                if (name !in types) types.add(name)
            }
        }
        return types
    }

    private fun rhsTypeNames(
        declaration: CrystalConstantAssignment,
        context: PsiElement,
        visited: Set<String>,
    ): List<String> {
        val rhs = declaration.expression ?: return emptyList()
        // `X = KODORRA`: follow the chain instead of asking the variable
        // flow (which has no constant branch).
        val chained = bareConstantName(rhs)
        if (chained != null) {
            if (visited.size >= MAX_CHAIN_DEPTH) return emptyList()
            val key = declarationKey(declaration) ?: return emptyList()
            if (key in visited) return emptyList()
            return inferFromDeclarations(visibleDeclarations(chained, declaration), declaration, visited + key)
        }
        return when (val resolved = CrystalTypeSetResolver.resolve(rhs)) {
            is CrystalTypeResolution.Known -> resolved.types.map { it.name }
            else -> emptyList()
        }
    }

    private fun visibleDeclarations(
        name: String,
        context: PsiElement,
    ): List<CrystalConstantAssignment> {
        val file = context.containingFile ?: return emptyList()
        val project = context.project
        val declarations = mutableListOf<CrystalConstantAssignment>()
        val seen = mutableSetOf<String>()
        PsiTreeUtil.findChildrenOfType(file, CrystalConstantAssignment::class.java)
            .filter { (it.stub?.name ?: it.name) == name }
            .sortedBy { it.textRange.startOffset }
            .forEach {
                declarationKey(it)?.let(seen::add)
                declarations.add(it)
            }
        if (DumbService.isDumb(project)) return declarations
        try {
            val service = CrystalRequireGraphService.getInstance(project)
            if (service.isProgramLessInjection(context)) return declarations
            val sources = service.effectiveSources(context).takeIf { it.files.isNotEmpty() } ?: return declarations
            val contextFile = file.originalFile.virtualFile
            val scope = GlobalSearchScope.allScope(project)
            CrystalIndexService.findConstants(name, project, scope)
                .filter { CrystalRequireVisibility.isConstantCandidateVisible(it, sources, contextFile) }
                .sortedBy { it.containingFile?.name ?: "" }
                .forEach {
                    if (declarationKey(it)?.let(seen::add) != false) declarations.add(it)
                }
        } catch (_: Throwable) {
            // Index or require graph unavailable — same-file declarations stand.
        }
        return declarations
    }

    private fun declarationKey(declaration: CrystalConstantAssignment): String? {
        val path = declaration.containingFile?.originalFile?.virtualFile?.path ?: return null
        return "$path:${declaration.textRange.startOffset}"
    }

    /**
     * The referenced constant name when [rhs] is exactly one bare `CONSTANT`
     * read (`X = KODORRA`), or null for anything else. Descends transparent
     * single-child composites; member access (`A::B`, `x.y`) never qualifies.
     */
    private fun bareConstantName(rhs: CrystalExpression): String? {
        var current: PsiElement = rhs
        while (true) {
            if (current is CrystalVariableReference) {
                val leaf = current.node.findChildByType(CrystalTypes.CONSTANT)?.psi ?: return null
                return leaf.text.takeIf { it == rhs.text.trim() }
            }
            val children = current.children.filterNot { it is PsiWhiteSpace }
            if (children.size != 1) return null
            current = children.single()
        }
    }

    /**
     * Source text of a single literal right-hand side (numbers, chars,
     * symbols, interpolation-free strings, true/false/nil), or null.
     *
     * Walks AST nodes (not PSI children: tokens are never PSI elements, so a
     * lone `123` has no PSI children at all) down transparent single-child
     * composites to the literal leaf.
     */
    private fun literalValueText(rhs: CrystalExpression): String? {
        var node: ASTNode? = rhs.node ?: return null
        while (node != null) {
            if (node is LeafElement) {
                if (node.elementType !in VALUE_LITERAL_TOKENS) return null
                return node.text.takeIf { it.length <= 60 && '\n' !in it }
            }
            val kids = node.getChildren(null).filterNot {
                it.elementType == TokenType.WHITE_SPACE || it.elementType == CrystalTypes.NEWLINE
            }
            if (kids.size != 1) {
                // Strings/symbols with plain content: interpolation tags lex
                // as direct children (the interpolation rule is inlined), so
                // their absence proves a static value. Anything else composed
                // (calls, operators, collections) has no single display value.
                val element = node.psi ?: return null
                if (!isPlainStringShape(element)) return null
                if (hasInterpolation(element)) return null
                val text = element.text.trim()
                if (text.length > 60 || '\n' in text) return null
                return text
            }
            node = kids.single()
        }
        return null
    }

    private fun hasInterpolation(element: PsiElement): Boolean {
        var child = element.node.firstChildNode
        while (child != null) {
            val type = child.elementType
            if (type == CrystalTypes.STRING_INTERPOLATION_BEGIN ||
                type == CrystalTypes.MACRO_INTERPOLATION_BEGIN
            ) {
                return true
            }
            child = child.treeNext
        }
        return false
    }

    private fun isPlainStringShape(element: PsiElement): Boolean {
        val type = element.node?.elementType
        return type == CrystalTypes.STRING_EXPRESSION || type == CrystalTypes.SYMBOL_STRING_EXPRESSION
    }

    private val VALUE_LITERAL_TOKENS = setOf(
        CrystalTypes.INTEGER_LITERAL,
        CrystalTypes.FLOAT_LITERAL,
        CrystalTypes.CHAR_LITERAL,
        CrystalTypes.SYMBOL_LITERAL,
        CrystalTypes.TRUE,
        CrystalTypes.FALSE,
        CrystalTypes.NIL,
    )
}
