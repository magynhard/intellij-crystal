package de.magynhard.crystal

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object CrystalFileType : LanguageFileType(CrystalLanguage) {
    override fun getName(): String = "Crystal"
    override fun getDescription(): String = "Crystal language file"
    override fun getDefaultExtension(): String = "cr"
    override fun getIcon(): Icon = CrystalIcons.FILE
}
