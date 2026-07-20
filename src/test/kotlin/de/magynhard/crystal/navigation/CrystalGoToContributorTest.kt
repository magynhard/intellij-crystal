package de.magynhard.crystal.navigation

import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters

class CrystalGoToContributorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("symbols.cr", """
            class IndexedClass
            end

            module IndexedModule
            end

            struct IndexedStruct
            end

            enum IndexedEnum
              Value
            end

            def indexed_method
            end

            macro indexed_macro
            end

            alias IndexedAlias = IndexedClass

            annotation IndexedAnnotation
            end

            lib IndexedLib
            end
        """.trimIndent())
    }

    fun testGoToClassContributorProcessesIndexedTypeNamesAndElements() {
        val contributor = CrystalGoToClassContributor()
        val expectedNames = setOf(
            "IndexedClass",
            "IndexedModule",
            "IndexedStruct",
            "IndexedEnum",
            "IndexedAlias",
            "IndexedAnnotation",
            "IndexedLib"
        )

        assertContainsElements(processNames(contributor), expectedNames)
        expectedNames.forEach { name -> assertContributorElement(contributor, name) }
    }

    fun testGoToSymbolContributorProcessesAllIndexedSymbolNamesAndElements() {
        val contributor = CrystalGoToSymbolContributor()
        val expectedNames = setOf(
            "IndexedClass",
            "IndexedModule",
            "IndexedStruct",
            "IndexedEnum",
            "indexed_method",
            "indexed_macro",
            "IndexedAlias",
            "IndexedAnnotation",
            "IndexedLib"
        )

        assertContainsElements(processNames(contributor), expectedNames)
        expectedNames.forEach { name -> assertContributorElement(contributor, name) }
    }

    private fun processNames(contributor: com.intellij.navigation.ChooseByNameContributorEx): Set<String> {
        val names = mutableSetOf<String>()
        contributor.processNames(Processor { names.add(it); true }, projectScope(), null)
        return names
    }

    private fun assertContributorElement(
        contributor: com.intellij.navigation.ChooseByNameContributorEx,
        name: String
    ) {
        val items = mutableListOf<NavigationItem>()
        val parameters = FindSymbolParameters(name, name, projectScope())

        contributor.processElementsWithName(name, Processor { items.add(it); true }, parameters)

        assertTrue("Expected contributor item for $name", items.any { it.name == name })
        assertTrue(
            "Expected contributor item for $name to point at symbols.cr",
            items.any { it.name == name && it.presentation?.locationString == "symbols.cr" }
        )
    }

    private fun projectScope(): GlobalSearchScope = GlobalSearchScope.projectScope(project)
}
