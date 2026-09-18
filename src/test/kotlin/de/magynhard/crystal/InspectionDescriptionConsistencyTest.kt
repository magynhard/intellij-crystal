package de.magynhard.crystal

import com.intellij.codeInspection.LocalInspectionTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards that every registered local inspection ships a static description.
 *
 * The Inspect Code results view crashes with "Inspection #X has no
 * description" as soon as a result node without one is selected
 * (`InspectionNodeInfo`), so a missing description breaks the whole results
 * UI — while every test stays green, because no test ever selected a node.
 *
 * These tests pin the full loading contract for each `localInspection`
 * entry in plugin.xml: the HTML file exists in source, it is reachable on
 * the runtime classpath under the platform's conventional path, the tool's
 * effective short name matches the registration, and `loadDescription()` —
 * the exact call the Inspect Code results view makes — returns non-blank
 * text. (Asserting `getStaticDescription()` instead would prove nothing: the
 * platform default returns null by design and only `loadDescription()`
 * falls back to the HTML resource.)
 */
class InspectionDescriptionConsistencyTest {

    private data class Registration(val shortName: String, val implementationClass: String)

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/resources/META-INF/plugin.xml").isFile) return dir
            dir = dir.parentFile
        }
        error("repository root not found above the working directory ${System.getProperty("user.dir")}")
    }

    private fun registrations(): List<Registration> {
        val pluginXml = File(repoRoot(), "src/main/resources/META-INF/plugin.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pluginXml)
            .getElementsByTagName("localInspection")
        return (0 until nodes.length).map { nodes.item(it) }.map { node ->
            val attributes = node.attributes
            Registration(
                attributes.getNamedItem("shortName").nodeValue,
                attributes.getNamedItem("implementationClass").nodeValue
            )
        }
    }

    private fun instantiate(implementationClass: String): LocalInspectionTool {
        val clazz = Class.forName(implementationClass)
        assertTrue(
            "$implementationClass must be a LocalInspectionTool",
            LocalInspectionTool::class.java.isAssignableFrom(clazz)
        )
        return clazz.getDeclaredConstructor().newInstance() as LocalInspectionTool
    }

    @Test
    fun `every registered inspection has a description source file`() {
        for (registration in registrations()) {
            val file = File(
                repoRoot(),
                "src/main/resources/inspectionDescriptions/${registration.shortName}.html"
            )
            assertTrue(
                "Missing description source for #${registration.shortName}: ${file.path}",
                file.isFile
            )
            assertTrue(
                "Description source for #${registration.shortName} must be an HTML document",
                file.readText().contains("<html", ignoreCase = true)
            )
        }
    }

    @Test
    fun `every registered inspection loads a non-blank description`() {
        for (registration in registrations()) {
            val tool = instantiate(registration.implementationClass)
            assertEquals(
                "Effective short name of ${registration.implementationClass} must match its plugin.xml registration",
                registration.shortName,
                tool.shortName
            )
            assertNotNull(
                "Description resource for #${registration.shortName} must be on the classpath",
                javaClass.getResource("/inspectionDescriptions/${registration.shortName}.html")
            )
            val description = tool.loadDescription()
            assertTrue(
                "Loaded description of #${registration.shortName} must be non-blank " +
                    "(Inspect Code results crash without one)",
                !description.isNullOrBlank()
            )
        }
    }
}
