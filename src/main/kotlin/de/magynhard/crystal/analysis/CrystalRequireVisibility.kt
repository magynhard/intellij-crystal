package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Require-graph visibility for name-based lookups that bypass the shared
 * resolution session (argument-count / type-check inspections, reference
 * fallbacks). Crystal resolves unqualified names only against the current
 * file plus its forward require closure and the prelude — a project-wide or
 * all-scope index search must be filtered through the same lens, otherwise
 * definitions from unrelated files leak into diagnostics and hovers.
 */
internal object CrystalRequireVisibility {

    /**
     * Keeps only methods whose defining file is visible from [context]'s
     * effective source set. Returns an empty list when no visibility can be
     * established (nonphysical PSI), so callers stay silent instead of guessing.
     */
    fun visibleMethods(
        methods: Collection<CrystalMethodDefinition>,
        context: PsiElement,
    ): List<CrystalMethodDefinition> {
        val sources = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context)
        if (sources.files.isEmpty()) return emptyList()
        return methods.filter { sources.contains(it) }
    }
}
