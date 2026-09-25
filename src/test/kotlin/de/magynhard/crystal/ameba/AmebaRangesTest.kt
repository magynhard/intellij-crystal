package de.magynhard.crystal.ameba

import com.intellij.openapi.editor.EditorFactory
import junit.framework.TestCase

class AmebaRangesTest : TestCase() {

    private fun document(text: String) = EditorFactory.getInstance().createDocument(text)

    fun testMapsJsonRangeOntoOffsets() {
        val doc = document("abcde\nsecond\n")
        val range = AmebaRanges.toRange(
            doc,
            AmebaIssue("Style/VerboseBlock", AmebaSeverity.CONVENTION, "m", 1, 1, 1, 5)
        )
        assertNotNull(range)
        // Ameba end columns are inclusive (verified against 1.7.0).
        assertEquals("abcde", doc.text.substring(range!!.startOffset, range.endOffset))
    }

    fun testDropsWildlyOutOfRangePositions() {
        val doc = document("ab\n")
        assertNull(
            AmebaRanges.toRange(
                doc,
                AmebaIssue("R", AmebaSeverity.WARNING, "m", 99, 50, 99, 60)
            )
        )
    }

    fun testClampsColumnPastEndOfLine() {
        val doc = document("ab\n")
        val range = AmebaRanges.toRange(
            doc,
            AmebaIssue("R", AmebaSeverity.WARNING, "m", 1, 50, null, null)
        )
        assertNotNull(range)
        assertTrue(range!!.startOffset <= doc.textLength)
        assertTrue(range.endOffset <= doc.textLength)
    }

    fun testRangelessIssueHighlightsOneCharacter() {
        val doc = document("puts x\n")
        val range = AmebaRanges.toRange(
            doc,
            AmebaIssue("R", AmebaSeverity.WARNING, "m", 1, 6, null, null)
        )
        assertNotNull(range)
        assertEquals("x", doc.text.substring(range!!.startOffset, range.endOffset))
    }

    fun testEmptyDocumentYieldsNull() {
        val doc = document("")
        assertNull(
            AmebaRanges.toRange(doc, AmebaIssue("R", AmebaSeverity.WARNING, "m", 1, 1, 1, 2))
        )
    }

    fun testSeverityMapping() {
        assertEquals(
            com.intellij.lang.annotation.HighlightSeverity.ERROR,
            AmebaExternalAnnotator.mapSeverity(AmebaSeverity.ERROR)
        )
        assertEquals(
            com.intellij.lang.annotation.HighlightSeverity.WARNING,
            AmebaExternalAnnotator.mapSeverity(AmebaSeverity.WARNING)
        )
        assertEquals(
            com.intellij.lang.annotation.HighlightSeverity.WEAK_WARNING,
            AmebaExternalAnnotator.mapSeverity(AmebaSeverity.CONVENTION)
        )
    }

    fun testAnnotationTextMentionsRule() {
        val text = AmebaExternalAnnotator.annotationText(
            AmebaIssue("Style/VerboseBlock", AmebaSeverity.CONVENTION, "Use short blocks.", 1, 1, null, null)
        )
        assertTrue(text.contains("Use short blocks."))
        assertTrue(text.contains("Style/VerboseBlock"))
    }
}
