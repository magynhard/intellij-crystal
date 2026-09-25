package de.magynhard.crystal.inspections

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.CrystalSettings
import java.io.File

class AmebaSuppressionTest : BasePlatformTestCase() {

    private var savedEnabled = false
    private var savedPath = ""
    private var savedConfig = ""

    override fun setUp() {
        super.setUp()
        val state = CrystalSettings.getInstance(project).state
        savedEnabled = state.amebaEnabled
        savedPath = state.amebaPath
        savedConfig = state.amebaConfigPath
    }

    override fun tearDown() {
        try {
            val state = CrystalSettings.getInstance(project).state
            state.amebaEnabled = savedEnabled
            state.amebaPath = savedPath
            state.amebaConfigPath = savedConfig
            AmebaBinary.clearCache(project)
        } finally {
            super.tearDown()
        }
    }

    fun testUnusedVariableSuppressedWhenAmebaActive() {
        if (SystemInfo.isWindows) return
        enableAmeba()
        myFixture.configureByText("test.cr", "def f\n  unused_var = 1\nend\n")
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        // No markers: Ameba owns the diagnostic, the built-in stays silent.
        myFixture.checkHighlighting()
    }

    fun testUnusedVariableReportedWhenAmebaDisabled() {
        myFixture.configureByText(
            "test.cr",
            "def f\n  <weak_warning descr=\"Variable 'unused_var' is never used\">unused_var</weak_warning> = 1\nend\n"
        )
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        myFixture.checkHighlighting()
    }

    fun testUnusedVariableReportedWhenBinaryMissing() {
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        CrystalSettings.getInstance(project).state.amebaPath = "/nonexistent/ameba"
        AmebaBinary.clearCache(project)
        myFixture.configureByText(
            "test.cr",
            "def f\n  <weak_warning descr=\"Variable 'unused_var' is never used\">unused_var</weak_warning> = 1\nend\n"
        )
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        myFixture.checkHighlighting()
    }

    fun testUnusedVariableReportedWhenBinaryTooOld() {        if (SystemInfo.isWindows) return
        val fake = File(createTempDir(), "old-ameba").apply {
            writeText("#!/bin/sh\necho \"ameba 1.6.4\"\nexit 0\n")
            assertTrue(setExecutable(true))
        }
        val state = CrystalSettings.getInstance(project).state
        state.amebaEnabled = true
        state.amebaPath = fake.absolutePath
        AmebaBinary.clearCache(project)
        // Below the 1.7.0 minimum the integration stays disabled: the
        // built-in is the fallback and must report.
        myFixture.configureByText(
            "test.cr",
            "def f\n  <weak_warning descr=\"Variable 'unused_var' is never used\">unused_var</weak_warning> = 1\nend\n"
        )
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        myFixture.checkHighlighting()
    }

    fun testColonSpacingSuppressedWhenAmebaActive() {
        if (SystemInfo.isWindows) return
        enableAmeba()
        myFixture.configureByText("test.cr", "def foo(): String\nend\n")
        myFixture.enableInspections(CrystalColonSpacingInspection::class.java)
        // No markers: Ameba's Lint/Formatting owns formatter conformance.
        myFixture.checkHighlighting()
    }

    fun testColonSpacingReportedWhenAmebaDisabled() {
        myFixture.configureByText(
            "test.cr",
            "def foo()<error descr=\"Space required around colon in type annotation (before colon)\">:</error> String\nend\n"
        )
        myFixture.enableInspections(CrystalColonSpacingInspection::class.java)
        myFixture.checkHighlighting()
    }

    fun testEcrFragmentSuppressedWhenAmebaActive() {
        if (SystemInfo.isWindows) return
        enableAmeba()
        myFixture.configureByText("test.ecr", "<% unused_var = 1 %>")
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        // The host file is what Ameba lints whole: the injected fragment
        // steps aside together with it, so the finding is never doubled.
        myFixture.checkHighlighting()
    }

    fun testEcrFragmentReportedWhenAmebaDisabled() {
        myFixture.configureByText(
            "test.ecr",
            "<% <weak_warning descr=\"Variable 'unused_var' is never used\">unused_var</weak_warning> = 1 %>"
        )
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
        myFixture.checkHighlighting()
    }

    private fun enableAmeba() {
        val fake = File(createTempDir(), "fake-ameba").apply {
            writeText("#!/bin/sh\necho \"ameba 1.7.0\"\nexit 0\n")
            assertTrue(setExecutable(true))
        }
        val state = CrystalSettings.getInstance(project).state
        state.amebaEnabled = true
        state.amebaPath = fake.absolutePath
        state.amebaConfigPath = ""
        AmebaBinary.clearCache(project)
    }

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "ameba-suppression-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        dir.deleteOnExit()
        return dir
    }
}
