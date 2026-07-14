package de.magynhard.crystal.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import de.magynhard.crystal.CrystalIcons
import javax.swing.Icon

class CrystalColorSettingsPage : ColorSettingsPage {

    companion object {
        val CRYSTAL_INJECTED_LANGUAGE = TextAttributesKey.createTextAttributesKey(
            "CRYSTAL_INJECTED_LANGUAGE"
        )

        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Injected language fragment", CRYSTAL_INJECTED_LANGUAGE),
            AttributesDescriptor("Keyword", CrystalSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Number", CrystalSyntaxHighlighter.NUMBER),
            AttributesDescriptor("String", CrystalSyntaxHighlighter.STRING),
            AttributesDescriptor("String escape", CrystalSyntaxHighlighter.STRING_ESCAPE),
            AttributesDescriptor("Character", CrystalSyntaxHighlighter.CHAR),
            AttributesDescriptor("Comment", CrystalSyntaxHighlighter.COMMENT),
            AttributesDescriptor("TODO/FIXME/NOTE comment", CrystalSyntaxHighlighter.TODO_COMMENT),
            AttributesDescriptor("Identifier", CrystalSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Constant", CrystalSyntaxHighlighter.CONSTANT),
            AttributesDescriptor("Instance variable", CrystalSyntaxHighlighter.INSTANCE_VAR),
            AttributesDescriptor("Class variable", CrystalSyntaxHighlighter.CLASS_VAR),
            AttributesDescriptor("Global variable", CrystalSyntaxHighlighter.GLOBAL_VAR),
            AttributesDescriptor("Symbol", CrystalSyntaxHighlighter.SYMBOL),
            AttributesDescriptor("Operator", CrystalSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Regular expression", CrystalSyntaxHighlighter.REGEX),
            AttributesDescriptor("String interpolation", CrystalSyntaxHighlighter.INTERPOLATION),
            AttributesDescriptor("Parentheses", CrystalSyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Brackets", CrystalSyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Braces", CrystalSyntaxHighlighter.BRACES),
            AttributesDescriptor("Comma", CrystalSyntaxHighlighter.COMMA),
            AttributesDescriptor("Semicolon", CrystalSyntaxHighlighter.SEMICOLON),
            AttributesDescriptor("Dot", CrystalSyntaxHighlighter.DOT),
            AttributesDescriptor("Bad character", CrystalSyntaxHighlighter.BAD_CHARACTER),
            AttributesDescriptor("Function declaration", CrystalSyntaxHighlighter.FUNCTION_DECLARATION),
            AttributesDescriptor("Class/Module declaration", CrystalSyntaxHighlighter.CLASS_DECLARATION),
            AttributesDescriptor("Parameter", CrystalSyntaxHighlighter.PARAMETER),
            AttributesDescriptor("Heredoc delimiter", CrystalSyntaxHighlighter.HEREDOC_DELIMITER),
        )
    }

    override fun getIcon(): Icon = CrystalIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = CrystalSyntaxHighlighter()

    override fun getDemoText(): String = """
        # Crystal example
        class Greeter
          @name : String
          @@count = 0

          def initialize(@name : String)
            @@count += 1
          end

          def greet(greeting = "Hello")
            puts "#{greeting}, #{@name}!"
          end

          def farewell(name, message)
            puts message
            puts name
          end
        end

        module Printable
          abstract def to_s : String
        end

        struct Point
          def initialize(x : Int32, y : Int32)
          end
        end

        enum Color
          Red; Green; Blue
        end

        # Literals
        number = 42_i64
        float = 3.14
        char = 'a'
        symbol = :my_symbol
        regex = /[a-z]+/i
        arr = %w(foo bar baz)
        heredoc = <<-END
          content
          END

        # Control flow
        if number > 0
          result = number ** 2
        end

        [1, 2, 3].each do |item|
          puts item
        end
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Crystal"
}
