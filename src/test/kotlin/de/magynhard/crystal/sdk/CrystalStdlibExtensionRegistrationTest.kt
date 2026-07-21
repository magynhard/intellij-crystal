package de.magynhard.crystal.sdk

import com.intellij.openapi.util.JDOMUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class CrystalStdlibExtensionRegistrationTest {

    @Test
    fun `synthetic provider is the only runtime stdlib root source`() {
        val extensions = pluginXml().getChild("extensions").children

        assertEquals(
            listOf("de.magynhard.crystal.sdk.CrystalStdlibLibraryProvider"),
            extensions.filter { it.name == "additionalLibraryRootsProvider" }
                .map { it.getAttributeValue("implementation") }
        )
        assertTrue(
            extensions.none {
                it.name == "postStartupActivity" &&
                    it.getAttributeValue("implementation") ==
                    "de.magynhard.crystal.sdk.CrystalStdlibSourceRootConfigurator"
            }
        )
    }

    @Test
    fun `pre-load stdlib converter is registered`() {
        val extensions = pluginXml().getChild("extensions").children

        assertTrue(
            extensions.any {
                it.name == "project.converterProvider" &&
                    it.getAttributeValue("implementation") ==
                    "de.magynhard.crystal.sdk.CrystalStdlibConverterProvider"
            }
        )
    }

    private fun pluginXml() = JDOMUtil.load(
        Path.of("src/main/resources/META-INF/plugin.xml")
    )
}
