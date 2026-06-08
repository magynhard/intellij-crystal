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
 * builds the same hierarchy, and provides a map of test name → List<(file, line)> for
 * navigation from the test runner to the source.
 *
 * When multiple tests share the same full name (e.g., two `it "works"` in different
 * describe blocks), each gets its own entry in the list, preserving source order.
 */
class CrystalSpecFileIndexer(private val filePath: String) {

    data class TestLocation(val file: String, val line: Int)

    fun buildIndex(): Map<String, List<TestLocation>> {
        val result = mutableMapOf<String, MutableList<TestLocation>>()
        val file = File(filePath)
        if (!file.exists()) return result

        val lines = file.readLines()
        val suiteStack = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            // Skip comment lines
            if (trimmed.startsWith("#")) {
                i++
                continue
            }

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

            // Match it blocks: it "name" or it("name")
            val itMatch = Regex("""it\s*\(\s*["'](.+?)["']\s*\)|it\s+["'](.+?)["']""").find(trimmed)
            if (itMatch != null) {
                val testName = itMatch.groupValues[1].ifEmpty { itMatch.groupValues[2] }
                val fullTestName = (suiteStack + testName).joinToString(" ")
                val location = TestLocation(filePath, i + 1) // 1-based line number
                result.getOrPut(fullTestName) { mutableListOf() }.add(location)
                i++
                continue
            }

            i++
        }

        return result
    }

    companion object {
        private val cache = mutableMapOf<String, CacheEntry>()
        private val indexedFiles = mutableSetOf<String>()

        private data class CacheEntry(
            val locations: Map<String, List<TestLocation>>,
            val lastModified: Long
        )

        fun getTestLocations(filePath: String): Map<String, List<TestLocation>> {
            val file = File(filePath)
            val currentModified = if (file.exists()) file.lastModified() else 0L

            val cached = cache[filePath]
            if (cached != null && indexedFiles.contains(filePath) && cached.lastModified >= currentModified) {
                return cached.locations
            }

            val indexer = CrystalSpecFileIndexer(filePath)
            val locations = indexer.buildIndex()
            cache[filePath] = CacheEntry(locations, currentModified)
            indexedFiles.add(filePath)
            return locations
        }

        /**
         * Index all *_spec.cr files in a directory (recursively).
         * Returns a merged map of test names to their source locations.
         */
        fun getTestLocationsForDirectory(dirPath: String): Map<String, List<TestLocation>> {
            val cacheKey = "dir:$dirPath"
            val dir = File(dirPath)

            // Check if any spec file in the directory has been modified since last index
            if (indexedFiles.contains(cacheKey) && dir.exists() && dir.isDirectory) {
                val cached = cache[cacheKey]!!
                val anyModified = dir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith("_spec.cr") }
                    .any { it.lastModified() > cached.lastModified }
                if (!anyModified) {
                    return cached.locations
                }
            }

            val result = mutableMapOf<String, MutableList<TestLocation>>()
            var latestModified = 0L
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith("_spec.cr") }
                    .forEach { specFile ->
                        val specModified = specFile.lastModified()
                        if (specModified > latestModified) latestModified = specModified
                        val indexer = CrystalSpecFileIndexer(specFile.absolutePath)
                        val locations = indexer.buildIndex()
                        for ((key, locs) in locations) {
                            result.getOrPut(key) { mutableListOf() }.addAll(locs)
                        }
                    }
            }
            cache[cacheKey] = CacheEntry(result, latestModified)
            indexedFiles.add(cacheKey)
            return result
        }

        fun clearCache() {
            cache.clear()
            indexedFiles.clear()
        }
    }
}
