package de.magynhard.crystal

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import de.magynhard.crystal.lexer.CrystalTokenTypes

class CrystalQuoteHandler : SimpleTokenSetQuoteHandler(
    CrystalTokenTypes.STRING_LITERAL,
    CrystalTokenTypes.CHAR_LITERAL
)
