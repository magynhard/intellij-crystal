package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils

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

    /**
     * True when [element]'s defining file is part of [context]'s effective
     * source set. Returns false when no visibility can be established, so
     * callers omit the candidate instead of leaking unrequired definitions.
     */
    fun isVisible(element: PsiElement, context: PsiElement): Boolean {
        val sources = CrystalRequireGraphService.getInstance(context.project).effectiveSources(context)
        return sources.files.isNotEmpty() && sources.contains(element)
    }

    /**
     * Callable-scope filter for receiver-less calls on top of [visibleMethods]:
     * an unqualified name resolves against top-level methods plus the
     * implicit-self scope of the enclosing type. Instance methods of unrelated
     * types (a shard's `Frame#context` meeting the spec DSL's top-level
     * `context`) need a receiver and must not enter the overload pool.
     * Top-level `def self.` methods stay excluded, mirroring the shared
     * unqualified-call resolution.
     */
    fun callableUnqualified(
        methods: Collection<CrystalMethodDefinition>,
        context: PsiElement,
    ): List<CrystalMethodDefinition> {
        val callSiteType = CrystalPsiUtils.callSiteOwnerQualifiedName(context)
        return methods.filter { method ->
            // An explicit header receiver (`def Time::Location.new`) owns the
            // method even outside any lexical type; otherwise ownership falls
            // back to the enclosing type or record.
            val owner = method.stub?.ownerQualifiedName
                ?: CrystalPsiUtils.methodOwnerQualifiedName(method)
            when {
                owner == null -> !CrystalPsiUtils.isSelfMethod(method)
                else -> callSiteType != null && (owner == callSiteType || callSiteType.startsWith("$owner::"))
            }
        }
    }
}
