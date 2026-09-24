package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import de.magynhard.crystal.sdk.CrystalShardStatus

/**
 * Reports uninstalled or outdated dependencies in the project-root
 * `shard.yml`: missing entries are errors, version mismatches are
 * warnings, each with hover text and a `shards install` quickfix.
 *
 * Deliberately language-agnostic (no YAML PSI dependency): dependency
 * keys are located with an indent-aware line scan, and markers are only
 * created for names the status service actually reports. `lib/` manifests
 * and non-root files are never checked.
 */
class CrystalShardDependencyInspection : LocalInspectionTool() {

    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        if (file.name != "shard.yml") return PsiElementVisitor.EMPTY_VISITOR
        val basePath = holder.project.basePath ?: return PsiElementVisitor.EMPTY_VISITOR
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
            ?: return PsiElementVisitor.EMPTY_VISITOR
        if (file.virtualFile?.parent != baseDir) return PsiElementVisitor.EMPTY_VISITOR
        val problems = CrystalShardStatus.problems(holder.project)
        if (problems.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR
        val ranges = shardDependencyKeyRanges(file.text)
        return object : PsiElementVisitor() {
            override fun visitFile(visited: PsiFile) {
                for (entry in problems) {
                    val range = ranges[entry.dependency.name] ?: continue
                    val (message, highlight) = when (val state = entry.state) {
                        is CrystalShardStatus.DependencyState.Missing ->
                            "Dependency '${entry.dependency.name}' is not installed (lib/${entry.dependency.name} is missing)" to
                                ProblemHighlightType.GENERIC_ERROR
                        is CrystalShardStatus.DependencyState.VersionMismatch ->
                            "Dependency '${entry.dependency.name}' is outdated: installed ${state.actual}, expected ${state.expected}" to
                                ProblemHighlightType.WARNING
                        else -> continue
                    }
                    holder.registerProblem(
                        holder.file,
                        message,
                        highlight,
                        range,
                        CrystalShardsInstallQuickFix()
                    )
                }
            }
        }
    }

    /**
     * Dependency key ranges (`name:`) in `dependencies:` and
     * `development_dependencies:` sections, keyed by dependency name.
     * Only lines at the section's own indent level qualify; nested
     * attribute lines, comments, and blank lines never produce ranges.
     * Markers are only created for names the status service reports, so
     * over-matching here is harmless.
     */
    internal fun shardDependencyKeyRanges(text: String): Map<String, TextRange> {
        val ranges = linkedMapOf<String, TextRange>()
        var inSection = false
        var baseIndent: Int? = null
        var offset = 0
        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                offset += rawLine.length + 1
                continue
            }
            val indent = line.length - line.trimStart().length
            if (indent == 0) {
                val header = trimmed.substringBefore('#').trim()
                inSection = header == "dependencies:" || header == "development_dependencies:"
                baseIndent = null
            } else if (inSection) {
                if (baseIndent == null) baseIndent = indent
                if (indent == baseIndent && ':' in trimmed) {
                    val key = trimmed.substringBefore(':').trim()
                    if (key.isNotEmpty()) {
                        ranges.putIfAbsent(key, TextRange(offset + indent, offset + indent + key.length))
                    }
                } else if (indent < baseIndent) {
                    inSection = false
                    baseIndent = null
                }
            }
            offset += rawLine.length + 1
        }
        return ranges
    }
}
