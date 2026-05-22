package de.magynhard.crystal.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element

class CrystalRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    var command: CrystalCommand = CrystalCommand.RUN
) : RunConfigurationBase<CrystalRunConfigurationOptions>(project, factory, name) {

    var filePath: String
        get() = options.filePath ?: ""
        set(value) { options.filePath = value }

    var arguments: String
        get() = options.arguments ?: ""
        set(value) { options.arguments = value }

    var workingDirectory: String
        get() = options.workingDirectory ?: project.basePath ?: ""
        set(value) { options.workingDirectory = value }

    var environmentVariables: String
        get() = options.environmentVariables ?: ""
        set(value) { options.environmentVariables = value }

    var crystalPath: String
        get() = options.crystalPath ?: "crystal"
        set(value) { options.crystalPath = value }

    override fun getOptions(): CrystalRunConfigurationOptions {
        return super.getOptions() as CrystalRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return CrystalRunSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return CrystalRunState(environment, this)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        command = try {
            CrystalCommand.valueOf(element.getAttributeValue("crystal-command") ?: "RUN")
        } catch (_: Exception) {
            CrystalCommand.RUN
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("crystal-command", command.name)
    }
}
