package de.magynhard.crystal.settings

import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import de.magynhard.crystal.CrystalLanguage

class CrystalCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = CrystalLanguage

    override fun customizeDefaults(settings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        indentOptions.apply {
            INDENT_SIZE = 2
            CONTINUATION_INDENT_SIZE = 2
            TAB_SIZE = 2
            USE_TAB_CHARACTER = false
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
