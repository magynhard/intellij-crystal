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

    fun testUpdateOfferAppliesWithDeclaredAmebaDependency() {
        val shardYml = "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    branch: master\n"
        assertTrue(AmebaBuild.updateOfferApplies(shardYml, true))
    }

    fun testUpdateOfferAppliesWithRegularDependency() {
        val shardYml = "name: app\ndependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    version: \"~> 1.0\"\n"
        assertTrue(AmebaBuild.updateOfferApplies(shardYml, true))
    }

    fun testUpdateOfferDeclinesWithoutAmebaDependency() {
        val shardYml = "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        assertFalse(AmebaBuild.updateOfferApplies(shardYml, true))
    }

    fun testUpdateOfferDeclinesWithoutShardsBinary() {
        val shardYml = "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        assertFalse(AmebaBuild.updateOfferApplies(shardYml, false))
    }

    fun testUpdateOfferDeclinesForUnreadableManifest() {
        assertFalse(AmebaBuild.updateOfferApplies(null, true))
        assertFalse(AmebaBuild.updateOfferApplies(": : :", true))
    }

    fun testEnsureBinDirCreatesMissingDir() {
        val base = java.nio.file.Files.createTempDirectory("ameba-build-test").toFile()
        try {
            val bin = java.io.File(base, "bin")
            assertFalse(bin.exists())
            assertTrue(AmebaBuild.ensureBinDir(base.absolutePath))
            assertTrue(bin.isDirectory)
            // Idempotent when the directory already exists.
            assertTrue(AmebaBuild.ensureBinDir(base.absolutePath))
        } finally {
            base.deleteRecursively()
        }
    }
}
