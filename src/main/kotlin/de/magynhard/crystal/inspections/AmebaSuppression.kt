package de.magynhard.crystal.inspections

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.ecr.EmbeddedCrystalFileType
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.CrystalSettings

/**
 * Overlap gate between built-in inspections and the Ameba linter.
 *
 * When Ameba is enabled and a usable binary resolves, diagnostics Ameba owns
 * come from Ameba only; the overlapping built-ins below step aside so users
 * never see the same problem twice. When Ameba is disabled or no binary
 * resolves, the built-ins stay the offline fallback. Pure file stats and the
 * cached binary resolution — no processes on a cache hit.
 *
 * ECR templates are judged by their top-level host file (which is what Ameba
 * lints whole): injected Crystal fragments step aside together with their
 * host, so a finding inside `<% %>` is never reported twice.
 */
object AmebaSuppression {

    fun isActiveFor(file: PsiFile): Boolean {
        val project = file.project
        if (!CrystalSettings.getInstance(project).state.amebaEnabled) return false
        if (AmebaBinary.resolve(project) == null) return false
        if (file.fileType == CrystalFileType) {
            return CrystalInspectionScope.isProjectSource(file)
        }
        val top = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
        if (top.fileType != EmbeddedCrystalFileType) return false
        return CrystalInspectionScope.isProjectSource(top)
    }
}
