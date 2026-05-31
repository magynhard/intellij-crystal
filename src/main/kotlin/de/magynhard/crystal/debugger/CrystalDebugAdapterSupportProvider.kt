package de.magynhard.crystal.debugger

import com.intellij.openapi.project.Project
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.DebugAdapterSupportProvider

class CrystalDebugAdapterSupportProvider : DebugAdapterSupportProvider<CrystalDebugAdapterId> {

    override val adapterId: CrystalDebugAdapterId get() = CrystalDebugAdapterId

    override fun createDebugAdapterDescriptor(project: Project): DebugAdapterDescriptor<CrystalDebugAdapterId> {
        return CrystalDebugAdapterDescriptor(project)
    }
}
