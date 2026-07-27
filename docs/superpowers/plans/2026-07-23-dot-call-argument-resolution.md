# DOT-Call Argument Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate argument counts consistently for constructor and instance DOT-calls in every Crystal call syntax when the receiver type can be proven exactly.

**Architecture:** Introduce a strict receiver-type resolver and a shared DOT-call target resolver. The argument-count inspection remains responsible for argument extraction, overload evaluation, and diagnostics, while target resolution owns receiver identity, hierarchy traversal, static/instance filtering, and constructor precedence.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2026.1, PSI, StubIndex, JUnit 4 platform fixtures, Gradle 9.4.1, JDK 21.

## Global Constraints

- Work directly in the current `master` checkout as explicitly requested previously.
- Use StubIndex APIs only for runtime project lookups; never scan project files through `FileTypeIndex`.
- Preserve exact qualified type identity and suppress unknown or ambiguous targets.
- Support `Foos.new`, `Foos.new()`, `Foos.new value`, `a.first`, `a.first()`, and `a.first value` through one target-resolution path.
- Constructor precedence is applicable `def self.new` overloads, otherwise applicable `initialize` overloads, otherwise an implicit argumentless constructor.
- Records remain on the existing separate path.
- Accept only exact local, typed-parameter, or instance-variable receiver types; suppress union, nilable, conflicting, unknown, and class-variable receivers.
- Include inherited methods and initializers while deduplicating overridden identical signatures in favor of the nearest definition.
- Static and instance methods never satisfy each other's calls.
- Preserve `ProblemHighlightType.GENERIC_ERROR` and innermost method-name highlighting.
- No serialized stub format or index emission semantics change; do not increment the stub version.
- Update the relevant behavioral spec, TODO, CHANGELOG, and project AGENTS rules.
- Every implementation change requires regression coverage and strict RED/GREEN evidence.
- Run `./gradlew test --rerun-tasks` before completion.
- The user's `go` authorizes implementation commits.

---

### Task 1: Document The Expanded Contract

**Files:**
- Modify: `docs/specs/call-argument-inspections.md`
- Modify: `TODO.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Produces: normative constructor, receiver inference, hierarchy, suppression, and all-call-syntax requirements consumed by later tasks.
- Produces: a project rule requiring every deferred or unresolved topic to be recorded in `TODO.md`.

- [ ] Extend `docs/specs/call-argument-inspections.md` so all DOT-call forms share exact receiver and overload semantics.
- [ ] Document constructor precedence: exact inherited/applicable `def self.new`, otherwise exact inherited/applicable `initialize`, otherwise implicit zero-argument construction.
- [ ] Document exact receiver sources: nearest resolvable local assignment, typed parameters, typed instance variables, and consistent instance-variable assignments.
- [ ] Document suppression for unknown, ambiguous, union, nilable, conflicting, class-variable, record, and macro-interpolated targets.
- [ ] Update the verification matrix with constructor, instance receiver, inheritance, overload, reassignment, suppression, and single-ownership cases.
- [ ] Rewrite the existing argumentless-applicability TODO to retain only unsupported unqualified/inexact behavior.
- [ ] Add concrete TODO entries for records through the shared resolver, class variables, union/nil narrowing, and migration of type checking/navigation/completion/parameter info.
- [ ] Add this rule under `AGENTS.md` Documentation:

```markdown
- **`TODO.md` tracks all deferred or unresolved work.** Whenever a task intentionally leaves behavior unsupported, discovers a follow-up, or defers part of the approved scope, add a concrete actionable entry to `TODO.md` before completing the task.
```

- [ ] Self-review the documents for contradictions, placeholders, and stale exclusions.
- [ ] Inspect status/diff/log and commit only these documentation files:

```bash
git commit -m "docs(inspections): specify resolved DOT-call validation"
```

### Task 2: Resolve Exact Instance Receiver Types

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolver.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolverTest.kt`

**Interfaces:**
- Produces: `CrystalExactReceiverTypeResolver.resolve(receiver: PsiElement, call: CrystalDotCallAccess): ExactReceiverType?`.
- Produces: `ExactReceiverType(simpleName: String, qualifiedName: String)` only when one concrete non-union, non-nilable type identity is proven.
- Consumes: `CrystalIndexService.findTypes`, `CrystalPsiUtils.buildQualifiedName`, local PSI assignments, parameter annotations, property declarations, and enclosing-type-bounded instance-variable assignments.

- [ ] Add failing tests for a local receiver from the nearest preceding `a = Foo.new` assignment, including qualified constructors.
- [ ] Add failing tests for exact typed method/block parameters and their internal parameter names.
- [ ] Add failing tests for explicitly typed `@instance_variables` and consistent `@value = Foo.new` assignments within the enclosing type.
- [ ] Add failing suppression tests for unknown variables, nilable/union annotations, conflicting instance-variable assignments, class variables, conditional ambiguity, and a nearest unresolvable reassignment after an older valid assignment.
- [ ] Add scope-boundary tests proving assignments in later statements, sibling methods, nested types, and unrelated files do not determine the receiver.
- [ ] Run the focused test class and verify RED:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalExactReceiverTypeResolverTest"
```

- [ ] Implement strict nearest-assignment resolution. A nearest unsupported assignment returns unknown and must never fall back to an older type.
- [ ] Resolve constructor expressions from PSI (`CrystalDotCallAccess` plus exact constant receiver), not a project-wide name fallback.
- [ ] Resolve annotations only when their syntax denotes one concrete non-nilable, non-union type.
- [ ] Resolve instance variables from an explicit exact annotation first, otherwise require every relevant assignment in the current type to agree on one exact type.
- [ ] Resolve the resulting simple name through `CrystalClassIndex` and retain only one exact qualified identity.
- [ ] Run focused tests and verify GREEN.
- [ ] Inspect status/diff/log and commit:

```bash
git commit -m "feat(inspections): resolve exact DOT receiver types"
```

### Task 3: Resolve DOT-Call Targets And Constructors

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolver.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolverTest.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalCallExtractor.kt`
- Modify only if needed for reusable hierarchy metadata: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt`

**Interfaces:**
- Produces: `CrystalDotCallTargetResolver.resolve(access: CrystalDotCallAccess): DotCallResolution`.
- Produces sealed results for exact method overloads, implicit zero-argument constructor, existing-record fallback, and unresolved/suppressed calls.
- Consumes: `CrystalExactReceiverTypeResolver`, exact constant receiver extraction, `CrystalIndexService.findMethodsByClass`, `CrystalPsiUtils.isSelfMethod`, and qualified enclosing type names.

- [ ] Add failing tests for direct, qualified, absolute, and lexically qualified constant receivers.
- [ ] Add failing tests for local, typed-parameter, and instance-variable receivers.
- [ ] Add failing tests proving static and instance methods are separated and unrelated same-name types/methods never contribute candidates.
- [ ] Add failing inheritance tests for instance methods, `def self.new`, and `initialize`.
- [ ] Add overload tests where one overload accepts the call and where one uniquely closest overload supplies missing parameters.
- [ ] Add constructor-priority tests: explicit `self.new` overrides a zero-argument initializer; without `self.new`, all initializer overloads apply; without either, construction accepts zero arguments.
- [ ] Add class and struct tests; return the record fallback result without migrating record resolution.
- [ ] Add override deduplication tests where the nearest identical signature wins while distinct inherited signatures remain overloads.
- [ ] Run the focused resolver tests and verify RED:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalDotCallTargetResolverTest"
```

- [ ] Generalize `CrystalCallExtractor` to identify the owning `CrystalDotCallAccess`, receiver PSI, method-name leaf, and all argument-holder forms without resolving targets.
- [ ] Implement exact hierarchy traversal through classes, structs, included modules, and extended modules using StubIndex-backed type identity filtering.
- [ ] Implement instance method candidate collection with `isSelfMethod == false` and static/constructor collection with `isSelfMethod == true`.
- [ ] Deduplicate candidates by method name plus parameter signature in nearest-first hierarchy order.
- [ ] Implement constructor precedence and explicit implicit-constructor result.
- [ ] Return unresolved for macro interpolation, unknown/ambiguous receiver types, union/nil receiver types, and unsupported class variables.
- [ ] Run focused tests and verify GREEN.
- [ ] Inspect status/diff/log and commit:

```bash
git commit -m "feat(inspections): resolve DOT-call targets"
```

### Task 4: Migrate Argument Count Validation To The Shared Resolver

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspection.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalDotCallTargetResolver.resolve()` for every parenthesized, bare-argument, and argumentless DOT-call.
- Preserves: existing `checkArgumentCount()`, argument extraction, record validation, splat handling, overload ranking, and diagnostic formatting.

- [ ] Replace the prior argumentless-constant-only regression with `Foo.new` missing-initialize coverage.
- [ ] Add all three constructor forms with required/default/nilable parameters: `Foo.new`, `Foo.new()`, and `Foo.new value`.
- [ ] Add all three instance forms: `a.first`, `a.first()`, and `a.first value`.
- [ ] Add integration tests for typed parameters, typed/assigned instance variables, inherited methods/initializers, overload acceptance, explicit `self.new` priority, structs, and implicit constructors.
- [ ] Add suppression tests for unknown, union, nilable, conflicting, reassigned, class-variable, record, ambiguous, and unrelated receivers.
- [ ] Add ownership tests proving every syntactic call site emits at most one diagnostic.
- [ ] Run the inspection tests and verify RED:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalArgumentCountInspectionTest"
```

- [ ] Route argument-holder visitors to their owning `CrystalDotCallAccess` and extract arguments once.
- [ ] Visit `CrystalDotCallAccess` directly only when it has no argument holder.
- [ ] For exact method/constructor overload results, call existing `checkArgumentCount()` with extracted arguments.
- [ ] Treat implicit constructors as accepting only zero arguments; preserve current excess-argument behavior when appropriate.
- [ ] Route record results through the unchanged existing record logic.
- [ ] Remove obsolete broad name-only DOT method lookup and constructor-first-or-null branches.
- [ ] Run focused inspection and resolver tests and verify GREEN.
- [ ] Run the full suite once before committing this integration task.
- [ ] Inspect status/diff/log and commit:

```bash
git commit -m "fix(inspections): validate resolved DOT calls"
```

### Task 5: Document And Verify The Complete Change

**Files:**
- Modify: `CHANGELOG.md`
- Verify: `docs/specs/call-argument-inspections.md`
- Verify: `TODO.md`
- Include: `docs/superpowers/plans/2026-07-23-dot-call-argument-resolution.md`

**Interfaces:**
- Consumes: completed resolver and inspection behavior.
- Produces: release documentation and final verification evidence.

- [ ] Add a current-version changelog bullet describing consistent constructor and exact inferred-instance argument-count checks across all call syntaxes.
- [ ] Confirm every intentionally unsupported case has a concrete `TODO.md` entry per the new `AGENTS.md` rule.
- [ ] Confirm no generated parser/lexer source, serialized stub field, index emission, or file stub version changed.
- [ ] Run focused tests freshly:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalExactReceiverTypeResolverTest" --tests "de.magynhard.crystal.inspections.CrystalDotCallTargetResolverTest" --tests "de.magynhard.crystal.inspections.CrystalArgumentCountInspectionTest" --rerun-tasks
```

- [ ] Run the full suite freshly:

```bash
./gradlew test --rerun-tasks
```

- [ ] Run `git status --short`, `git diff --check`, inspect the complete diff from the starting commit, and review `git log --oneline -10`.
- [ ] Stage only changelog and plan if not already tracked, then commit:

```bash
git commit -m "docs(inspections): document resolved DOT-call checks"
```
