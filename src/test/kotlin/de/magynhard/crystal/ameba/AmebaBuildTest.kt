package de.magynhard.crystal.ameba

import junit.framework.TestCase

class AmebaBuildTest : TestCase() {

    fun testSelectsShardsBuildWhenTargetDeclared() {
        val shardYml = "name: app\ntargets:\n  ameba:\n    main: lib/ameba/bin/ameba.cr\n"
        val plan = AmebaBuild.selectBuild(shardYml, "/usr/bin/shards", "crystal", "lib/ameba/bin/ameba.cr")
        assertNotNull(plan)
        assertEquals("/usr/bin/shards", plan!!.executable)
        assertEquals(listOf("build", "ameba"), plan.args)
    }

    fun testSelectsCrystalBuildWithoutTarget() {
        val shardYml = "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        val plan = AmebaBuild.selectBuild(shardYml, "/usr/bin/shards", "/usr/bin/crystal", "lib/ameba/bin/ameba.cr")
        assertNotNull(plan)
        assertEquals("/usr/bin/crystal", plan!!.executable)
        assertEquals(listOf("build", "-o", "bin/ameba", "lib/ameba/bin/ameba.cr"), plan.args)
    }

    fun testSelectsNothingWithoutToolchain() {
        val shardYml = "name: app\ntargets:\n  ameba:\n    main: lib/ameba/bin/ameba.cr\n"
        assertNull(AmebaBuild.selectBuild(shardYml, null, "", ""))
        assertNull(AmebaBuild.selectBuild(null, null, "", ""))
    }

    fun testSelectsNothingForMalformedManifest() {
        // Malformed YAML still allows the direct crystal build when the
        // sources exist; without them there is nothing to run.
        assertNull(AmebaBuild.selectBuild(": : :", "/usr/bin/shards", "", ""))
    }
}
