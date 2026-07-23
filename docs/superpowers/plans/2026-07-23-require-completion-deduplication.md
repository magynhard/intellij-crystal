# Require Completion Deduplication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show exactly one method-style `require(path)` completion at valid file-scope statement positions while preserving special path insertion and normal receiver method completion.

**Architecture:** The synthesized statement lookup remains the canonical bare `require` candidate because Crystal does not define a stdlib method for it. Method stubs record whether the definition has a `self` receiver so such definitions cannot leak into the top-level method index; local completion also reserves the bare `require` name for the statement provider.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2026.1, StubIndex, JUnit 4 platform fixtures, Gradle 9.4.1, JDK 21.

## Global Constraints

- Work directly in the current `master` checkout as explicitly requested by the user.
- Use `CrystalTypes` as the single source of truth for generated token and element types.
- Never scan project Crystal files at runtime; use StubIndex lookups only.
- Preserve ordinary DOT completion and insertion for real methods such as `Loader.require`.
- Every implementation change must have regression coverage.
- Update `docs/specs/require.md` and `CHANGELOG.md`.
- Run `./gradlew test` before committing.
- The user's `go` explicitly authorizes the final commit.

---

### Task 1: Exclude Self-Receiver Methods From The Top-Level Index

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/psi/CrystalPsiUtils.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/stubs/CrystalStubs.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/stubs/CrystalStubElementTypes.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/CrystalParserDefinition.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalLocalCompletionProvider.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/stubs/CrystalIndexServiceTest.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt`

**Interfaces:**
- Produces: `CrystalPsiUtils.isSelfMethod(method: CrystalMethodDefinition): Boolean`.
- Produces: `CrystalMethodDefinitionStub.isSelfMethod: Boolean` serialized in method stubs.
- Preserves: class-scoped method indexing and DOT completion.

- [ ] Add failing index and completion tests for a file-level `def self.require(path)`.
- [ ] Run the focused tests and confirm the self-receiver method is incorrectly returned as top-level.
- [ ] Persist the direct `SELF` header-token state in `CrystalMethodDefinitionStub`.
- [ ] Exclude self-receiver stubs from `CrystalTopLevelMethodIndex` and current-file top-level collection.
- [ ] Increase the Crystal file stub version from `2` to `3`.
- [ ] Run the focused tests and confirm they pass.

### Task 2: Provide One Canonical Method-Style Require Lookup

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalRequireCompletionProvider.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalLocalCompletionProvider.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalRequireCompletionTest.kt`
- Modify: `docs/specs/require.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Produces: one synthesized bare lookup with lookup string `require`, method icon, tail text `(path)`, no `keyword` type text, and the existing require insert handler.
- Preserves: `require "<caret>"` insertion, automatic path popup, context restrictions, and normal `Loader.require` insertion.

- [ ] Add failing tests asserting exactly one bare `require` candidate with method presentation even when an indexed same-name method exists.
- [ ] Run `CrystalRequireCompletionTest` and confirm the presentation/count assertions fail.
- [ ] Rename keyword-oriented lookup APIs to statement-oriented names and apply the method presentation.
- [ ] Reserve bare `require` in local completion so indexed methods cannot duplicate the canonical statement lookup.
- [ ] Update the behavioral specification and current changelog entry.
- [ ] Run focused completion tests and confirm they pass.
- [ ] Run `./gradlew test` and confirm the full suite passes.
- [ ] Review the complete diff and commit the approved changes.
