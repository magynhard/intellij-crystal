package de.magynhard.crystal

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import de.magynhard.crystal.psi.CrystalTypes

class CrystalQuoteHandler : SimpleTokenSetQuoteHandler(
    CrystalTypes.STRING_LITERAL,
    CrystalTypes.CHAR_LITERAL
)
