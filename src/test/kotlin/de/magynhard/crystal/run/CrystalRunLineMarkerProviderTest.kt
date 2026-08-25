package de.magynhard.crystal.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRunLineMarkerProviderTest : BasePlatformTestCase() {

    private fun identifierAt(fileText: String, needle: String): PsiElement {
        myFixture.configureByText("dummy_spec.cr", fileText)
        val offset = myFixture.file.text.indexOf(needle)
        assertTrue("'$needle' not found in fixture", offset >= 0)
        val leaf = myFixture.file.findElementAt(offset)
        assertNotNull(leaf)
        assertTrue(leaf is LeafPsiElement)
        return leaf!!
    }

    fun testInfoForDescribeHasTooltipAndActions() {
        val provider = CrystalRunLineMarkerProvider()
        val element = identifierAt("describe \"math\" do\nend\n", "describe")

        val info = provider.getInfo(element)

        assertNotNull("Should create run marker Info for 'describe' in spec file", info)
        assertEquals("Run spec suite", info!!.tooltipProvider.apply(element))
        assertTrue("Info should carry executor actions", info.actions.isNotEmpty())
    }

    fun testTooltipForIt() {
        val provider = CrystalRunLineMarkerProvider()
        val element = identifierAt("it \"adds\" do\nend\n", "it")

        val info = provider.getInfo(element)

        assertNotNull(info)
        assertEquals("Run spec", info!!.tooltipProvider.apply(element))
    }

    fun testNoInfoOutsideSpecFiles() {
        myFixture.configureByText("plain.cr", "describe \"x\" do\nend\n")
        val offset = myFixture.file.text.indexOf("describe")
        val leaf = myFixture.file.findElementAt(offset)!!

        assertNull(
            "No run marker outside *_spec.cr files",
            CrystalRunLineMarkerProvider().getInfo(leaf)
        )
    }

    fun testNoInfoOnNonCallIdentifiers() {
        val provider = CrystalRunLineMarkerProvider()
        val element = identifierAt("describe_var = 1\n", "describe_var")

        assertNull(
            "Identifiers merely starting with a keyword must not get a marker",
            provider.getInfo(element)
        )
    }
}
