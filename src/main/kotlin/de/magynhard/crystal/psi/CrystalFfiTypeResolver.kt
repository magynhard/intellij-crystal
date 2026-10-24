package de.magynhard.crystal.psi

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalEffectiveSourceSet
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.stubs.CrystalIndexService

/** Exact FFI declarations shared by type-path navigation and receiver diagnostics. */
internal object CrystalFfiTypeResolver {
    /** Null means no library prefix was found; an empty list means its member is missing. */
    fun resolve(
        name: String,
        context: PsiElement,
        sources: CrystalEffectiveSourceSet = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context),
    ): List<PsiElement>? {
        if (DumbService.isDumb(context.project) || CrystalMacroContext.isInMacroContext(context)) return emptyList()
        if (sources.files.isEmpty()) return emptyList()
        val absolute = name.startsWith("::")
        val parts = name.removePrefix("::").split("::")
        val scope = GlobalSearchScope.allScope(context.project)
        if (!absolute && parts.size == 1) {
            val enclosing = PsiTreeUtil.getParentOfType(context, CrystalLibDefinition::class.java)
            if (enclosing != null && sources.contains(enclosing)) {
                val localMembers = libraryMembers(listOf(enclosing), parts.single(), sources, context, false)
                if (localMembers.isNotEmpty()) return localMembers
            }
        }
        for (length in parts.size downTo 1) {
            val prefix = parts.take(length).joinToString("::")
            val libraries = CrystalIndexService.findLibs(parts[length - 1], context.project, scope)
                .filter(sources::contains)
            val identities = if (absolute || prefix.contains("::")) setOf(prefix)
                else CrystalPsiUtils.buildLexicalQualifiedNameCandidates(prefix, context)
            for (identity in identities) {
                val matches = libraries.filter { CrystalPsiUtils.libQualifiedName(it) == identity }
                if (matches.isEmpty()) continue
                if (length == parts.size) return matches
                // Inspect only the bodies of exact indexed libraries, never project files.
                if (length + 1 != parts.size) return emptyList()
                return libraryMembers(matches, parts.last(), sources, context, true)
            }
        }
        return null
    }

    private fun libraryMembers(
        libraries: List<CrystalLibDefinition>,
        name: String,
        sources: CrystalEffectiveSourceSet,
        context: PsiElement,
        qualifiedAccess: Boolean,
    ): List<PsiElement> = libraries.flatMap { it.libBody?.children.orEmpty().toList() }
        .filter { member ->
            (member is CrystalLibStructDefinition || member is CrystalLibUnionDefinition ||
                member is CrystalTypeAliasLib || member is CrystalLibTypeAlias ||
                member is CrystalEnumDefinition || member is CrystalConstantAssignment) &&
                member.node.findChildByType(CrystalTypes.CONSTANT)?.text == name &&
                (member !is CrystalConstantAssignment ||
                    CrystalRequireVisibility.isConstantCandidateVisible(member, sources, context, qualifiedAccess))
        }
}
