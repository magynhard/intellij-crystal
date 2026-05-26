package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons

/**
 * Provides class/struct/module body-level completion for Crystal macros and keywords.
 * These are statements valid at the class body level (not inside methods).
 */
object CrystalClassBodyCompletionProvider {

    data class ClassBodyItem(
        val name: String,
        val description: String,
        val insertSpace: Boolean = true
    )

    private val CLASS_BODY_ITEMS = listOf(
        // Accessor macros
        ClassBodyItem("getter", "define getter method"),
        ClassBodyItem("getter?", "define Bool? getter"),
        ClassBodyItem("getter!", "define raise-on-nil getter"),
        ClassBodyItem("setter", "define setter method"),
        ClassBodyItem("property", "define getter + setter"),
        ClassBodyItem("property?", "define Bool? getter + setter"),
        ClassBodyItem("property!", "define raise-on-nil getter + setter"),
        ClassBodyItem("class_getter", "define class-level getter"),
        ClassBodyItem("class_getter?", "define class-level Bool? getter"),
        ClassBodyItem("class_getter!", "define class-level raise-on-nil getter"),
        ClassBodyItem("class_setter", "define class-level setter"),
        ClassBodyItem("class_property", "define class-level getter + setter"),
        ClassBodyItem("class_property?", "define class-level Bool? getter + setter"),
        ClassBodyItem("class_property!", "define class-level raise-on-nil getter + setter"),

        // Delegation
        ClassBodyItem("delegate", "delegate methods to another object"),
        ClassBodyItem("forward_missing_to", "forward unknown calls to delegate"),

        // Equality / Hash generation
        ClassBodyItem("def_equals", "generate == from fields"),
        ClassBodyItem("def_hash", "generate hash from fields"),
        ClassBodyItem("def_equals_and_hash", "generate == and hash from fields"),
        ClassBodyItem("def_clone", "generate clone method"),

        // Include / Extend
        ClassBodyItem("include", "include module (instance methods)"),
        ClassBodyItem("extend", "extend module (class methods)"),

        // Visibility
        ClassBodyItem("private", "private visibility"),
        ClassBodyItem("protected", "protected visibility"),

        // Abstract
        ClassBodyItem("abstract", "abstract method or class"),

        // Alias
        ClassBodyItem("alias", "define type alias"),

        // Macro definition
        ClassBodyItem("macro", "define a macro")
    )

    /**
     * Returns LookupElements for all class body macros/keywords.
     */
    fun getClassBodyLookups(): List<LookupElementBuilder> {
        return CLASS_BODY_ITEMS.map { item ->
            LookupElementBuilder.create(item.name)
                .withIcon(AllIcons.Nodes.Function)
                .withTypeText(item.description, true)
                .withInsertHandler(if (item.insertSpace) SpaceInsertHandler else null)
        }
    }

    /**
     * InsertHandler that appends a space after the inserted text.
     */
    private object SpaceInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor
            val offset = editor.caretModel.offset
            context.document.insertString(offset, " ")
            editor.caretModel.moveToOffset(offset + 1)
        }
    }
}
