package de.magynhard.crystal.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver

/**
 * Resolves Crystal `require "..."` paths to their target `.cr` files, mirroring
 * Crystal's require expansion closely enough for Go to Definition:
 *
 * - Relative requires (`require "./foo"`, `require "../x/y"`) resolve against the
 *   directory of the requiring file.
 * - Bare requires (`require "json"`, `require "compiler/crystal/syntax"`) are searched
 *   in the project's own `src/` and root, then installed shards (`lib/<shard>/src`),
 *   then the Crystal standard library (`crystal env CRYSTAL_PATH`/src).
 *
 * For a path `p` the following candidates are tried (Crystal expands a require into
 * several): `p.cr`, `p/index.cr`, and `p/<last-segment>.cr` (the latter covers shard
 * main files like `lib/colorize/src/colorize.cr`).
 */
object CrystalRequireResolver {

    fun resolve(requireStatement: CrystalRequireStatement, project: Project): List<PsiFile> {
        val raw = extractPath(requireStatement) ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val containingFile = requireStatement.containingFile
        val candidates = LinkedHashSet<com.intellij.openapi.vfs.VirtualFile>()

        if (raw.startsWith(".") || raw.startsWith("/")) {
            val base = containingFile.virtualFile?.parent
            if (base != null) collectCandidates(base, raw, candidates)
        } else {
            val roots = com.intellij.openapi.roots.ProjectRootManager.getInstance(project)
                .contentRoots
                .toMutableList()
            @Suppress("DEPRECATION")
            project.baseDir?.let { if (!roots.contains(it)) roots.add(it) }
            for (root in roots) {
                collectCandidates(root, "src/$raw", candidates)
                collectCandidates(root, raw, candidates)
                val lib = root.findFileByRelativePath("lib")
                if (lib != null) {
                    val first = raw.substringBefore("/")
                    val rest = raw.substringAfter("/", "")
                    val shardSrc = lib.findFileByRelativePath("$first/src")
                    if (shardSrc != null) {
                        // Shards may lay out sources as `src/<rest>.cr` or
                        // `src/<shard>/<rest>.cr` (a top-level dir matching the shard name).
                        if (rest.isEmpty()) {
                            collectCandidates(shardSrc, first, candidates)
                        } else {
                            collectCandidates(shardSrc, rest, candidates)
                            collectCandidates(shardSrc, "$first/$rest", candidates)
                        }
                    }
                }
            }
            val stdlib = CrystalStdlibResolver.resolveStdlibPath(project)
            if (stdlib != null) collectCandidates(stdlib, raw, candidates)
        }

        return candidates.mapNotNull { psiManager.findFile(it) }
    }

    private fun collectCandidates(
        base: com.intellij.openapi.vfs.VirtualFile,
        p: String,
        out: MutableSet<com.intellij.openapi.vfs.VirtualFile>
    ) {
        if (p.isEmpty()) return
        base.findFileByRelativePath("$p.cr")?.let { out.add(it) }
        base.findFileByRelativePath("$p/index.cr")?.let { out.add(it) }
        val last = p.substringAfterLast("/")
        if (last.isNotEmpty()) base.findFileByRelativePath("$p/$last.cr")?.let { out.add(it) }
    }

    private fun extractPath(requireStatement: CrystalRequireStatement): String? {
        val text = requireStatement.text
        val m = Regex("""require\s+("([^"]*)"|'([^']*)')""").find(text) ?: return null
        val g2 = m.groupValues.getOrNull(2)
        val g3 = m.groupValues.getOrNull(3)
        return (g2 ?: g3)?.takeIf { it.isNotEmpty() }
    }
}
