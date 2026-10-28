package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import java.io.File

object CrystalSdkDetector {

    /**
     * Attempts to find the Crystal executable automatically.
     * Checks PATH first, then known installation locations.
     */
    fun detect(): String? {
        // Try known locations
        val knownPaths = buildList {
            add("/usr/bin/crystal")
            add("/usr/local/bin/crystal")
            add("/opt/homebrew/bin/crystal")
            add("/snap/bin/crystal")
            val home = System.getProperty("user.home")
            add("$home/.asdf/shims/crystal")
            add("$home/.local/bin/crystal")
            add("$home/.crenv/shims/crystal")
            if (SystemInfo.isWindows) {
                add("C:\\Program Files\\Crystal\\crystal.exe")
                add("C:\\crystal\\crystal.exe")
            }
        }

        val executable = if (SystemInfo.isWindows) "crystal.exe" else "crystal"
        val pathCandidates = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
            .filter(String::isNotBlank)
            .map { File(it, executable).path }
        return selectFirstWorking(pathCandidates + knownPaths)
    }

    internal fun selectFirstWorking(candidates: List<String>): String? =
        candidates.distinct().firstOrNull { File(it).canExecute() && validate(it) != null }

    /**
     * Validates that the given path is a working Crystal executable.
     * Returns the version string on success, null on failure.
     */
    fun validate(path: String): String? {
        return try {
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.contains("Crystal")) output else null
        } catch (_: Exception) {
            null
        }
    }

}
