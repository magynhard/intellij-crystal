package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.inspections.CrystalTypeCompatibility
import de.magynhard.crystal.psi.CrystalIncludeStatement
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalTypeParameters
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Generic include-edge compatibility for argument type checking: Crystal
 * accepts `use "/multi", [TestHeaderHandler.new(...), ...]` against an
 * `Enumerable(HTTP::Handler)` parameter because `Array(T)` includes
 * `Indexable::Mutable(T)` → `Indexable(T)` → `Enumerable(T)` transitively,
 * with the include's type parameter substituted by the includer's own. A
 * plain string comparison of `Array(TestHeaderHandler)` against
 * `Enumerable(HTTP::Handler)` cannot see that chain.
 *
 * The traversal resolves the argument base's declarations through the
 * require-graph lens, walks include statements transitively (include
 * arguments that reference the includer's own type parameters are
 * substituted with the caller's actual type arguments), and reports
 * compatible when a reached include's base matches the parameter's generic
 * base and the substituted type arguments are compatible. Anything
 * unresolvable stays null so callers keep their existing verdicts — this
 * helper can only turn a definite mismatch into an acceptance, never the
 * other way around.
 */
internal object CrystalGenericIncludeCompat {

    private const val MAX_DEPTH = 8

    /**
     * Pseudo type argument standing for an unresolved generic instantiation
     * (`paragraph : Array` is `Array(_)` in Crystal). It comes only from
     * [wildcardArgs] — no crystal source can contain it as a type name.
     */
    internal const val WILDCARD_ARG = "_"

    fun includeEdgeCompatible(argType: String, paramType: String, context: PsiElement): Boolean? {
        val argBase = argType.substringBefore("(").trim().substringAfterLast("::")
        val paramBase = paramType.substringBefore("(").trim().substringAfterLast("::")
        val argArgs = splitGenericArgs(argType) ?: wildcardArgs(argBase, context)
            ?: return null
        if (argArgs.isEmpty()) return null
        val paramArgs = splitGenericArgs(paramType) ?: wildcardArgs(paramBase, context) ?: return null
        if (paramArgs.isEmpty()) return null
        if (argBase == paramBase) return null

        val sources = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context)
        if (sources.files.isEmpty()) return null
        val visited = mutableSetOf<Pair<String, List<String>>>()
        return walk(argBase, argArgs, paramBase, paramArgs, context.project, sources, visited, 0)
    }

    /**
     * Bare generic forms on either side model the Crystal restriction `X` as
     * `X(_)`: the arity is taken from the base's own type parameters (require-
     * effective resolved), and the synthetic wildcard later matches every
     * argument- or param-side element through [CrystalTypeCompatibility].
     * Anything that cannot resolve to exactly one arity stays `null` so
     * callers keep their existing verdicts.
     */
    private fun wildcardArgs(base: String, context: PsiElement): List<String>? {
        val sources = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context)
        if (sources.files.isEmpty()) return null
        val simpleName = base.substringAfterLast("::")
        val declarations = CrystalIndexService.findTypes(simpleName, context.project, GlobalSearchScope.allScope(context.project))
            .filter(sources::contains)
            .filter { declaration ->
                val qualified = CrystalPsiUtils.buildQualifiedName(declaration)
                qualified == base || qualified?.endsWith("::$base") == true
            }
        if (declarations.isEmpty()) return null
        val arities = declarations.map(::typeParameterNames).map { it.size }.distinct()
        if (arities.size != 1 || arities.single() == 0) return null
        return List(arities.single()) { WILDCARD_ARG }
    }

    private fun walk(
        base: String,
        actualArgs: List<String>,
        paramBase: String,
        paramArgs: List<String>,
        project: com.intellij.openapi.project.Project,
        sources: CrystalEffectiveSourceSet,
        visited: MutableSet<Pair<String, List<String>>>,
        depth: Int,
    ): Boolean? {
        if (depth > MAX_DEPTH) return null
        if (!visited.add(base to actualArgs)) return null

        val simpleName = base.substringAfterLast("::")
        val declarations = CrystalIndexService.findTypes(simpleName, project, GlobalSearchScope.allScope(project))
            .filter(sources::contains)
            .filter { declaration ->
                val qualified = CrystalPsiUtils.buildQualifiedName(declaration)
                qualified == base || qualified?.endsWith("::$base") == true
            }
        if (declarations.isEmpty()) return null

        for (declaration in declarations) {
            val typeParameters = typeParameterNames(declaration)
            for (include in PsiTreeUtil.findChildrenOfType(declaration, CrystalIncludeStatement::class.java)) {
                val includeText = include.typeReference?.text?.filterNot(Char::isWhitespace) ?: continue
                val includeBase = includeText.substringBefore("(").trim().substringAfterLast("::")
                val includeArgs = splitGenericArgs(includeText) ?: continue

                // Substitute the include's references to the includer's own type
                // parameters with the caller's actual arguments (positionally).
                val effectiveArgs = includeArgs.map { includeArg ->
                    val position = typeParameters.indexOf(includeArg)
                    if (position >= 0 && position < actualArgs.size) actualArgs[position] else includeArg
                }

                if (includeBase == paramBase) {
                    if (effectiveArgs.size == paramArgs.size &&
                        effectiveArgs.zip(paramArgs).all { (actual, param) ->
                            CrystalTypeCompatibility.isCompatible(actual, param)
                        }
                    ) {
                        return true
                    }
                    continue
                }

                walk(includeBase, effectiveArgs, paramBase, paramArgs, project, sources, visited, depth + 1)
                    ?.let { return true }
            }
        }
        return null
    }

    private fun typeParameterNames(declaration: PsiElement): List<String> {
        val parameters = PsiTreeUtil.getChildOfType(declaration, CrystalTypeParameters::class.java) ?: return emptyList()
        val list = parameters.typeReferenceList.map { it.text }
        if (list.isNotEmpty()) return list
        // PSI fallback: some parse shapes expose the parameter list only as text
        // ("(T)" with no typed children), so split the "("...")" text directly.
        val text = parameters.text.trim()
        if (!text.startsWith("(")) return emptyList()
        val inner = text.substring(1, text.length - 1)
        if (inner.isBlank()) return emptyList()
        val names = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (char in inner) {
            when (char) {
                '(', '[', '{' -> { depth++; current.append(char) }
                ')', ']', '}' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) { names.add(current.toString()); current = StringBuilder() }
                    else current.append(char)
                }
                else -> current.append(char)
            }
        }
        names.add(current.toString())
        return names.filter { it.isNotBlank() }.map { token ->
            // `STAR CONSTANT [ASSIGN type_reference]` items: only the name counts.
            token.trim().removePrefix("*").trim().substringBefore('=').trim()
        }
    }

    /** Splits the argument list of a generic type text; null when no parens. */
    private fun splitGenericArgs(text: String): List<String>? {
        val start = text.indexOf('(')
        if (start < 0) return null
        val end = text.lastIndexOf(')')
        if (end <= start) return null
        val inner = text.substring(start + 1, end)
        if (inner.isBlank()) return emptyList()
        val args = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (char in inner) {
            when (char) {
                '(', '{', '[' -> { depth++; current.append(char) }
                ')', '}', ']' -> { depth--; current.append(char) }
                ',' -> if (depth == 0) { args.add(current.toString().trim()); current = StringBuilder() } else current.append(char)
                else -> current.append(char)
            }
        }
        args.add(current.toString().trim())
        return args.filter { it.isNotEmpty() }
    }
}
