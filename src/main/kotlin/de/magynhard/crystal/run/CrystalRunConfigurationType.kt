package de.magynhard.crystal.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import de.magynhard.crystal.CrystalIcons
import javax.swing.Icon

class CrystalRunConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "Crystal"
    override fun getConfigurationTypeDescription(): String = "Crystal run configuration"
    override fun getIcon(): Icon = CrystalIcons.FILE
    override fun getId(): String = "CrystalRunConfiguration"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(
        CrystalRunFactory(this),
        CrystalBuildFactory(this),
        CrystalSpecFactory(this),
    )
}

class CrystalRunFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Crystal Run"
    override fun getName(): String = "Run"
    override fun getOptionsClass(): Class<out BaseState> = CrystalRunConfigurationOptions::class.java
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CrystalRunConfiguration(project, this, "Crystal Run", CrystalCommand.RUN)
}

class CrystalBuildFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Crystal Build"
    override fun getName(): String = "Build"
    override fun getOptionsClass(): Class<out BaseState> = CrystalRunConfigurationOptions::class.java
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CrystalRunConfiguration(project, this, "Crystal Build", CrystalCommand.BUILD)
}

class CrystalSpecFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Crystal Spec"
    override fun getName(): String = "Spec"
    override fun getOptionsClass(): Class<out BaseState> = CrystalRunConfigurationOptions::class.java
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CrystalRunConfiguration(project, this, "Crystal Spec", CrystalCommand.SPEC)
}
