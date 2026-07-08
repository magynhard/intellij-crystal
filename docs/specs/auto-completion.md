# Auto-Completion Enhancement — Specification

## Goal

Improve Crystal autocompletion to provide scope-aware suggestions that match RubyMine's behavior: within a method/block, prioritize parameters, local variables, class methods, and block parameters over global methods.

## Current State

The existing `CrystalCompletionContributor` has a `addLocalVariablesAndParameters` method that:
- Collects parameters from the enclosing method ✅
- Collects ALL `CrystalAssignment` elements from the ENTIRE FILE (not scope-aware) ❌
- Does NOT handle block parameters ❌
- Does NOT handle `for` loop variables ❌
- Does NOT complete `@var` or `@@var` in free-text mode ❌
- Does NOT prioritize class methods over global methods ❌
- Does NOT handle inherited methods ❌

## Design

### Scope-Aware Local Variables

**Current behavior:** `PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)` scans ALL assignments in the entire file, showing variables from other methods/files.

**New behavior:** Determine the enclosing scope (method → block → file) and scan only within that scope:

```kotlin
fun findCompletionScope(element: PsiElement): PsiElement {
    return PsiTreeUtil.getParentOfType(element, CrystalMethodDefinition::class.java)
        ?: PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java)
        ?: element.containingFile
}
```

Crystal is method-scoped for local variables (like Ruby), so all assignments within a method are visible from anywhere in that method.

### Block Parameters

Crystal blocks use `do |param|` or `{ |param| }` syntax. Block parameters are block-scoped (only visible inside the block).

PSI structure:
```
block ::= DO [PIPE NLS parameter_list NLS PIPE] statement_list END
         | LBRACE [PIPE NLS parameter_list NLS PIPE] statement_list RBRACE
```

Block parameters are collected from `CrystalBlock.parameterList.parameterList` and added with highest priority (120).

### For-Loop Variables

PSI structure:
```
for_statement ::= FOR IDENTIFIER (COMMA IDENTIFIER)* IN expression statement_list END
```

The `FOR`-keyword is followed by one or more IDENTIFIER tokens before the `IN` keyword. These are collected as variables with priority 90.

### Instance and Class Variables in Free-Text

`CrystalAssignment` supports three variable types:
- `IDENTIFIER` (local variable) — priority 50
- `INSTANCE_VAR` (`@var`) — priority 40
- `CLASS_VAR` (`@@var`) — priority 40

All three are collected from scope-aware assignments.

### Class Method Prioritization

When the cursor is inside a method, methods of the enclosing class (via `CrystalMethodByClassIndex`) are added with higher priority than global methods:
- Own class methods: priority 30
- Inherited methods (direct superclass): priority 20
- Global methods: priority 0 (default)

### Inherited Methods

For classes with superclass clauses (`class Foo < Bar`), the direct parent class is identified from `CrystalClassDefinition.superclassClause`. Methods from the parent are added with priority 20 via `CrystalMethodByClassIndex`. Only the direct superclass is queried (no hierarchy traversal for performance).

## Priority Schema

| Item | Priority | Icon | Bold |
|------|----------|------|------|
| Block parameters | 120 | Parameter | ✓ |
| Method parameters | 100 | Parameter | ✓ |
| For-loop variables | 90 | Variable | ✓ |
| Local variables | 50 | Variable | ✓ |
| Instance/class variables | 40 | Variable | ✓ |
| Own class methods | 30 | Method | ✗ |
| Inherited class methods | 20 | Method | ✗ |
| Global methods | 0 | Method | ✗ |

## Files Changed

| File | Change |
|---|---|
| `CrystalCompletionContributor.kt` | Rename `addLocalVariablesAndParameters` → `addLocalCompletions`. Add: scope filtering, block parameters, for variables, @var/@@var, class method priority, inherited methods |
| `CrystalCompletionTest.kt` | Add ~6 new tests: block params, for vars, scope awareness, @var free-text, class method priority, inherited methods |

## Tests

- `testBlockParameterCompletion` — `do |x, y|` inside block → `x`, `y` are suggestions
- `testForVariableCompletion` — `for item in list` → `item` is suggestion
- `testScopeAwareLocalVariables` — variable from OTHER method in same file is NOT shown
- `testInstanceVarFreeTextCompletion` — `@name = "foo"` then typing `@` → `@name` suggested
- `testClassMethodPriority` — own class methods appear before global methods in list
- `testInheritedMethodCompletion` — `class Foo < Bar` → methods from `Bar` appear in `Foo`

## Constraints

- All changes are in the existing `addLocalVariablesAndParameters` method — no new files needed.
- The existing `addAllMethods`, `addAllClasses`, `addStdlibTypes` methods remain unchanged.
- The existing `addAllMethods` continues to add global methods with default priority (0).
- Class method priority (30) is applied on top — no global methods are removed.
- Performance: `CrystalMethodByClassIndex` is O(1); `PsiTreeUtil.findChildrenOfType` is bounded by scope element.
