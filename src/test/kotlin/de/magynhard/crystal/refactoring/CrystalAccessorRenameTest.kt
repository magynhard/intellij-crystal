package de.magynhard.crystal.refactoring

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.parameterNameInfo
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end renames of the accessor-macro family: `property active` renaming
 * `active` carries the whole implicit chain (declaration argument, the coupled
 * `@active` variable with the `initialize(@active)` storage shortcut, reader
 * call sites and setter member assignments) — from the declaration (forward)
 * and from the variable side (reverse). `property?` declares only the `?`
 * reader; `class_property` couples `@@foo`. The coupling is deterministic — no
 * prompt.
 */
class CrystalAccessorRenameTest : BasePlatformTestCase() {

    fun testForwardRenamePropertyPullsVariableAndCallSites() {
        myFixture.configureByText("session.cr", """
            class Session
              property acti<caret>ve : Bool = false

              def initialize(@active : Bool)
              end

              def flip(other : Session)
                @active = true
                other.active = false
              end

              def read(other : Session)
                return other.active
              end
            end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "enabled")

        val text = myFixture.editor.document.text
        assertTrue(
            "chain follows:\n$text",
            text.contains("property enabled : Bool = false") &&
                text.contains("initialize(@enabled : Bool)") &&
                text.contains("@enabled = true") &&
                text.contains("other.enabled = false") &&
                text.contains("return other.enabled"),
        )
        assertFalse("original name gone:\n$text", text.contains("active"))
    }

    fun testReverseRenameVariablePullsAccessorDeclaration() {
        myFixture.configureByText("session.cr", """
            class Session
              property active : Bool = false

              def initialize(<caret>@active : Bool)
              end

              def flip(other : Session)
                @active = true
                other.active = false
              end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "enabled")

        val text = myFixture.editor.document.text
        assertTrue(
            "reverse chain follows:\n$text",
            text.contains("property enabled : Bool = false") &&
                text.contains("initialize(@enabled : Bool)") &&
                text.contains("@enabled = true") &&
                text.contains("other.enabled = false"),
        )
        assertFalse("original name gone:\n$text", text.contains("active"))
    }

    fun testMultiDeclarationRenamesOnlyTheTargetedArgument() {
        myFixture.configureByText("session.cr", """
            class Session
              property foo, ba<caret>r, baz

              def set(other : Session)
                other.bar = 1
                other.baz = 2
              end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "quux")

        val text = myFixture.editor.document.text
        assertTrue(
            "multi-decl chain:\n$text",
            text.contains("property foo, quux, baz") &&
                text.contains("other.quux = 1") &&
                text.contains("other.baz = 2"),
        )
        assertFalse("untouched argument stays:\n$text", text.contains("other.bar"))
    }

    fun testClassPropertyRenameCouplesClassVariableAndStaticAccess() {
        myFixture.configureByText("server.cr", """
            class Server
              class_property time<caret>out : Int32 = 30

              def reset
                @@timeout = 60
              end
        """.trimIndent() + "\n                Server.timeout = 45\nend\n")
        myFixture.renameElement(elementAtCaret(), "expiration")

        val text = myFixture.editor.document.text
        assertTrue(
            "class chain:\n$text",
            text.contains("class_property expiration : Int32 = 30") &&
                text.contains("@@expiration = 60") &&
                text.contains("Server.expiration = 45"),
        )
        assertFalse("original name gone:\n$text", text.contains("timeout"))
    }

    fun testGetterQuestionReaderCallSitesFollow() {
        myFixture.configureByText("flags.cr", """
            class Flags
              property? ena<caret>bled : Bool = false

              def read(other : Flags)
                return other.enabled?
              end

              def set(other : Flags)
                other.enabled = true
              end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "on")

        val text = myFixture.editor.document.text
        assertTrue(
            "?-variant chain:\n$text",
            text.contains("property? on : Bool = false") &&
                text.contains("other.on?") &&
                text.contains("other.on = true"),
        )
        assertFalse("original name gone:\n$text", text.contains("enabled"))
    }

    fun testUnrelatedTypeMembersNeverFollow() {
        myFixture.configureByText("collide.cr", """
            class Session
              property acti<caret>ve : Bool = false
        """.trimIndent() + """
            end

            class Agenda
              property active : Bool = false
            end

            def exercise(a : Agenda)
              a.active = true
            end
        """)
        myFixture.renameElement(elementAtCaret(), "enabled")

        val text = myFixture.editor.document.text
        assertTrue("Session declaration follows:\n$text", text.contains("property enabled : Bool = false"))
        assertFalse("unrelated Agenda member NOT renamed:\n$text", text.contains("a.enabled = true"))
        assertTrue("unrelated Agenda call site stays:\n$text", text.contains("a.active = true"))
    }

    /** The platform resolves the caret element to its rename target (the
     *  accessor argument composite / the ivar accessor composite). */
    private fun elementAtCaret(): PsiElement {
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        var current: PsiElement = leaf
        while (current.parent != null) {
            val parent = current.parent
            if (parent is de.magynhard.crystal.psi.CrystalArgument ||
                parent is de.magynhard.crystal.psi.CrystalBareArgument ||
                parent is de.magynhard.crystal.psi.CrystalInstanceVarAccess ||
                parent is de.magynhard.crystal.psi.CrystalClassVarAccess
            ) {
                return parent
            }
            current = parent
        }
        error("no rename target at caret")
    }

    fun testReverseRenameStorageParameterKeepsSigil() {
        // flow_expression.cr:35 — renaming the storage shortcut `@in_loop`
        // inside `def initialize(@node, @in_loop)` must NOT lose the `@`:
        // the inplace renamer dropped the sigil and left the code broken, so
        // ivar renames go through the dialog flow, which re-applies the sigil
        // and pulls the coupled accessor declaration.
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              getter? in_loop : Bool

              def initialize(node, @i<caret>n_loop)
              end
            end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "uses_loop")

        val text = myFixture.editor.document.text
        assertTrue(
            "sigil retained on the storage parameter:\n$text",
            text.contains("def initialize(node, @uses_loop)"),
        )
        assertTrue(
            "coupled accessor declaration follows:\n$text",
            text.contains("getter? uses_loop : Bool"),
        )
        assertFalse("original name gone:\n$text", text.contains("in_loop"))
    }

    fun testReverseRenameClassVarStorageKeepsSigil() {
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              class_getter looped : Bool

              def initialize(@@lo<caret>oped : Bool)
              end
            end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "cycled")

        val text = myFixture.editor.document.text
        assertTrue(
            "class-var sigil retained:\n$text",
            text.contains("def initialize(@@cycled : Bool)"),
        )
        assertTrue(
            "coupled class accessor follows:\n$text",
            text.contains("class_getter cycled : Bool"),
        )
    }

    fun testPlainParameterKeepsInplaceRenameAvailable() {
        // Normal parameters (no sigil) stay on the inplace path — the sigil
        // gate must only catch storage shortcuts.
        val file = myFixture.configureByText("plain.cr", """
            class Greeter
              def work(node<caret>_name : String)
                name
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val parameter = PsiTreeUtil.getParentOfType(leaf, CrystalParameter::class.java)!!
        assertTrue(parameter.parameterNameInfo().storageName == null)
        assertTrue(
            "Plain parameters keep the inplace rename behavior",
            CrystalRefactoringSupportProvider().isMemberInplaceRenameAvailable(parameter, null),
        )
    }

    fun testStorageParameterInplaceIsDisabled() {
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              def initialize(node, @i<caret>n_loop : Bool)
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val parameter = PsiTreeUtil.getParentOfType(leaf, CrystalParameter::class.java)!!
        assertTrue(
            "Storage shortcut joins the accessor coupling (no inplace)",
            parameter.parameterNameInfo().storageName != null,
        )
        assertFalse(
            "Inplace rename drops the sigil — storage shortcuts run the dialog",
            CrystalRefactoringSupportProvider().isMemberInplaceRenameAvailable(parameter, null),
            )
    }
}
