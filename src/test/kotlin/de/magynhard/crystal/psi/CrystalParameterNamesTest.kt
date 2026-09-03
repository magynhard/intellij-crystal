package de.magynhard.crystal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalParameterNamesTest : BasePlatformTestCase() {

    fun testSeparatesCallLocalAndStorageNames() {
        val file = myFixture.configureByText("test.cr", """
            def names(plain, public internal, @direct, label @stored, @@class_direct, class_label @@class_stored)
            end
        """.trimIndent())
        val parameters = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java).toList()

        assertNames(parameters[0], "plain", "plain", null, null, "plain")
        assertNames(parameters[1], "public", "internal", null, "public", "public internal")
        assertNames(parameters[2], "direct", "direct", "@direct", null, "@direct")
        assertNames(parameters[3], "label", "stored", "@stored", "label", "label @stored")
        assertNames(parameters[4], "class_direct", "class_direct", "@@class_direct", null, "@@class_direct")
        assertNames(parameters[5], "class_label", "class_stored", "@@class_stored", "class_label", "class_label @@class_stored")
    }

    fun testSeparatesNamesForPrefixedStorageParameters() {
        val file = myFixture.configureByText("test.cr", """
            def names(&@block, *@splat, **@@double_splat)
            end
        """.trimIndent())
        val parameters = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java).toList()

        assertNames(parameters[0], "block", "block", "@block", null, "@block")
        assertNames(parameters[1], "splat", "splat", "@splat", null, "@splat")
        assertNames(parameters[2], "double_splat", "double_splat", "@@double_splat", null, "@@double_splat")
    }

    private fun assertNames(
        parameter: CrystalParameter,
        callSiteName: String,
        localName: String,
        storageName: String?,
        explicitExternalName: String?,
        sourceName: String,
    ) {
        val names = parameter.parameterNameInfo()
        assertEquals(callSiteName, names.callSiteName)
        assertEquals(localName, names.localName)
        assertEquals(storageName, names.storageName)
        assertEquals(explicitExternalName, names.explicitExternalName)
        assertEquals(sourceName, names.sourceName)
    }
}
