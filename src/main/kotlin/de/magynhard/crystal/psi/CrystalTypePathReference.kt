package de.magynhard.crystal.psi

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.analysis.CrystalEffectiveSourceSet
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalStaticDeclarations
import de.magynhard.crystal.stubs.CrystalIndexService

/** Each constant token owns its navigation range, including FFI namespace members. */
class CrystalTypePathReference private constructor(
    private val path: CrystalTypePath,
    private val parts: List<String>,
    private val absolute: Boolean,
    range: TextRange,
) : PsiPolyVariantReferenceBase<CrystalTypePath>(path, range, true) {
    override fun resolve(): PsiElement? = multiResolve(false).singleOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        resolveTargets().map { PsiElementResolveResult(it) as ResolveResult }.toTypedArray()

    private fun resolveTargets(): List<PsiElement> {
        if (DumbService.isDumb(path.project) || CrystalMacroContext.isInMacroContext(path)) return emptyList()
        if (!absolute) {
            CrystalForallBinding.findDeclaration(parts.first(), path)?.let {
                return if (parts.size == 1) listOf(it) else emptyList()
            }
        }
        val sources = CrystalRequireGraphService.getInstance(path.project).effectiveSources(path)
        if (sources.files.isEmpty()) return emptyList()
        val scope = GlobalSearchScope.allScope(path.project)
        val ffiPath = (if (absolute) "::" else "") + parts.joinToString("::")
        CrystalFfiTypeResolver.resolve(ffiPath, path, sources)?.let { return it }

        // Ordinary type paths retain the shared session's lexical/exact lookup.
        val prefix = parts.joinToString("::")
        val session = CrystalTypeSetResolver.session(path)
        session.resolveType(if (absolute) "::$prefix" else prefix, path)?.let { identity ->
            val declarations = session.findExactTypeDeclarations(identity)
            if (declarations.isNotEmpty()) return listOfNotNull(stableTarget(declarations))
        }
        aliasTarget(prefix, sources)?.let { return listOf(it) }
        // Static-array sizes and other value type arguments also use type_path.
        val constants = CrystalIndexService.findConstants(parts.last(), path.project, scope)
            .filter { CrystalRequireVisibility.isConstantCandidateVisible(it, sources, path,
                qualifiedAccess = absolute || parts.size > 1) }
        for (identity in identities(prefix)) {
            val matches = constants.filter { declaration ->
                val owner = declaration.stub?.ownerQualifiedName ?: CrystalPsiUtils.constantOwnerQualifiedName(declaration)
                (owner?.let { "$it::${declaration.name}" } ?: declaration.name) == identity
            }
            if (matches.isNotEmpty()) return matches
        }
        return emptyList()
    }

    private fun identities(prefix: String): Set<String> =
        if (absolute || prefix.contains("::")) setOf(prefix)
        else CrystalPsiUtils.buildLexicalQualifiedNameCandidates(prefix, path)

    private fun aliasTarget(prefix: String, sources: CrystalEffectiveSourceSet): PsiElement? {
        val candidates = CrystalIndexService.findAliases(
            parts.last(), path.project, GlobalSearchScope.allScope(path.project)
        ).filter(sources::contains)
        for (identity in identities(prefix)) {
            val matches = candidates.filter { CrystalPsiUtils.buildQualifiedName(it) == identity }
            if (matches.isNotEmpty()) return matches.singleOrNull()
        }
        return null
    }

    private fun stableTarget(candidates: List<PsiElement>): PsiElement? = candidates.minWithOrNull(
        compareBy<PsiElement> { CrystalStaticDeclarations.sourceFile(it) != CrystalStaticDeclarations.sourceFile(path) }
            .thenBy { CrystalStaticDeclarations.sourceFile(it)?.virtualFile?.url.orEmpty() }
            .thenBy { CrystalStaticDeclarations.sourceOffset(it) }
    )

    override fun handleElementRename(newElementName: String): PsiElement {
        val token = path.node.getChildren(null).firstOrNull {
            it.startOffset - path.textOffset == rangeInElement.startOffset
        } ?: return path
        val replacement = createLeafFromText(path.project, newElementName, CrystalTypes.CONSTANT) ?: return path
        token.treeParent.replaceChild(token, replacement)
        return path
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private val PATH = Regex("(?:::)?[\\p{Lu}][\\p{L}\\p{N}_]*(?:::[\\p{Lu}][\\p{L}\\p{N}_]*)*")

        fun create(path: CrystalTypePath): Array<PsiReference> {
            // Interpolated/incomplete paths do not prove an exact declaration identity.
            val text = path.text.filterNot(Char::isWhitespace)
            if (!PATH.matches(text)) return PsiReference.EMPTY_ARRAY
            val tokens = path.node.getChildren(null).filter { it.elementType == CrystalTypes.CONSTANT }
            return tokens.mapIndexed { index, token ->
                val start = token.startOffset - path.textOffset
                CrystalTypePathReference(path, tokens.take(index + 1).map { it.text }, text.startsWith("::"),
                    TextRange(start, start + token.textLength)) as PsiReference
            }.toTypedArray()
        }
    }
}
