package de.magynhard.crystal.ecr

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

object EmbeddedCrystalTokenTypes {
    @JvmField val WHITE_SPACE = com.intellij.psi.TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER = com.intellij.psi.TokenType.BAD_CHARACTER
    @JvmField val OUTER = TokenSet.create(EmbeddedCrystalTypes.ECR_OUTER)
    @JvmField val TAGS = TokenSet.create(
        EmbeddedCrystalTypes.ECR_TAG_BEGIN, EmbeddedCrystalTypes.ECR_TAG_END, EmbeddedCrystalTypes.ECR_RAW)
}
