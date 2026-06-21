# TODO — IntelliJ Crystal Plugin

## Rename Refactoring — Follow-up Tasks

The scope-aware rename infrastructure is in place (PsiNameIdentifierOwner on
CrystalVariableReference, CrystalParameter, CrystalAssignment; resolve() promotion
logic; CrystalRefactoringSupportProvider). These follow-up tasks complete the work:

- [ ] **Fix resolveLocal() to find variable assignments** — currently only finds
  parameters (via CrystalMethodDefinition check). Variable assignments like
  `sas = Senf.new` are not found because the sibling-walking logic checks
  `sibling.firstChild` which is an `expression_statement` composite, not the
  IDENTIFIER leaf. Need to walk into siblings to find CrystalAssignment composites
  with matching IDENTIFIER children.

- [ ] **Add rename tests for PsiNameIdentifierOwner composites** — test that
  CrystalParameter, CrystalAssignment, and CrystalVariableReference correctly
  implement getNameIdentifier/getName/setName, and that resolve() returns the
  composite (not the leaf) for elements inside these containers.

- [ ] **Update rename spec** — document the resolveLocal() limitation and the
  planned fix. Track in openspec/specs/rename-refactoring/spec.md.

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
