package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalTypeText
import de.magynhard.crystal.psi.CrystalFunDefinition
import de.magynhard.crystal.psi.CrystalLibDefinition
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalTypeAliasLib
import de.magynhard.crystal.psi.CrystalTypePath
import de.magynhard.crystal.psi.CrystalTypeReference
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.parameterNameInfo
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * One positional-or-named parameter of a `lib fun` signature. Unnamed
 * type-only parameters (`Int32`, `Char*`) carry a null name; parameters
 * whose type cannot be named (proc fragments, untyped names) carry a null
 * type and are skipped by type comparison while still counting for arity.
 */
internal data class LibFunParameter(
    val name: String?,
    val typeText: String?,
)

internal data class LibFunSignature(
    val parameters: List<LibFunParameter>,
    val isVariadic: Boolean,
) {
    /** Groups duplicate declarations: same shape means the same verdicts. */
    fun key(): String = buildString {
        append(if (isVariadic) "variadic|" else "fixed|")
        append(parameters.joinToString(",") { "${it.name}:${it.typeText}" })
    }
}

/**
 * Totally-ordered view of a `lib fun` parameter list, or null for shapes the
 * checkers cannot model (bare `*`, macro interpolation/splicing, unbalanced
 * proc types). All `lib fun` parameters are required (the compiler rejects
 * defaults, splats, and bare separators), so the model needs no
 * optional/named-only/splat states.
 */
internal fun signatureOf(funDef: CrystalFunDefinition): LibFunSignature? {
    // No parentheses at all (bare external-symbol aliases like
    // `fun getch = GetChar`) carry no checkable shape: suppress.
    val list = funDef.parameterList ?: return null
    val kids = list.node.getChildren(null).map { it.psi }.filterNot {
        it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE
    }
    val parameters = mutableListOf<LibFunParameter>()
    var isVariadic = false
    var index = 0
    while (index < kids.size) {
        val child = kids[index]
        when {
            child.node.elementType == CrystalTypes.COMMA -> index++
            child.node.elementType == CrystalTypes.DOTDOTDOT -> {
                isVariadic = true
                index++
            }
            child is CrystalParameter -> {
                // Splat, block, and macro-spliced parameters have no FFI call
                // semantics the checkers could verify: suppress the signature.
                if (hasUnmodelableParameterShape(child)) return null
                val name = child.parameterNameInfo().callSiteName ?: return null
                parameters.add(LibFunParameter(name, child.typeReference?.text))
                index++
            }
            child is CrystalTypeReference || child is CrystalTypePath -> {
                // Unnamed type-only parameter (`Int32`, `Char*`) plus any
                // `|`-separated continuation types.
                val parts = mutableListOf(child.text)
                var next = index + 1
                while (next + 1 < kids.size &&
                    kids[next].node.elementType == CrystalTypes.PIPE &&
                    (kids[next + 1] is CrystalTypeReference || kids[next + 1] is CrystalTypePath)
                ) {
                    parts.add(kids[next + 1].text)
                    next += 2
                }
                parameters.add(LibFunParameter(null, parts.joinToString(" | ")))
                index = next
            }
            child.node.elementType == CrystalTypes.LPAREN -> {
                // Proc-typed unnamed parameter (`(Bio*, Int) -> Int`): counts
                // positionally, but its type stays out of comparisons.
                val end = matchParen(kids, index) ?: return null
                parameters.add(LibFunParameter(null, null))
                index = end + 1
            }
            else -> return null
        }
    }
    return LibFunSignature(parameters, isVariadic)
}

private fun hasUnmodelableParameterShape(param: CrystalParameter): Boolean {
    val node = param.node
    return node.findChildByType(CrystalTypes.STAR) != null ||
        node.findChildByType(CrystalTypes.DOUBLE_STAR) != null ||
        node.findChildByType(CrystalTypes.AMPERSAND) != null ||
        node.findChildByType(CrystalTypes.MACRO_INTERPOLATION) != null
}

private fun matchParen(kids: List<PsiElement>, open: Int): Int? {
    var depth = 0
    var index = open
    while (index < kids.size) {
        when (kids[index].node.elementType) {
            CrystalTypes.LPAREN -> depth++
            CrystalTypes.RPAREN -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return null
}

/**
 * FFI-only implicit conversions the general type compatibility must not
 * apply: a `String` converts to a C-char pointer (`Pointer(UInt8)`, including
 * `Char*` aliases resolving to `UInt8`), and `nil` is a valid null pointer
 * for any pointee. Verified against the compiler (`LibC.getenv("hello")`
 * compiles; the same shapes fail outside `lib fun` calls).
 */
internal fun isLibFunImplicitConversion(
    argType: String,
    paramType: String,
    context: PsiElement,
    funs: List<CrystalFunDefinition> = emptyList(),
): Boolean {
    val pointee = pointerPointee(paramType) ?: return false
    if (argType == "Nil") return true
    if (argType != "String") return false
    if (pointee == "UInt8") return true
    return resolvesToUInt8(pointee, context, funs)
}

/** Pointee of `Pointer(T)` / `T*` spellings, or null for anything else. */
internal fun pointerPointee(typeText: String): String? {
    val compact = typeText.replace(" ", "")
    if (compact.isEmpty()) return null
    if (compact.endsWith("*")) {
        val stem = compact.dropLast(1)
        if (stem.isEmpty() || stem.any { it in "()|,[]{}&" }) return null
        return stem
    }
    val (base, args) = CrystalTypeText.genericBaseAndArguments(compact) ?: return null
    if (base != "Pointer" || args.size != 1) return null
    return args.single().takeIf { it.isNotEmpty() }
}

/**
 * True when [pointee] names `UInt8` directly or through a require-visible
 * alias (`Char` via `alias Char = UInt8`). A lib-local alias in the called
 * `fun`'s own library shadows global same-name aliases (lexical scope), so
 * the owner libs are consulted first; lib bodies use the unstubbed
 * `type_alias_lib` shape, which the global alias index never sees.
 * Qualified spellings (`LibC::Char`) must resolve through the named library;
 * ambiguous targets stay false.
 */
private fun resolvesToUInt8(
    pointee: String,
    context: PsiElement,
    funs: List<CrystalFunDefinition>,
): Boolean {
    val clean = pointee.trim()
    if (clean == "UInt8" || clean == "::UInt8") return true
    val qualifier = clean.substringBeforeLast("::", "").takeIf { "::" in clean }
    val simpleName = clean.substringAfterLast("::")
    if (simpleName.isEmpty()) return false
    val ownerLibs = funs.mapNotNull { PsiTreeUtil.getParentOfType(it, CrystalLibDefinition::class.java) }
        .distinct()
    val localTargets = ownerLibs
        .filter { qualifier == null || CrystalPsiUtils.libOwnerQualifiedName(it) == qualifier.removePrefix("::") }
        .flatMap { libLocalAliasTargets(it, simpleName) }
        .distinct()
    if (localTargets.isNotEmpty()) {
        return localTargets.size == 1 && localTargets.single() == "UInt8"
    }
    return try {
        val project = context.project
        val scope = GlobalSearchScope.allScope(project)
        val service = CrystalRequireGraphService.getInstance(project)
        if (service.isProgramLessInjection(context)) return false
        val sources = service.effectiveSources(context).takeIf { it.files.isNotEmpty() }
            ?: return false
        val targets = CrystalIndexService.findAliases(simpleName, project, scope)
            .filter { alias ->
                val aliasName = alias.name ?: return@filter false
                if (!CrystalRequireVisibility.isAliasNameVisible(aliasName, project, scope, sources)) {
                    return@filter false
                }
                if (qualifier != null &&
                    CrystalPsiUtils.libOwnerQualifiedName(alias) != qualifier.removePrefix("::")
                ) {
                    return@filter false
                }
                true
            }
            .mapNotNull { it.typeReference?.text?.trim()?.removePrefix("::") }
            .distinct()
        targets.size == 1 && targets.single() == "UInt8"
    } catch (_: Throwable) {
        false
    }
}

/**
 * Targets of `alias <name> = <target>` declarations directly owned by [lib]
 * (the `type_alias_lib` shape has no stub, so the global alias index cannot
 * serve them). Nested libraries re-anchor the search to themselves and are
 * excluded here.
 */
private fun libLocalAliasTargets(lib: CrystalLibDefinition, name: String): List<String> =
    PsiTreeUtil.findChildrenOfType(lib, CrystalTypeAliasLib::class.java)
        .filter { PsiTreeUtil.getParentOfType(it, CrystalLibDefinition::class.java) == lib }
        .filter { decl ->
            decl.node.getChildren(null).any { leaf ->
                leaf.elementType == CrystalTypes.CONSTANT && leaf.text == name
            }
        }
        .map { it.typeReference.text.trim().removePrefix("::") }
