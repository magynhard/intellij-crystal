package de.magynhard.crystal.ameba

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import de.magynhard.crystal.inspections.CrystalInspectionScope
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaConfigDiscovery
import de.magynhard.crystal.sdk.AmebaNotifications
import de.magynhard.crystal.sdk.CrystalSettings

/**
 * Live Ameba diagnostics for Crystal files.
 *
 * `collectInformation` (read thread) snapshots everything the slow part
 * needs: master switch, scope gates, resolved binary, config, and the
 * document text. `doAnnotate` (background, no read action) runs the binary
 * on STDIN. `apply` (read thread) maps issues onto the document, dropping
 * stale results via the modification stamp. Heavy lifting never touches PSI
 * or VFS off the read thread; the runner itself uses only `java.io` and
 * processes.
 *
 * Disabled entirely while the paired `Ameba` inspection is switched off in
 * the current profile (platform `ExternalToolPass` contract).
 */
class AmebaExternalAnnotator : ExternalAnnotator<AmebaExternalAnnotator.Info, AmebaExternalAnnotator.Result>() {

    data class Info(
        val binaryPath: String,
        val configPath: String?,
        val workDirectory: String?,
        val stdinFileName: String,
        val targetFilePath: String,
        val documentText: String,
        val modificationStamp: Long
    )

    data class Result(
        val issues: List<AmebaIssue>,
        val error: String?,
        val modificationStamp: Long
    )

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? {
        if (hasErrors) return null
        val project = file.project
        if (!CrystalSettings.getInstance(project).state.amebaEnabled) return null
        if (!isLintableAmebaFile(file)) return null
        // Injected Crystal fragments (ECR tag bodies) share their host's
        // VirtualFile: the host run below covers the whole file, so fragments
        // must not lint again.
        if (InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) return null
        if (!CrystalInspectionScope.isProjectSource(file)) return null
        val virtualFile = file.virtualFile ?: return null
        if (!virtualFile.isInLocalFileSystem || virtualFile.isDirectory) return null
        val binary = AmebaBinary.resolve(project) ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        val text = document?.text ?: file.text
        val basePath = project.basePath
        val stdinName = if (basePath != null && virtualFile.path.startsWith("$basePath/")) {
            virtualFile.path.removePrefix("$basePath/")
        } else {
            virtualFile.name
        }
        return Info(
            binaryPath = binary.path,
            configPath = AmebaConfigDiscovery.discover(project, virtualFile)?.path,
            workDirectory = basePath,
            stdinFileName = stdinName,
            targetFilePath = virtualFile.path,
            documentText = text,
            modificationStamp = document?.modificationStamp ?: file.modificationStamp
        )
    }

    override fun doAnnotate(collectedInfo: Info): Result {
        val request = AmebaRunner.Request(
            binaryPath = collectedInfo.binaryPath,
            configPath = collectedInfo.configPath,
            workDirectory = collectedInfo.workDirectory,
            stdinFileName = collectedInfo.stdinFileName,
            targetFilePath = collectedInfo.targetFilePath,
            documentText = collectedInfo.documentText
        )
        val result = AmebaRunner.run(request)
        return Result(result.issues, result.error, collectedInfo.modificationStamp)
    }

    override fun apply(file: PsiFile, annotationResult: Result, holder: AnnotationHolder) {
        ProgressManager.checkCanceled()
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        if (document.modificationStamp != annotationResult.modificationStamp) return
        if (annotationResult.error != null) {
            notifyErrorOnce(file, annotationResult.error)
        }
        for (issue in annotationResult.issues) {
            ProgressManager.checkCanceled()
            val range = AmebaRanges.toRange(document, issue) ?: continue
            holder.newAnnotation(mapSeverity(issue.severity), annotationText(issue))
                .range(range)
                .withFix(AmebaFileFix())
                .create()
        }
    }

    override fun getPairedBatchInspectionShortName(): String = AmebaInspection.SHORT_NAME

    private fun notifyErrorOnce(file: PsiFile, error: String) {
        val project = file.project
        ApplicationManager.getApplication().invokeLater(
            { AmebaNotifications.errorOnce(project, error) },
            project.disposed
        )
    }

    companion object {
        fun mapSeverity(severity: AmebaSeverity): HighlightSeverity {
            return when (severity) {
                AmebaSeverity.ERROR -> HighlightSeverity.ERROR
                AmebaSeverity.WARNING -> HighlightSeverity.WARNING
                AmebaSeverity.CONVENTION -> HighlightSeverity.WEAK_WARNING
            }
        }

        fun annotationText(issue: AmebaIssue): String {
            return "${issue.message} [Ameba: ${issue.ruleName}]"
        }
    }
}
