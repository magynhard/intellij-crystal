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

        assertTrue(result.complete)
        assertTrue(result.methods.isEmpty())
    }

    fun testAllMethodCollectionKeepsCertainMethodsAroundMacroUncertainty() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n" +
                "  def visible\n    {{ body_value }}\n  end\n" +
                "  {% if flag %}\n  def hidden\n  end\n  {% end %}\n" +
                "  def after_control\n  end\n" +
                "end"
        )
        val session = CrystalTypeSetResolver.session(file)
        val identity = CrystalTypeIdentity("Service", "Service")

        val all = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)
        val visible = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "visible")
        val hidden = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "hidden")

        assertTrue(all.complete)
        assertEquals(setOf("visible", "after_control"), all.methods.mapNotNull { it.method.name }.toSet())
        assertTrue(visible.complete)
        assertEquals(listOf("visible"), visible.methods.map { it.name })
        assertFalse(hidden.complete)
    }

    fun testImplicitPrimitiveHierarchyIsCycleSafe() {
        val file = myFixture.configureByText(
            "test.cr",
            "struct Int\n  def times\n  end\nend\n" +
                "struct Int32\n  def own\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Int32", "Int32"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("own", "times"), result.methods.map { it.method.name })
    }

    fun testEveryImplicitPrimitiveFamilyUsesItsCompilerParent() {
        val signed = listOf("Int8", "Int16", "Int32", "Int64", "Int128")
        val unsigned = listOf("UInt8", "UInt16", "UInt32", "UInt64", "UInt128")
        val floats = listOf("Float32", "Float64")
        val declarations = buildString {
            append("struct Int\n  def signed_parent\n  end\nend\n")
            append("struct UInt\n  def unsigned_parent\n  end\nend\n")
            append("struct Float\n  def float_parent\n  end\nend\n")
            (signed + unsigned + floats).forEach { append("struct $it\nend\n") }
        }
        val file = myFixture.configureByText("test.cr", declarations)
        val session = CrystalTypeSetResolver.session(file)

        signed.forEach { type ->
            assertEquals(
                listOf("signed_parent"),
                session.collectMethods(CrystalTypeIdentity(type, type), CrystalReceiverMode.INSTANCE)
                    .methods.map { it.method.name }
            )
        }
        unsigned.forEach { type ->
            assertEquals(
                listOf("unsigned_parent"),
                session.collectMethods(CrystalTypeIdentity(type, type), CrystalReceiverMode.INSTANCE)
                    .methods.map { it.method.name }
            )
        }
        floats.forEach { type ->
            assertEquals(
                listOf("float_parent"),
                session.collectMethods(CrystalTypeIdentity(type, type), CrystalReceiverMode.INSTANCE)
                    .methods.map { it.method.name }
            )
        }
    }
}
