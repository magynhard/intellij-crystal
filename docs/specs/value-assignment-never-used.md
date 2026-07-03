# Value Assigned to Variable Never Used

Spec for the `CrystalUnusedVariableInspection` — specifically the "Value assigned to variable is never used" warning.

## Current Algorithm

The inspection works as follows:

1. Collect all local variable assignments and references in scope
2. For each assignment `A`, find the next assignment `B` to the same variable (by offset)
3. Check if there is a read (reference) between `A` and `B`
4. If no read → report "Value assigned to 'x' is never used"

## Problem

The algorithm assumes linear execution. It does not account for control flow constructs
that may or may not execute. When a variable is conditionally reassigned, the initial
assignment's value is still used as a fallback if the conditional branch is not taken.

---

## False Positive Cases

### Case 1: `if` without else

```crystal
proxy_command = ""
if condition
  proxy_command = "set HTTP_PROXY=#{proxy["hostname"]}"
end
command = %Q{#{proxy_command}"#{source}"}
```

**Expected:** No warning on `proxy_command = ""`.
**Actual:** Warning "Value assigned to 'proxy_command' is never used".
**Reason:** If `condition` is false, the initial `""` value flows through to `command`.

### Case 2: `unless` without else

```crystal
x = ""
unless condition
  x = "overridden"
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If `condition` is true, `x` keeps its initial value.

### Case 3: `begin` with `rescue`

```crystal
proxy_command2 = ""
begin
  proxy_command2 = "set HTTP_PROXY=#{proxy["hostname"]}"
rescue
end
command2 = %Q{#{proxy_command2}"#{source}"}
```

**Expected:** No warning on `proxy_command2 = ""`.
**Actual:** Warning.
**Reason:** If the body raises before assignment completes, `proxy_command2` keeps `""`.

### Case 4: `while` loop (0 iterations possible)

```crystal
x = ""
while condition
  x = "from_loop"
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If `condition` is false from the start, the loop body never executes and `x` keeps `""`.

### Case 5: `until` loop (0 iterations possible)

```crystal
x = ""
until done
  x = "from_loop"
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** Same as `while` — loop may never execute.

### Case 6: `for` loop on potentially empty collection

```crystal
x = ""
for item in items
  x = item
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If `items` is empty, the loop body never executes.

### Case 7: `case` without else

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

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If no `when` clause matches, `x` keeps `""`.

### Case 8: `select` without else

```crystal
x = ""
select
when signal.ready?
  x = signal.receive
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If no `when` clause matches, `x` keeps `""`.

### Case 9: Nested conditionals

```crystal
x = ""
if outer_condition
  if inner_condition
    x = "deep"
  end
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If either condition is false, `x` keeps `""`.

### Case 10: `elsif` chain without else

```crystal
x = ""
if a == 1
  x = "one"
elsif a == 2
  x = "two"
end
puts x
```

**Expected:** No warning on `x = ""`.
**Actual:** Warning.
**Reason:** If neither condition matches, `x` keeps `""`.

---

## True Positive Cases (should still warn)

These should continue to produce warnings:

```crystal
# Linear reassignment — value is truly lost
x = 1      # ← warning: value never used
x = 2
puts x
```

```crystal
# begin without rescue — body always executes
x = ""
begin
  x = "override"
end
puts x      # x is always "override" — warning on x = ""
```

```crystal
# Unconditional overwrite after conditional
x = ""      # ← warning: overwritten unconditionally by x = 2
if cond
  x = 1    # this assignment is also dead (overwritten by x = 2)
end
x = 2
puts x
```

---

## Proposed Fix

### 1. New helper: `isInConditionalBranch`

Walk up the PSI tree from an assignment and check if any ancestor is a conditional construct:

```kotlin
private fun isInConditionalBranch(element: PsiElement): Boolean {
    var current = element.parent
    while (current != null) {
        when (current) {
            is CrystalIfStatement,
            is CrystalUnlessStatement,
            is CrystalWhileStatement,
            is CrystalUntilStatement,
            is CrystalForStatement,
            is CrystalCaseStatement,
            is CrystalSelectStatement -> return true
            is CrystalBeginStatement -> {
                if (current.rescueClauseList.isNotEmpty()) return true
            }
        }
        current = current.parent
    }
    return false
}
```

Note: `CrystalBeginStatement` without rescue is NOT considered conditional — a plain
`begin ... end` always executes its body.

### 2. Modified search range in `analyzeScope`

Replace the current logic:

```kotlin
val nextAssignEndOffset = nextAssign?.identifierElement?.parent?.textRange?.endOffset ?: Int.MAX_VALUE
```

With:

```kotlin
val effectiveUpperBound = if (nextAssign != null
    && isInConditionalBranch(nextAssign.identifierElement.parent)) {
    // Next assignment might not execute — extend search to include reads after it
    val nextUnconditional = assignments
        .filter { it.name == assignment.name && it.offset > assignOffset && !it.isCompound }
        .firstOrNull { !isInConditionalBranch(it.identifierElement.parent) }
    nextUnconditional?.identifierElement?.parent?.textRange?.endOffset ?: Int.MAX_VALUE
} else {
    nextAssignEndOffset
}
```

Then use `effectiveUpperBound` for the read check:

```kotlin
val hasRead = references.any { ref ->
    ref.name == assignment.name && ref.offset > assignOffset && ref.offset < effectiveUpperBound
}
```

Remove the separate `hasLaterRead` variable — the `hasRead` check with `effectiveUpperBound`
handles all cases.

### 3. Rationale

When the next assignment to a variable is inside a conditional branch:
- The conditional branch may not execute
- The initial assignment's value may survive and be read
- Therefore, extend the search range to include reads that come AFTER the conditional
- If there IS a subsequent unconditional assignment to the same variable, use that as
  the upper bound instead (since that unconditional assignment always overwrites)

---

## Impact

- Fixes the two user-reported false positives (Cases 1 and 2)
- Prevents false positives in all conditional constructs (Cases 3–10)
- No regression for true positives — linear reassignment, begin-without-rescue, and
  unconditional overwrites are correctly handled
