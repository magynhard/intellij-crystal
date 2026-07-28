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
