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
