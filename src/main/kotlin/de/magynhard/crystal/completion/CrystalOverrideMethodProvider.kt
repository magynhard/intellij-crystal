package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil

/**
 * Provides override method completions when typing `def ` inside a class/struct body.
 *
 * The 9 core Crystal methods that are commonly overridden:
 * - initialize, to_s, inspect, hash, ==, <=>, clone, dup, finalize
 */
object CrystalOverrideMethodProvider {

    data class OverrideMethod(
        val name: String,
        val signature: String,
        val withSuper: Boolean
    )

    private val OVERRIDE_METHODS = listOf(
        OverrideMethod("initialize", "initialize", true),
        OverrideMethod("to_s", "to_s(io : IO)", false),
        OverrideMethod("inspect", "inspect(io : IO)", false),
        OverrideMethod("hash", "hash(hasher)", true),
        OverrideMethod("==", "==(other)", false),
        OverrideMethod("<=>", "<=>(other)", false),
        OverrideMethod("clone", "clone", true),
        OverrideMethod("dup", "dup", true),
        OverrideMethod("finalize", "finalize", false)
    )

    /**
     * Returns LookupElements for all 9 override methods.
     */
    fun getOverrideLookups(): List<LookupElementBuilder> {
        return OVERRIDE_METHODS.map { method ->
            LookupElementBuilder.create(method.name)
                .withIcon(AllIcons.Gutter.OverridingMethod)
                .withTailText(
                    if (method.signature != method.name)
                        method.signature.removePrefix(method.name)
                    else
                        "",
                    true
                )
                .withTypeText("override", true)
                .withInsertHandler(OverrideInsertHandler(method))
                .withLookupString(method.signature)
        }
    }

    /**
     * InsertHandler that replaces the typed text with the full method block.
     * For methods with super: inserts body with `super` and places cursor before `super`.
     * For methods without super: inserts body with empty line and places cursor there.
     */
    private class OverrideInsertHandler(
        private val method: OverrideMethod
    ) : InsertHandler<LookupElement> {

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor
            val document = editor.document
            val caretOffset = editor.caretModel.offset

            // Determine the indentation from the line where `def` starts
            val lineNumber = document.getLineNumber(caretOffset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, caretOffset))

            // Find indentation: everything before `def` on this line (which was already typed)
            // But we need the indentation of the def line — look at current line start
            val fullLineText = document.getText(
                com.intellij.openapi.util.TextRange(
                    lineStart,
                    document.getLineEndOffset(lineNumber)
                )
            )
            val baseIndent = fullLineText.takeWhile { it == ' ' || it == '\t' }
            val bodyIndent = "$baseIndent  "

            // Build the text to insert after the method name (which was already inserted by completion)
            val paramsText = if (method.signature != method.name) {
                method.signature.removePrefix(method.name)
            } else {
                ""
            }

            // We need to replace from caret to end of line, then append our block
            val endOfLine = document.getLineEndOffset(lineNumber)

            // The completion already inserted the method name after `def `.
            // Now we need to add params + newline + body + end
            val bodyContent = if (method.withSuper) "super" else ""
            val insertText = buildString {
                append(paramsText)
                append("\n")
                append(bodyIndent)
                append(bodyContent)
                append("\n")
                append(baseIndent)
                append("end")
            }

            // Insert after current caret position, replacing anything to end of line
            document.replaceString(caretOffset, endOfLine, insertText)

            // Place cursor at the body line: before `super` or at empty line
            val cursorOffset = caretOffset + paramsText.length + 1 + bodyIndent.length
            editor.caretModel.moveToOffset(cursorOffset)

            context.commitDocument()
        }
    }
}
