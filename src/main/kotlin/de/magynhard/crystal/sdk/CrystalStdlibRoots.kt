package de.magynhard.crystal.sdk

import com.intellij.openapi.vfs.VirtualFile

/**
 * Enumerates the stdlib source roots to be indexed, **excluding** parts of
 * the Crystal distribution that are not part of the user-facing standard
 * library.
 *
 * Background — Crystal 1.20+ ships stdlib files directly under
 * `/usr/lib/crystal` (no `src/` subdirectory). The distribution tree also
 * contains the CLI tools (`crystal/`), C ABI bindings (`lib_c/`, `lib_z/`),
 * LLVM bindings (`llvm/`, `ll/`), the Boehm GC interface (`gc/`), and
 * samples — together ~960 files that are NOT part of the stdlib but would
 * be indexed by IntelliJ if the bare `/usr/lib/crystal` root was handed to
 * a library.
 *
 * The compiler source (`compiler/`, ~190 files) IS indexed: real projects
 * require compiler internals — ameba, the standard Crystal linter and a
 * bundled dev dependency of kemal, requires the compiler syntax tree with
 * a wildcard require and reopens `Crystal::Location`.
 * Without the tree, a shard's reopening becomes the only indexed
 * declaration of the type and its constructor pool resolves empty
 * ("expected at most 0, got N" false positives). The historical concern
 * (a one-time StubIndex rebuild parsing the metaprogramming-heavy source
 * took minutes on the EDT) predates the current parser, whose compiler
 * tree is mostly clean; the build runs on the platform's background
 * indexing threads.
 *
 * This helper returns the indexed roots:
 *
 * - All top-level `.cr` files under the stdlib root (`array.cr`,
 *   `string.cr`, …) — added **individually** as source roots.
 * - All subdirectories **except** a hardcoded exclusion list.
 *
 * For Crystal ≤ 1.19 (where `src/` exists) the previous behaviour is kept:
 * the `src/` directory is returned as a single root (it does not contain
 * `compiler/`, etc.).
 *
 * The exclusion list uses lower-case comparison to stay robust against any
 * future filesystem-case changes in the distribution.
 */
object CrystalStdlibRoots {

    /**
     * Subdirectories of the stdlib root that are NOT part of the stdlib and
     * must be excluded from indexing.
     */
    private val EXCLUDED_DIRS = setOf(
        "crystal",    // `crystal` CLI tooling — ~176 files
        "lib_c",      // C ABI bindings — ~721 files, mostly per-arch duplicates
        "lib_z",      // zlib bindings
        "ll",         // low-level LLVM bindings
        "llvm",       // LLVM bindings — ~62 files
        "gc",         // Boehm GC interface
        "samples",    // example snippets (if present)
    )

    /**
     * Returns the list of stdlib source roots to index. See class doc for
     * the layout rules used to decide between the single-`src/` case and
     * the Crystal ≥ 1.20 flat-layout filtered case.
     */
    fun enumerate(stdlibRoot: VirtualFile): List<VirtualFile> {
        if (!stdlibRoot.isDirectory) return listOf(stdlibRoot)

        // Crystal ≤ 1.19 layout: <root>/src/ contains the stdlib only.
        val srcDir = stdlibRoot.findChild("src")
        if (srcDir != null && srcDir.isDirectory) return listOf(srcDir)

        // Crystal ≥ 1.20 layout: <root> contains stdlib files AND compiler/
        // lib_c/ llvm/ etc. Use the filtered enumeration.
        val result = mutableListOf<VirtualFile>()
        for (child in stdlibRoot.children) {
            if (child.isDirectory) {
                if (child.name.lowercase() in EXCLUDED_DIRS) continue
                result.add(child)
            } else if (child.extension == "cr") {
                result.add(child)
            }
        }
        return result
    }

    fun excludedDirectories(stdlibRoot: VirtualFile): List<VirtualFile> =
        EXCLUDED_DIRS.mapNotNull { stdlibRoot.findChild(it)?.takeIf(VirtualFile::isDirectory) }

    fun legacyExclusions(sourceRoot: VirtualFile): List<VirtualFile> {
        if (!sourceRoot.isDirectory) return emptyList()
        if (sourceRoot.name.lowercase() in EXCLUDED_DIRS) return listOf(sourceRoot)
        if (sourceRoot.name.equals("src", ignoreCase = true)) return emptyList()
        return excludedDirectories(sourceRoot)
    }
}
