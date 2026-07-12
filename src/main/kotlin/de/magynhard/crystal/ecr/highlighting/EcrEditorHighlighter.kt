package de.magynhard.crystal.ecr.highlighting

import com.intellij.ide.highlighter.HtmlFileHighlighter
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import de.magynhard.crystal.ecr.EmbeddedCrystalTypes

class EcrEditorHighlighter(
    baseHighlighter: SyntaxHighlighter,
    colorsScheme: EditorColorsScheme
) : LayeredLexerEditorHighlighter(baseHighlighter, colorsScheme) {

    init {
        registerLayer(
            EmbeddedCrystalTypes.ECR_RAW,
            LayerDescriptor(EcrCrystalSyntaxHighlighter(), "")
        )
        registerLayer(
            EmbeddedCrystalTypes.ECR_OUTER,
            LayerDescriptor(HtmlFileHighlighter(), "")
        )
    }
}
