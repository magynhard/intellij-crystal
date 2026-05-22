package de.magynhard.crystal.lexer

import com.intellij.lexer.FlexAdapter

class CrystalLexerAdapter : FlexAdapter(CrystalLexer(null))
