package de.magynhard.crystal.run

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class CrystalRunConfigurationOptions : RunConfigurationOptions() {
    private val _filePath = string("").provideDelegate(this, "filePath")
    private val _arguments = string("").provideDelegate(this, "arguments")
    private val _workingDirectory = string("").provideDelegate(this, "workingDirectory")
    private val _environmentVariables = string("").provideDelegate(this, "environmentVariables")
    private val _crystalPath = string("crystal").provideDelegate(this, "crystalPath")

    var filePath: String?
        get() = _filePath.getValue(this)
        set(value) { _filePath.setValue(this, value) }

    var arguments: String?
        get() = _arguments.getValue(this)
        set(value) { _arguments.setValue(this, value) }

    var workingDirectory: String?
        get() = _workingDirectory.getValue(this)
        set(value) { _workingDirectory.setValue(this, value) }

    var environmentVariables: String?
        get() = _environmentVariables.getValue(this)
        set(value) { _environmentVariables.setValue(this, value) }

    var crystalPath: String?
        get() = _crystalPath.getValue(this)
        set(value) { _crystalPath.setValue(this, value) }
}
