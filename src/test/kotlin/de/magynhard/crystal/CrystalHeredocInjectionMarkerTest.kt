package de.magynhard.crystal

import de.magynhard.crystal.injection.CrystalHeredocInjection.extractMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for heredoc marker extraction (see CrystalHeredocInjection).
 * Marker-to-language resolution needs the IDE language registry and is covered
 * by the platform test CrystalHeredocInjectionTest.
 */
class CrystalHeredocInjectionMarkerTest {

    fun testSimpleMarker() {
        assertEquals("SQL", extractMarker("<<-SQL"))
        assertEquals("JS", extractMarker("<<-JS"))
    }

    fun testQuotedRawMarker() {
        assertEquals("SQL", extractMarker("<<-'SQL'"))
        assertEquals("CR", extractMarker("<<-'CR'"))
    }

    fun testMarkerWithDigitsAndUnderscores() {
        assertEquals("X1_2", extractMarker("<<-X1_2"))
        assertEquals("_priv", extractMarker("<<-_priv"))
    }

    fun testMarkerInsideLargerHeader() {
        assertEquals("SQL", extractMarker("q = <<-SQL.squish"))
    }

    fun testMalformedHeadersReturnNull() {
        assertNull(extractMarker("<<-"))
        assertNull(extractMarker("<<~SQL"))
        assertNull(extractMarker("<<-1ABC"))
        assertNull(extractMarker("<<-'SQL"))
        assertNull(extractMarker("just text"))
    }
}
