package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import de.magynhard.crystal.sdk.CrystalShardsInstall

/**
 * Quickfix shared by shard dependency diagnostics: runs a full
 * `shards install` for the project (the only install shards supports).
 */
class CrystalShardsInstallQuickFix : LocalQuickFix {

    override fun getName(): String = "Run 'shards install'"

    override fun getFamilyName(): String = "Crystal shards"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        CrystalShardsInstall.runInstall(project)
    }
}
