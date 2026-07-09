package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

/**
 * Forces the completion auto-popup to appear when the char immediately before
 * the caret is `@`. By default IntelliJ only auto-shows the popup on identifier
 * characters, so typing `@` (the instance/class variable sigil) would not open it.
 *
 * Annotation context (`@[`) is handled by the contributor's early return and is
 * excluded here (string literals return UNSURE so they fall back to default behaviour;
 * `@[` will be taken over by the annotation provider once `[` is typed).
 *
 * Implemented via [shouldSkipAutopopup]: returning [ThreeState.NO] means "do not skip
 * the autopopup", i.e. force it to show.
 */
class CrystalAtCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, position: PsiElement, file: PsiFile, offset: Int): ThreeState {
        if (offset <= 0) return ThreeState.UNSURE
        val charBefore = editor.document.charsSequence[offset - 1]
        if (charBefore != '@') return ThreeState.UNSURE
        if (isInsideStringLiteral(position)) return ThreeState.UNSURE
        return ThreeState.NO
    }
}
