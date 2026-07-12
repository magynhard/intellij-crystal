package de.magynhard.crystal.ecr.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer

object EmbeddedCrystalLexerFactory {
    fun create(): Lexer = FlexAdapter(EmbeddedCrystalLexer(null))
}
