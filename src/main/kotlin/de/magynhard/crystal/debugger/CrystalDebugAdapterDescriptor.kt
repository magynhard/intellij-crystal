package de.magynhard.crystal.debugger

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.connection.CommandLineDebugAdapterHandle
import com.intellij.platform.dap.connection.DebugAdapterHandle

class CrystalDebugAdapterDescriptor(
    private val project: Project
) : DebugAdapterDescriptor<CrystalDebugAdapterId>() {

    override val id: CrystalDebugAdapterId = CrystalDebugAdapterId

    override val breakpointsDescription: DapBreakpointsDescription = DapBreakpointsDescription(
        CrystalLineBreakpointType::class.java,
        CrystalExceptionBreakpointType::class.java
    )

    override suspend fun launchDebugAdapter(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult?,
        sessionId: String
    ): DebugAdapterHandle {
        val lldbDapPath = findLldbDap()
        val commandLine = GeneralCommandLine(lldbDapPath)
        return CommandLineDebugAdapterHandle(commandLine)
    }

    private fun findLldbDap(): String {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val candidates = if (isWindows) {
            listOf(
                "C:\\Program Files\\LLVM\\bin\\lldb-dap.exe",
                "C:\\Program Files\\LLVM\\bin\\lldb-vscode.exe",
                "C:\\Program Files (x86)\\LLVM\\bin\\lldb-dap.exe",
                "C:\\Program Files (x86)\\LLVM\\bin\\lldb-vscode.exe"
            )
        } else {
            listOf(
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/bin/lldb-vscode",
                "/usr/local/bin/lldb-vscode"
            )
        }
        for (candidate in candidates) {
            if (java.io.File(candidate).canExecute()) {
                return candidate
            }
        }
        return if (isWindows) "lldb-dap.exe" else "lldb-dap"
    }
}
