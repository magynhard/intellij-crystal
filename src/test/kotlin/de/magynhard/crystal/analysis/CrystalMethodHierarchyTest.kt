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

    fun testStorageTargetDoesNotChangeCallableSignature() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  def configure(label @value : Int32)\n  end\n" +
                "  def configure(label @@value : Int32)\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE,
        )
        val configureMethods = result.methods.filter { it.method.name == "configure" }

        assertTrue(result.complete)
        assertEquals(1, configureMethods.size)
        assertTrue(configureMethods.single().method.text.contains("@@value"))
    }

    fun testNamedOnlyExternalLabelsDistinguishConstructorSignatures() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  def initialize(*, left @value : Int32)\n  end\n" +
                "  def initialize(*, right @value : String)\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE,
        )
        val constructors = result.methods.filter { it.method.name == "initialize" }

        assertTrue(result.complete)
        assertEquals(2, constructors.size)
        assertEquals(2, constructors.map { it.signatureKey }.distinct().size)
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

    fun testInterpolatedMacroMethodNameSuppressesOnlyUnknownNames() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Service\n  def visible\n  end\n  def {{dynamic_name}}\n  end\nend"
        )
        val session = CrystalTypeSetResolver.session(file)

        // An explicitly written method stays resolvable even when sibling methods are
        // generated through macro interpolation (stdlib http/client.cr pattern).
        val visible = session.collectNamedMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE,
            "visible"
        )
        assertTrue(visible.complete)
        assertEquals(listOf("visible"), visible.methods.map { it.name })

        // A name that is not explicitly defined anywhere is genuinely uncertain: the
        // macro could generate it, so the lookup must stay incomplete.
        val unknown = session.collectNamedMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE,
            "anything_else"
        )
        assertFalse(unknown.complete)
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
        // Compiler truth (crystal eval): Int8..Int128.superclass == Int,
        // UInt8..UInt128.superclass == Int (the stdlib source declares no abstract
        // `UInt`), Float32/Float64.superclass == Float.
        val signed = listOf("Int8", "Int16", "Int32", "Int64", "Int128")
        val unsigned = listOf("UInt8", "UInt16", "UInt32", "UInt64", "UInt128")
        val floats = listOf("Float32", "Float64")
        val declarations = buildString {
            append("struct Int\n  def int_parent\n  end\nend\n")
            append("struct Float\n  def float_parent\n  end\nend\n")
            (signed + unsigned + floats).forEach { append("struct $it\nend\n") }
        }
        val file = myFixture.configureByText("test.cr", declarations)
        val session = CrystalTypeSetResolver.session(file)

        signed.forEach { type ->
            assertEquals(
                listOf("int_parent"),
                session.collectMethods(CrystalTypeIdentity(type, type), CrystalReceiverMode.INSTANCE)
                    .methods.map { it.method.name }
            )
        }
        unsigned.forEach { type ->
            assertEquals(
                listOf("int_parent"),
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

    fun testMethodHierarchyUsesOnlyForwardRequireClosure() {
        myFixture.addFileToProject("loaded.cr", "class Service\n  def loaded\n  end\nend")
        myFixture.addFileToProject("not_loaded.cr", "class Service\n  def leaked\n  end\nend")
        val context = myFixture.configureByText("main.cr", "require \"./loaded\"\nService.new")
        val session = CrystalTypeSetResolver.session(context)
        val identity = CrystalTypeIdentity("Service", "Service")

        val all = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)
        val loaded = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "loaded")
        val leaked = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "leaked")

        assertTrue(all.complete)
        assertEquals(listOf("loaded"), all.methods.map { it.method.name })
        assertTrue(loaded.complete)
        assertEquals(listOf("loaded"), loaded.methods.map { it.name })
        assertTrue(leaked.complete)
        assertTrue(leaked.methods.isEmpty())
    }

    fun testInvisibleDuplicateReopeningDoesNotMakeCollectionIncomplete() {
        myFixture.addFileToProject(
            "loaded.cr",
            "class Service\n  def convert(value : Int32)\n  end\nend"
        )
        myFixture.addFileToProject(
            "not_loaded.cr",
            "class Service\n  def convert(value : Int32)\n  end\nend"
        )
        val context = myFixture.configureByText("main.cr", "require \"./loaded\"\nService.new")

        val result = CrystalTypeSetResolver.session(context).collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("convert"), result.methods.map { it.method.name })
        assertEquals("loaded.cr", result.methods.single().method.containingFile.name)
    }

    fun testIncludeAndExtendEdgesUseOnlyForwardRequireClosure() {
        myFixture.addFileToProject(
            "loaded.cr",
            "module LoadedFeature\n  def loaded_instance\n  end\nend\n" +
                "module LoadedFactory\n  def loaded_static\n  end\nend\n" +
                "class Service\n  include LoadedFeature\n  extend LoadedFactory\nend"
        )
        myFixture.addFileToProject(
            "not_loaded.cr",
            "module HiddenFeature\n  def leaked_instance\n  end\nend\n" +
                "module HiddenFactory\n  def leaked_static\n  end\nend\n" +
                "class Service\n  include HiddenFeature\n  extend HiddenFactory\nend"
        )
        val context = myFixture.configureByText("main.cr", "require \"./loaded\"\nService.new")
        val session = CrystalTypeSetResolver.session(context)
        val identity = CrystalTypeIdentity("Service", "Service")

        val instanceMethods = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)
        val staticMethods = session.collectMethods(identity, CrystalReceiverMode.STATIC)

        assertTrue(instanceMethods.complete)
        assertEquals(listOf("loaded_instance"), instanceMethods.methods.map { it.method.name })
        assertTrue(staticMethods.complete)
        assertEquals(listOf("loaded_static"), staticMethods.methods.map { it.method.name })
    }

    fun testSuperclassUsesOnlyForwardRequireClosure() {
        myFixture.addFileToProject(
            "loaded.cr",
            "class LoadedParent\n  def loaded_parent\n  end\nend\nclass Service < LoadedParent\nend"
        )
        myFixture.addFileToProject(
            "not_loaded.cr",
            "class HiddenParent\n  def leaked_parent\n  end\nend\nclass Service < HiddenParent\nend"
        )
        val context = myFixture.configureByText("main.cr", "require \"./loaded\"\nService.new")

        val result = CrystalTypeSetResolver.session(context).collectMethods(
            CrystalTypeIdentity("Service", "Service"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("loaded_parent"), result.methods.map { it.method.name })
    }

    // ==================== Compiler-imposed base hierarchy ====================

    fun testStructWithoutDeclaredSuperclassReachesObjectThroughStructAndValue() {
        // Compiler truth: struct X → Struct → Value → Object (no stdlib struct
        // declares its superclass). Reported false positive: {…}.to_json was
        // checked against NamedTuple#to_json(json) only, while Object#to_json
        // (the zero-argument target the compiler dispatches to) was invisible.
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "struct Value\n  def value_marker\n  end\nend\n" +
                "struct Struct\n  def struct_marker\n  end\nend\n" +
                "struct NamedTuple\n  def own\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("NamedTuple", "NamedTuple"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(
            listOf("own", "struct_marker", "value_marker", "object_marker"),
            result.methods.map { it.method.name }
        )
        assertEquals(listOf(0, 1, 2, 3), result.methods.map { it.depth })
    }

    fun testClassWithoutDeclaredSuperclassReachesObjectThroughReference() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "class Reference\n  def reference_marker\n  end\nend\n" +
                "class String\n  def own\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("String", "String"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(
            listOf("own", "reference_marker", "object_marker"),
            result.methods.map { it.method.name }
        )
    }

    fun testPrimitiveChainReachesObjectRootThroughNumberAndValue() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "struct Value\n  def value_marker\n  end\nend\n" +
                "struct Number\n  def number_marker\n  end\nend\n" +
                "struct Int\n  def int_marker\n  end\nend\n" +
                "struct Int32\n  def own\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Int32", "Int32"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(
            listOf("own", "int_marker", "number_marker", "value_marker", "object_marker"),
            result.methods.map { it.method.name }
        )
    }

    fun testObjectRootDoesNotInheritReferenceSelfLoop() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "class Reference\n  def reference_marker\n  end\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Object", "Object"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("object_marker"), result.methods.map { it.method.name })
    }

    fun testImplicitBaseEdgeDegradesGracefullyWithoutBaseDeclarations() {
        // No Struct/Value/Object declarations visible → the implicit edge is
        // dropped silently and the type stays chainless-but-complete.
        val file = myFixture.configureByText("test.cr", "struct NamedTuple\n  def own\n  end\nend")
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("NamedTuple", "NamedTuple"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(listOf("own"), result.methods.map { it.method.name })
    }

    fun testEnumReachesObjectThroughEnumAndValue() {
        // Compiler truth (crystal eval): user enum Status < Enum (its real
        // parent — NOT Struct), Enum < Value. json/to_json.cr's reopened
        // `struct Enum` supplies to_json; enum.cr's `abstract struct Enum`
        // supplies to_s/hash.
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "struct Value\n  def value_marker\n  end\nend\n" +
                "struct Enum\n  def enum_marker\n  end\nend\n" +
                "enum Status\n  ACTIVE\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Status", "Status"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        assertEquals(
            listOf("enum_marker", "value_marker", "object_marker"),
            result.methods.map { it.method.name }
        )
        assertEquals(listOf(1, 2, 3), result.methods.map { it.depth })
    }

    fun testEnumBodyMethodsAndBaseEnumMethodsAreCollected() {
        val file = myFixture.configureByText(
            "test.cr",
            "struct Enum\n  def enum_marker : String\n  end\nend\n" +
                "enum Status\n  ACTIVE\n  def label\n  end\n  def self.default\n  end\nend"
        )
        val session = CrystalTypeSetResolver.session(file)
        val identity = CrystalTypeIdentity("Status", "Status")

        val instance = session.collectMethods(identity, CrystalReceiverMode.INSTANCE)
        val static = session.collectMethods(identity, CrystalReceiverMode.STATIC)
        val named = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "label")

        assertTrue(instance.complete)
        assertTrue(static.complete)
        assertTrue(named.complete)
        assertEquals(listOf("label", "enum_marker"), instance.methods.map { it.method.name })
        assertEquals(listOf("label"), named.methods.map { it.name })
        assertEquals(listOf("default"), static.methods.map { it.method.name })
    }

    fun testEnumTypeSuffixIsNotTreatedAsSuperclass() {
        // `enum Color : UInt8` — the colon suffix is the underlying integer
        // type, not a nominal superclass; the compiler-imposed Enum parent
        // must stay the only edge.
        val file = myFixture.configureByText(
            "test.cr",
            "class Object\n  def object_marker\n  end\nend\n" +
                "struct Value\n  def value_marker\n  end\nend\n" +
                "struct Enum\n  def enum_marker\n  end\nend\n" +
                "struct UInt8\n  def uint_marker\n  end\nend\n" +
                "enum Color : UInt8\n  RED\nend"
        )
        val result = CrystalTypeSetResolver.session(file).collectMethods(
            CrystalTypeIdentity("Color", "Color"),
            CrystalReceiverMode.INSTANCE
        )

        assertTrue(result.complete)
        // Enum → Value → Object, but NOT UInt8 (the `: UInt8` suffix is the
        // underlying storage type, invisible to method lookup).
        assertEquals(
            listOf("enum_marker", "value_marker", "object_marker"),
            result.methods.map { it.method.name }
        )
    }
}
