package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalVersionRequirementTest : BasePlatformTestCase() {

    fun testBareVersionIsExact() {
        assertEquals(true, CrystalVersionRequirement.satisfies("1.2.3", "1.2.3"))
        assertEquals(false, CrystalVersionRequirement.satisfies("1.2.3", "1.2.4"))
    }

    fun testWildcardAndMissingArePermissive() {
        assertEquals(true, CrystalVersionRequirement.satisfies("*", "9.9.9"))
        assertEquals(true, CrystalVersionRequirement.satisfies(null, "1.0.0"))
        assertEquals(true, CrystalVersionRequirement.satisfies("  ", "1.0.0"))
    }

    fun testComparisonOperators() {
        assertEquals(true, CrystalVersionRequirement.satisfies(">= 1.0.0", "1.0.0"))
        assertEquals(false, CrystalVersionRequirement.satisfies("> 1.0.0", "1.0.0"))
        assertEquals(true, CrystalVersionRequirement.satisfies("<= 2.0", "2.0.0"))
        assertEquals(false, CrystalVersionRequirement.satisfies("< 2.0", "2.0.0"))
        assertEquals(true, CrystalVersionRequirement.satisfies("!= 1.0", "1.1"))
        assertEquals(false, CrystalVersionRequirement.satisfies("!= 1.0", "1.0.0"))
        assertEquals(true, CrystalVersionRequirement.satisfies("= 1.0", "1.0.0"))
    }

    fun testPessimisticOperatorFollowsShardsTable() {
        // ~> 0.3.5 is >= 0.3.5, < 0.4.0
        assertEquals(true, CrystalVersionRequirement.satisfies("~> 0.3.5", "0.3.9"))
        assertEquals(false, CrystalVersionRequirement.satisfies("~> 0.3.5", "0.4.0"))
        assertEquals(false, CrystalVersionRequirement.satisfies("~> 0.3.5", "0.3.4"))
        // ~> 2.1 is >= 2.1, < 3.0
        assertEquals(true, CrystalVersionRequirement.satisfies("~> 2.1", "2.9"))
        assertEquals(false, CrystalVersionRequirement.satisfies("~> 2.1", "3.0"))
        // ~> 1 is >= 1, < 2
        assertEquals(true, CrystalVersionRequirement.satisfies("~> 1", "1.99"))
        assertEquals(false, CrystalVersionRequirement.satisfies("~> 1", "2.0"))
        // ~> 0.3 is >= 0.3, < 1.0
        assertEquals(true, CrystalVersionRequirement.satisfies("~> 0.3", "0.9"))
        assertEquals(false, CrystalVersionRequirement.satisfies("~> 0.3", "1.0"))
    }

    fun testCommaSeparatedRequirementsAllApply() {
        assertEquals(true, CrystalVersionRequirement.satisfies(">= 1.0.0, < 2.0", "1.5"))
        assertEquals(false, CrystalVersionRequirement.satisfies(">= 1.0.0, < 2.0", "2.0"))
        assertEquals(false, CrystalVersionRequirement.satisfies(">= 1.0.0, < 2.0", "0.9"))
    }

    fun testPrereleaseSortsBelowRelease() {
        assertEquals(true, CrystalVersionRequirement.satisfies("> 2.1.0-dev", "2.1.0"))
        assertEquals(false, CrystalVersionRequirement.satisfies(">= 2.1.0", "2.1.0-dev"))
    }

    fun testUnknownShapesYieldNoVerdict() {
        assertNull(CrystalVersionRequirement.satisfies("fancy", "1.0"))
        assertNull(CrystalVersionRequirement.satisfies("=> 1.0", "1.0"))
        assertNull(CrystalVersionRequirement.satisfies(">= 1.0, fancy", "1.5"))
        assertNull(CrystalVersionRequirement.satisfies(">= 1.0", null))
    }
}
