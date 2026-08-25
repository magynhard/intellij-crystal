package de.magynhard.crystal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the plugin descriptor wiring for dependent extensions.
 *
 * Product module names cannot be resolved as legacy `<depends>` targets at
 * runtime: any config-file fragment behind such a target is silently excluded
 * ("plugin intellij.platform.structureView is not resolved"). They must use
 * `<dependencies><module name="..."/></dependencies>`, while their extensions
 * stay registered in the main descriptor. Registering them inside excluded
 * fragments made the ECR Structure View and spec test locator disappear in
 * 0.2.5 while all tests stayed green (the test constructed the factory directly).
 *
 * These tests read src/main/resources/META-INF/plugin.xml — the source of
 * truth copied verbatim into the packaged plugin — and fail the build when a
 * critical registration disappears, a config-file reference dangles, or a
 * fragment descriptor is orphaned.
 */
class PluginDescriptorConsistencyTest {

    private fun metaInfDirectory(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/main/resources/META-INF")
            if (File(candidate, "plugin.xml").isFile) return candidate
            dir = dir.parentFile
        }
        error("src/main/resources/META-INF not found above the working directory ${System.getProperty("user.dir")}")
    }

    private fun descriptorFile(name: String): File = File(metaInfDirectory(), name)

    private fun parse(xml: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)

    private fun pluginDescriptor(): File = descriptorFile("plugin.xml")

    private fun elements(tagName: String) =
        parse(pluginDescriptor()).getElementsByTagName(tagName).let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }
        }

    private fun configFiles(): Set<String> = elements("depends")
        .mapNotNull { it.attributes.getNamedItem("config-file")?.nodeValue }
        .toSet()

    @Test
    fun `ecr structure view factory is registered in main plugin dot xml`() {
        val registration = elements("lang.psiStructureViewFactory").singleOrNull { node ->
            node.attributes.getNamedItem("language")?.nodeValue == "EmbeddedCrystal"
        }
        assertNotNull(
            "The EmbeddedCrystal psiStructureViewFactory must stay registered unconditionally in plugin.xml",
            registration
        )
        assertEquals(
            "de.magynhard.crystal.ecr.EcrStructureViewFactory",
            registration?.attributes?.getNamedItem("implementationClass")?.nodeValue
        )
    }

    @Test
    fun `spec test locator is registered in main plugin dot xml`() {
        val registration = elements("testLocator").singleOrNull { node ->
            node.attributes.getNamedItem("implementation")?.nodeValue ==
                "de.magynhard.crystal.run.CrystalTestLocator"
        }
        assertNotNull(
            "CrystalTestLocator must stay registered unconditionally in plugin.xml",
            registration
        )
    }

    @Test
    fun `required product modules use modern module dependencies`() {
        val modules = elements("module")
            .mapNotNull { it.attributes.getNamedItem("name")?.nodeValue }
            .toSet()
        assertTrue("The DAP product module must be declared", "intellij.platform.dap" in modules)
        assertTrue("The SM Test Runner product module must be declared", "intellij.platform.smRunner" in modules)
        assertTrue("The Structure View product module must be declared", "intellij.platform.structureView" in modules)

        val legacyDepends = elements("depends").map { it.textContent.trim() }.toSet()
        assertTrue(
            "Product module names must not use legacy <depends>; the runtime treats them as unresolved plugin IDs",
            modules.none { it in legacyDepends }
        )
    }

    @Test
    fun `every config-file reference points to an existing descriptor`() {
        for (name in configFiles()) {
            assertTrue(
                "config-file '$name' referenced from plugin.xml does not exist in META-INF",
                descriptorFile(name).isFile
            )
        }
    }

    @Test
    fun `every fragment descriptor is wired from plugin dot xml`() {
        val wired = configFiles()
        val fragments = metaInfDirectory()
            .listFiles { file -> file.extension == "xml" && file.name != "plugin.xml" }
            .orEmpty()
            .map { it.name }
        val orphaned = fragments.filterNot { it in wired }
        assertEquals(
            "Orphaned fragment descriptors are never loaded at runtime; register their extensions in plugin.xml instead: $orphaned",
            emptyList<String>(),
            orphaned
        )
    }
}
