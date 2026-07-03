# Value Assigned to Variable Never Used

Spec for the `CrystalUnusedVariableInspection` — specifically the "Value assigned to variable is never used" warning.

## Overview

The inspection warns when a value assigned to a local variable is never read before the variable is reassigned or goes out of scope. It is aware of Crystal's control flow — assignments inside conditional branches (which may not execute) do NOT count as overwriting the previous value.

## Algorithm

1. Collect all local variable assignments and references in scope.
2. For each assignment `A`, find the next assignment `B` to the same variable (by offset).
3. If the next assignment `B` is inside a conditional branch:
   - Extend the search range to the next unconditional assignment `C` (or end of scope if none exists).
   - Check for reads between `A` and `C` (instead of between `A` and `B`).
4. If no read is found in the search range → report "Value assigned to 'x' is never used".

## Conditional Constructs

A construct is considered *conditional* when its body may not execute at runtime. The following PSI element types are recognized:

| Construct | Conditional? | Reason |
|-----------|-------------|--------|
| `CrystalIfStatement` (with or without else/elsif) | Yes | `if` body only executes when condition is truthy |
| `CrystalUnlessStatement` | Yes | Body only executes when condition is falsy |
| `CrystalWhileStatement` | Yes | Body may not execute if condition is initially falsy |
| `CrystalUntilStatement` | Yes | Body may not execute if condition is initially truthy |
| `CrystalForStatement` | Yes | Body may not execute if the collection is empty |
| `CrystalCaseStatement` | Yes | Only executes the matching `when` branch (or none) |
| `CrystalSelectStatement` | Yes | Only executes the matching `when` branch (or none) |
| `CrystalBeginStatement` with rescue clauses | Yes | The body after `rescue` only executes if an exception is raised |
| `CrystalBeginStatement` without rescue | No | Plain `begin...end` always executes its body |

### Purpose of the Rules

An assignment is considered "used" if its value can possibly be read by subsequent code. When the next assignment to the same variable is inside a conditional branch, the branch may not execute — so the previous assignment's value could still flow through. Therefore, the search for reads must extend past conditional boundaries until the next unconditional overwrite (or end of scope).

## Assignment Lifecycle Rules

- **Linear consecutive assignments:** Only check for reads between `A` and the next assignment `B`. If `B` always executes, `A`'s value is dead.
- **Conditional next assignment:** Extend the read-search range to the next unconditional assignment (or end of scope).
- **Compound assignments** (`x += 1`, `x ||= true`) count as both a read of the current value and a write — they never trigger the warning by themselves.
- **Any read** (not just direct references — also reads via compound assignment) suppresses the warning for preceding assignments.

## Examples: Correct Behavior (No Warning Expected)

Unless otherwise stated, no warning is expected on the first assignment.

### `if` without else

```crystal
x = ""
if condition
  x = "overridden"
end
puts x
```

### `unless` without else

```crystal
x = ""
unless condition
  x = "overridden"
end
puts x
```

### `begin` with `rescue`

```crystal
x = ""
begin
  x = compute_something
rescue
end
puts x
```

### `while` loop (0 iterations possible)

```crystal
x = ""
while condition
  x = "from_loop"
end
puts x
```

### `until` loop (0 iterations possible)

```crystal
x = ""
until done
  x = "from_loop"
end
puts x
```

### `for` loop on potentially empty collection

```crystal
x = ""
for item in items
  x = item
end
puts x
```

### `case` without else

```crystal
x = ""
case value
when 1
  x = "one"
when 2
  x = "two"
end
puts x
```

### `select` without else

```crystal
x = ""
select
when signal.ready?
  x = signal.receive
end
puts x
```

### Nested conditionals

```crystal
x = ""
if outer
  if inner
    x = "deep"
  end
end
puts x
```

### `elsif` chain without else

```crystal
x = ""
if a == 1
  x = "one"
elsif a == 2
  x = "two"
end
puts x
```

## Examples: Should Still Warn

### Linear reassignment

```crystal
x = 1      # ← warning: value never used
x = 2
puts x
```

### `begin` without rescue (always executes)

```crystal
x = ""     # ← warning: overwritten unconditionally
begin
  x = "override"
end
puts x
```

### Unconditional overwrite after conditional

```crystal
x = ""     # ← warning: overwritten unconditionally by x = 2
if cond
  x = 1    # also dead (overwritten by x = 2)
end
x = 2
puts x
```

### No read before end of scope

```crystal
def foo
  x = 42   # ← warning: never read
end
```
