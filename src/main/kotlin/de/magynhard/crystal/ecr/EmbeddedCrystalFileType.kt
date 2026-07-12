package de.magynhard.crystal.ecr

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object EmbeddedCrystalFileType : LanguageFileType(EmbeddedCrystalLanguage) {
    override fun getName(): String = "Embedded Crystal"
    override fun getDescription(): String = "Embedded Crystal (ECR) template"
    override fun getDefaultExtension(): String = "ecr"
    override fun getIcon(): Icon = EmbeddedCrystalIcons.FILE
}
