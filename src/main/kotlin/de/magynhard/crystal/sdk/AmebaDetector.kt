package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Detection and validation for the Ameba linter binary
 * (`github.com/crystal-ameba/ameba`).
 *
 * Mirrors [CrystalSdkDetector]: `PATH` first, then known install locations.
 * `detect` takes an injectable path lookup so tests can supply a fake
 * environment without touching the real `PATH`.
 */
object AmebaDetector {

    /**
     * Attempts to find the Ameba executable automatically.
     * Checks PATH first, then known installation locations.
     */
    fun detect(pathLookup: (String) -> File? = ::findOnPath): String? {
        for (name in executableNames()) {
            try {
                pathLookup(name)?.takeIf { it.isFile && it.canExecute() }?.let { return it.absolutePath }
            } catch (_: Exception) {
                // A broken PATH entry must not abort detection.
            }
        }
        return knownLocations().firstOrNull { File(it).let { file -> file.isFile && file.canExecute() } }
    }

    /**
     * Validates that the given path is a working Ameba executable.
     * Returns the version string (the `ameba --version` output) on success,
     * null on failure. Never throws.
     *
     * Accepts both shapes: release builds print `ameba 1.7.0 (sha)`, while
     * shards-built binaries print the bare version (`1.7.0`).
     */
    fun validate(path: String): String? {
        if (path.isBlank()) return null
        return try {
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            // Close STDIN first: wrapper scripts (and any `cat`-style
            // preamble) block on it, and --version needs no input.
            try {
                process.outputStream.close()
            } catch (_: Exception) {
                // Already closed by the child; proceed to the version read.
            }
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            val output = try {
                process.inputStream.bufferedReader().readText().trim()
            } catch (_: Exception) {
                return null
            }
            if (process.exitValue() != 0 || output.isBlank()) return null
            if (output.contains("ameba", ignoreCase = true)) return output
            // Shards-built binaries print the bare version without a name —
            // but only a *bare* version (a foreign tool printing "crystal
            // 1.17.0" must still be rejected).
            if (output.matches(Regex("""v?\d+(?:\.\d+)*(?:[-.+][0-9A-Za-z.]+)?"""))) return output
            null
        } catch (_: Exception) {
            null
        }
    }

    internal fun executableNames(): List<String> =
        if (SystemInfo.isWindows) listOf("ameba.exe", "ameba") else listOf("ameba")

    internal fun knownLocations(home: String = System.getProperty("user.home")): List<String> {
        val locations = mutableListOf(
            "/usr/bin/ameba",
            "/usr/local/bin/ameba",
            "/opt/homebrew/bin/ameba",
            "$home/.local/bin/ameba",
            "$home/bin/ameba"
        )
        if (SystemInfo.isWindows) {
            locations.add("C:\\Program Files\\Ameba\\ameba.exe")
        }
        return locations
    }

    private fun findOnPath(name: String): File? {
        val cmd = if (SystemInfo.isWindows) arrayOf("where", name) else arrayOf("which", name)
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0 || output.isBlank()) return null
        return File(output.lines().first().trim()).takeIf { it.canExecute() }
    }
}
