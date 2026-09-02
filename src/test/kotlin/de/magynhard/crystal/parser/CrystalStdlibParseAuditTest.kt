package de.magynhard.crystal.parser

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.CrystalStdlibRoots
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.system.measureNanoTime

class CrystalStdlibParseAuditTest : BasePlatformTestCase() {
    fun testCorpusParsesWithoutErrors() {
        if (System.getProperty(ENABLED_PROPERTY) != "true") return
        val reportPath = Paths.get(requireProperty(REPORT_PROPERTY))
        val tsvPath = Paths.get(requireProperty(TSV_PROPERTY))
        Files.deleteIfExists(reportPath)
        Files.deleteIfExists(tsvPath)

        val scopeValue = System.getProperty(SCOPE_PROPERTY)?.takeIf(String::isNotBlank)
        val scope = AuditScope.parseOrNull(scopeValue)
        requireAudit(scope != null, reportPath, tsvPath) {
            "Unsupported Crystal corpus '${scopeValue.orEmpty()}'; expected indexed or distribution"
        }
        val rootValue = System.getProperty(ROOT_PROPERTY)?.takeIf(String::isNotBlank)
        requireAudit(rootValue != null, reportPath, tsvPath) {
            "Required system property is missing: $ROOT_PROPERTY"
        }
        val rootPath = Paths.get(rootValue!!).toAbsolutePath().normalize()
        val versionFile = rootPath.resolve("VERSION")

        requireAudit(Files.isRegularFile(versionFile), reportPath, tsvPath) {
            "Crystal stdlib root has no VERSION file: $rootPath"
        }
        val version = versionFile.readText().trim()
        requireAudit(version == REQUIRED_VERSION, reportPath, tsvPath) {
            "Crystal $REQUIRED_VERSION is required, found '$version' at $rootPath"
        }

        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootPath)
        requireAudit(root?.isDirectory == true, reportPath, tsvPath) {
            "Crystal stdlib root is not a VFS directory: $rootPath"
        }

        val files = collectCrystalFiles(scope!!.selectRoots(root!!))
        requireAudit(files.size == scope.expectedFileCount, reportPath, tsvPath) {
            "${scope.label} corpus must contain ${scope.expectedFileCount} Crystal files, found ${files.size}"
        }

        val issues = mutableListOf<ParseIssue>()
        val timings = mutableListOf<FileTiming>()
        var totalBytes = 0L
        val psiManager = PsiManager.getInstance(project)
        val totalNanos = measureNanoTime {
            for (file in files) {
                val relativePath = rootPath.relativize(Paths.get(file.path)).toString().replace('\\', '/')
                val bytes = file.length
                totalBytes += bytes
                var fileIssues = emptyList<ParseIssue>()
                val elapsed = measureNanoTime {
                    val psiFile = psiManager.findFile(file)
                    if (psiFile == null) {
                        fileIssues = listOf(
                            ParseIssue(relativePath, 1, 1, 0, "No Crystal PSI file created", "")
                        )
                    } else {
                        val text = psiFile.text
                        fileIssues = PsiTreeUtil.collectElementsOfType(psiFile, PsiErrorElement::class.java)
                            .map { ParseIssue.from(relativePath, text, it) }
                    }
                }
                issues.addAll(fileIssues)
                timings.add(FileTiming(relativePath, bytes, elapsed))
            }
        }

        writeReports(
            reportPath,
            tsvPath,
            scope,
            rootPath,
            version,
            files.size,
            totalBytes,
            totalNanos,
            timings,
            issues
        )

        if (issues.isNotEmpty()) {
            val affectedFiles = issues.mapTo(linkedSetOf()) { it.path }
            fail(buildString {
                appendLine("${issues.size} parse error(s) in ${affectedFiles.size} of ${files.size} files")
                appendLine("Full report: $reportPath")
                issues.take(30).forEach {
                    appendLine("${it.path}:${it.line}:${it.column}: ${it.description}")
                }
            })
        }
    }

    private fun collectCrystalFiles(roots: List<VirtualFile>): List<VirtualFile> {
        val files = linkedMapOf<String, VirtualFile>()

        fun visit(file: VirtualFile) {
            if (file.`is`(VFileProperty.SYMLINK)) return
            if (file.isDirectory) {
                file.children.forEach(::visit)
            } else if (file.extension == "cr") {
                files[file.path] = file
            }
        }

        roots.forEach(::visit)
        return files.values.sortedBy { it.path }
    }

    private fun writeReports(
        reportPath: Path,
        tsvPath: Path,
        scope: AuditScope,
        rootPath: Path,
        version: String,
        fileCount: Int,
        totalBytes: Long,
        totalNanos: Long,
        timings: List<FileTiming>,
        issues: List<ParseIssue>
    ) {
        Files.createDirectories(reportPath.parent)
        Files.createDirectories(tsvPath.parent)
        val affectedFiles = issues.mapTo(linkedSetOf()) { it.path }
        val firstErrors = issues.groupBy { it.path }.mapValues { it.value.minBy(ParseIssue::offset) }
        val slowest = timings.sortedByDescending(FileTiming::elapsedNanos).take(20)

        Files.writeString(reportPath, buildString {
            appendLine("Crystal stdlib parse audit")
            appendLine("==========================")
            appendLine("Status: ${if (issues.isEmpty()) "PASS" else "FAIL"}")
            appendLine("Scope: ${scope.label}")
            appendLine("Crystal version: $version")
            appendLine("Stdlib root: $rootPath")
            appendLine("Files parsed: $fileCount")
            appendLine("Bytes parsed: $totalBytes")
            appendLine("Elapsed ms: ${totalNanos / 1_000_000}")
            appendLine("Files with errors: ${affectedFiles.size}")
            appendLine("Parse errors: ${issues.size}")
            appendLine()
            appendLine("Slowest files")
            appendLine("-------------")
            slowest.forEach {
                appendLine("${it.elapsedNanos / 1_000_000} ms\t${it.bytes} bytes\t${it.path}")
            }
            if (issues.isNotEmpty()) {
                appendLine()
                appendLine("First error per file")
                appendLine("--------------------")
                firstErrors.values.sortedBy(ParseIssue::path).forEach { issue ->
                    appendLine("${issue.path}:${issue.line}:${issue.column}: ${issue.description}")
                    appendLine("  Tail bytes: ${issue.tailBytes}")
                    appendLine("  Source: ${issue.context}")
                }
                appendLine()
                appendLine("All errors")
                appendLine("----------")
                issues.forEach { issue ->
                    appendLine("${issue.path}:${issue.line}:${issue.column}: ${issue.description}")
                    appendLine("  Offset: ${issue.offset}")
                    appendLine("  Source: ${issue.context}")
                }
            }
        }, StandardCharsets.UTF_8)

        Files.writeString(tsvPath, buildString {
            appendLine("path\tline\tcolumn\toffset\ttail_bytes\tdescription\tcontext")
            issues.forEach { issue ->
                appendLine(listOf(
                    issue.path,
                    issue.line,
                    issue.column,
                    issue.offset,
                    issue.tailBytes,
                    issue.description,
                    issue.context
                ).joinToString("\t") { sanitizeTsv(it.toString()) })
            }
        }, StandardCharsets.UTF_8)
    }

    private fun requireProperty(name: String): String =
        System.getProperty(name)?.takeIf(String::isNotBlank)
            ?: error("Required system property is missing: $name")

    private fun requireAudit(condition: Boolean, reportPath: Path, tsvPath: Path, message: () -> String) {
        if (condition) return
        Files.createDirectories(reportPath.parent)
        Files.createDirectories(tsvPath.parent)
        Files.writeString(reportPath, "Status: ENVIRONMENT_ERROR\n${message()}\n", StandardCharsets.UTF_8)
        Files.writeString(tsvPath, "path\tline\tcolumn\toffset\ttail_bytes\tdescription\tcontext\n", StandardCharsets.UTF_8)
        fail("${message()}\nReport: $reportPath")
    }

    private fun sanitizeTsv(value: String): String =
        value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')

    private enum class AuditScope(val label: String, val expectedFileCount: Int) {
        INDEXED("indexed", 461) {
            override fun selectRoots(root: VirtualFile): List<VirtualFile> = CrystalStdlibRoots.enumerate(root)
        },
        DISTRIBUTION("distribution", 1625) {
            override fun selectRoots(root: VirtualFile): List<VirtualFile> = listOf(root)
        };

        abstract fun selectRoots(root: VirtualFile): List<VirtualFile>

        companion object {
            fun parseOrNull(value: String?): AuditScope? = entries.firstOrNull { it.label == value?.lowercase() }
        }
    }

    private data class FileTiming(val path: String, val bytes: Long, val elapsedNanos: Long)

    private data class ParseIssue(
        val path: String,
        val line: Int,
        val column: Int,
        val offset: Int,
        val description: String,
        val context: String,
        val tailBytes: Int = 0
    ) {
        companion object {
            fun from(path: String, text: String, error: PsiErrorElement): ParseIssue {
                val offset = error.textOffset.coerceIn(0, text.length)
                val line = StringUtil.offsetToLineNumber(text, offset) + 1
                val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1
                val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
                return ParseIssue(
                    path,
                    line,
                    offset - lineStart + 1,
                    offset,
                    error.errorDescription,
                    text.substring(lineStart, lineEnd).trim().take(300),
                    text.substring(offset).toByteArray(StandardCharsets.UTF_8).size
                )
            }
        }
    }

    companion object {
        private const val REQUIRED_VERSION = "1.21.0"
        private const val ENABLED_PROPERTY = "crystal.stdlib.parse.audit.enabled"
        private const val ROOT_PROPERTY = "crystal.stdlib.parse.audit.root"
        private const val SCOPE_PROPERTY = "crystal.stdlib.parse.audit.scope"
        private const val REPORT_PROPERTY = "crystal.stdlib.parse.audit.report"
        private const val TSV_PROPERTY = "crystal.stdlib.parse.audit.tsv"
    }
}
