package de.magynhard.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex

class CrystalIndexServiceTest : BasePlatformTestCase() {

    fun testFindTypesRespectsProvidedScope() {
        val included = myFixture.addFileToProject("included.cr", "class ScopedType\nend")
        myFixture.addFileToProject("excluded.cr", "class ScopedType\nend")

        val types = CrystalIndexService.findTypes(
            "ScopedType",
            project,
            GlobalSearchScope.fileScope(included)
        )

        assertEquals(1, types.size)
        assertEquals("included.cr", types.single().containingFile.name)
    }

    fun testProcessTypesStopsWhenProcessorReturnsFalse() {
        myFixture.addFileToProject("first.cr", "class RepeatedType\nend")
        myFixture.addFileToProject("second.cr", "class RepeatedType\nend")
        var processed = 0

        val completed = CrystalIndexService.processTypes(
            "RepeatedType",
            project,
            GlobalSearchScope.projectScope(project),
            Processor {
                processed++
                false
            }
        )

        assertFalse(completed)
        assertEquals(1, processed)
    }

    fun testFindsMethodsByClassAndTopLevelMethods() {
        myFixture.addFileToProject("methods.cr", """
            class MethodOwner
              def owned_method
              end
            end

            def global_method
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val namedMethods = CrystalIndexService.findMethods("owned_method", project, scope)
        val classMethods = CrystalIndexService.findMethodsByClass("MethodOwner", project, scope)
        val topLevelMethods = CrystalIndexService.findTopLevelMethods("global_method", project, scope)

        assertContainsElements(namedMethods.mapNotNull { it.name }, "owned_method")
        assertContainsElements(classMethods.mapNotNull { it.name }, "owned_method")
        assertContainsElements(topLevelMethods.mapNotNull { it.name }, "global_method")
    }

    fun testIndexesRecordBodyMethodsUnderRecordType() {
        myFixture.addFileToProject("record.cr", """
            record Config, value : Int32 do
              def formatted_value
                value.to_s
              end
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val recordMethods = CrystalIndexService.findMethodsByClass("Config", project, scope)
        val topLevelMethods = CrystalIndexService.findTopLevelMethods("formatted_value", project, scope)

        assertContainsElements(recordMethods.mapNotNull { it.name }, "formatted_value")
        assertEmpty(topLevelMethods)
    }

    fun testIndexesRecordBodySelfMethodsUnderRecordType() {
        myFixture.addFileToProject("record_self.cr", """
            record Meter, value : Int32 do
              def self.build(value : Int32)
                new(value)
              end

              def reset
              end
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val recordMethods = CrystalIndexService.findMethodsByClass("Meter", project, scope)
        val topLevelMethods = CrystalIndexService.findTopLevelMethods("build", project, scope)

        assertContainsElements(recordMethods.mapNotNull { it.name }, "build", "reset")
        assertEmpty(topLevelMethods)
    }

    fun testIndexesQualifiedRecordBodyMethodsUnderRecordType() {
        myFixture.addFileToProject("record_qualified.cr", """
            record Registry::Entry, name : String do
              def label
                name
              end
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val byClass = CrystalIndexService.findMethodsByClass("Entry", project, scope)
        val topLevelMethods = CrystalIndexService.findTopLevelMethods("label", project, scope)

        assertContainsElements(byClass.mapNotNull { it.name }, "label")
        assertEquals("Registry::Entry", byClass.single().stub?.ownerQualifiedName)
        assertEmpty(topLevelMethods)
    }

    fun testIndexesNestedTypeMethodsInsideRecordBodyUnderNestedType() {
        myFixture.addFileToProject("record_nested.cr", """
            record Outer do
              class Inner
                def value
                end
              end
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val innerMethods = CrystalIndexService.findMethodsByClass("Inner", project, scope)
        val outerMethods = CrystalIndexService.findMethodsByClass("Outer", project, scope)

        assertContainsElements(innerMethods.mapNotNull { it.name }, "value")
        assertEmpty(outerMethods)
    }

    fun testFileLevelSelfMethodIsNotIndexedAsTopLevel() {
        myFixture.addFileToProject("self_method.cr", """
            def self.require(path)
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val namedMethods = CrystalIndexService.findMethods("require", project, scope)
        val topLevelMethods = CrystalIndexService.findTopLevelMethods("require", project, scope)

        assertContainsElements(namedMethods.mapNotNull { it.name }, "require")
        assertEmpty(topLevelMethods)
    }

    fun testIndexesQualifiedReceiverMethodUnderReceiverType() {
        myFixture.addFileToProject("qualified_receiver.cr", """
            def Time::Location.new(pull)
              load(pull)
            end

            def Time::Location.from_json_object_key?(key : String) : Time::Location
              load(key)
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val byName = CrystalIndexService.findMethods("new", project, scope)
        val byClass = CrystalIndexService.findMethodsByClass("Location", project, scope)
        val topLevel = CrystalIndexService.findTopLevelMethods("new", project, scope)
        val receiverNameHits = CrystalIndexService.findMethods("Time", project, scope)

        assertContainsElements(byName.mapNotNull { it.name }, "new")
        assertContainsElements(byClass.mapNotNull { it.name }, "new", "from_json_object_key?")
        assertEmpty(topLevel)
        assertTrue(receiverNameHits.none { it.name == "Time" })

        val stub = byClass.single { it.name == "new" }.stub
        assertEquals("new", stub?.name)
        assertEquals(true, stub?.isSelfMethod)
        assertEquals("Time::Location", stub?.ownerQualifiedName)
    }

    fun testExplicitReceiverBeatsLexicalEnclosureInIndex() {
        myFixture.addFileToProject("receiver_enclosure.cr", """
            struct Int8
              def Float64.new(value)
                value
              end
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        val floatMethods = CrystalIndexService.findMethodsByClass("Float64", project, scope)
        val intMethods = CrystalIndexService.findMethodsByClass("Int8", project, scope)

        assertContainsElements(floatMethods.mapNotNull { it.name }, "new")
        assertTrue(intMethods.none { it.name == "new" })
    }

    fun testFindsNestedTypes() {
        myFixture.addFileToProject("nested.cr", """
            class OuterType
              module InnerType
              end
            end
        """.trimIndent())

        val nestedTypes = CrystalIndexService.findNestedTypes(
            "OuterType",
            project,
            GlobalSearchScope.projectScope(project)
        )

        assertContainsElements(nestedTypes.mapNotNull { it.name }, "InnerType")
    }

    fun testFindsMacrosAliasesAnnotationsAndLibs() {
        myFixture.addFileToProject("symbols.cr", """
            class AliasTarget
            end

            macro indexed_macro
            end

            alias IndexedAlias = AliasTarget

            annotation IndexedAnnotation
            end

            lib IndexedLib
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        assertSize(1, CrystalIndexService.findMacros("indexed_macro", project, scope))
        assertSize(1, CrystalIndexService.findAliases("IndexedAlias", project, scope))
        assertSize(1, CrystalIndexService.findAnnotations("IndexedAnnotation", project, scope))
        assertSize(1, CrystalIndexService.findLibs("IndexedLib", project, scope))
    }

    fun testProcessesTypeNameCandidatesOutsideProvidedScope() {
        val included = myFixture.addFileToProject("included.cr", "class IncludedType\nend")
        val excluded = myFixture.addFileToProject("excluded.cr", "class ExcludedType\nend")
        // Key enumeration reads the index as-is without waiting for pending
        // stub updates. Under full-suite load the fresh files can lag behind
        // background indexing (e.g. stdlib reindex storms queued by earlier
        // classes), so poll the enumerable keys with a deadline instead of
        // asserting a single possibly-stale snapshot. Reindexing is requested
        // explicitly so late VFS events cannot leave a file unseen, and the
        // event queue is pumped so pending updates get delivered.
        // NOTE: the processor must always return true: Set.add returns false
        // for duplicate keys, which would abort the enumeration early.
        val index = FileBasedIndex.getInstance()
        index.requestReindex(included.virtualFile)
        index.requestReindex(excluded.virtualFile)
        val names = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            names.clear()
            CrystalIndexService.processTypeNames(
                GlobalSearchScope.fileScope(included),
                null,
                Processor { names.add(it); true }
            )
            if ("IncludedType" in names && "ExcludedType" in names) break
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(50)
        }

        assertContainsElements(names, "IncludedType")
        assertContainsElements(names, "ExcludedType")
    }

    fun testProcessesEveryPublicSymbolNameKind() {
        myFixture.addFileToProject("names.cr", """
            class ProcessedType
              def processed_method
              end
            end

            macro processed_macro
            end

            alias ProcessedAlias = ProcessedType

            annotation ProcessedAnnotation
            end

            lib ProcessedLib
            end
        """.trimIndent())
        val scope = GlobalSearchScope.projectScope(project)

        assertProcessedName("ProcessedType") { processor ->
            CrystalIndexService.processTypeNames(scope, null, processor)
        }
        assertProcessedName("processed_method") { processor ->
            CrystalIndexService.processMethodNames(scope, null, processor)
        }
        assertProcessedName("processed_macro") { processor ->
            CrystalIndexService.processMacroNames(scope, null, processor)
        }
        assertProcessedName("ProcessedAlias") { processor ->
            CrystalIndexService.processAliasNames(scope, null, processor)
        }
        assertProcessedName("ProcessedAnnotation") { processor ->
            CrystalIndexService.processAnnotationNames(scope, null, processor)
        }
        assertProcessedName("ProcessedLib") { processor ->
            CrystalIndexService.processLibNames(scope, null, processor)
        }
    }

    fun testProcessTypeNamesStopsWhenProcessorReturnsFalse() {
        myFixture.addFileToProject("names.cr", "class FirstType\nend\nclass SecondType\nend")
        var processed = 0

        val completed = CrystalIndexService.processTypeNames(
            GlobalSearchScope.projectScope(project),
            null,
            Processor {
                processed++
                false
            }
        )

        assertFalse(completed)
        assertEquals(1, processed)
    }

    private fun assertProcessedName(
        expectedName: String,
        process: (Processor<String>) -> Boolean
    ) {
        val names = mutableSetOf<String>()
        assertTrue(process(Processor { names.add(it) }))
        assertContainsElements(names, expectedName)
    }
}
