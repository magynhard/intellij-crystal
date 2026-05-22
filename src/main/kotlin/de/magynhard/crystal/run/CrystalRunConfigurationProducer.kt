package de.magynhard.crystal.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import de.magynhard.crystal.CrystalFileType

class CrystalRunConfigurationProducer : LazyRunConfigurationProducer<CrystalRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        val type = CrystalRunConfigurationType()
        return CrystalRunFactory(type)
    }

    override fun isConfigurationFromContext(
        configuration: CrystalRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return file.extension == "cr" && configuration.filePath == file.path
    }

    override fun setupConfigurationFromContext(
        configuration: CrystalRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.extension != "cr") return false

        configuration.filePath = file.path
        configuration.workingDirectory = context.project.basePath ?: ""

        // Detect if it's a spec file
        if (file.path.contains("/spec/") || file.name.endsWith("_spec.cr")) {
            configuration.command = CrystalCommand.SPEC
            configuration.name = "spec: ${file.name}"
        } else {
            configuration.command = CrystalCommand.RUN
            configuration.name = "run: ${file.name}"
        }

        return true
    }
}
