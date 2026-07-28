package de.magynhard.crystal.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalMethodHierarchyTest : BasePlatformTestCase() {

    fun testCollectsAllMethodsWithStableDepthSignatureAndPrecedence() {
        val file = myFixture.configureByText(
            "test.cr",
            "module Feature\n  def included(value : Int32)\n  end\nend\n" +
                "class Parent\n  def inherited\n  end\nend\n" +
                "class Service < Parent\n  include Feature\n  def direct\n  end\nend"
        )
        val session = CrystalTypeSetResolver.session(file)
        val result = session.collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("direct", "included", "inherited"), result.methods.map { it.method.name })
        assertEquals(listOf(0, 1, 1), result.methods.map { it.depth })
        assertEquals(result.methods.indices.toList(), result.methods.map { it.precedence })
        assertTrue(result.methods.all { it.signatureKey.isNotBlank() })
    }

    fun testAllMethodCollectionIsCachedPerSession() {
        val file = myFixture.configureByText("test.cr", "class Service\n  def value\n  end\nend")
        val session = CrystalTypeSetResolver.session(file)
        val identity = CrystalTypeIdentity("Service", "Service")

        val first = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)
        val second = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)

        assertSame(first, second)
    }

    fun testNamedMethodCollectionIsCachedAndMacroNameSensitive() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  def visible\n  end\n  {% if false %}\n  def hidden\n  end\n  {% end %}\nend"
        )
        val session = CrystalTypeSetResolver.session(file)
        val identity = CrystalTypeIdentity("Service", "Service")

        val first = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "visible")
        val second = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "visible")
        val hidden = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "hidden")

        assertSame(first, second)
        assertTrue(first.complete)
        assertEquals(listOf("visible"), first.methods.map { it.name })
        assertFalse(hidden.complete)
    }

    fun testInterpolatedMacroMethodNameMakesNamedLookupIncomplete() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  def visible\n  end\n  def {{dynamic_name}}\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectNamedMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE,
            "visible"
        )

        assertFalse(result.complete)
    }

    fun testAllMethodCollectionReportsIncompleteMacroControlledMethods() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  {% if false %}\n  def hidden\n  end\n  {% end %}\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE
        )

        assertFalse(result.complete)
        assertTrue(result.methods.isEmpty())
    }
}
