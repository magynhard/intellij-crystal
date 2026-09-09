package de.magynhard.crystal.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import de.magynhard.crystal.navigation.CrystalAccessorDeclarationRenameReference
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
        // The IDE trigger resolves the caret to the PARAMETER composite — the
        // rename must go through the accessor coupling and rename the
        // declaration argument too.
        myFixture.renameElementAtCaret("uses_loop")

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

    fun testPostRenameHighlightSearchStillReachesAccessorDeclaration() {
        // The user-reported flow: rename @in_loop once, then click it — the
        // highlight-usages pipeline resolves the click to the storage-shortcut
        // CrystalParameter (CrystalInstanceVarReference promotes the first
        // offset occurrence), so both searchers must route Parameter targets
        // through the wrapped access composite.
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              def initialize(node, @i<caret>n_loop : Bool)
                loop(node, @in_loop)
              end

              getter? in_loop : Bool
            end
        """.trimIndent())
        myFixture.renameElement(elementAtCaret(), "uses_loop")

        val text = myFixture.editor.document.text
        assertTrue(
            "chain follows:\n$text",
            text.contains("def initialize(node, @uses_loop : Bool)") &&
                text.contains("loop(node, @uses_loop)") &&
                text.contains("getter? uses_loop : Bool"),
        )

        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val parameter = PsiTreeUtil.getParentOfType(leaf, CrystalParameter::class.java)!!
        assertEquals("@uses_loop", parameter.parameterNameInfo().storageName)

        val refs = com.intellij.openapi.application.ReadAction.compute<List<PsiReference>, RuntimeException> {
            com.intellij.psi.search.searches.ReferencesSearch.search(parameter).findAll().filterNotNull()
        }
        assertTrue(
            "parameter target reaches its var-access references:\n$refs",
            refs.any { it.element.text.contains("@uses_loop") },
        )
        assertTrue(
            "parameter target reaches the coupled accessor declaration:\n$refs",
            refs.any { ref ->
                ref is CrystalAccessorDeclarationRenameReference && ref.element.text.contains("uses_loop")
            },
        )
        // The highlight range must cover ONLY the accessor name — a typed
        // declaration (`getter? uses_loop : Bool`) must not mark ` : Bool`.
        val declarationRange = refs
            .filterIsInstance<CrystalAccessorDeclarationRenameReference>()
            .first { it.element.text.contains("uses_loop") }
            .let { it.element.text.substring(it.rangeInElement.startOffset, it.rangeInElement.endOffset) }
        assertEquals("uses_loop", declarationRange)
    }

    fun testReverseFromBodyIvarRenamesTypedDeclarationAndReaderCalls() {
        // User-reported regression: the typed getter declaration sits BEFORE
        // initialize, the caret is on a body ivar occurrence — the rename
        // must still reach the typed declaration argument AND the reader
        // call sites.
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              # Is true only if some of the nodes parents is a loop.
              getter? in_loop : Bool

              # Creates a new flow expression.
              def initialize(@node, @in_loop)
                @in_lo<caret>op = false
              end

              def walk(outer : FlowExpression)
                run if in_loop?
                run if outer.in_loop?
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("uses_loop")

        val text = myFixture.editor.document.text
        assertTrue(
            "chain follows:\n$text",
            text.contains("getter? uses_loop : Bool") &&
                text.contains("@uses_loop = false") &&
                text.contains("run if uses_loop?") &&
                text.contains("outer.uses_loop?"),
        )
    }

    fun testBareReaderRenameSkipsShadowedLocals() {
        // Crystal resolves a bare name to a LOCAL first — for a no-suffix
        // reader (`getter in_loop`) a same-name local binding inside the
        // method shadows the accessor, so those occurrences must NOT join
        // the rename.
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              def initialize(@in_loop : Bool)
              end

              getter in_loop : Bool

              def check
                in_loop = 5
                bronze if in_loop
              end
            end
        """.trimIndent())
        val leaf = myFixture.editor.document.text.indexOf("getter in_loo") + "getter in_loo".indexOf("in_loo") + 3
        myFixture.editor.caretModel.moveToOffset(leaf)
        myFixture.renameElementAtCaret("uses_loop")

        val text = myFixture.editor.document.text
        assertTrue(
            "declaration follows:\n$text",
            text.contains("getter uses_loop : Bool") && text.contains("initialize(@uses_loop : Bool)"),
        )
        assertTrue(
            "shadowed local and its reads keep their name:\n$text",
            text.contains("in_loop = 5") && text.contains("bronze if in_loop"),
        )
    }

    fun testDeclarationRenameReferenceRenamesUntypedArgument() {
        // Untyped `getter? in_loop` wraps the identifier in a variable
        // reference — the declaration rename reference must rewrite it via
        // the coupling's name-identifier resolution.
        myFixture.configureByText("flow.cr", """
            class FlowExpression
              def initialize(@in_l<caret>oop : Bool)
              end

              getter? in_loop
            end
        """.trimIndent())
        val access = elementAtCaret()
        val refs = com.intellij.psi.search.searches.ReferencesSearch.search(access).findAll()
        val declarationRef = refs.mapNotNull { ref -> ref as? CrystalAccessorDeclarationRenameReference }
            .firstOrNull() ?: error("no declaration rename reference: $refs")

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            declarationRef.handleElementRename("uses_loop")
        }

        val text = myFixture.editor.document.text
        assertTrue(
            "untyped declaration follows:\n$text",
            text.contains("getter? uses_loop") && !text.contains(" in_loop"),
        )
    }
}
