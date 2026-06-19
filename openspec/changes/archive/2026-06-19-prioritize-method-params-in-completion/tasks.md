## 1. Parameter Priority Boost

- [x] 1.1 In `CrystalCompletionContributor.kt`, modify `addLocalVariablesAndParameters()` to wrap parameter `LookupElementBuilder` with `PrioritizedLookupElement.withPriority(param, 10000000.0)` and `withBoldness(true)`
- [x] 1.2 In `CrystalCompletionContributor.kt`, wrap local variable `LookupElementBuilder` with `PrioritizedLookupElement.withPriority(varLookup, 50.0)` — lower than parameters but above methods

## 2. Tests

- [x] 2.1 Add test `testParameterPriorityAboveMethods` — inside `def foo(bar : String)`, type `b`, verify `bar` appears above `break`/`begin` in the completion list
- [x] 2.2 Add test `testLocalVariablePriorityAboveMethods` — inside method with `my_var = 1`, verify `my_var` appears above project-wide methods
- [x] 2.3 Run `./gradlew test` — verify no regressions

## 3. Cleanup

- [x] 3.1 Run full test suite and verify all pre-existing tests still pass
