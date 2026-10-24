package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.stubs.CrystalIndexService
import com.intellij.psi.search.GlobalSearchScope

class CrystalHasherConstantResolutionTest : BasePlatformTestCase() {
    fun testPrivateHasherConstantInMulmodArgumentsResolvesThroughPrelude() {
        val prelude = myFixture.addFileToProject("stdlib/prelude.cr", "require \"hash\"")
        myFixture.addFileToProject("stdlib/hash.cr", "require \"crystal/hasher\"")
        val hasher = myFixture.addFileToProject("stdlib/crystal/hasher.cr", """
            struct Crystal::Hasher
              private HASH_BITS = 61
              private HASH_MODULUS = (1_i64 << HASH_BITS) - 1
            end
        """.trimIndent())
        myFixture.addFileToProject("stdlib/int.cr", """
            class Object
            end
            struct Value
            end
            struct Number < Value
            end
            struct Int < Number
            end
            struct UInt64 < Int
              def self.mulmod(x, y, z)
                x
              end
            end
        """.trimIndent())
        val root = requireNotNull(prelude.virtualFile.parent)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { root }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(root, CrystalStdlibResolver.resolveStdlibPath(project))
        myFixture.configureByText("decimal.cr", """
            require "int"
            struct Crystal::Hasher
              def self.reduce_num
                v = 1
                x = 2
                v = UInt64.mulmod(v, x, HASH_MODUL<caret>US) if true
                x = UInt64.mulmod(x, x, HASH_MODULUS)
              end
            end
        """.trimIndent())
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val sources = CrystalRequireGraphService.getInstance(project).effectiveSources(leaf)
        assertTrue("Prelude must load hasher.cr: ${sources.files.map { it.url }}", sources.contains(hasher))
        val declaration = CrystalIndexService.findConstants("HASH_MODULUS", project, GlobalSearchScope.allScope(project)).single()
        assertTrue("Hasher reopening must access its private member", CrystalRequireVisibility.isConstantAccessible(declaration, leaf))
        myFixture.checkHighlighting()
        val resolved = (leaf.reference ?: leaf.parent?.reference)?.resolve()
        assertTrue("HASH_MODULUS must resolve to a constant, not mulmod", resolved is CrystalConstantAssignment)
        assertEquals("hasher.cr", resolved!!.containingFile.name)
        val targets = CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
            leaf, myFixture.caretOffset, myFixture.editor
        )
        assertTrue("The handler must not override the argument's own constant reference",
            targets == null || targets.all { it is CrystalConstantAssignment })
    }
}
