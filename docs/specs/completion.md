# Completion

Specs to define the behaviour of code completion in the Crystal plugin.

## General Behaviours

### Triggering
- `.` after CONSTANT → auto-popup with static methods (platform default)
- `.` after identifier → auto-popup with instance methods (platform default)
- `::` after CONSTANT → auto-popup with nested types (via `CrystalTypedHandler.checkAutoPopup`)

### Disambiguation
Completion respects namespace hierarchy. When multiple classes share the same simple
name (e.g. `Foo::Sub` and `Bar::Sub`), only methods/types from the correct enclosing
class are suggested.

---

## Specific Behaviours

### Type Completion (`Foo::<caret>`)
Shows only types nested inside `Foo` via `CrystalClassByEnclosingIndex`. Stdlib types
are shown as baseline, but nested types are filtered by the enclosing class name.

### Method Completion (`Foo::Sub.<caret>`)
Shows only methods from `Foo::Sub` (not from `Bar::Sub`). The completion contributor
detects `CrystalNamespaceAccess` before the dot, builds the full path via
`buildNamespacePath`, and filters `CrystalMethodByClassIndex` results by comparing
each method's enclosing class qualified name against the expected path.

### Static Method Completion (`CONSTANT.<caret>`)
Shows all static methods of the given class via `CrystalMethodByClassIndex`.

### Instance Method Completion (`variable.<caret>`)
Shows instance methods based on type inference via `CrystalTypeInference`. If the
type is unknown, all project methods are shown as fallback.

### Free-text Completion
When no specific context is detected, shows:
- All types (stdlib + project)
- All methods
- Local variables and parameters

---

## Edge Cases

- **`::` auto-popup timing:** `checkAutoPopup` is called BEFORE the character is
  inserted. The check must look at the character at `offset - 1` (before caret),
  not `offset - 2`, to detect the first `:` when the second is being typed.
- **`Foo::<caret>` vs `Foo::<caret>Bar`:** Both show nested types, but the
  latter filters by prefix `Bar`.
- **`Foo::Sub.<caret>` with ambiguous `Sub`:** Only methods from the correct
  `Foo::Sub` are shown, not from `Bar::Sub`.
