# Plan: Crystal StdLib as Indexed Source Library

## Goal

Add the Crystal standard library (`/usr/lib/crystal/src/`) as an indexed source library so that features like Go to Definition, Parameter Info, Code Completion, and Type Checking work for stdlib symbols (e.g., `ENV.fetch`, `Array#push`, `HTTP::Client`).

## Approach: `AdditionalLibraryRootsProvider` + `SyntheticLibrary`

This is the lightest-weight IntelliJ API for adding external libraries. It does **not** create persistent library entries in the project model — the library is "virtual" and provided dynamically on each project open.

**How it works:**
1. A `SyntheticLibrary` subclass provides the stdlib source root(s)
2. An `AdditionalLibraryRootsProvider` returns this library for Crystal projects
3. The platform automatically indexes all `.cr` files under the source root
4. Indexing progress is displayed in the status bar automatically

**Why not `ModuleRootModificationUtil`?**
- That creates persistent library entries in `.idea/` (project model pollution)
- `SyntheticLibrary` is how GoLand handles GOROOT — clean and automatic

## Files to Create/Modify

### 1. NEW: `CrystalStdlibLibraryProvider.kt`
**Path:** `src/main/kotlin/de/magynhard/crystal/sdk/CrystalStdlibLibraryProvider.kt`

```kotlin
package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile

class CrystalStdlibLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val stdlibPath = CrystalStdlibResolver.resolveStdlibPath(project) ?: return emptyList()
        val root = stdlibPath.findFileByPath("src") ?: stdlibPath
        return listOf(CrystalStdlibLibrary(root))
    }
}

private class CrystalStdlibLibrary(private val root: VirtualFile) : SyntheticLibrary() {
    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)
    override fun getBinaryRoots(): Collection<VirtualFile> = emptyList()
    override fun equals(other: Any?) = other is CrystalStdlibLibrary && other.root == root
    override fun hashCode() = root.hashCode()
}
```

### 2. NEW: `CrystalStdlibResolver.kt`
**Path:** `src/main/kotlin/de/magynhard/crystal/sdk/CrystalStdlibResolver.kt`

Resolves the stdlib path by running `crystal env CRYSTAL_PATH`.

```kotlin
package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object CrystalStdlibResolver {

    fun resolveStdlibPath(project: Project): VirtualFile? {
        val crystalPath = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        val crysalEnv = runCrystalEnv(crystalPath) ?: return null
        
        // CRYSTAL_PATH is colon-separated: "lib:/usr/lib/crystal"
        // We want the stdlib entry (starts with "/" on Unix, drive letter on Windows)
        val stdlibEntry = crysalEnv.split(":")
            .firstOrNull { it.startsWith("/") || it.matches(Regex("^[A-Z]:.*")) }
            ?: return null
        
        val stdlibDir = File(stdlibEntry)
        if (!stdlibDir.isDirectory) return null
        
        // Crystal stdlib has src/ subdirectory with .cr files
        val srcDir = File(stdlibDir, "src")
        val root = if (srcDir.isDirectory) srcDir else stdlibDir
        
        return LocalFileSystem.getInstance().findFileByPath(root.absolutePath)
    }

    private fun runCrystalEnv(crystalPath: String): String? {
        return try {
            val process = ProcessBuilder(crystalPath, "env", "CRYSTAL_PATH")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) output else null
        } catch (_: Exception) {
            null
        }
    }
}
```

### 3. MODIFY: `plugin.xml`
**Path:** `src/main/resources/META-INF/plugin.xml`

Add extension point (after the existing `postStartupActivity`):

```xml
<!-- Crystal stdlib as indexed source library -->
<additionalLibraryRootsProvider
        implementation="de.magynhard.crystal.sdk.CrystalStdlibLibraryProvider"/>
```

### 4. MODIFY: `CrystalTypeCompletionProvider.kt`
**Path:** `src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeCompletionProvider.kt`

**Change:** Remove the hardcoded `STDLIB_TYPES` list. Instead, query the class index which will now include stdlib types after indexing.

```kotlin
// Before: hardcoded list of ~100 types
// After: dynamic from StubIndex (which now includes stdlib)
fun getTypeLookups(position: PsiElement, project: Project): List<LookupElementBuilder> {
    val result = mutableListOf<LookupElementBuilder>()
    
    // All types from index (includes stdlib after library provider is active)
    val allTypes = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
    for (typeName in allTypes) {
        result.add(
            LookupElementBuilder.create(typeName)
                .withIcon(AllIcons.Nodes.Class)
                .withTypeText("stdlib", true)
        )
    }
    
    // `self` if inside a class or struct
    if (isInsideClassOrStruct(position)) {
        result.add(
            LookupElementBuilder.create("self")
                .withIcon(AllIcons.Nodes.Type)
                .withTypeText("current type", true)
        )
    }
    
    return result
}
```

**Note:** Keep `STDLIB_TYPES` as a fallback for when Crystal is not installed (no stdlib path available). The `getStdlibTypeLookups()` method can remain for that case.

### 5. MODIFY: `TODO.md`
Mark stdlib indexing as complete (or add entry).

## Implementation Order

1. Create `CrystalStdlibResolver.kt` (path resolution)
2. Create `CrystalStdlibLibraryProvider.kt` (library provider)
3. Register in `plugin.xml`
4. Test: Open a Crystal project, verify stdlib files appear in "External Libraries"
5. Test: Verify Go to Definition works for `ENV.fetch`, `Array.new`, etc.
6. Test: Verify Parameter Info shows correct stdlib signatures
7. Update `CrystalTypeCompletionProvider.kt` to use dynamic index
8. Run `./gradlew test` and fix any regressions
9. Update TODO.md

## Expected Behavior

### Indexing
- When a Crystal project opens, the platform detects the stdlib via `crystal env CRYSTAL_PATH`
- The stdlib source root is added as a synthetic library
- The platform automatically indexes all ~702 `.cr` files
- Progress is shown in the status bar ("Analyzing...")

### User Experience
- **External Libraries** section shows "Crystal Stdlib" with the source tree
- **Go to Definition** (Ctrl+Click) on `ENV.fetch` navigates to `/usr/lib/crystal/src/env.cr`
- **Parameter Info** (Ctrl+P) shows correct signatures for stdlib methods
- **Code Completion** includes all stdlib types and methods
- **Type Check Inspection** knows about stdlib method signatures

### Memory/Performance
- ~702 files × ~8KB average = ~5.5MB of source to parse
- Initial indexing: ~10-30 seconds (first project open)
- Subsequent opens: cached by IntelliJ's indexing infrastructure
- Memory: ~20-50MB additional for stdlib PSI trees

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Parser errors in complex stdlib files (macros, `lib` blocks) | The parser already handles user code with these constructs; stubs are created for valid definitions even with errors |
| Slow indexing on first open | Acceptable tradeoff; subsequent opens use cache |
| Crystal not installed | `CrystalStdlibResolver` returns null → library not added → falls back to hardcoded `STDLIB_TYPES` |
| CRYSTAL_PATH format varies | Parse robustly (split on `:`, filter absolute paths) |
| stdlib structure varies between Crystal versions | Check for `src/` subdirectory, fall back to root |

## Testing Strategy

1. **Unit test:** `CrystalStdlibResolver` with mocked `crystal env` output
2. **Integration test:** Open project with Crystal installed, verify indexing completes
3. **Manual test:** Ctrl+Click on `ENV.fetch` → should navigate to `env.cr`
4. **Regression test:** Run `./gradlew test` to ensure no existing tests break
