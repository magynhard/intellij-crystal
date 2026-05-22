package de.magynhard.crystal

import com.intellij.lang.Language

object CrystalLanguage : Language("Crystal") {
    private fun readResolve(): Any = CrystalLanguage
}
