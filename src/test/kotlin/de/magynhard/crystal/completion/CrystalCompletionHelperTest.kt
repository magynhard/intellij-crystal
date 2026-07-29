package de.magynhard.crystal.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalCompletionHelperTest : BasePlatformTestCase() {

    fun testMergesMethodsFromMultipleExactTypesByCanonicalSignature() {
        myFixture.configureByText("types.cr", """
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

        val lookups = CrystalCompletionHelper.getMethodsAsLookups(listOf("First", "Second"), project)
        val names = lookups.map { it.lookupString }

        assertEquals(1, names.count { it == "first_only" })
        assertEquals(1, names.count { it == "second_only" })
        assertEquals(1, names.count { it == "shared" })
        assertEquals(2, names.count { it == "convert" })
    }

    fun testQualifiedTypesOnlyContributeMethodsFromWrittenIdentities() {
        myFixture.configureByText("qualified.cr", """
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
            project
        ).map { it.lookupString }

        assertEquals(listOf("from_left", "from_right"), names)
        assertFalse(names.contains("from_other"))
    }

    fun testStaticMethodsUseExactQualifiedIdentityAndRetainOverloads() {
        myFixture.configureByText("qualified_static.cr", """
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

        val lookups = CrystalCompletionHelper.getStaticMethodsAsLookups("Left::Service", project)
        val names = lookups.map { it.lookupString }

        assertEquals(1, names.count { it == "from_left" })
        assertEquals(2, names.count { it == "build" })
        assertFalse(names.contains("from_right"))
    }

    fun testFindsIndexedPrimitiveAndGenericBaseMethods() {
        myFixture.addFileToProject("stdlib/int.cr", "struct Int32\n  def integer_method\n  end\nend")
        myFixture.addFileToProject("stdlib/string.cr", "class String\n  def string_method\n  end\nend")
        myFixture.addFileToProject("stdlib/array.cr", "class Array(T)\n  def array_method\n  end\nend")
        myFixture.configureByText("usage.cr", "value = nil")

        val names = CrystalCompletionHelper.getMethodsAsLookups(
            listOf("Int32", "String", "Array"),
            project
        ).map { it.lookupString }

        assertEquals(listOf("integer_method", "string_method", "array_method"), names)
    }

    fun testUnionLookupRequiresEveryExactBranch() {
        myFixture.configureByText("types.cr", "class Foo\n  def foo_method\n  end\nend")

        assertEquals(
            emptyList<String>(),
            CrystalCompletionHelper.getMethodsAsLookups(listOf("Foo", "NotIndexed"), project)
                .map { it.lookupString }
        )
    }

    fun testUnionLookupRetainsAllKnownBranches() {
        myFixture.configureByText(
            "types.cr",
            "class Foo\n  def foo_method\n  end\nend\n" +
                "class Bar\n  def bar_method\n  end\nend"
        )

        assertEquals(
            listOf("foo_method", "bar_method"),
            CrystalCompletionHelper.getMethodsAsLookups(listOf("Foo", "Bar"), project)
                .map { it.lookupString }
        )
    }

    fun testCompletionKeepsCertainMethodsFromMacroHeavyStdlibTypes() {
        myFixture.configureByText(
            "stdlib.cr",
            "class String\n" +
                "  def upcase\n    {{ body_value }}\n  end\n" +
                "  {% if flag %}\n  def conditional\n  end\n  {% end %}\n" +
                "end\n" +
                "struct Time\n  def year\n  end\n  {% if flag %}\n  def zone\n  end\n  {% end %}\nend"
        )

        assertEquals(
            listOf("upcase"),
            CrystalCompletionHelper.getMethodsAsLookups("String", project).map { it.lookupString }
        )
        assertEquals(
            listOf("year"),
            CrystalCompletionHelper.getMethodsAsLookups("Time", project).map { it.lookupString }
        )
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
}
