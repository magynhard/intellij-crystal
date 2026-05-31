package de.magynhard.crystal.debugger

import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType

class CrystalExceptionBreakpointType : XBreakpointType<XBreakpoint<CrystalBreakpointProperties>, CrystalBreakpointProperties>(
    "crystal-exception-breakpoint",
    "Crystal Exception Breakpoint"
) {
    override fun createDefaultBreakpoint(creator: XBreakpointCreator<CrystalBreakpointProperties>): XBreakpoint<CrystalBreakpointProperties> {
        return creator.createBreakpoint(CrystalBreakpointProperties())
    }

    override fun getDisplayText(breakpoint: XBreakpoint<CrystalBreakpointProperties>?): String {
        return "Crystal Exception"
    }
}
