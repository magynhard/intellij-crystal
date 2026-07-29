# Require-Aware DOT Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore core stdlib DOT completion and expose optional stdlib, shard, and project reopenings only when they are reachable through the current file's forward require closure.

**Architecture:** Add a project-level cached require DAG whose immutable effective-source snapshots combine the SDK's cached `prelude.cr` closure with the current file's transitive require closure. Keep StubIndex as the symbol source, but filter every neutral type/hierarchy query by the effective source set before applying existing exact-identity and completeness rules. Resolve paths directly through VFS using Crystal 1.20's documented lookup order; use PSI and targeted VFS/root listeners for invalidation without `FileTypeIndex` scans or compiler subprocesses during completion.

**Tech Stack:** Kotlin 2.3.20, IntelliJ Platform SDK 2026.1.3, PSI, StubIndex, light project services, `PsiTreeChangeListener`, `BulkFileListener`, JUnit 4 platform fixtures, Gradle 9.4.1, JDK 21.

## Global Constraints

- Work in the current checkout and preserve unrelated worktree changes.
- Use strict RED/GREEN TDD for every behavior change.
- Never use `FileTypeIndex`, iterate every project `.cr` file, or run `crystal` from a completion request.
- Core visibility is the transitive closure of configured `prelude.cr`, not a hardcoded method list.
- A file sees only `prelude + itself + its own forward require closure`; reverse and sibling requirements never leak.
- Treat valid top-level requires surrounded by `{% if %}`/other macro controls as unconditional graph edges.
- Exclude requires in methods, types, runtime control flow, macro interpolation, and other contexts rejected by `CrystalRequireContextInspection`.
- Crystal bare-path lookup follows the compiler sequence: `<path>.cr`, `<path>/<basename>.cr`, and shard-style `<first-segment>/src/<remaining-path>.cr` plus its directory form.
- `require "kemal"` must resolve `lib/kemal/src/kemal.cr`. `shard.yml` executable `targets.*.main` entries are build targets, not Crystal library-require overrides; `shard.yml` and `shard.lock` still invalidate path caches because dependency installation and versions may change.
- Support only Crystal's documented wildcard suffixes `/*` and `/**`; do not implement arbitrary glob syntax.
- Wildcard expansion watches the resolved directory, or the nearest existing ancestor when the target does not yet exist, so create/delete/move/rename events invalidate it.
- Ordinary source edits that do not alter valid top-level require paths must not invalidate graph nodes or closures.
- Missing prelude and unresolved/ambiguous require edges fail conservatively without stale results or all-index fallback.
- Keep generated sources and the stub version unchanged; this feature does not alter serialized stubs or index keys.
- Update `docs/specs/completion.md`, `docs/specs/type-inference.md`, `docs/specs/require.md`, and `CHANGELOG.md`. Add `TODO.md` entries only for intentionally deferred behavior.
- Run `./gradlew test` before completion; `buildSearchableOptions` failures remain ignorable only when they match the documented IntelliJ Platform bug.

---

## File Structure

- `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequirePathResolver.kt`: pure VFS path lookup and wildcard expansion; no PSI, index, or cache ownership.
- `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequireCollector.kt`: valid top-level static require extraction and stable ordered fingerprinting from Crystal PSI.
- `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphService.kt`: project service, DAG/reverse edges, prelude/effective closure caches, immutable snapshots, and invalidation listeners.
- `src/main/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolver.kt`: acquire one effective-source snapshot per analysis session and filter StubIndex results.
- `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt`: create method-lookup sessions from the actual completion PSI context instead of a project directory.
- `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt`: pass the current completion position through instance/static method lookup.
- `src/main/kotlin/de/magynhard/crystal/inspections/CrystalRequireContextInspection.kt`: expose the existing context classification for collector reuse without changing diagnostics.
- Focused tests live beside the relevant analysis/completion suites; no integration test depends on the machine's `/usr/lib/crystal`.

---

### Task 1: Resolve Crystal Require Paths

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequirePathResolver.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequirePathResolverTest.kt`

**Interfaces:**
- Produces:

```kotlin
internal data class CrystalRequireResolution(
    val files: List<VirtualFile>,
    val watchedDirectories: Set<VirtualFile>,
)

internal class CrystalRequirePathResolver(
    private val project: Project,
    private val stdlibRoot: () -> VirtualFile? = { CrystalStdlibResolver.resolveStdlibPath(project) },
) {
    fun resolve(requiringFile: VirtualFile, requirePath: String): CrystalRequireResolution
    fun resolvePrelude(): VirtualFile?
}
```

- `files` preserves Crystal lookup/wildcard order and contains valid `.cr` files only.
- `watchedDirectories` contains wildcard targets or nearest existing ancestors; non-wildcard lookups return an empty set.

- [ ] **Step 1: Write failing exact-path tests.** Create fixture files for all compiler forms and assert the first existing candidate wins:

```kotlin
fun testResolvesRelativeFileAndDirectoryMain() {
    val source = file("src/main.cr")
    val direct = file("src/models/user.cr")
    assertEquals(listOf(direct), resolver().resolve(source, "./models/user").files)

    direct.delete(this)
    val directoryMain = file("src/models/user/user.cr")
    assertEquals(listOf(directoryMain), resolver().resolve(source, "./models/user").files)
}

fun testResolvesBareShardMainUnderSrc() {
    val source = file("src/main.cr")
    val kemal = file("lib/kemal/src/kemal.cr")
    assertEquals(listOf(kemal), resolver().resolve(source, "kemal").files)
}

fun testResolvesNestedShardPath() {
    val source = file("src/main.cr")
    val helper = file("lib/kemal/src/helpers.cr")
    assertEquals(listOf(helper), resolver().resolve(source, "kemal/helpers").files)
}
```

- [ ] **Step 2: Run the resolver suite and verify RED.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequirePathResolverTest"
```

Expected: compilation fails because `CrystalRequirePathResolver` does not exist.

- [ ] **Step 3: Implement ordered non-wildcard lookup.** For each selected root, generate candidates in this exact order and return the first existing `.cr` file:

```kotlin
private fun candidates(root: VirtualFile, path: String, allowShardSrc: Boolean): List<VirtualFile?> {
    val parts = path.split('/').filter(String::isNotEmpty)
    val basename = parts.lastOrNull() ?: return emptyList()
    val direct = root.findFileByRelativePath(path)
    val result = mutableListOf(
        root.findFileByRelativePath("$path.cr"),
        direct?.findChild("$basename.cr"),
    )
    if (allowShardSrc && parts.isNotEmpty()) {
        val shard = root.findChild(parts.first())?.findChild("src")
        val nested = parts.drop(1).joinToString("/")
        val shardPath = if (nested.isEmpty()) parts.first() else nested
        val shardTarget = shard?.findFileByRelativePath(shardPath)
        result += shard?.findFileByRelativePath("$shardPath.cr")
        result += shardTarget?.findChild("$basename.cr")
    }
    return result
}
```

Use the requiring file's parent for `./` and `../`. For bare paths, try `<project>/lib` before the configured stdlib root. Do not parse `shard.yml` executable targets as require aliases.

- [ ] **Step 4: Write failing wildcard tests.** Cover direct/recursive expansion, stable path ordering, non-`.cr` exclusion, and watched directories:

```kotlin
fun testSingleStarExpandsOnlyDirectCrystalFiles() {
    val source = file("src/main.cr")
    val first = file("src/models/a.cr")
    val second = file("src/models/b.cr")
    file("src/models/nested/c.cr")
    val resolution = resolver().resolve(source, "./models/*")
    assertEquals(listOf(first, second), resolution.files)
    assertEquals(setOf(directory("src/models")), resolution.watchedDirectories)
}

fun testDoubleStarExpandsRecursively() {
    val source = file("src/main.cr")
    val direct = file("src/models/a.cr")
    val nested = file("src/models/nested/b.cr")
    assertEquals(listOf(direct, nested), resolver().resolve(source, "./models/**").files)
}
```

- [ ] **Step 5: Implement wildcard expansion.** Recognize only terminal `/*` and `/**`, resolve the prefix with relative/bare directory semantics, sort children by path, and recurse only for `/**`. If the target is missing, return no files and watch its nearest existing parent.

- [ ] **Step 6: Run the resolver suite and verify GREEN.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequirePathResolverTest"
```

Expected: all resolver tests pass without invoking Crystal or querying an index.

- [ ] **Step 7: Inspect and commit Task 1.**

```bash
git diff --check
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequirePathResolverTest.kt
```

### Task 2: Extract Static Require Edges

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequireCollector.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalRequireContextInspection.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireCollectorTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalRequireContextInspectionTest.kt`

**Interfaces:**
- Produces:

```kotlin
internal data class CrystalDirectRequires(
    val paths: List<String>,
    val fingerprint: String,
)

internal object CrystalRequireCollector {
    fun collect(file: PsiFile): CrystalDirectRequires
}

internal object CrystalRequireContext {
    fun errorMessage(statement: CrystalRequireStatement): String?
    fun isValidTopLevel(statement: CrystalRequireStatement): Boolean = errorMessage(statement) == null
}
```

- Preserves all existing inspection messages and highlight targets.
- Only complete, interpolation-free double-quoted string paths enter the graph.

- [ ] **Step 1: Write failing collector tests.** Assert ordered extraction and stable fingerprints for direct and macro-control-wrapped top-level requires:

```kotlin
fun testCollectsDirectAndMacroControlledTopLevelRequires() {
    val file = configure(
        "require \"json\"\n" +
            "{% if flag?(:win32) %}\nrequire \"win32\"\n{% end %}\n" +
            "require \"./models/**\""
    )
    val result = CrystalRequireCollector.collect(file)
    assertEquals(listOf("json", "win32", "./models/**"), result.paths)
    assertEquals(result.fingerprint, CrystalRequireCollector.collect(file).fingerprint)
}

fun testExcludesDynamicAndInterpolatedRequires() {
    val file = configure(
        "def load\nrequire \"inside_def\"\nend\n" +
            "if true\nrequire \"dynamic\"\nend\n" +
            "require \"json/#{format}\""
    )
    assertTrue(CrystalRequireCollector.collect(file).paths.isEmpty())
}
```

- [ ] **Step 2: Run collector and inspection suites and verify RED.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequireCollectorTest" \
  --tests "de.magynhard.crystal.CrystalRequireContextInspectionTest"
```

Expected: collector symbols are missing.

- [ ] **Step 3: Extract the existing inspection classifier.** Move `errorMessage()` unchanged into internal `CrystalRequireContext`; make the inspection delegate to it. Keep `checkMacroControl()` unchanged because `{% require ... %}` remains invalid and is not a `CrystalRequireStatement` edge.

- [ ] **Step 4: Implement collection and fingerprinting.** Use `PsiTreeUtil.findChildrenOfType(file, CrystalRequireStatement::class.java)`, filter with `CrystalRequireContext.isValidTopLevel`, reject `stringExpression.expressionList.isNotEmpty()`, strip the surrounding quotes, and encode ordered paths with length prefixes:

```kotlin
val fingerprint = paths.joinToString("|") { "${it.length}:$it" }
```

- [ ] **Step 5: Add mutation assertions.** Verify a method-body edit leaves the fingerprint unchanged while adding, removing, reordering, or editing a valid top-level require changes it.

- [ ] **Step 6: Run focused suites and verify GREEN.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequireCollectorTest" \
  --tests "de.magynhard.crystal.CrystalRequireContextInspectionTest"
```

- [ ] **Step 7: Inspect and commit Task 2.**

```bash
git diff --check
  src/main/kotlin/de/magynhard/crystal/inspections/CrystalRequireContextInspection.kt \
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireCollectorTest.kt \
  src/test/kotlin/de/magynhard/crystal/CrystalRequireContextInspectionTest.kt
```

### Task 3: Build And Cache The Require DAG

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphService.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphServiceTest.kt`

**Interfaces:**
- Consumes: `CrystalRequireCollector.collect()` and `CrystalRequirePathResolver.resolve()`/`resolvePrelude()`.
- Produces:

```kotlin
internal data class CrystalEffectiveSourceSet(val files: Set<VirtualFile>) {
    fun contains(element: PsiElement): Boolean
}

@Service(Service.Level.PROJECT)
internal class CrystalRequireGraphService private constructor(
    private val project: Project,
    private val pathResolver: CrystalRequirePathResolver,
    registerListeners: Boolean,
) {
    constructor(project: Project) : this(project, CrystalRequirePathResolver(project), true)
    internal constructor(project: Project, pathResolver: CrystalRequirePathResolver) :
        this(project, pathResolver, false)

    fun effectiveSources(context: PsiElement): CrystalEffectiveSourceSet
    fun invalidateAll()
    internal fun cacheStats(): CrystalRequireCacheStats

    companion object {
        fun getInstance(project: Project): CrystalRequireGraphService = project.service()
    }
}

internal data class CrystalRequireCacheStats(
    val nodeBuilds: Long,
    val closureBuilds: Long,
    val fullInvalidations: Long,
)
```

- `contains()` compares physical/original containing-file `VirtualFile` identities and returns false for unrelated nonphysical files.
- The production constructor creates `CrystalRequirePathResolver(project)` and registers listeners. An internal secondary test constructor accepts a resolver and `registerListeners = false`, allowing deterministic fixture stdlib roots without replacing a live project service or invoking Crystal.

- [ ] **Step 1: Write failing closure tests.** Build a fake stdlib `prelude.cr -> string.cr, int.cr, array.cr`, a project chain `main.cr -> feature.cr -> extension.cr`, and a cycle. Assert effective sets contain prelude, current, and forward closure exactly once.

```kotlin
fun testCombinesPreludeCurrentFileAndForwardClosure() {
    val sources = service.effectiveSources(elementIn("src/main.cr"))
    assertEquals(
        setOf(
            vf("stdlib/prelude.cr"), vf("stdlib/string.cr"), vf("stdlib/int.cr"),
            vf("stdlib/array.cr"), vf("src/main.cr"), vf("src/feature.cr"),
            vf("src/extension.cr"),
        ),
        sources.files,
    )
}
```

- [ ] **Step 2: Run the graph suite and verify RED.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequireGraphServiceTest"
```

- [ ] **Step 3: Implement lazy node and closure caches.** A node stores its fingerprint, resolved outgoing files, watched directories, and version. Maintain reverse edges. Compute PSI/VFS data outside the mutation lock, then publish only if the captured global generation is still current. Cache closure snapshots by root node version plus dependency versions.

```kotlin
private data class Node(
    val fingerprint: String,
    val outgoing: List<VirtualFile>,
    val watchedDirectories: Set<VirtualFile>,
    val version: Long,
)

private data class Closure(
    val files: Set<VirtualFile>,
    val dependencyVersions: Map<VirtualFile, Long>,
)
```

- [ ] **Step 4: Implement prelude caching.** Resolve `prelude.cr` once per global generation, compute its closure through the same DAG, and union its immutable set into every file snapshot. A missing prelude returns an empty foundation.

- [ ] **Step 5: Add cache reuse tests.** Call `effectiveSources()` twice and assert `nodeBuilds`/`closureBuilds` do not change. Edit a method body, invoke targeted node validation, and assert the direct-require fingerprint keeps the cached node and closure.

- [ ] **Step 6: Add direct invalidation and reverse-edge tests.** Change a dependency's require fingerprint and assert only that node and its transitive dependents rebuild; unrelated roots retain the same closure snapshot.

- [ ] **Step 7: Add conservative failure tests.** Cover missing prelude, unresolved edge, ambiguous root candidate, invalid file, and cycles. Assert no stale file survives invalidation.

- [ ] **Step 8: Run the graph suite and verify GREEN.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequireGraphServiceTest"
```

- [ ] **Step 9: Inspect and commit Task 3.**

```bash
git diff --check
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphServiceTest.kt
```

### Task 4: Invalidate The Graph From PSI, VFS, Roots, And SDK Changes

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphService.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/sdk/CrystalSettingsConfigurable.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphServiceTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/sdk/CrystalSettingsConfigurableTest.kt`

**Interfaces:**
- Adds internal targeted invalidation entry points used by listeners and tests:

```kotlin
internal fun invalidateRequireFile(file: VirtualFile)
internal fun handleVfsEvents(events: List<VFileEvent>)
```

- Production listeners are registered by the light project service and disposed with the project.

- [ ] **Step 1: Write failing unsaved PSI invalidation tests.** Prime a closure, edit a top-level require through `myFixture.editor.document`, commit PSI, and assert the new edge is visible. Edit only a method body and assert cache counters do not change.

- [ ] **Step 2: Implement a `PsiTreeChangeAdapter`.** Ignore events outside Crystal files. Revalidate a node only when the changed/old/new child is a `CrystalRequireStatement`, lies inside one, or a file-level structural replacement can add/remove one. Compare the fresh fingerprint before invalidating dependents; do not invalidate on every PSI modification.

- [ ] **Step 3: Write failing wildcard VFS tests.** Prime `require "./models/*"`, then create, delete, rename, and move `.cr` files under `src/models`. Repeat under `lib/shard/src/extensions/**`. Assert only closures owning those watched directories rebuild.

- [ ] **Step 4: Subscribe to VFS changes.** Use `project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, BulkFileListener)` and process create/delete/move/property/content events after they complete. Match event paths against watched directory paths, exact required files, `shard.yml`, `shard.lock`, and relevant `lib/` roots. Create/delete/move/rename events invalidate affected path owners. A `VFileContentChangeEvent` for a Crystal graph node recomputes and compares its require fingerprint first; saving an ordinary method-body edit must retain that node and its dependent closures.

- [ ] **Step 5: Add nearest-existing-parent coverage.** Prime a wildcard for a missing `src/models` directory, create the directory and a `.cr` file, and assert the edge appears because `src` was watched.

- [ ] **Step 6: Add root and shard invalidation tests.** Fire project-root changes and modify `shard.yml`/`shard.lock`; assert `fullInvalidations` increases and prelude/path closures rebuild lazily on the next query.

- [ ] **Step 7: Subscribe to roots and SDK changes.** Subscribe to `ProjectTopics.PROJECT_ROOTS` with `ModuleRootListener`. In `CrystalSettingsConfigurable.apply()`, call `CrystalRequireGraphService.getInstance(project).invalidateAll()` immediately after clearing `CrystalStdlibResolver` and before resolving new roots.

- [ ] **Step 8: Run focused invalidation tests and verify GREEN.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalRequireGraphServiceTest" \
  --tests "de.magynhard.crystal.sdk.CrystalSettingsConfigurableTest"
```

- [ ] **Step 9: Inspect and commit Task 4.**

```bash
git diff --check
  src/main/kotlin/de/magynhard/crystal/sdk/CrystalSettingsConfigurable.kt \
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalRequireGraphServiceTest.kt \
  src/test/kotlin/de/magynhard/crystal/sdk/CrystalSettingsConfigurableTest.kt
```

### Task 5: Filter Neutral Type And Hierarchy Queries By Load Context

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolver.kt:21-29,1096-1106`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt:157-207`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt:61-69,153-160`
- Modify: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalMethodHierarchyTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolverTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelperTest.kt`

**Interfaces:**
- Changes completion helper context APIs to:

```kotlin
fun getMethodsAsLookups(typeName: String, context: PsiElement): List<LookupElement>
fun getMethodsAsLookups(typeNames: List<String>, context: PsiElement): List<LookupElement>
fun getStaticMethodsAsLookups(typeName: String, context: PsiElement): List<LookupElement>
```

- Removes project-only overloads after updating every caller and test; there is no valid file load context in a `Project` alone.
- `CrystalTypeResolutionSession` captures one `CrystalEffectiveSourceSet` at construction and applies it to cached type, method-name, and method-by-class StubIndex results.

- [ ] **Step 1: Write failing analysis filtering tests.** Create `main.cr`, required and unrequired files that reopen the same exact type, and assert only current/prelude/required declarations and methods reach `resolveType`, `collectMethods`, `collectNamedMethods`, include/extend edges, and superclasses.

```kotlin
fun testMethodHierarchyUsesOnlyForwardRequireClosure() {
    val context = configure("main.cr", "require \"./loaded\"\nService.new")
    add("loaded.cr", "class Service\n  def loaded\n  end\nend")
    add("not_loaded.cr", "class Service\n  def leaked\n  end\nend")
    val methods = CrystalTypeSetResolver.session(context).collectMethods(
        CrystalTypeIdentity("Service", "Service"),
        CrystalReceiverMode.INSTANCE,
    )
    assertEquals(listOf("loaded"), methods.methods.map { it.method.name })
}
```

- [ ] **Step 2: Run analysis/helper suites and verify RED.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalMethodHierarchyTest" \
  --tests "de.magynhard.crystal.analysis.CrystalTypeSetResolverTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionHelperTest"
```

Expected: unrequired reopenings still leak because current StubIndex queries use `allScope` without source filtering.

- [ ] **Step 3: Capture and apply the snapshot in `CrystalTypeResolutionSession`.**

```kotlin
private val effectiveSources = CrystalRequireGraphService.getInstance(context.project)
    .effectiveSources(context)

private fun types(name: String): List<CrystalNamedElement> = typeCache.getOrPut(name) {
    CrystalIndexService.findTypes(name, context.project, GlobalSearchScope.allScope(context.project))
        .filter(effectiveSources::contains)
        .toList()
}
```

Apply the identical filter to `methods(name)` and `classMethods(name)`. Do not filter after hierarchy completeness decisions; invisible reopenings must never enter metadata or duplicate-signature checks.

- [ ] **Step 4: Change completion helper APIs to accept `PsiElement`.** Build the session from that context and derive `project` from it. Delete `ProjectRootManager`, `PsiManager`, and project-directory fallback logic.

- [ ] **Step 5: Pass `parameters.position` from `CrystalCompletionContributor`.** Use it for both `ValueTypes` and `TypeObject` method collection. Keep constructor resolution on the same position/session.

- [ ] **Step 6: Update existing tests to express legal load context.** Same-file fixtures need no change. Cross-file fixtures must add explicit relative requires instead of relying on all-project visibility. Do not weaken production filtering to preserve unrealistic fixtures.

- [ ] **Step 7: Add duplicate optional-reopening regression coverage.** Put an identical canonical method signature in two optional files, require only one, and assert collection remains complete rather than being suppressed by the invisible duplicate.

- [ ] **Step 8: Run focused suites and verify GREEN.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.CrystalMethodHierarchyTest" \
  --tests "de.magynhard.crystal.analysis.CrystalTypeSetResolverTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionHelperTest" \
  --tests "de.magynhard.crystal.CrystalDotCallReferenceTest" \
  --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest"
```

- [ ] **Step 9: Inspect and commit Task 5.**

```bash
git diff --check
  src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt \
  src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt \
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalMethodHierarchyTest.kt \
  src/test/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolverTest.kt \
  src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelperTest.kt
```

### Task 6: Restore Realistic Stdlib DOT Completion End To End

**Files:**
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/ecr/EcrCompletionTest.kt`
- Modify: `docs/specs/completion.md`
- Modify: `docs/specs/type-inference.md`
- Modify: `docs/specs/require.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Verifies the public completion behavior; introduces no new production API.
- Uses a fixture stdlib/prelude supplied to the graph service/path resolver, never the developer machine's SDK.

- [ ] **Step 1: Write failing realistic prelude completion tests.** Model separate core files and inheritance/include edges:

```crystal
# prelude.cr
require "string"
require "int"
require "array"
require "indexable"
require "enumerable"

# string.cr
class String
  def upcase; end
  def downcase; end
end

# int.cr
struct Int
  def times; end
end
struct Int32
end

# array.cr
class Array(T)
  include Indexable(T)
end
```

Assert `"text".<caret>` contains `upcase` and `downcase`, `3.<caret>` contains `times`, and `[1, 2].<caret>` contains `each` through `Indexable -> Enumerable`.

- [ ] **Step 2: Add optional extension tests.** Put `String#to_json` in `json/to_json.cr`; assert it is absent without `require "json"`, present with a direct require, and present through a transitive helper require.

- [ ] **Step 3: Add shard and project reopening tests.** Verify `require "kemal"` loads methods from `lib/kemal/src/kemal.cr`; verify relative project reopenings appear only through direct/transitive forward closure and never through reverse/sibling context.

- [ ] **Step 4: Add macro-control and wildcard completion tests.** Assert methods from a top-level macro-wrapped require and from `require "./extensions/*"` appear. Trigger a wildcard file creation and assert the next completion includes its method without restarting the fixture.

- [ ] **Step 5: Run completion suites and verify RED, then GREEN after any minimal integration fixes.**

```bash
./gradlew test --tests "de.magynhard.crystal.CrystalCompletionTest" \
  --tests "de.magynhard.crystal.ecr.EcrCompletionTest"
```

For ECR, assert the prelude foundation still supplies literal methods even when the host `.ecr` file is not itself a Crystal source node. Do not infer reverse project context for injected fragments.

- [ ] **Step 6: Update behavioral specs.** In `completion.md`, replace the current all-index stdlib wording with effective-source filtering and document the literal examples. In `type-inference.md`, replace “load order cannot be proven” for invisible reopenings with the new require-context boundary while retaining strict ambiguity inside the effective set. In `require.md`, document compiler path order, shard `src` mapping, macro-control collection, wildcard behavior, and cache invalidation triggers.

- [ ] **Step 7: Update `CHANGELOG.md`.** Replace the design-only entry with a fixed entry describing restored core `String`/numeric/collection DOT completion and require-aware optional reopenings.

- [ ] **Step 8: Run focused and full verification.**

```bash
./gradlew test --tests "de.magynhard.crystal.analysis.*" \
  --tests "de.magynhard.crystal.CrystalCompletionTest" \
  --tests "de.magynhard.crystal.ecr.EcrCompletionTest"
./gradlew test
./gradlew build
git diff --check
```

Expected: all tests pass. Ignore `buildSearchableOptions` only if the failure is the repository-documented IntelliJ Platform bug; report any other build failure.

- [ ] **Step 9: Review runtime constraints.** Search changed production code and confirm there is no `FileTypeIndex`, project-wide `.cr` traversal, completion-time `ProcessBuilder`, stale debug logging, or hardcoded stdlib method baseline.

- [ ] **Step 10: Commit documentation and end-to-end coverage.**

```bash
git add src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt \
  src/test/kotlin/de/magynhard/crystal/ecr/EcrCompletionTest.kt \
  docs/specs/completion.md docs/specs/type-inference.md docs/specs/require.md CHANGELOG.md \
  docs/superpowers/plans/2026-07-29-require-aware-dot-completion.md
```

## Final Review Checklist

- [ ] Every design requirement in `docs/specs/2026-07-29-require-aware-dot-completion-design.md` maps to a task above.
- [ ] Core prelude, direct/transitive requirements, forward-only semantics, macro controls, shard `src`, wildcards, cache reuse, and every invalidation trigger have automated coverage.
- [ ] All analysis consumers use one immutable source snapshot per session.
- [ ] Existing exact namespace, overload, hierarchy, union, constructor, and no-fallback semantics remain covered.
- [ ] `git status --short` contains only intended files before each commit.
- [ ] No behavior is deferred without an actionable `TODO.md` entry.
