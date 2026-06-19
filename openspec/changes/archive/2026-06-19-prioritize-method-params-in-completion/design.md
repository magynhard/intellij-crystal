## Context

The completion system in `CrystalCompletionContributor.kt` handles free-text completion (Case 3) by adding stdlib types, all project classes, all project methods, and local variables/parameters in that order. Parameters and local variables are added via `addLocalVariablesAndParameters()` at line 147 without any explicit priority, so they appear at the default priority level — same as everything else.

The `CrystalCompletionHelper.buildMethodLookup()` already supports priority via `PrioritizedLookupElement.withPriority()`. Instance methods from own class get priority 10.0, parent class methods get 5.0, etc.

## Goals / Non-Goals

**Goals:**
- Method parameters from the enclosing `def` SHALL appear at the top of the completion list when inside a method body on a new line
- Local variables defined before the cursor SHALL also get a priority boost (but lower than parameters)
- The boost SHALL only apply in free-text completion context (not after `.`)

**Non-Goals:**
- Changing priority for dot-completion (`a.`)
- Changing priority for type annotation context
- Smart ranking based on usage frequency or recency

## Decisions

### Decision 1: Use `PrioritizedLookupElement.withPriority()` for parameters

**Choice:** Wrap parameter `LookupElementBuilder` with `PrioritizedLookupElement.withPriority(param, 100.0)`.

**Why:** The plugin already uses this pattern for instance method priority. It's the standard IntelliJ approach for controlling completion ordering. Priority 100.0 ensures parameters appear above all other items (methods get max 10.0, stdlib types get 0.0).

**Alternative considered:** Reordering the `addElement` calls (adding parameters first). This doesn't work because IntelliJ's completion framework sorts by priority, not insertion order.

### Decision 2: Local variables get medium priority (50.0)

**Choice:** Local variables get priority 50.0 — above methods but below parameters.

**Why:** Local variables are more contextually relevant than project-wide methods, but parameters are the most relevant since they're always available in the method body.

### Decision 3: Only boost in method body context

**Choice:** The priority boost only applies when `PsiTreeUtil.getParentOfType(position, CrystalMethodDefinition::class.java)` returns non-null.

**Why:** At top level, parameters don't exist. The boost is meaningless outside a method body. The existing code already checks for enclosing method — we just add priority to the existing logic.

## Risks / Trade-offs

- **Risk:** Priority 100.0 might be too high, pushing down important keywords like `return`, `if`, `end`. → **Mitigation:** These keywords are added by IntelliJ's built-in completion with their own priorities. We can tune the value later if needed.
- **Risk:** `@param` shorthand parameters (e.g. `@cool`) might not be found by `extractParameterName`. → **Mitigation:** `extractParameterName` already handles `@param` correctly (strips `@` prefix). This was verified in earlier work.
