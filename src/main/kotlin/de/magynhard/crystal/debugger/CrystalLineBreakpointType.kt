package de.magynhard.crystal.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

class CrystalLineBreakpointType : XLineBreakpointType<CrystalBreakpointProperties>(
    "crystal-line-breakpoint",
    "Crystal Line Breakpoint"
) {
    override fun createBreakpointProperties(file: VirtualFile, line: Int): CrystalBreakpointProperties {
        return CrystalBreakpointProperties()
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        return file.name.endsWith(".cr")
    }
}
