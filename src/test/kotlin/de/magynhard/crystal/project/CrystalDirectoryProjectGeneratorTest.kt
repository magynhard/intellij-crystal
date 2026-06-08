package de.magynhard.crystal.project

import org.junit.Assert.*
import org.junit.Test

class CrystalDirectoryProjectGeneratorTest {

    @Test
    fun testProjectSettingsDefaults() {
        val settings = CrystalProjectSettings()
        assertEquals("app", settings.projectType)
        assertEquals("", settings.crystalPath)
    }

    @Test
    fun testGeneratorMetadata() {
        val generator = CrystalDirectoryProjectGenerator()
        assertEquals("Crystal", generator.name)
        assertEquals("Create a new Crystal project (application or library)", generator.description)
        assertNotNull(generator.logo)
        assertNotNull(generator.createPeer())
    }

    @Test
    fun testPeerReturnsDefaultSettingsBeforeUIInitialised() {
        val peer = CrystalProjectGeneratorPeer()
        val settings = peer.getSettings()
        assertEquals("app", settings.projectType)
        assertEquals("", settings.crystalPath)
    }
}
