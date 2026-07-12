package de.magynhard.crystal.ecr.lexer

import com.intellij.lexer.LayeredLexer
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.ecr.EmbeddedCrystalTypes

class EmbeddedCrystalLexerAdapter : LayeredLexer(EmbeddedCrystalLexerFactory.create()) {
    init {
        registerLayer(CrystalLexerAdapter(), EmbeddedCrystalTypes.ECR_RAW)
    }
}
