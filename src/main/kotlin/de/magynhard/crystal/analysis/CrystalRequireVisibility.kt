package de.magynhard.crystal.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.stubs.CrystalIndexService

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
        return visibleMethods(methods, sources)
    }

    /**
     * Keeps only methods whose defining file is part of [sources]. Returns an
     * empty list for an empty snapshot, so callers stay silent instead of
     * leaking unrequired definitions. Prefer this overload when the caller
     * already holds the snapshot so it is computed once per completion.
     */
    fun visibleMethods(
        methods: Collection<CrystalMethodDefinition>,
        sources: CrystalEffectiveSourceSet,
    ): List<CrystalMethodDefinition> {
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
        return isVisible(element, sources)
    }

    /**
     * True when [element]'s defining file is part of [sources]. Returns false
     * for an empty snapshot, so callers omit the candidate instead of leaking
     * unrequired definitions.
     */
    fun isVisible(element: PsiElement, sources: CrystalEffectiveSourceSet): Boolean {
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

    /**
     * True when the stub index holds at least one type declaration [name]
     * whose defining file is part of [sources]. Stops at the first visible
     * declaration instead of loading every same-named PSI element. Returns
     * false for an empty snapshot.
     */
    fun isTypeNameVisible(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        sources: CrystalEffectiveSourceSet,
    ): Boolean {
        if (sources.files.isEmpty()) return false
        var visible = false
        try {
            CrystalIndexService.processTypes(name, project, scope) { element ->
                if (sources.contains(element)) {
                    visible = true
                    false
                } else {
                    true
                }
            }
        } catch (_: Throwable) {
            // A single broken index entry must not abort completion.
        }
        return visible
    }

    /**
     * True when the stub index holds any type declaration [name], visible or
     * not. Lets callers distinguish "known but unrequired" (hide) from
     * "unknown to the index" (best-effort offer, e.g. stdlib without an
     * indexed SDK).
     */
    fun hasIndexedType(name: String, project: Project, scope: GlobalSearchScope): Boolean {
        var found = false
        try {
            CrystalIndexService.processTypes(name, project, scope) {
                found = true
                false
            }
        } catch (_: Throwable) {
            // Fall through with whatever was observed.
        }
        return found
    }

    /**
     * True when the stub index holds at least one constant declaration [name]
     * visible from [context]: the defining file is part of the effective
     * source set, and private constants additionally require the same file.
     * Stops at the first visible declaration instead of loading every
     * same-named PSI element. Returns false for an empty snapshot.
     */
    fun isConstantNameVisible(
        name: String,
        context: PsiElement,
        scope: GlobalSearchScope,
        sources: CrystalEffectiveSourceSet,
    ): Boolean {
        if (sources.files.isEmpty()) return false
        val contextFile = context.containingFile?.originalFile?.virtualFile
        var visible = false
        try {
            CrystalIndexService.processConstants(name, context.project, scope) { element ->
                if (isConstantCandidateVisible(element, sources, contextFile)) {
                    visible = true
                    false
                } else {
                    true
                }
            }
        } catch (_: Throwable) {
            // A single broken index entry must not abort completion.
        }
        return visible
    }

    /**
     * True when the stub index holds any constant declaration [name], visible
     * or not. Lets callers distinguish "known but unrequired" (report) from
     * "unknown to the index" (stay silent).
     */
    fun hasIndexedConstant(name: String, project: Project, scope: GlobalSearchScope): Boolean {
        var found = false
        try {
            CrystalIndexService.processConstants(name, project, scope) {
                found = true
                false
            }
        } catch (_: Throwable) {
            // Fall through with whatever was observed.
        }
        return found
    }

    /**
     * Shared constant-candidate gate: the defining file must be in [sources],
     * and private constants match only [contextFile] (their own file).
     */
    fun isConstantCandidateVisible(
        element: CrystalConstantAssignment,
        sources: CrystalEffectiveSourceSet,
        contextFile: VirtualFile?,
    ): Boolean {
        if (!sources.contains(element)) return false
        val isPrivate = element.stub?.isPrivate ?: CrystalPsiUtils.isPrivateConstant(element)
        if (isPrivate) {
            val candidateFile = element.containingFile?.originalFile?.virtualFile ?: return false
            if (candidateFile != contextFile) return false
        }
        return true
    }
}
