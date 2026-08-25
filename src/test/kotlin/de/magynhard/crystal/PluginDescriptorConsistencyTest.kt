package de.magynhard.crystal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the plugin descriptor wiring for optional module fragments.
 *
 * A fragment descriptor in META-INF that no `<depends optional="true"
 * config-file="...">` entry references is silently never loaded at runtime:
 * its extensions disappear (this broke the ECR Structure View and the spec
 * test locator in 0.2.5), and an undeclared module dependency makes the
 * Marketplace verifier report binary incompatibilities. These tests fail the
 * build as soon as a fragment is orphaned or a config-file reference dangles.
 *
 * The descriptors are read from the source tree (src/main/resources/META-INF)
 * because that is the source of truth the packaging step copies verbatim.
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

    private fun configFiles(): Set<String> {
        val nodes = parse(pluginDescriptor()).getElementsByTagName("depends")
        return (0 until nodes.length).mapNotNull { index ->
            nodes.item(index).attributes.getNamedItem("config-file")?.nodeValue
        }.toSet()
    }

    @Test
    fun `smRunner and structureView fragments are wired via config-file`() {
        val wired = configFiles()
        assertTrue(
            "plugin-smRunner.xml must be referenced from plugin.xml via a config-file depends",
            "plugin-smRunner.xml" in wired
        )
        assertTrue(
            "plugin-structureView.xml must be referenced from plugin.xml via a config-file depends",
            "plugin-structureView.xml" in wired
        )
    }

    @Test
    fun `smRunner and structureView depends are optional`() {
        val nodes = parse(pluginDescriptor()).getElementsByTagName("depends")
        val optionalModules = (0 until nodes.length).filter { index ->
            nodes.item(index).attributes.getNamedItem("optional")?.nodeValue == "true"
        }.map { index -> nodes.item(index).textContent.trim() }.toSet()
        assertTrue(
            "intellij.platform.smRunner must be an optional depends (the platform test application does not bundle it)",
            "intellij.platform.smRunner" in optionalModules
        )
        assertTrue(
            "intellij.platform.structureView must be an optional depends (the platform test application does not bundle it)",
            "intellij.platform.structureView" in optionalModules
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
        assertTrue("Expected optional fragment descriptors next to plugin.xml", fragments.isNotEmpty())
        val orphaned = fragments.filterNot { it in wired }
        assertEquals(
            "Orphaned fragment descriptors are never loaded at runtime and must be wired via a config-file depends: $orphaned",
            emptyList<String>(),
            orphaned
        )
    }
}
