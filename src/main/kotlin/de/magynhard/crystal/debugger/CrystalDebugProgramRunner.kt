package de.magynhard.crystal.debugger

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.platform.dap.DapProcessStarter
import com.intellij.platform.dap.DebugAdapterSupportProvider
import com.intellij.xdebugger.XDebuggerManager
import de.magynhard.crystal.run.CrystalRunConfiguration

class CrystalDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {

    companion object {
        private val EP_NAME = ExtensionPointName.create<DebugAdapterSupportProvider<*>>(
            "com.intellij.platform.dap.debugAdapterSupportProvider"
        )
    }

    override fun getRunnerId(): String = "CrystalDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID
            && profile is CrystalRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val dapState = state as CrystalDebugRunState

        // Ensure our DebugAdapterSupportProvider is registered in the EP system.
        // XML-based registration doesn't work for module-scoped EPs (intellij.platform.dap),
        // so we register programmatically before each debug session.
        ensureProviderRegistered(environment)

        val starter = DapProcessStarter(
            environment,
            environment.executor,
            state,
            dapState.adapterId,
            dapState.request,
            dapState.arguments()
        )

        val session = XDebuggerManager.getInstance(environment.project)
            .startSession(environment, starter)

        return session.runContentDescriptor
    }

    private fun ensureProviderRegistered(environment: ExecutionEnvironment) {
        val providers = EP_NAME.extensionList
        val alreadyRegistered = providers.any { it.adapterId.type == "crystal-lldb" }
        if (!alreadyRegistered) {
            val disposable: Disposable = Disposer.newDisposable("CrystalDebugAdapterSupportProvider")
            Disposer.register(environment.project, disposable)
            EP_NAME.point.registerExtension(CrystalDebugAdapterSupportProvider(), disposable)
        }
    }
}
