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

- [x] **Rename `@example` to `@other`** — `CrystalNamesValidator.isIdentifier()` now
  accepts `@`-prefixed names. The `@` prefix is automatically added by `setName()` and
  `handleElementRename()` when the user types just the bare name (e.g. `other`).

- [ ] **Rename `@some` to `@come` (typing `@` prefix)** — mixed result: some
  occurrences become `@@come`, others `@come`. The rename framework passes the
  name with `@` prefix to some handlers and without to others. Our `fixedName`
  logic adds `@` when missing, but doesn't strip it when present, causing
  inconsistency. All occurrences should become `@come`.

- [ ] **Rename `@@barta` to `@@cool` (typing `@@` prefix)** — some occurrences
  lose the `@@` prefix and become just `@cool`. The rename framework strips the
  `@@` prefix before calling `handleElementRename`, and our code adds back only
  a single `@`. All occurrences should become `@@cool`.

- [ ] **Rename `my_var` to `@my_var`** (adding `@` prefix) — currently not possible
  because the rename target is a plain IDENTIFIER, and adding `@` would change the
  token type from IDENTIFIER to INSTANCE_VAR. The setName() / handleElementRename()
  creates a new token via createLeafFromText(), but the replacement may not produce
  the correct token type in all contexts.

- [ ] **Rename `@other_var` to `some_var`** (removing `@` prefix) — same issue in
  reverse. Removing `@` changes the token type from INSTANCE_VAR to IDENTIFIER.
  The replacement may fail or produce an incorrect AST.

### Root Cause Analysis

~~The core problem is that `CrystalNamesValidator.isIdentifier()` uses a simple~~
~~character check that rejects `@`-prefixed names.~~ **Fixed**: validator now
accepts `@`/`@@`-prefixed identifiers.

~~The remaining issues (token type changes when adding/removing `@`) are deep~~
~~structural problems~~ **Fixed**: `createLeafFromText()` helper now properly walks
the parsed PSI tree to find the correct leaf token, instead of using `firstChildNode`
which returned wrapper composites (statement/expression_statement). This was causing
`@@` double-prefix corruption when renaming `@sample` to `@pump`.

**Remaining issue**: The rename framework inconsistently passes the new name
with or without the `@`/`@@` prefix to `setName()` vs `handleElementRename()`.
The `fixedName` logic that adds `@` when missing works when the name is passed
without prefix, but causes double-prefix when the name already includes `@`.
The framework's behavior varies depending on which reference is being renamed
(definition site vs usage site). Need to investigate the exact rename flow to
determine whether to always strip the prefix and re-add it, or to detect the
framework's convention.

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
