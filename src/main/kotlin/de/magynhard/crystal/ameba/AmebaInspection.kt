package de.magynhard.crystal.ameba

import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import de.magynhard.crystal.inspections.CrystalInspectionScope
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaConfigDiscovery
import de.magynhard.crystal.sdk.CrystalSettings

/**
 * Paired batch inspection for [AmebaExternalAnnotator] (Inspect Code and
 * profile control). Disabling this inspection stops live Ameba highlighting
 * via the platform `ExternalToolPass` contract; the master switch stays the
 * "Enable Ameba linting" checkbox in the Crystal settings.
 *
 * Threading contract (platform-enforced): the 3-arg [checkFile] overload runs
 * under a read action — it must never start a process and always returns
 * null (live highlighting is the annotator's job). Only [runBatchCheck],
 * reached via the [ExternalAnnotatorBatchInspection] overload that runs
 * without a read action, executes the binary synchronously and converts
 * issues to descriptors with the explicit `--fix` action.
 * Defensive throughout: any failure yields no descriptors, never exceptions.
 */
class AmebaInspection : LocalInspectionTool(), ExternalAnnotatorBatchInspection {

    override fun getShortName(): String = SHORT_NAME

    override fun getDisplayName(): String = "Ameba linter"

    override fun getGroupDisplayName(): String = "Crystal"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDescriptionFileName(): String = "$SHORT_NAME.html"

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        // Never runs the binary: the InspectionRunner invokes this overload
        // under a read action (live highlighting and batch alike), where
        // synchronous process execution is forbidden and would additionally
        // duplicate the annotator's diagnostics. Batch work happens only in
        // runBatchCheck via the ExternalAnnotatorBatchInspection overload.
        return null
    }

    override fun checkFile(
        file: PsiFile,
        context: GlobalInspectionContext,
        manager: InspectionManager
    ): Array<ProblemDescriptor> {
        return runBatchCheck(file, manager) ?: emptyArray()
    }

    internal fun runBatchCheck(file: PsiFile, manager: InspectionManager): Array<ProblemDescriptor>? {
        return try {
            val project = file.project
            if (!CrystalSettings.getInstance(project).state.amebaEnabled) return null
            if (!isLintableAmebaFile(file)) return null
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
            val result = AmebaRunner.run(
                AmebaRunner.Request(
                    binaryPath = binary.path,
                    configPath = AmebaConfigDiscovery.discover(project, virtualFile)?.path,
                    workDirectory = basePath,
                    stdinFileName = stdinName,
                    targetFilePath = virtualFile.path,
                    documentText = text
                )
            )
            if (result.error != null) return null
            val resultDocument = document ?: return null
            result.issues.mapNotNull { issue ->
                val range = AmebaRanges.toRange(resultDocument, issue) ?: return@mapNotNull null
                manager.createProblemDescriptor(
                    file,
                    range,
                    "${issue.message} [Ameba: ${issue.ruleName}]",
                    highlightType(issue),
                    false,
                    AmebaFileFix()
                )
            }.toTypedArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun highlightType(issue: AmebaIssue): ProblemHighlightType {
        return when (issue.severity) {
            AmebaSeverity.ERROR -> ProblemHighlightType.ERROR
            AmebaSeverity.WARNING -> ProblemHighlightType.WARNING
            AmebaSeverity.CONVENTION -> ProblemHighlightType.WEAK_WARNING
        }
    }

    companion object {
        const val SHORT_NAME = "Ameba"
    }
}
