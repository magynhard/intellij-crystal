package de.magynhard.crystal.analysis

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalIndexedTypeLookupTest : BasePlatformTestCase() {
    fun testOrdinaryAndMissingTypeLookupsDoNotLoadUnrelatedRequiredFileTrees() {
        val dependencies = (1..4).map { number ->
            myFixture.addFileToProject("dependency$number.cr", "class Dependency$number\nend\n") as PsiFileImpl
        }
        val requires = dependencies.joinToString("\n") { "require \"./${it.name}\"" }
        val file = myFixture.configureByText("crystalline.cr", "$requires\nclass Entry\nend\n")
        // Capture the require closure first. Its construction may legitimately
        // read dependency syntax; later indexed type queries must not scan it.
        val session = CrystalTypeSetResolver.session(file)
        WriteAction.run<RuntimeException> {
            dependencies.forEach { it.setTreeElementPointer(null) }
        }
        dependencies.forEach { assertFalse(it.name, it.isContentsLoaded) }

        assertEquals(CrystalTypeIdentity("Entry", "Entry"), session.resolveType("Entry", file))
        assertNull(session.resolveType("Missing", file))
        assertNull(session.resolveType("Other::Missing", file))
        dependencies.forEach {
            assertFalse("Type lookup must not load ${it.name} without an indexed candidate", it.isContentsLoaded)
        }
    }
}
