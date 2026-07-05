package de.magynhard.crystal.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.options.ConfigurationException
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import de.magynhard.crystal.CrystalLanguage
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

class CrystalCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = CrystalLanguage

    override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
        return object : CodeStyleConfigurable {
            private val indentEditor = IndentOptionsEditor()
            private var panel: JComponent? = null

            override fun createComponent(): JComponent {
                if (panel == null) {
                    panel = JPanel(BorderLayout()).apply {
                        add(indentEditor.createPanel(), BorderLayout.NORTH)
                    }
                    indentEditor.reset(baseSettings, baseSettings.getCommonSettings(CrystalLanguage).indentOptions!!)
                }
                return panel!!
            }

            override fun isModified(): Boolean {
                return indentEditor.isModified(baseSettings, baseSettings.getCommonSettings(CrystalLanguage).indentOptions!!)
            }

            override fun apply() {
                indentEditor.apply(baseSettings, baseSettings.getCommonSettings(CrystalLanguage).indentOptions!!)
            }

            override fun reset() {
                indentEditor.reset(baseSettings, baseSettings.getCommonSettings(CrystalLanguage).indentOptions!!)
            }

            override fun apply(settings: CodeStyleSettings) {
                indentEditor.apply(settings, settings.getCommonSettings(CrystalLanguage).indentOptions!!)
            }

            override fun reset(settings: CodeStyleSettings) {
                indentEditor.reset(settings, settings.getCommonSettings(CrystalLanguage).indentOptions!!)
            }

            override fun disposeUIResources() {
                panel = null
            }

            override fun getDisplayName(): String = getLanguage().displayName

            override fun getHelpTopic(): String? = null
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun getDefaultCommonSettings(): CommonCodeStyleSettings {
        return CommonCodeStyleSettings(CrystalLanguage).apply {
            initIndentOptions().apply {
                INDENT_SIZE = 2
                CONTINUATION_INDENT_SIZE = 2
                TAB_SIZE = 2
                USE_TAB_CHARACTER = false
            }
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String? {
        return """
            |class Greeter
            |  def initialize(@name : String)
            |  end
            |
            |  def greet
            |    if @name.empty?
            |      puts "Hello, stranger!"
            |    else
            |      puts "Hello, #{@name}!"
            |    end
            |  end
            |end
            |
            |greeter = Greeter.new("World")
            |greeter.greet
        """.trimMargin()
    }
}
