package de.magynhard.crystal.sdk

import junit.framework.TestCase

class CrystalStdlibPathParserTest : TestCase() {

    fun testSelectPreludeRootSkipsCustomRootBeforeStdlib() {
        assertEquals(
            "/usr/lib/crystal",
            CrystalStdlibResolver.selectCrystalPathPreludeRoot(
                "/opt/custom:/usr/lib/crystal:/opt/fallback",
                ':',
            ) { it == "/usr/lib/crystal/prelude.cr" },
        )
    }

    fun testSelectPreludeRootSkipsMultipleBlankEntries() {
        assertEquals(
            "/opt/crystal",
            CrystalStdlibResolver.selectCrystalPathPreludeRoot("::  ::/opt/crystal::", ':') {
                it == "/opt/crystal/prelude.cr"
            },
        )
    }

    fun testSelectPreludeRootSupportsOptionalSrcChild() {
        assertEquals(
            "/opt/crystal/src",
            CrystalStdlibResolver.selectCrystalPathPreludeRoot("/opt/custom:/opt/crystal", ':') {
                it == "/opt/crystal/src/prelude.cr"
            },
        )
    }

    fun testSelectPreludeRootReturnsNullWhenNoCandidateContainsPrelude() {
        assertNull(
            CrystalStdlibResolver.selectCrystalPathPreludeRoot("/opt/custom:relative:/opt/empty", ':') { false },
        )
    }

    fun testParseRecognizesUnixAndWindowsAbsoluteForms() {
        assertEquals(
            listOf(
                "C:\\Crystal\\lib",
                "\\\\server\\share\\crystal",
                "\\\\?\\C:\\Crystal\\lib",
                "\\\\?\\UNC\\server\\share\\crystal",
                "/opt/crystal",
            ),
            CrystalStdlibResolver.parseAbsoluteCrystalPathCandidates(
                "relative;C:\\Crystal\\lib;\\\\server\\share\\crystal;" +
                    "\\\\?\\C:\\Crystal\\lib;\\\\?\\UNC\\server\\share\\crystal;/opt/crystal",
                ';',
            ),
        )
    }

    fun testParseRejectsIncompleteWindowsAbsoluteForms() {
        assertEquals(
            emptyList<String>(),
            CrystalStdlibResolver.parseAbsoluteCrystalPathCandidates(
                "C:relative;\\server;\\\\server;\\\\?\\C:relative;\\\\?\\UNC\\server",
                ';',
            ),
        )
    }
}
