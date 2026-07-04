package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.highlighting.CrystalHighlightUsagesHandlerFactory
import de.magynhard.crystal.highlighting.CrystalHighlightUsagesHandler
import de.magynhard.crystal.navigation.CrystalFindUsagesHandlerFactory
import de.magynhard.crystal.navigation.CrystalFindUsagesHandler
import de.magynhard.crystal.psi.*

/**
 * Tests for bidirectional name resolution:
 * - Highlighting usages when cursor is on a definition name
 * - Find Usages / Rename from definition name
 */
class CrystalHighlightUsagesTest : BasePlatformTestCase() {

    // ==================== Highlight Usages Factory Detection ====================

    fun testFactoryCreatesHandlerForModuleName() {
        val file = myFixture.configureByText("test.cr", """
            module Kann
            end
            Kann
        """.trimIndent())
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)!!
        val nameId = moduleDef.nameIdentifier!!
        myFixture.editor.caretModel.moveToOffset(nameId.textRange.startOffset)

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file)
        assertNotNull("Should create handler for module definition name", handler)
        assertTrue("Handler should be CrystalHighlightUsagesHandler",
            handler is CrystalHighlightUsagesHandler)
    }

    fun testFactoryCreatesHandlerForClassName() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
            x = Foo.new
        """.trimIndent())
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        val nameId = classDef.nameIdentifier!!
        myFixture.editor.caretModel.moveToOffset(nameId.textRange.startOffset)

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file)
        assertNotNull("Should create handler for class definition name", handler)
    }

    fun testFactoryCreatesHandlerForMethodName() {
        val file = myFixture.configureByText("test.cr", """
            def hello
            end
            hello
        """.trimIndent())
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)!!
        val nameId = methodDef.nameIdentifier!!
        myFixture.editor.caretModel.moveToOffset(nameId.textRange.startOffset)

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file)
        assertNotNull("Should create handler for method definition name", handler)
    }

    fun testFactoryReturnsNullOnKeyword() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend")
        myFixture.editor.caretModel.moveToOffset(0) // on "class" keyword

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file)
        assertNull("Should NOT create handler for keyword (only for name)", handler)
    }

    fun testFactoryReturnsNullOnNonDefinition() {
        val file = myFixture.configureByText("test.cr", """
            x = 1
            puts x
        """.trimIndent())
        myFixture.editor.caretModel.moveToOffset(0) // on "x"

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file)
        assertNull("Should NOT create handler for non-definition", handler)
    }

    // ==================== Handler Targets ====================

    fun testHandlerTargetsReturnDefinition() {
        val file = myFixture.configureByText("test.cr", """
            module Kann
            end
            Kann
        """.trimIndent())
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)!!
        val nameId = moduleDef.nameIdentifier!!
        myFixture.editor.caretModel.moveToOffset(nameId.textRange.startOffset)

        val factory = CrystalHighlightUsagesHandlerFactory()
        val handler = factory.createHighlightUsagesHandler(myFixture.editor, file) as CrystalHighlightUsagesHandler
        val targets = handler.targets
        assertEquals("Should have 1 target", 1, targets.size)
        assertTrue("Target should be module definition", targets[0] is CrystalModuleDefinition)
        assertEquals("Kann", (targets[0] as CrystalModuleDefinition).name)
    }

    // ==================== Find Usages Handler ====================

    fun testCanFindUsagesForClassDefinition() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend")
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        assertTrue("Should be able to find usages for class definition", factory.canFindUsages(classDef))
    }

    fun testCanFindUsagesForModuleDefinition() {
        val file = myFixture.configureByText("test.cr", "module Bar\nend")
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        assertTrue("Should be able to find usages for module definition", factory.canFindUsages(moduleDef))
    }

    fun testCanFindUsagesForMethodDefinition() {
        val file = myFixture.configureByText("test.cr", "def hello\nend")
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        assertTrue("Should be able to find usages for method definition", factory.canFindUsages(methodDef))
    }

    fun testFindUsagesHandlerCreated() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend")
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(classDef, false)
        assertNotNull("Should create handler", handler)
        assertTrue("Handler should be CrystalFindUsagesHandler",
            handler is CrystalFindUsagesHandler)
    }

    fun testFindUsagesFromDefinitionFindsReferences() {
        val file = myFixture.configureByText("test.cr", """
            module Kann
            end
            Kann
        """.trimIndent())
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(moduleDef, false) as CrystalFindUsagesHandler

        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(moduleDef, { usages.add(it); true },
            handler.findUsagesOptions)
        assertTrue("Should find at least one usage", usages.isNotEmpty())
    }

    fun testFindUsagesMethodFromDefinition() {
        val file = myFixture.configureByText("test.cr", """
            def greet
            end
            greet
        """.trimIndent())
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)!!

        val factory = CrystalFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(methodDef, false) as CrystalFindUsagesHandler

        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(methodDef, { usages.add(it); true },
            handler.findUsagesOptions)
        assertTrue("Should find at least one usage of method", usages.isNotEmpty())
    }
}
