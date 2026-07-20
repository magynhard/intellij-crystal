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

The `@`/`@@` prefix is always preserved from the original token type. The user
only types the bare name. The prefix is never changed during rename.

| Scenario | User types | Result | Status |
|----------|-----------|--------|--------|
| `@var` → `foo` | `foo` | `@foo` | ✅ Works |
| `@var` → `@foo` | `@foo` | `@foo` | ✅ Works |
| `@@var` → `cool` | `cool` | `@@cool` | ✅ Works |
| `@@var` → `@cool` | `@cool` | `@@cool` | ✅ Works |
| `my_var` → `@my_var` | — | Not supported | N/A (different types) |
| `@var` → `var` | — | Not supported | N/A (different types) |

Type changes (`IDENTIFIER` ↔ `INSTANCE_VAR` ↔ `CLASS_VAR`) are intentionally
not supported — they are fundamentally different variable types.

### Root Cause Analysis

~~The core problem is that `CrystalNamesValidator.isIdentifier()` uses a simple~~
~~character check that rejects `@`-prefixed names.~~ **Fixed**: validator now
accepts `@`/`@@`-prefixed identifiers.

~~The remaining issues (token type changes when adding/removing `@`) are deep~~
~~structural problems~~ **Fixed**: `createLeafFromText()` helper now properly walks
the parsed PSI tree to find the correct leaf token, instead of using `firstChildNode`
which returned wrapper composites (statement/expression_statement).

**Fixed**: All `setName()` and `handleElementRename()` methods now always strip
any `@`/`@@` prefix from the user input and re-apply it from the original token
type. This ensures consistent behavior regardless of what the user types.

## Type Inference (Issue #1)

- [x] **Extend CrystalTypeInference for literal assignments** — currently only
  handles `Klasse.new`, `Klasse.method`, bare method_call. Add inference for
  literal assignments (`x = "hello"` → String, `x = 1` → Int32, `x = :sym` → Symbol).
- [x] **Add array/hash/named-tuple literal inference** — `x = [1, 2]` → Array(Int32)
- [x] **Add control-flow union inference** — `x = cond ? 1 : nil` → Int32?

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

## Indexed Declaration Follow-up

- [ ] **Add constant declaration stubs and indexes** (`CrystalConstantIndex` and `CrystalConstantByClassIndex`) only after the grammar separates constant definitions from ordinary statement assignment contexts.
- [ ] **Design instance/class-variable declaration indexing** only if a valid stubbed declaration model can represent declarations without indexing arbitrary usages or assignments.

## Type Resolution Unification

- [ ] **Unify type resolution ownership** by replacing the dependency cycle between `CrystalTypeInference` and `CrystalExpressionTypeResolver` with one coherent design. Do not add the rejected delegation-only `CrystalTypeResolver` facade; a facade without unified ownership would preserve the cycle rather than resolve it.
