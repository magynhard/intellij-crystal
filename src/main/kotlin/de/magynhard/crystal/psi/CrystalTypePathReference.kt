package de.magynhard.crystal.psi

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalEffectiveSourceSet
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
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
        val sources = CrystalRequireGraphService.getInstance(path.project).effectiveSources(path)
        if (sources.files.isEmpty()) return emptyList()
        val scope = GlobalSearchScope.allScope(path.project)
        if (!absolute && parts.size == 1) {
            val enclosing = PsiTreeUtil.getParentOfType(path, CrystalLibDefinition::class.java)
            if (enclosing != null && sources.contains(enclosing)) {
                val localMembers = libraryMembers(listOf(enclosing), parts.single(), sources)
                if (localMembers.isNotEmpty()) return localMembers
            }
        }

        // Prove the exact library prefix before looking up its member. Missing
        // members never fall back to an unrelated type with the same simple name.
        for (length in parts.size downTo 1) {
            val prefix = parts.take(length).joinToString("::")
            val libraries = CrystalIndexService.findLibs(parts[length - 1], path.project, scope)
                .filter(sources::contains)
            for (identity in identities(prefix)) {
                val matches = libraries.filter { CrystalPsiUtils.libQualifiedName(it) == identity }
                if (matches.isEmpty()) continue
                if (length == parts.size) return matches
                // FFI struct/union/alias nodes are not ordinary type stubs.
                // Inspect only the bodies of these exact indexed libraries.
                if (length + 1 != parts.size) return emptyList()
                return libraryMembers(matches, parts.last(), sources, qualifiedAccess = true)
            }
        }

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

    private fun libraryMembers(
        libraries: List<CrystalLibDefinition>,
        name: String,
        sources: CrystalEffectiveSourceSet,
        qualifiedAccess: Boolean = false,
    ): List<PsiElement> = libraries.flatMap { it.libBody?.children.orEmpty().toList() }
        .filter { member ->
            (member is CrystalLibStructDefinition || member is CrystalLibUnionDefinition ||
                member is CrystalTypeAliasLib || member is CrystalLibTypeAlias ||
                member is CrystalEnumDefinition || member is CrystalConstantAssignment) &&
                member.node.findChildByType(CrystalTypes.CONSTANT)?.text == name &&
                (member !is CrystalConstantAssignment ||
                    CrystalRequireVisibility.isConstantCandidateVisible(member, sources, path, qualifiedAccess))
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
        compareBy<PsiElement> { it.containingFile != path.containingFile }
            .thenBy { it.containingFile?.virtualFile?.url.orEmpty() }
            .thenBy { it.textOffset }
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
