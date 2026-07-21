package de.magynhard.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor

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
        myFixture.addFileToProject("excluded.cr", "class ExcludedType\nend")
        val names = mutableSetOf<String>()

        CrystalIndexService.processTypeNames(
            GlobalSearchScope.fileScope(included),
            null,
            Processor { names.add(it) }
        )

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
