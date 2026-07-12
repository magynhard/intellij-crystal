package de.magynhard.crystal.ecr

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

object EmbeddedCrystalLanguage : Language("EmbeddedCrystal"), TemplateLanguage {
    override fun getDisplayName(): String = "ECR"
    private fun readResolve(): Any = EmbeddedCrystalLanguage
}
