package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalShardManifestTest : BasePlatformTestCase() {

    fun testParsesFullManifest() {
        val manifest = CrystalShardManifest.parse(
            """
            name: my_app
            version: 0.1.0

            dependencies:
              kemal:
                github: kemalcr/kemal
                version: "~> 1.0"
              pg:
                git: https://github.com/will/crystal-pg.git
                branch: master
              local_tools:
                path: ../local_tools

            development_dependencies:
              ameba:
                github: crystal-ameba/ameba
            """.trimIndent()
        ) ?: error("Expected manifest")
        assertEquals("my_app", manifest.name)
        assertEquals("0.1.0", manifest.version)
        assertEquals(4, manifest.dependencies.size)

        val kemal = manifest.dependencies[0]
        assertEquals("kemal", kemal.name)
        assertEquals(CrystalShardManifest.Source.Hosted("github", "kemalcr/kemal"), kemal.source)
        assertEquals("~> 1.0", kemal.requirement)
        assertNull(kemal.ref)
        assertFalse(kemal.development)

        val pg = manifest.dependencies[1]
        assertEquals(
            CrystalShardManifest.Source.Repository("git", "https://github.com/will/crystal-pg.git"),
            pg.source
        )
        assertNull(pg.requirement)
        assertEquals("master", pg.ref)

        val local = manifest.dependencies[2]
        assertEquals(CrystalShardManifest.Source.Path("../local_tools"), local.source)

        assertTrue(manifest.dependencies[3].development)
    }

    fun testMissingSectionsYieldEmptyDependencies() {
        val manifest = CrystalShardManifest.parse("name: lonely\nversion: 0.0.1\n")
            ?: error("Expected manifest")
        assertEquals("lonely", manifest.name)
        assertTrue(manifest.dependencies.isEmpty())
    }

    fun testMalformedInputYieldsNull() {
        assertNull(CrystalShardManifest.parse("dependencies: [unclosed"))
        assertNull(CrystalShardManifest.parse("- just\n- a\n- list\n"))
        assertNull(CrystalShardManifest.parse(""))
    }

    fun testSkipsNamelessAndNonMappingEntries() {
        val manifest = CrystalShardManifest.parse(
            """
            name: app
            dependencies:
              good:
                github: org/good
              broken: plain-string
            """.trimIndent()
        ) ?: error("Expected manifest")
        assertEquals(listOf("good"), manifest.dependencies.map { it.name })
    }

    fun testParsesRealLockShape() {
        val lock = CrystalShardManifest.parseLock(
            """
            version: 2.0
            shards:
              local_dep:
                path: ../local_dep
                version: 0.2.0
              kemal:
                git: https://github.com/kemalcr/kemal.git
                version: 1.9.0
            """.trimIndent()
        ) ?: error("Expected lock")
        assertEquals("0.2.0", lock["local_dep"])
        assertEquals("1.9.0", lock["kemal"])
    }

    fun testLockWithoutShardsSectionYieldsNull() {
        assertNull(CrystalShardManifest.parseLock("version: 2.0\n"))
        assertNull(CrystalShardManifest.parseLock("shards: [oops"))
    }
}
