package de.magynhard.crystal.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalTypeSetResolver

class CrystalCompletionHelperTest : BasePlatformTestCase() {

    fun testMergesMethodsFromMultipleExactTypesByCanonicalSignature() {
        val context = myFixture.configureByText("types.cr", """
            class First
              def first_only
              end

              def shared(value : Int32)
              end

              def convert(value : Int32)
              end
            end

            class Second
              def second_only
              end

              def shared(value : Int32)
              end

              def convert(value : String)
              end
            end
        """.trimIndent())

        val lookups = CrystalCompletionHelper.getMethodsAsLookups(listOf("First", "Second"), context)
        val names = lookups.map { it.lookupString }

        assertEquals(1, names.count { it == "first_only" })
        assertEquals(1, names.count { it == "second_only" })
        assertEquals(1, names.count { it == "shared" })
        assertEquals(2, names.count { it == "convert" })
    }

    fun testQualifiedTypesOnlyContributeMethodsFromWrittenIdentities() {
        val context = myFixture.configureByText("qualified.cr", """
            module Left
              class Service
                def from_left
                end
              end
            end

            module Right
              class Service
                def from_right
                end
              end
            end

            module Other
              class Service
                def from_other
                end
              end
            end
        """.trimIndent())

        val names = CrystalCompletionHelper.getMethodsAsLookups(
            listOf("Left::Service", "Right::Service"),
            context
        ).map { it.lookupString }

        assertEquals(listOf("from_left", "from_right"), names)
        assertFalse(names.contains("from_other"))
    }

    fun testStaticMethodsUseExactQualifiedIdentityAndRetainOverloads() {
        val context = myFixture.configureByText("qualified_static.cr", """
            module Left
              class Service
                def self.from_left
                end

                def self.build(value : Int32)
                end

                def self.build(value : String)
                end
              end
            end

            module Right
              class Service
                def self.from_right
                end
              end
            end
        """.trimIndent())

        val lookups = CrystalCompletionHelper.getStaticMethodsAsLookups("Left::Service", context)
        val names = lookups.map { it.lookupString }

        assertEquals(1, names.count { it == "from_left" })
        assertEquals(2, names.count { it == "build" })
        assertFalse(names.contains("from_right"))
    }

    fun testFindsIndexedPrimitiveAndGenericBaseMethods() {
        myFixture.addFileToProject("stdlib/int.cr", "struct Int32\n  def integer_method\n  end\nend")
        myFixture.addFileToProject("stdlib/string.cr", "class String\n  def string_method\n  end\nend")
        myFixture.addFileToProject("stdlib/array.cr", "class Array(T)\n  def array_method\n  end\nend")
        val context = myFixture.configureByText(
            "usage.cr",
            "require \"./stdlib/int\"\nrequire \"./stdlib/string\"\nrequire \"./stdlib/array\"\nvalue = nil"
        )

        val names = CrystalCompletionHelper.getMethodsAsLookups(
            listOf("Int32", "String", "Array"),
            context
        ).map { it.lookupString }

        assertEquals(listOf("integer_method", "string_method", "array_method"), names)
    }

    fun testUnionLookupRequiresEveryExactBranch() {
        val context = myFixture.configureByText("types.cr", "class Foo\n  def foo_method\n  end\nend")

        assertEquals(
            emptyList<String>(),
            CrystalCompletionHelper.getMethodsAsLookups(listOf("Foo", "NotIndexed"), context)
                .map { it.lookupString }
        )
    }

    fun testUnionLookupRetainsAllKnownBranches() {
        val context = myFixture.configureByText(
            "types.cr",
            "class Foo\n  def foo_method\n  end\nend\n" +
                "class Bar\n  def bar_method\n  end\nend"
        )

        assertEquals(
            listOf("foo_method", "bar_method"),
            CrystalCompletionHelper.getMethodsAsLookups(listOf("Foo", "Bar"), context)
                .map { it.lookupString }
        )
    }

    fun testCompletionKeepsCertainMethodsFromMacroHeavyStdlibTypes() {
        val context = myFixture.configureByText(
            "stdlib.cr",
            "class String\n" +
                "  def upcase\n    {{ body_value }}\n  end\n" +
                "  {% if flag %}\n  def conditional\n  end\n  {% end %}\n" +
                "end\n" +
                "struct Time\n  def year\n  end\n  {% if flag %}\n  def zone\n  end\n  {% end %}\nend"
        )

        assertEquals(
            listOf("upcase"),
            CrystalCompletionHelper.getMethodsAsLookups("String", context).map { it.lookupString }
        )
        assertEquals(
            listOf("year"),
            CrystalCompletionHelper.getMethodsAsLookups("Time", context).map { it.lookupString }
        )
    }

    fun testMethodLookupsUseOnlyForwardRequiredReopenings() {
        myFixture.addFileToProject("loaded.cr", "class Service\n  def loaded\n  end\nend")
        myFixture.addFileToProject("not_loaded.cr", "class Service\n  def leaked\n  end\nend")
        val context = myFixture.configureByText("main.cr", "require \"./loaded\"\nService.new")

        val names = CrystalCompletionHelper.getMethodsAsLookups("Service", context)
            .map { it.lookupString }

        assertEquals(listOf("loaded"), names)
    }

    fun testFindTypeByNamePrefersCurrentFileAndFindsProjectTypes() {
        myFixture.addFileToProject("other.cr", """
            class SharedType
            end

            struct ProjectType
            end
        """.trimIndent())
        val currentFile = myFixture.addFileToProject("current.cr", """
            module SharedType
            end
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(currentFile.virtualFile)

        val preferred = CrystalCompletionHelper.findTypeByName("SharedType", project, myFixture.file)
        val projectType = CrystalCompletionHelper.findTypeByName("ProjectType", project)

        assertNotNull(preferred)
        assertEquals(CrystalCompletionHelper.TypeKind.MODULE, preferred!!.kind)
        assertEquals(myFixture.file.virtualFile, preferred.element.containingFile.virtualFile)
        assertNotNull(projectType)
        assertEquals(CrystalCompletionHelper.TypeKind.STRUCT, projectType!!.kind)
        assertEquals("other.cr", projectType.element.containingFile.name)
    }

    fun testGetMethodsAsLookupsUsesProvidedSharedSession() {
        val context = myFixture.configureByText("shared.cr", """
            class Service
              def serve
              end
            end
        """.trimIndent())

        val session = CrystalTypeSetResolver.session(context)
        val lookups = CrystalCompletionHelper.getMethodsAsLookups(listOf("Service"), context, session)
        assertTrue("Shared session must resolve instance methods: ${lookups.map { it.lookupString }}",
            lookups.any { it.lookupString == "serve" })
    }
}
