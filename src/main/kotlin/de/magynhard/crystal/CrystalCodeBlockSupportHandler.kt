package de.magynhard.crystal

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalTypes

class CrystalCodeBlockSupportHandler : CodeBlockSupportHandler {

    override fun getCodeBlockMarkerRanges(elementAtCursor: PsiElement): List<TextRange> {
        if (elementAtCursor.node?.elementType !in MARKER_TOKENS) return emptyList()
        val block = findEnclosingBlock(elementAtCursor) ?: return emptyList()
        return collectMarkers(block)
    }

    override fun getCodeBlockRange(elementAtCursor: PsiElement): TextRange {
        val block = findEnclosingBlock(elementAtCursor) ?: return TextRange.EMPTY_RANGE
        return block.textRange
    }

    private fun findEnclosingBlock(element: PsiElement): PsiElement? {
        return generateSequence(element.parent) { it.parent }
            .firstOrNull { it.node?.elementType in BLOCK_TYPES }
    }

    private fun collectMarkers(block: PsiElement): List<TextRange> {
        val markers = mutableListOf<TextRange>()
        collectMarkersRecursive(block, markers, block)
        markers.sortBy { it.startOffset }
        return markers
    }

    private fun collectMarkersRecursive(element: PsiElement, markers: MutableList<TextRange>, topBlock: PsiElement) {
        for (child in element.children) {
            if (child != topBlock && child.node?.elementType in BLOCK_TYPES) continue
            if (child.node?.elementType in MARKER_TOKENS) {
                markers.add(child.textRange)
            }
            collectMarkersRecursive(child, markers, topBlock)
        }
    }

    companion object {
        private val MARKER_TOKENS = setOf(
            CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
            CrystalTypes.STRUCT, CrystalTypes.ENUM, CrystalTypes.ANNOTATION,
            CrystalTypes.LIB, CrystalTypes.MACRO, CrystalTypes.VERBATIM,
            CrystalTypes.IF, CrystalTypes.UNLESS, CrystalTypes.WHILE,
            CrystalTypes.UNTIL, CrystalTypes.FOR, CrystalTypes.CASE,
            CrystalTypes.DO, CrystalTypes.BEGIN,
            CrystalTypes.END, CrystalTypes.ELSE, CrystalTypes.ELSIF,
            CrystalTypes.WHEN, CrystalTypes.RESCUE, CrystalTypes.ENSURE,
        )

        private val BLOCK_TYPES = setOf(
            CrystalTypes.METHOD_DEFINITION, CrystalTypes.CLASS_DEFINITION,
            CrystalTypes.MODULE_DEFINITION, CrystalTypes.STRUCT_DEFINITION,
            CrystalTypes.ENUM_DEFINITION, CrystalTypes.ANNOTATION_DEFINITION,
            CrystalTypes.LIB_DEFINITION, CrystalTypes.MACRO_DEFINITION,
            CrystalTypes.IF_STATEMENT, CrystalTypes.UNLESS_STATEMENT,
            CrystalTypes.WHILE_STATEMENT, CrystalTypes.UNTIL_STATEMENT,
            CrystalTypes.FOR_STATEMENT, CrystalTypes.CASE_STATEMENT,
            CrystalTypes.BLOCK, CrystalTypes.BEGIN_STATEMENT,
        )
    }
}
