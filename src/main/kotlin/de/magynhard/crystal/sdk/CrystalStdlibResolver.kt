package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Resolves the Crystal standard library path by running `crystal env CRYSTAL_PATH`.
 * Returns the VirtualFile pointing to the stdlib src/ directory, or null if unavailable.
 */
object CrystalStdlibResolver {

    fun resolveStdlibPath(project: Project): VirtualFile? {
        val crystalPath = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        val crystalEnv = runCrystalEnv(crystalPath) ?: return null

        // CRYSTAL_PATH is colon-separated: "lib:/usr/lib/crystal"
        // We want the stdlib entry (starts with "/" on Unix, drive letter on Windows)
        val stdlibEntry = crystalEnv.split(":")
            .firstOrNull { it.startsWith("/") || it.matches(Regex("^[A-Z]:.*")) }
            ?: return null

        val stdlibDir = File(stdlibEntry)
        if (!stdlibDir.isDirectory) return null

        // Crystal stdlib has src/ subdirectory with .cr files
        val srcDir = File(stdlibDir, "src")
        val root = if (srcDir.isDirectory) srcDir else stdlibDir

        return LocalFileSystem.getInstance().findFileByPath(root.absolutePath)
    }

    fun resolveCrystalVersion(project: Project): String? {
        val crystalPath = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        return runCrystalVersion(crystalPath)
    }

    private fun runCrystalVersion(crystalPath: String): String? {
        return try {
            val process = ProcessBuilder(crystalPath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun runCrystalEnv(crystalPath: String): String? {
        return try {
            val process = ProcessBuilder(crystalPath, "env", "CRYSTAL_PATH")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
    }
}
