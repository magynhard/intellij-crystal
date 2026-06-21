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
