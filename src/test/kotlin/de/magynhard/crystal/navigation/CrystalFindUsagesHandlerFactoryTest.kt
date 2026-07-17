package de.magynhard.crystal.navigation

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalConstantAssignment

class CrystalFindUsagesHandlerFactoryTest : BasePlatformTestCase() {

    fun testCanFindUsagesForMethodDefinition() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def <caret>bar
              end
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset)!!
        val factory = CrystalFindUsagesHandlerFactory()
        // Walk up from IDENTIFIER "bar" to CrystalMethodDefinition
        val methodDef = element.parent ?: element
        assertTrue("Can find usages for CrystalMethodDefinition",
            methodDef is de.magynhard.crystal.psi.CrystalMethodDefinition
                && factory.canFindUsages(methodDef))
    }

    fun testCanFindUsagesForDotNewIdentifier() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
            Foo.<caret>new
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset)!!
        val factory = CrystalFindUsagesHandlerFactory()
        assertTrue("Can find usages for .new DOT-call identifier", factory.canFindUsages(element))
    }

    fun testCannotFindUsagesForRegularIdentifier() {
        val file = myFixture.configureByText("test.cr", """
            <caret>x = 1
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset)!!
        val factory = CrystalFindUsagesHandlerFactory()
        assertFalse("Cannot find usages for a plain identifier", factory.canFindUsages(element))
    }

    fun testResolveNewToInitialize() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def initialize(name : String)
              end
            end
            Foo.<caret>new
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset)!!
        val resolved = CrystalFindUsagesHandlerFactory.resolveNewToInitialize(element)
        assertNotNull(".new should resolve to initialize", resolved)
        assertTrue("Resolved element should be a method named 'initialize'",
            resolved is de.magynhard.crystal.psi.CrystalMethodDefinition
                && resolved.name == "initialize")
    }

    fun testResolveNewToInitializeNoArgs() {
        val file = myFixture.configureByText("test.cr", """
            class Bar
              def initialize(x : Int32, y : String)
              end
            end
            a = Bar.<caret>new
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset)!!
        val resolved = CrystalFindUsagesHandlerFactory.resolveNewToInitialize(element)
        assertNotNull("Bare .new should also resolve to initialize", resolved)
    }

    fun testCanFindUsagesForConstant() {
        val file = myFixture.configureByText("test.cr", """
            DEFAULT_CREATE_PERMISSIONS = 0o644
        """.trimIndent())
        val constDef = PsiTreeUtil.findChildOfType(file, CrystalConstantAssignment::class.java)!!
        val factory = CrystalFindUsagesHandlerFactory()
        assertTrue("Can find usages for CrystalConstantAssignment",
            factory.canFindUsages(constDef))
    }
}
