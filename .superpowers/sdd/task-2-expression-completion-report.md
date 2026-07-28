# Task 2 Expression Completion Report

## Scope

- Added PSI-based completion receiver location and classification.
- Added exact indexed type-object identity resolution and ordered value type sets.
- Added top-level union splitting and outer generic base normalization.
- Did not integrate contributor dispatch or candidate lookup across multiple types.

## TDD Evidence

- RED: `./gradlew test --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest"`
  failed during test compilation because `CompletionReceiver`,
  `CrystalCompletionReceiverResolver`, and `normalizeLookupTypes` did not exist.
- GREEN: the same focused command completed successfully after implementation.
- Regression: the focused suite plus `CrystalTypeInferenceTest` and
  `CrystalTypeCheckInspectionTest` completed successfully.
- Full suite: `./gradlew test` completed successfully.
- Hygiene: `git diff --check` produced no output.

## Self-Review

- Checked every Task 2 brief case against the fixture suite, including exact and ambiguous type
  identities, nested grouping, variables, literals, collections, operators, method and control-flow
  results, whitespace/newline location, generic/union normalization, and conservative negatives.
- Confirmed receiver discovery follows PSI relationships around `CrystalDotCallAccess` and does not
  reconstruct expressions from source-text regexes.
- Confirmed indexed lookup uses `CrystalIndexService.findTypes()` and qualified identities use
  `CrystalPsiUtils.buildQualifiedName`.
- Confirmed only Task 2 implementation, tests, changelog/spec documentation, and this report are
  intended for the commit. The pre-existing untracked plan remains untouched and uncommitted.

## Concerns

- No blocking concerns found.
- This task intentionally has no user-visible completion integration. Existing inference precision
  limits the value type sets available to the resolver until later tasks integrate and extend lookup.

## Commit

- Base: `e8c0950`
- Subject: `feat(completion): resolve expression receiver types`

## Review Fix Evidence

- RED: after adding seven review regression tests, running
  `./gradlew test --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest"`
  executed 18 tests and failed 7. The failures covered constructor prefixes, instance and chained
  method prefixes, qualified generic roots, typed-parameter unions, explicit method-return unions,
  and assignments nested inside control-flow receivers.
- PSI diagnosis: focused fixture output confirmed direct `CrystalDotCallAccess` sibling flattening
  and the alternate `CrystalImplicitObjectCall` nesting used for argumentless continuations. The
  temporary diagnostic output was removed before implementation completion.
- GREEN: the same focused resolver command executed all 18 tests successfully after the prefix and
  inference changes.
- Additional RED/GREEN: after temporarily removing the unannotated-body fallback, the focused
  `testInfersCompletedInstanceMethodResultFromBody` test failed with `Unknown`; restoring
  `inferReturnTypeFromBody` made the completed-call inference test pass. The final focused suite
  contains 19 tests.
- Adjacent GREEN: `./gradlew test --tests "de.magynhard.crystal.CrystalTypeInferenceTest" --tests
  "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest"` completed successfully.
- Review full-suite GREEN: `./gradlew test` completed successfully after all review fixes and
  documentation changes.

## Review Fix Interfaces

- Added `CrystalTypeInference.inferTypePreservingUnion(...)` for completion receiver variables.
- Added `CrystalTypeInference.inferReturnTypePreservingUnion(...)` for exact completed-call targets.
- Kept `CrystalTypeInference.inferType(...)` and its first-branch normalization unchanged for
  existing single-type consumers.
- `CrystalCompletionReceiverResolver.resolve(...)` retains its public package interface but now
  analyzes complete semantic postfix prefixes instead of requiring one standalone preceding PSI
  receiver.

## Review Fix Concerns

- Completed method calls intentionally return `Unknown` when a receiver identity or exact direct
  method target is ambiguous or unavailable. Contributor dispatch and multi-type candidate lookup
  remain outside Task 2.

## Second Re-Review Evidence

- RED: after adding long-chain, unsupported-tail, overload, assignment/control-flow, grouped-union,
  empty-name, and external/internal parameter regressions, running
  `./gradlew test --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest"
  --tests "de.magynhard.crystal.inspections.CrystalExpressionTypeResolverTest"` executed 30 tests
  and failed 11 for the reviewed behavior gaps. The single exact method candidate control passed.
- PSI diagnosis: focused fixture output showed nested source-ordered `CrystalImplicitObjectCall` and
  `CrystalDotCallAccess` components for three/four-step chains, and explicit bracket/list PSI for an
  unsupported index tail. Temporary diagnostic output was removed before implementation.
- Focused GREEN: the same receiver and expression-resolver command completed successfully after the
  fixes.
- Nested-grouping RED/GREEN: `Foo | (Bar | Baz)` initially remained one grouped lookup arm; the
  focused normalization test failed, then passed after recursive transparent-group normalization.
- Adjacent GREEN: `./gradlew test --tests
  "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest" --tests
  "de.magynhard.crystal.CrystalTypeInferenceTest" --tests
  "de.magynhard.crystal.inspections.CrystalExpressionTypeResolverTest" --tests
  "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest"` completed successfully.
- Full-suite GREEN: `./gradlew test` completed successfully.

## Second Re-Review Interfaces

- `CrystalExpressionTypeResolver.resolveType(...)` now treats `CrystalAssignment` as the type of its
  RHS and requires every reachable `if`, `case`, or ternary branch to resolve.
- `normalizeLookupTypes(...)` now removes balanced transparent outer grouping, preserves generic
  argument grouping, and omits empty normalized names.
- Completion-specific parameter inference now reads `PsiNameIdentifierOwner.name`, preserving the
  internal parameter name without changing call-site external-name behavior.
- Completed-call resolution still exposes no argument-aware overload API; the actionable follow-up
  is recorded in `TODO.md`.

## Approved Architecture Correction

### Rationale

- Removed the completion dependency on inspection-owned `CrystalExpressionTypeResolver` logic and
  legacy `CrystalTypeInference` file-wide/first-match behavior.
- Added a neutral ordered type-set model and one per-call `CrystalTypeResolutionSession` with PSI
  memoization, expression/method recursion guards, lexical flow, and cached StubIndex lookups.
- Variable flow walks only preceding PSI siblings and enclosing lexical scopes. It cannot consume
  later assignments or assignments from sibling methods, nested types, or other files.
- Exact method resolution is receiver/implicit-self specific. Top-level methods are considered only
  without an implicit-self target; ambiguous overloads and recursion remain `Unknown`.

### TDD Evidence

- RED: `./gradlew test --tests "de.magynhard.crystal.analysis.CrystalTypeSetResolverTest"`
  failed at test compilation because `CrystalTypeResolution`, `CrystalResolvedType`, and
  `CrystalTypeSetResolver` did not exist.
- First GREEN attempt executed 24 neutral tests with 23 passing; `a = b = 1` exposed missing nested
  assignment-chain evidence. Explicit chain traversal made all 24 pass.
- Migration RED: the combined neutral/completion/type-inference/expression suite exposed legacy
  file/declaration contexts, generic rendering, shorthand hash, numeric metadata, and nested postfix
  compatibility differences. Each was corrected in the adapters/neutral session without restoring
  project-wide fallback behavior.
- Adjacent GREEN: neutral resolver, completion receiver, type inference, expression facade,
  type-check inspection, and exact receiver suites completed successfully together.
- Full-suite RED/GREEN: the first `./gradlew test --rerun-tasks` executed 1,123 tests with one
  instance-variable completion failure. The fixture supplied `@apfel : Apfel` on the active class
  path; adding scoped property-declaration evidence and adapting the legacy cleaned `apfel` input
  fixed it without reading the sibling `initialize` assignment. The second fresh full run passed all
  1,123 tests.

### Changed APIs

- Added `CrystalResolvedType`, `CrystalTypeResolution.Known`, and `CrystalTypeResolution.Unknown`.
- Added `CrystalTypeSetResolver.resolve(...)`, `CrystalTypeSetResolver.session(...)`, and
  `CrystalTypeResolutionSession` scoped resolve/variable/call/method-return operations.
- Preserved `CompletionReceiver` and `CrystalCompletionReceiverResolver.resolve(position)`.
- Preserved `CrystalExpressionTypeResolver.ResolvedType?`, `CrystalTypeInference.inferType(...)`,
  and union-preserving string APIs as compatibility adapters.

### Commits

- `b5e054a refactor(analysis): centralize scoped type resolution`
- `6e6fb58 fix(analysis): preserve scoped instance declarations`
- Documentation subject: `docs(analysis): specify scoped type-set resolution`

### Concerns

- Argument-aware overload selection remains intentionally deferred; multiple exact candidates are
  `Unknown` and the existing actionable completion TODO remains open.
- Completion contributor dispatch and multi-type candidate lookup remain outside Task 2.
