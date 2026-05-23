package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object CrystalSdkDetector {

    /**
     * Attempts to find the Crystal executable automatically.
     * Checks PATH first, then known installation locations.
     */
    fun detect(): String? {
        // Try PATH via 'which' / 'where'
        val fromPath = findInPath()
        if (fromPath != null) return fromPath

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

        return knownPaths.firstOrNull { File(it).canExecute() }
    }

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

    private fun findInPath(): String? {
        return try {
            val cmd = if (SystemInfo.isWindows) arrayOf("where", "crystal") else arrayOf("which", "crystal")
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                val path = output.lines().first().trim()
                if (File(path).canExecute()) path else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
