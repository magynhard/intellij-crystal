package de.magynhard.crystal.ecr.highlighting

import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import de.magynhard.crystal.ecr.EmbeddedCrystalSyntaxHighlighter

class EcrEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        file: VirtualFile?,
        colorsScheme: EditorColorsScheme
    ): EditorHighlighter = EcrEditorHighlighter(
        EmbeddedCrystalSyntaxHighlighter(),
        colorsScheme
    )
}
