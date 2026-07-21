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
    fun `legacy root protection and cleanup are registered without converter`() {
        val extensions = pluginXml().getChild("extensions").children

        assertTrue(
            extensions.any {
                it.name == "directoryIndexExcludePolicy" &&
                    it.getAttributeValue("implementation") ==
                    "de.magynhard.crystal.sdk.CrystalLegacyStdlibExcludePolicy"
            }
        )
        assertTrue(
            extensions.any {
                it.name == "postStartupActivity" &&
                    it.getAttributeValue("implementation") ==
                    "de.magynhard.crystal.sdk.CrystalLegacyStdlibCleanupActivity"
            }
        )
        assertTrue(extensions.none { it.name == "project.converterProvider" })
    }

    private fun pluginXml() = JDOMUtil.load(
        Path.of("src/main/resources/META-INF/plugin.xml")
    )
}
