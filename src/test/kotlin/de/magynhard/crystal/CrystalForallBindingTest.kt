package de.magynhard.crystal

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalTypes

class CrystalForallBindingTest : BasePlatformTestCase() {
    fun testSignatureAndBodyReferencesNavigateToForallInsteadOfGlobalType() {
        myFixture.configureByText("test.cr", """
            class T
            end
            def convert(type : T.class) : T forall T
              value : T
              T
            end
        """.trimIndent())
        val text = myFixture.editor.document.text
        val declaration = text.indexOf("forall T") + "forall ".length
        val offsets = listOf(text.indexOf("T.class"), text.indexOf(": T forall") + 2,
            text.indexOf("value : T") + "value : ".length, text.lastIndexOf("T\n"))
        for (offset in offsets) {
            val reference = myFixture.file.findReferenceAt(offset)
            assertNotNull("Each type-parameter use needs a reference", reference)
            val target = reference!!.resolve()
            assertNotNull(target)
            assertEquals(CrystalTypes.CONSTANT, target!!.node.elementType)
            assertEquals(declaration, target.textOffset)
            myFixture.editor.caretModel.moveToOffset(offset)
            myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
            assertEquals(declaration, myFixture.caretOffset)
        }
    }

    fun testMultipleForallNamesAndMethodBoundaries() {
        myFixture.configureByText("test.cr", """
            def convert(left : T.class, right : U.class) : U forall T, U
            end
            def unrelated(value : T)
            end
        """.trimIndent())
        val text = myFixture.editor.document.text
        val t = text.indexOf("forall T") + "forall ".length
        val u = text.indexOf("T, U") + 3
        assertEquals(t, myFixture.file.findReferenceAt(text.indexOf("T.class"))!!.resolve()!!.textOffset)
        assertEquals(u, myFixture.file.findReferenceAt(text.indexOf("U.class"))!!.resolve()!!.textOffset)
        assertNull(myFixture.file.findReferenceAt(text.indexOf("value : T") + "value : ".length)!!.resolve())
    }

    fun testInterpolatedTypePrefixDoesNotNavigateToStaticType() {
        myFixture.configureByText("test.cr", "class UInt\nend\ndef generated : UInt{{8}}\nend")
        val offset = myFixture.editor.document.text.indexOf("UInt{{")
        assertNull(myFixture.file.findReferenceAt(offset))
    }

    fun testInterpolatedExpressionPrefixDoesNotNavigateToStaticType() {
        myFixture.configureByText("test.cr", "class UInt\nend\ndef consume(type)\nend\nconsume(UInt{{8}})")
        val offset = myFixture.editor.document.text.indexOf("UInt{{")
        assertNull(myFixture.file.findReferenceAt(offset))
    }

    fun testForallMemberDoesNotNavigateToSameNamedGlobalNamespace() {
        myFixture.configureByText("test.cr", """
            module T
              MAX = 1
            end
            def convert(type : T.class) forall T
              T::MAX
              ::T::MAX
            end
        """.trimIndent())
        val text = myFixture.editor.document.text
        val use = text.indexOf("T::MAX")
        assertEquals(text.indexOf("forall T") + "forall ".length,
            myFixture.file.findReferenceAt(use)!!.resolve()!!.textOffset)
        assertNull(myFixture.file.findReferenceAt(use + 3)!!.resolve())
        val absoluteUse = text.lastIndexOf("::T::MAX")
        assertEquals(text.indexOf("MAX ="), myFixture.file.findReferenceAt(absoluteUse + 5)!!.resolve()!!.textOffset)
    }
}
