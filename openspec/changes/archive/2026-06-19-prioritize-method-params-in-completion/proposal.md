## Why

When inside a method body on a new line, autocomplete shows all project-wide methods, local variables, stdlib types, and keywords in a flat list. Method parameters (e.g. `bar`, `baz` in `def foo(bar : String, baz : Int32)`) have the same priority as everything else, making them hard to find quickly. Since parameters are the most contextually relevant completions inside a method body, they should appear at the top of the list.

## What Changes

- Boost priority of method parameters in free-text completion when the cursor is inside a method body
- Parameters from the enclosing `def` (including `@param` shorthand) should rank above keywords, stdlib types, and project-wide methods
- Only applies to free-text completion (new line, not after a dot or in a specific context like type annotation)

## Capabilities

### New Capabilities
- `param-completion-priority`: Boost method parameter priority in autocomplete when inside a method body

### Modified Capabilities

## Impact

- `CrystalCompletionContributor.kt` — modify `addLocalVariablesAndParameters` or add new priority logic
- `CrystalCompletionHelper.kt` — possibly add helper to extract enclosing method parameters
- Existing completion tests — verify no regressions in completion ordering
