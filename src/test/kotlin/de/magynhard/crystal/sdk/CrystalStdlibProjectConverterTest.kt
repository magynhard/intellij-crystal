package de.magynhard.crystal.sdk

import com.intellij.conversion.ConversionContext
import com.intellij.conversion.ModuleSettings
import org.jdom.Element
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Path

class CrystalStdlibProjectConverterTest {

    @Test
    fun `converter reports legacy Crystal stdlib module library`() {
        val settings = moduleSettings(moduleXml(legacyCrystalLibrary()))
        val processor = CrystalStdlibConverterProvider().createConverter(conversionContext()).createModuleFileConverter()

        assertNotNull(processor)
        assertTrue(processor!!.isConversionNeeded(settings))
    }

    @Test
    fun `conversion removes only legacy Crystal stdlib module library`() {
        val unrelatedModuleLibrary = moduleLibrary("Other Library")
        val moduleDependency = Element("orderEntry")
            .setAttribute("type", "module")
            .setAttribute("module-name", "shared")
        val settings = moduleSettings(moduleXml(legacyCrystalLibrary(), unrelatedModuleLibrary, moduleDependency))
        val processor = CrystalStdlibConverterProvider().createConverter(conversionContext()).createModuleFileConverter()!!

        processor.process(settings)

        assertFalse(processor.isConversionNeeded(settings))
        assertTrue(settings.orderEntries().contains(unrelatedModuleLibrary))
        assertTrue(settings.orderEntries().contains(moduleDependency))
        assertFalse(settings.orderEntries().any { it.moduleLibraryName() == "Crystal StdLib" })
    }

    @Test
    fun `conversion is idempotent`() {
        val settings = moduleSettings(moduleXml(legacyCrystalLibrary()))
        val processor = CrystalStdlibConverterProvider().createConverter(conversionContext()).createModuleFileConverter()!!

        processor.process(settings)
        processor.process(settings)

        assertFalse(processor.isConversionNeeded(settings))
        assertTrue(settings.orderEntries().isEmpty())
    }

    @Test
    fun `converter ignores similarly named module libraries`() {
        val settings = moduleSettings(moduleXml(moduleLibrary("Crystal Stdlib")))
        val processor = CrystalStdlibConverterProvider().createConverter(conversionContext()).createModuleFileConverter()!!

        assertFalse(processor.isConversionNeeded(settings))
        processor.process(settings)
        assertEquals("Crystal Stdlib", settings.orderEntries().single().moduleLibraryName())
    }

    private fun conversionContext(): ConversionContext = proxy { methodName ->
        when (methodName) {
            "getModulePaths" -> emptyList<Path>()
            else -> null
        }
    }

    private fun moduleSettings(root: Element): ModuleSettings = proxy { methodName ->
        when (methodName) {
            "getRootElement" -> root
            "getOrderEntries" -> root.getChild("component").getChildren("orderEntry")
            "getPath" -> Path.of("project.iml")
            else -> null
        }
    }

    private inline fun <reified T> proxy(crossinline result: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            result(method.name)
        } as T

    private fun moduleXml(vararg orderEntries: Element): Element {
        val component = Element("component").setAttribute("name", "NewModuleRootManager")
        orderEntries.forEach(component::addContent)
        return Element("module").addContent(component)
    }

    private fun legacyCrystalLibrary(): Element = moduleLibrary("Crystal StdLib")

    private fun moduleLibrary(name: String): Element =
        Element("orderEntry")
            .setAttribute("type", "module-library")
            .addContent(Element("library").setAttribute("name", name))

    private fun ModuleSettings.orderEntries(): List<Element> = getOrderEntries()

    private fun Element.moduleLibraryName(): String? =
        takeIf { getAttributeValue("type") == "module-library" }
            ?.getChild("library")
            ?.getAttributeValue("name")
}
