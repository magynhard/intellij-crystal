# TODO — IntelliJ Crystal Plugin

## Rename Refactoring — Follow-up Tasks

The scope-aware rename infrastructure is in place (PsiNameIdentifierOwner on
CrystalVariableReference, CrystalParameter, CrystalAssignment; resolve() promotion
logic; CrystalRefactoringSupportProvider). These follow-up tasks complete the work:

- [x] **Fix resolveLocal() to find variable assignments** — now uses recursive
  `findAssignmentWithName()` that walks sibling subtrees to find `CrystalAssignment`
  composites. Stops at method/macro/class boundaries to prevent cross-scope resolution.

- [x] **Add rename tests for PsiNameIdentifierOwner composites** — 18 tests in
  `CrystalRenamePsiNameIdentifierOwnerTest` covering CrystalParameter, CrystalAssignment,
  CrystalVariableReference PsiNameIdentifierOwner implementation, resolve() promotion,
  and resolveLocal() scope boundary behavior.

- [x] **Update rename spec** — documented resolveLocal() fix (section 7), updated test
  matrix (section 9.2), added known limitations. See openspec/specs/rename-refactoring/spec.md.

- [x] **Fix handleElementRename() for INSTANCE_VAR/CLASS_VAR** — CrystalReference,
  CrystalInstanceVarReference, and all setName() mixins now handle @/@@ prefixed tokens
  and ensure the prefix is preserved during rename.

- [x] **Fix CrystalParameterMixin for instance var parameters** — getNameIdentifier()
  now recognizes INSTANCE_VAR_ACCESS composites (e.g. `def initialize(@x : Int32)`).

### Instance Variable Rename — Remaining Issues

The following scenarios are broken when renaming @variables:

- [ ] **Rename `@example` to `@other`** — typing a new name with `@` prefix in the
  rename dialog triggers "Inserted identifier is not valid". The `CrystalNamesValidator`
  rejects `@other` as an identifier because it starts with `@`. Need to make the
  validator accept `@`-prefixed names when renaming an instance variable.

- [ ] **Rename `my_var` to `@my_var`** (adding `@` prefix) — currently not possible
  because the rename target is a plain IDENTIFIER, and adding `@` would change the
  token type from IDENTIFIER to INSTANCE_VAR. The setName() / handleElementRename()
  creates a new token via PsiFileFactory, but the replacement may not produce the
  correct token type in all contexts.

- [ ] **Rename `@other_var` to `some_var`** (removing `@` prefix) — same issue in
  reverse. Removing `@` changes the token type from INSTANCE_VAR to IDENTIFIER.
  The replacement may fail or produce an incorrect AST.

### Root Cause Analysis

The core problem is that `CrystalNamesValidator.isIdentifier()` uses a simple
character check that rejects `@`-prefixed names. When the user types `@newname`
in the rename dialog, the validator says "not valid identifier" and the rename
is blocked before it even reaches `setName()`.

**Fix approach:**
1. Make `CrystalNamesValidator.isIdentifier()` accept `@`-prefixed names when
   the context is an instance variable rename (check if the target element is
   `CrystalInstanceVarAccess` or `CrystalParameter` with `instance_var_access`)
2. OR: Strip the `@` prefix in the validator and let `setName()`/`handleElementRename()`
   re-add it — but this requires passing context about what's being renamed

The `@`-prefix handling in `setName()` and `handleElementRename()` is already
correct (adds `@` if missing). The blocker is the validator rejecting the input.

## Type Inference (Issue #1)

- [ ] **Extend CrystalTypeInference for literal assignments** — currently only
  handles `Klasse.new`, `Klasse.method`, bare method_call. Add inference for
  literal assignments (`x = "hello"` → String, `x = 1` → Int32, `x = :sym` → Symbol).
- [ ] **Add array/hash/named-tuple literal inference** — `x = [1, 2]` → Array(Int32)
- [ ] **Add control-flow union inference** — `x = cond ? 1 : nil` → Int32?

## Inlay Hints (Issue #2)

- [ ] **Implement InlayHintsProvider** — show inferred types on variables inline
  in the editor. Depends on type inference (Issue #1).

## Crystal Shards (Issue #3)

- [ ] **Parse shard.yml** — extract dependency declarations
- [ ] **Index lib/ directory** — include shard sources in StubIndex
- [ ] **Dependency-aware completion** — suggest types/methods from installed shards

## Implement Members (Issue #5)

- [ ] **Discover abstract methods** from parent classes/modules
- [ ] **Generate implementing stubs** with correct method signatures
- [ ] **Register OverrideImplement action** in plugin.xml
