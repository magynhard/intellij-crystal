package de.magynhard.crystal.run

import java.io.File

/**
 * Scans Crystal spec files to build a mapping of test names to their source locations.
 *
 * Crystal's verbose output (-v) shows test names in a hierarchy:
 *   ClassName
 *     #method
 *       test description  test description
 *
 * This indexer parses the spec file to find `describe`, `context`, and `it` blocks,
 * builds the same hierarchy, and provides a map of test name → (file, line) for
 * navigation from the test runner to the source.
 */
class CrystalSpecFileIndexer(private val filePath: String) {

    data class TestLocation(val file: String, val line: Int)

    fun buildIndex(): Map<String, TestLocation> {
        val result = mutableMapOf<String, TestLocation>()
        val file = File(filePath)
        if (!file.exists()) return result

        val lines = file.readLines()
        val suiteStack = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            // Match describe/context blocks (with string or constant/class name)
            val describeMatch = Regex("""(?:describe|context)\s+(?:"(.+?)"|'(.+?)'|(\w+))""").find(trimmed)
            if (describeMatch != null) {
                val name = describeMatch.groupValues[1].ifEmpty {
                    describeMatch.groupValues[2].ifEmpty { describeMatch.groupValues[3] }
                }
                // Pop stack to current indent level
                while (suiteStack.size > indent / 2) {
                    suiteStack.removeAt(suiteStack.size - 1)
                }
                suiteStack.add(name)
                i++
                continue
            }

            // Match it blocks
            val itMatch = Regex("""it\s+["'](.+?)["']""").find(trimmed)
            if (itMatch != null) {
                val testName = itMatch.groupValues[1]
                val fullTestName = (suiteStack + testName).joinToString(" ")
                val location = TestLocation(filePath, i + 1) // 1-based line number
                result[fullTestName] = location
                i++
                continue
            }

            i++
        }

        return result
    }

    companion object {
        private val cache = mutableMapOf<String, Map<String, TestLocation>>()
        private val indexedFiles = mutableSetOf<String>()

        fun getTestLocations(filePath: String): Map<String, TestLocation> {
            if (!indexedFiles.contains(filePath)) {
                val indexer = CrystalSpecFileIndexer(filePath)
                val locations = indexer.buildIndex()
                cache[filePath] = locations
                indexedFiles.add(filePath)
            }
            return cache[filePath] ?: emptyMap()
        }

        fun clearCache() {
            cache.clear()
            indexedFiles.clear()
        }
    }
}
