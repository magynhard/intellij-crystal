package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalProjectDetectorTest : BasePlatformTestCase() {

    fun testDetectsCrystalFilesInModuleContentRoots() {
        assertFalse(CrystalProjectDetector.isCrystalProject(project))

        myFixture.addFileToProject("main.cr", "puts 1")

        assertTrue(CrystalProjectDetector.isCrystalProject(project))
    }
}
