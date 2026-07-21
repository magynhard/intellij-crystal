package de.magynhard.crystal.sdk

import com.intellij.conversion.ConversionContext
import com.intellij.conversion.ConversionProcessor
import com.intellij.conversion.ConverterProvider
import com.intellij.conversion.ModuleSettings
import com.intellij.conversion.ProjectConverter
import org.jdom.Element

class CrystalStdlibConverterProvider : ConverterProvider() {

    override fun getConversionDescription(): String =
        "Remove obsolete Crystal stdlib module roots; the stdlib is now supplied as a filtered synthetic library."

    override fun createConverter(context: ConversionContext): ProjectConverter =
        object : ProjectConverter() {
            override fun createModuleFileConverter(): ConversionProcessor<ModuleSettings> =
                CrystalStdlibModuleFileConverter
        }
}

private object CrystalStdlibModuleFileConverter : ConversionProcessor<ModuleSettings>() {

    override fun isConversionNeeded(settings: ModuleSettings): Boolean =
        settings.orderEntries.any { it.isLegacyCrystalStdlibLibrary() }

    override fun process(settings: ModuleSettings) {
        settings.orderEntries
            .filter { it.isLegacyCrystalStdlibLibrary() }
            .forEach(Element::detach)
    }

    private fun Element.isLegacyCrystalStdlibLibrary(): Boolean =
        getAttributeValue("type") == "module-library" &&
            getChild("library")?.getAttributeValue("name") == LEGACY_LIBRARY_NAME

    private const val LEGACY_LIBRARY_NAME = "Crystal StdLib"
}
