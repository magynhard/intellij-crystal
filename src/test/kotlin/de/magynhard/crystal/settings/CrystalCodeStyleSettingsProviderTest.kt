package de.magynhard.crystal.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalCodeStyleSettingsProviderTest : BasePlatformTestCase() {

    fun testDefaultIndentSizeIsTwoSpaces() {
        val provider = CrystalCodeStyleSettingsProvider()
        val settings = provider.defaultCommonSettings
        assertEquals("INDENT_SIZE should be 2", 2, settings.indentOptions?.INDENT_SIZE)
    }

    fun testDefaultContinuationIndentIsTwoSpaces() {
        val provider = CrystalCodeStyleSettingsProvider()
        val settings = provider.defaultCommonSettings
        assertEquals("CONTINUATION_INDENT_SIZE should be 2", 2, settings.indentOptions?.CONTINUATION_INDENT_SIZE)
    }

    fun testDefaultTabSizeIsTwo() {
        val provider = CrystalCodeStyleSettingsProvider()
        val settings = provider.defaultCommonSettings
        assertEquals("TAB_SIZE should be 2", 2, settings.indentOptions?.TAB_SIZE)
    }

    fun testDefaultUsesSpacesNotTabs() {
        val provider = CrystalCodeStyleSettingsProvider()
        val settings = provider.defaultCommonSettings
        assertFalse("USE_TAB_CHARACTER should be false", settings.indentOptions?.USE_TAB_CHARACTER ?: true)
    }

    fun testLanguageIsCrystal() {
        val provider = CrystalCodeStyleSettingsProvider()
        assertEquals("Language should be Crystal", "Crystal", provider.language.id)
    }
}
