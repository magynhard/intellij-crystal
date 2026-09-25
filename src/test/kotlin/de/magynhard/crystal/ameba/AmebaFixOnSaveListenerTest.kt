package de.magynhard.crystal.ameba

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.CrystalSettings

class AmebaFixOnSaveListenerTest : BasePlatformTestCase() {

    private var savedEnabled = false
    private var savedFixOnSave = false

    override fun setUp() {
        super.setUp()
        val state = CrystalSettings.getInstance(project).state
        savedEnabled = state.amebaEnabled
        savedFixOnSave = state.amebaFixOnSave
        state.amebaEnabled = false
        state.amebaFixOnSave = false
        AmebaBinary.clearCache(project)
    }

    override fun tearDown() {
        try {
            val state = CrystalSettings.getInstance(project).state
            state.amebaEnabled = savedEnabled
            state.amebaFixOnSave = savedFixOnSave
            AmebaBinary.clearCache(project)
        } finally {
            super.tearDown()
        }
    }

    fun testGateNeedsBothSwitches() {
        val file = myFixture.addFileToProject("main.cr", "puts 1\n").virtualFile
        val listener = AmebaFixOnSaveListener()
        assertFalse(listener.shouldFixOnSave(project, file))

        CrystalSettings.getInstance(project).state.amebaEnabled = true
        assertFalse(listener.shouldFixOnSave(project, file))

        CrystalSettings.getInstance(project).state.amebaEnabled = false
        CrystalSettings.getInstance(project).state.amebaFixOnSave = true
        assertFalse(listener.shouldFixOnSave(project, file))

        CrystalSettings.getInstance(project).state.amebaEnabled = true
        assertTrue(listener.shouldFixOnSave(project, file))
    }

    fun testGateRejectsNonCrystalAndEcrFiles() {
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        CrystalSettings.getInstance(project).state.amebaFixOnSave = true
        val listener = AmebaFixOnSaveListener()

        val txt = myFixture.addFileToProject("notes.txt", "hi\n").virtualFile
        assertFalse(listener.shouldFixOnSave(project, txt))

        // --fix through generated-code positions proved unreliable on
        // templates (verified against 1.7.0): diagnostics yes, fixing no.
        val ecr = myFixture.addFileToProject("page.ecr", "<p>hi</p>\n").virtualFile
        assertFalse(listener.shouldFixOnSave(project, ecr))

        val crystal = myFixture.addFileToProject("ok.cr", "puts 1\n").virtualFile
        assertTrue(listener.shouldFixOnSave(project, crystal))
    }
}
