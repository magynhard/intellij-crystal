package de.magynhard.crystal

import com.intellij.openapi.fileTypes.LanguageFileType
import de.magynhard.crystal.stubs.CrystalStubElementTypeHolder
import javax.swing.Icon

object CrystalFileType : LanguageFileType(CrystalLanguage) {
    override fun getName(): String = "Crystal"
    override fun getDescription(): String = "Crystal language file"
    override fun getDefaultExtension(): String = "cr"
    override fun getIcon(): Icon = CrystalIcons.FILE

    init {
        // Force the stub element type constants to exist at file type
        // registration (application start, before any project index
        // initialization). IStubElementType forbids construction after index
        // initialization ("All stub element types should be created before
        // index initialization is complete"), and the stubElementTypeHolder
        // enumeration normally creates them via reflection — but plugin
        // descriptor loading order (e.g. <module> dependencies) can defer
        // that enumeration past it. Referencing the holder here initializes
        // its constants deterministically and early.
        CrystalStubElementTypeHolder.CLASS_DEFINITION
    }
}
