# TODO Implementation Planning

## Purpose

Every open `TODO.md` item must have an implementation plan before its
implementation starts. Planning turns a deferred idea into an evidence-backed,
bounded change and exposes uncertainty before code, generated files, or tests
are modified.

This document defines the workflow. `AGENTS.md` remains authoritative for
repository invariants, implementation rules, and verification requirements.

## Plan Storage And Lifecycle

1. Create one local plan at `docs/todos/<kebab-case-todo-title>.md` before
   starting implementation work.
2. `docs/todos/` is intentionally ignored by Git. Plans are working artifacts,
   not shared architecture documentation or commit content.
3. Create the directory locally when it is absent in a fresh checkout.
4. Keep the plan current while the task is in progress.
5. Promote durable behavior contracts and architecture decisions to the
   relevant tracked document under `docs/specs/`.
6. Remove the local plan after the implementation is validated and its TODO is
   removed. Git history, tests, and specifications retain the lasting record.

## Implementation Gate

1. Do not change implementation code for a TODO until its local plan exists.
2. Present the plan for review and wait for an explicit `go` before starting
   implementation.
3. An implementation request alone does not bypass this planning gate.
4. A stale, blocked, or documentation-only TODO still needs a short plan, but
   it may be a verification-and-cleanup plan instead of an implementation
   design.

## Required Investigation

Before writing the plan:

1. Inspect the relevant TODO, specification, implementation, tests, and
   applicable repository skill.
2. Inspect the worktree and existing diffs so user-owned changes are not
   overwritten or included accidentally.
3. Reproduce the behavior through the smallest suitable source: an existing
   test, focused fixture, compiler invocation, or PSI/lexer evidence.
4. Record uncertainty explicitly. Do not turn an unverified TODO premise into
   an implementation requirement.

## Required Plan Content

Each plan must use the following structure. Sections may be concise, but no
section may be omitted.

```md
# <TODO title>

## Status
Planned | Blocked | Verification only

## Goal
<Observable desired behavior>

## Evidence
- <Relevant implementation, specification, or test>
- <Minimal reproduction or compiler behavior, when applicable>

## Scope
- <Included behavior>
- <Explicit non-goal>

## Implementation
1. <Concrete file and API change>
2. <Concrete file and API change>

## Invariants And Risks
- <Repository constraint and failure mode>

## Validation
- <Focused tests>
- <Generation, audit, or full suite when required>

## Follow-Through
- <Specification, README, CHANGELOG, and TODO updates>
```

## Quality Standard

1. Base every implementation claim on code, tests, specifications, compiler
   behavior, or a reproducible experiment.
2. Prefer the smallest complete solution. State why broader refactoring is
   required when it is unavoidable.
3. Name concrete files, APIs, PSI elements, index keys, or grammar rules; avoid
   plans that only say to "update the resolver" or "fix parsing".
4. State explicit non-goals so related work is not silently absorbed.
5. For parser or lexer work, identify BNF/flex sources, regeneration commands,
   golden fixtures, and `PsiErrorElement` checks.
6. For stub or index work, identify serialization changes, the stub-version
   impact, registrations, scope/require visibility, and stub-to-PSI tests.
7. For resolution work, preserve exact receiver-aware behavior. Unknown,
   ambiguous, incomplete, or suppressed receivers must not gain name-only
   fallbacks.
8. For caches, listeners, and performance work, measure first and record the
   decision criteria before proposing an implementation.
9. For platform limitations, write a verification or decision plan rather than
   a speculative workaround.

## Completion

Before closing the TODO:

1. Run the focused validation named by the plan and the broader checks required
   by `AGENTS.md`.
2. Inspect the final diff and report failed, skipped, or inconclusive checks
   honestly.
3. Update durable specifications, user documentation, and CHANGELOG entries
   when the repository policy requires them.
4. Remove the completed TODO entry and its local plan.
