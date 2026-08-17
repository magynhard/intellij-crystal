# Final Review Fixes Report

## Result

All adjudicated final-review blockers for `417651a` were implemented. The final focused suite, full
test suite, and full build pass. No production diff introduces `FileTypeIndex`, `processFiles()`, a
project-wide Crystal traversal, a completion-time process call, debug logging, or a hardcoded stdlib
method baseline.

## Adjudications

- Bare require candidates retain Crystal 1.20 compiler order: `<path>.cr`,
  `<path>/<basename>.cr`, `<first>/src/<remaining>.cr`, then the corresponding directory form.
  The first existing candidate wins. Candidate forms are not treated as ambiguous; only competing
  roots at one precedence level can be ambiguous.
- Namespaced shards continue to use the existing `<first>/src/<remaining>` representation.
- Runtime type/index queries remain `allScope` StubIndex queries filtered by one effective-source
  snapshot. No `FileTypeIndex` or project source scan was introduced.
- Recursive wildcard targets equal to the project root are intentionally suppressed as a conservative
  performance boundary. Explicit targets such as `src/extensions/**` remain supported. A TODO records
  acceptance criteria for any future opt-in.
- Per-root cache eviction and composite prelude source sets remain deferred with bounded-memory,
  coherence, invalidation, and concurrency acceptance criteria in `TODO.md`.

## Implemented Fixes

- Explicit relative and bare `.cr` require paths resolve directly and path completion preserves an
  explicitly typed extension.
- `CrystalStringLiteralDecoder` decodes named, pass-through, octal, hex, fixed/braced Unicode, and
  escaped-newline forms before collection, fingerprinting, resolution, and invalidation. Invalid
  sequences, invalid codepoints, interpolation, incomplete strings, and compound strings are omitted.
- `CrystalRequireSemantics` is neutral analysis infrastructure shared by collection and inspection.
- Effective sources return the full closure only for physical `.cr` files, the prelude only for valid
  Crystal fragments injected into ECR, and an empty set for arbitrary nonphysical, unrelated, or
  invalid contexts.
- PSI invalidation recognizes file/macro-control structural edits even when removed/replaced children
  are invalid. Full unsaved removal and replacement update graph consumers immediately.
- Exact DOT receiver evidence uses the caller-owned `CrystalTypeResolutionSession` for every identity
  lookup. Completion and polyvariant navigation retain visible lexical identities despite unrequired
  global and namespaced collisions.
- Dirty closure state records dependency reasons. An unchanged dependency validation clears that
  reason from every owner without clearing simultaneous reasons.
- Recursive wildcard traversal is iterative, deterministic, and cancellation-aware.
- Cold prelude readers share one owner resolution and immutable snapshot. Owner failure or
  cancellation releases waiters and permits a clean retry.
- Stdlib discovery tests use project-local disposable function/latch collaborators. Graph and settings
  tests no longer use POSIX scripts, executable bits, `touch`, `printf`, or sleeps.
- SDK A is cleared before SDK B discovery; failed/unresolved B cannot restore A, publishes no new root,
  and still performs final graph invalidation.
- Legacy real-SDK diagnostics remain diagnostic-only and skip when no machine SDK is available.
  General resolver tests use artificial project-local stdlib and version collaborators.
- `CrystalStdlibVfsAccess` no longer caches a static SDK path. JVM property changes are synchronized,
  LIFO-scoped, restored exactly, and resolve the current project root per scope.

## TDD Evidence

### RED

- Decoder/resolver/collector tests initially failed compilation because
  `CrystalStringLiteralDecoder` did not exist.
- The first decoder/resolver GREEN attempt exposed four behavioral failures: decoded expectation,
  escaped-newline lexing, explicit-test fixture leakage, and canonical `./**` relative classification.
- ECR focused tests failed after arbitrary nonphysical Prelude access was removed, proving injected
  fragment detection needed the original injected file/host boundary.
- DOT suites exposed stale machine-SDK VFS access and the former unfiltered receiver identity path.
- Settings tests exposed fixture publication ordering and SDK transition invalidation assumptions.
- The first full `./gradlew test` run reached 1,375 tests and failed only five machine-dependent stdlib
  diagnostics/resolver tests, which were then isolated or made explicitly diagnostic.

### GREEN

- Decoder/resolver/collector/require-semantics focus: passed.
- Require graph plus ECR focus: passed.
- Exact DOT receiver, DOT target, reference, navigation, and inspection focus: passed.
- Graph/settings/require completion focus: passed.
- Explicit unrequired-collision completion/reference regressions: passed.
- Broad requested focus command covering resolver, decoder, collector, graph, settings, require and
  DOT completion, DOT reference/target, Go To, type/require inspections, and ECR: passed in 3m50s.
- Final `./gradlew test`: 1,375 tests passed in 5m16s.
- Final `./gradlew build`: passed, including searchable options, in 710ms with cached tasks.
- `git diff --check 417651a`: passed.
- Production diff forbidden-pattern scan: no matches.
- Targeted POSIX-dependency scan of `CrystalRequireGraphServiceTest` and
  `CrystalSettingsConfigurableTest`: no matches.

## Commits

- `c08c40f` `fix(analysis): decode and resolve static requires safely`
- `5bf0b3d` `fix(analysis): harden require graph concurrency`
- `2a24b4a` `fix(inspection): resolve DOT receivers in load context`
- `85b3439` `test(sdk): isolate stdlib discovery fixtures`

The documentation/report commit is recorded by the repository history containing this file.

## Re-review Blockers at `9000324`

### Result

**DONE.** All remaining re-review blockers were implemented in `475d773`
(`fix(analysis): match Crystal require semantics`). No concerns remain.

### Canonical Behavior

- Ported Crystal 1.20.3 `Crystal::CrystalPath#each_file_expansion` order for every require root:
  initial ensured `.cr`, nested shard non-namespaced, nested shard namespaced, ordinary
  directory-main, shard non-namespaced directory-main, and shard namespaced directory-main.
  Bare and relative branches retain their compiler-specific forms. Explicit nested `.cr` paths still
  enter shard expansion, and the first existing candidate always wins.
- Aligned static string decoding with `lexer.cr`: exactly two hex digits, up to four octal digits with
  byte-range validation, fixed four-digit Unicode, braced Unicode digit/separator/closing-brace rules,
  surrogate and maximum-codepoint rejection, named/pass-through escapes, and escaped-newline ASCII
  whitespace consumption. Invalid literals produce neither dependency paths nor fingerprint edges.
- Invalid PSI is rejected before containing-file access and cannot belong to an effective source set.
- Recursive wildcards reject targets equal to or containing the project root. Iterative traversal now
  tracks canonical identities, terminates across symbolic-link cycles, checks cancellation, and emits
  Crystal's sorted depth-first order while retaining targeted descendant traversal.
- Discovery and version test overrides now use synchronized active stack frames. Out-of-order disposal
  skips inactive frames and cannot restore a disposed callback.
- General completion, type-check, expression-resolution, and Find Usages tests use project-local
  synthetic stdlib roots. The obsolete global VFS-property helper was deleted; unavailable discovery
  is deterministic and does not fall through to `crystal env`.
- Concurrent prelude owner and waiter tests assert exact `ExecutionException` causes, including the
  original `ProcessCanceledException`, and retain successful retry assertions.
- SDK A to failed/unresolved SDK B behavior remains deterministic and covered.

### TDD Evidence

#### RED

- The first path/decoder/collector/override run had nine expected failures covering compiler candidate
  order, explicit nested `.cr`, depth-first traversal, ancestor safety, numeric escape bounds, invalid
  collector edges, and out-of-order override disposal.
- The stale physical PSI replacement test failed because the old element remained a member of its
  captured source set.
- Removing real SDK access exposed three Require Completion fixtures that had accidentally relied on
  machine stdlib entries; they were replaced with real project-local `lib/` fixtures.
- The first broad focused run exposed one SDK A/B regression caused by clearing the published A cache
  when merely installing a null test callback. Cache lifecycle was restored and callback-stack tests
  now clear only when intentionally evaluating another frame.

#### GREEN

- Final focused path/decoder/collector/graph/settings/completion/type-check/expression/find-usages/ECR
  command: passed in 5m02s.
- Final `./gradlew test`: passed in 5m44s.
- Final `./gradlew build`: passed, including searchable options, in 697ms.
- `git diff --check`: passed.
- `rg -n "FileTypeIndex|processFiles\\(" src/main || true`: no matches.
- General-suite scan for `CrystalStdlibVfsAccess`, `/usr/lib/crystal`, and `crystal env`: no matches in
  the four migrated test classes.

### Commits

- `475d773` `fix(analysis): match Crystal require semantics`
- The report append is recorded by the repository history containing this section.

## Final Release Gates at `1654c2d`

### Result

**DONE.** All four final release-gate findings were implemented in `aa02753`
(`fix(analysis): close require release gates`). No concerns remain.

### Closed Findings

- One `startsWith('.')` predicate now selects relative roots for exact and wildcard requires, including
  `.hidden`, `.hidden.cr`, `.hidden/*`, and `.hidden/**`. Candidate expansion order remains unchanged.
  Completion treats dot-prefixed names as real partial segments, exposes hidden entries only after an
  explicit dot prefix, and retains explicit `.cr` suffix behavior.
- Stdlib discovery now captures a project-scoped generation, effective SDK path, and discovery override
  frame before invoking an external process or test callback outside the lock. Cache clears, project-root
  changes, direct cache publication, and discovery override ownership changes advance the generation.
  Publication requires the generation, SDK identity, active frame identity, and project lifetime to
  remain current, so blocked, disposed, null, failed, or superseded SDK A work cannot overwrite SDK B.
- Escaped LF and CRLF continuations may consume only ASCII continuation whitespace through the collected
  content boundary immediately before the PSI-validated closing quote. Existing malformed CRLF, numeric,
  Unicode, interpolation, incomplete-string, and compound-string rejection remains covered.
- Wildcard traversal canonicalizes the project root and every pending directory before safety checks and
  child enumeration. Canonical directories equal to or containing the project root are skipped before
  reading children, including initial and nested symlink aliases. Canonical visited identities preserve
  cycle safety and prevent alias duplication; graph sources and invalidation cannot admit project files
  through an alias.

### TDD Evidence

#### RED

- The initial focused run produced 13 failures. Expected product failures covered dot-prefixed exact and
  wildcard resolution, hidden completion, LF/CRLF boundary decoding and collection, graph loading,
  disposed discovery publication, and SDK A/B settings publication.
- The first symlink fixtures reached `NoSuchFileException` rather than product behavior because they used
  a nonphysical fixture parent. They were moved to physical project paths without weakening assertions.
- The first implementation run exposed a test-order project-detection leak from a root-level Crystal
  fixture; the settings race fixture was nested and SDK B discovery was made explicit after apply.

#### GREEN

- Final focused path/decoder/collector/graph/settings/require-completion/general-completion/ECR command:
  passed in 5m19s.
- Final `./gradlew test`: passed in 5m09s.
- Final `./gradlew build`: passed, including searchable options, in 637ms.
- `git diff --check HEAD`: passed before the implementation commit.
- `rg -n 'FileTypeIndex|processFiles\\(|CRYSTAL DEBUG' src/main || true`: no matches.
- Path resolver and graph XML results report zero skipped tests. Initial-target, nested-target, and graph
  symlink tests all executed with zero failures, proving the host platform created the symlinks.

### Commits

- `aa02753` `fix(analysis): close require release gates`
- The report append is recorded by the repository history containing this section.

## Last Release-Gate Findings at `28e922d`

### Result

**DONE.** All three remaining findings were implemented in `7dc3388`
(`fix(analysis): close remaining release gates`) after the regression-test commit `ca090f3`
(`test(analysis): cover final release gate regressions`). No concerns remain.

### Closed Findings

- Existing wildcard targets now retain both the lexical target/watch identity and a canonical target
  identity. Structural create, delete, rename, and move paths match either identity, so direct and
  recursive wildcard owners rebuild immediately when an allowed symlink target is changed through its
  canonical external path. Unresolved targets retain their lexical intended path and nearest-existing-
  parent watch. Canonical identities equal to or containing the project root are omitted, and traversal
  still independently suppresses those directories before enumeration.
- Stdlib discovery delegates `CRYSTAL_PATH` selection to an internal pure helper and supplies
  `File.pathSeparatorChar` in production. The helper trims output and entries, skips blank and relative
  entries, preserves Windows drive-letter paths with `;`, and handles Unix paths with `:`. Unit tests
  call only the helper and launch no process.
- The recursive symbolic-link cycle test now uses the shared skip helper. Both resolver and graph helpers
  skip `UnsupportedOperationException`, concrete `AccessDeniedException`, recognized unsupported or
  permission `FileSystemException` reasons, and security-manager denials; unexpected filesystem failures
  continue to propagate.

### TDD Evidence

#### RED

- `ca090f3` added the tests first. The focused resolver/graph/SDK command failed compilation because
  `CrystalWildcardWatch.canonicalTargetPath` and `selectCrystalPathCandidate` did not exist.
- The first GREEN attempt compiled and passed SDK/resolver coverage, then both external-target graph tests
  failed in fixture setup with `NoSuchFileException`. Their alias owners were moved to the existing
  physical project-path fixture while canonical targets remained external temporary directories.

#### GREEN

- Focused resolver, graph, SDK, and wildcard-completion command: passed in 3m54s.
- Full `./gradlew test`: passed in 5m14s.
- Full `./gradlew build`: passed, including searchable options, in 699ms with cached tasks.
- `git diff --check 28e922d`: passed.
- Production forbidden scan for `FileTypeIndex`, `processFiles(`, `CRYSTAL DEBUG`, and hardcoded
  `split(":")`: no matches.
- SDK unit-test process scan found no `ProcessBuilder` or `crystal env` invocation. The only
  `/usr/lib/crystal` match is intentional pure Unix parsing input.

### Commits

- `ca090f3` `test(analysis): cover final release gate regressions`
- `7dc3388` `fix(analysis): close remaining release gates`
- The report append is recorded by the repository history containing this section.

## Adversarial Medium Findings at `36e401d`

### Result

**DONE.** All three medium findings were closed by `9191a1f`
(`fix(analysis): resolve canonical candidates and preludes`) after dedicated regression commits
`a41bfae` and `f4cf40e`. The pre-existing SDK library-provider fixture was aligned with the new
prelude requirement in `603639e`. No release-blocking concerns remain.

### Closed Findings

- Unresolved exact candidates and missing wildcard targets now canonicalize their longest existing VFS
  prefix and append every unresolved segment. Exact event ownership includes lexical and canonical paths;
  wildcard ownership retains the lexical nearest-parent watch plus the safe canonical intended target.
  Canonical-path fallback resolution makes external shard-cache create, delete, rename, and move events
  visible immediately. Recursive project-root canonical suppression remains unchanged.
- `CRYSTAL_PATH` discovery parses all ordered absolute candidates and selects the first candidate root or
  optional `src/` child that actually contains `prelude.cr`. A preceding custom root without the prelude
  no longer disables the core foundation or completion. Full participation of arbitrary custom roots in
  bare exact/wildcard requires and path completion is explicitly tracked in `TODO.md` with acceptance criteria.
- The pure path parser recognizes Unix absolute paths, Windows drive-letter paths, UNC paths, extended-length
  drive paths, and extended-length UNC paths. Production supplies `File.pathSeparatorChar` and real file
  predicates; pure tests inject existence checks and invoke no process.

### TDD Evidence

#### RED

- The initial focused SDK/resolver command failed test compilation because
  `selectCrystalPathPreludeRoot` and `parseAbsoluteCrystalPathCandidates` did not exist.
- After the first implementation pass, the new missing direct/recursive wildcard and exact shard lifecycle
  tests invalidated their owners but still failed effective-source membership. This exposed stale lexical
  symlink VFS children and led to canonical fallback resolution rather than a test-only refresh.

#### GREEN

- Focused resolver, graph, SDK, and completion matrix: passed in 3m42s.
- Final `./gradlew test`: 1,405 tests passed in 5m48s.
- Final `./gradlew build`: passed, including searchable options, in 754ms.
- `git diff --check 36e401d`: passed.
- Production forbidden scan for `FileTypeIndex`, `processFiles(`, `CRYSTAL DEBUG`, and hardcoded
  `split(":")`: no matches.
- Pure SDK parser process scan found no `ProcessBuilder` or `crystal env` invocation.

### Commits

- `a41bfae` `test(analysis): cover remaining adversarial findings`
- `f4cf40e` `test(completion): protect selected prelude root`
- `9191a1f` `fix(analysis): resolve canonical candidates and preludes`
- `603639e` `test(sdk): make library fixture a valid stdlib`
- The report append is recorded by the repository history containing this section.
