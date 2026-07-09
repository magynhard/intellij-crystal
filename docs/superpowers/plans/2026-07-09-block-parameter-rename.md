# Block Parameter Reference Resolution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable rename, go-to-definition, and find-usages for block parameters (e.g., `|ola|` in `each do |ola| ... end`).

**Architecture:** Extend `CrystalReference.resolveLocal()` to check `CrystalBlock.parameterList` when walking up the PSI tree, matching the existing pattern used for `CrystalMethodDefinition` and `CrystalMacroDefinition`. Add comprehensive tests.

**Tech Stack:** Kotlin, IntelliJ Platform PSI, JUnit 4 (BasePlatformTestCase)

## Global Constraints

- Project language: English (code, comments, commits, docs)
- Build: `./gradlew build` (compile + generate lexer/parser + package)
- Tests: `./gradlew test` (JUnit 4, BasePlatformTestCase)
- Never add `recoverWhile` to BNF rules
- All extensions registered in `src/main/resources/META-INF/plugin.xml`

---

### Task 1: Fix CrystalReference.resolveLocal() to handle block parameters

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/psi/CrystalReference.kt:80-91`

**Interfaces:**
- Consumes: `CrystalBlock.parameterList` (generated PSI, already exists)
- Produces: `resolve()` returns non-null for block parameter usages

- [ ] **Step 1: Add CrystalBlock import**

Add to imports at top of file:
```kotlin
// No new import needed — CrystalBlock is in the same package (de.magynhard.crystal.psi)
```

- [ ] **Step 2: Extend resolveLocal() to check block parameters**

In `resolveLocal()` method, after the existing `CrystalMethodDefinition`/`CrystalMacroDefinition` check (lines 80-90), add a check for `CrystalBlock`:

```kotlin
private fun resolveLocal(): PsiElement? {
    val containingFile = element.containingFile ?: return null
    var scope: PsiElement? = element.parent
    while (scope != null && scope !== containingFile) {
        var sibling = scope.prevSibling
        while (sibling != null) {
            val assignment = findAssignmentWithName(sibling, name)
            if (assignment != null) return assignment
            sibling = sibling.prevSibling
        }
        // Check parameters if we're inside a method or macro
        if (scope is CrystalMethodDefinition || scope is CrystalMacroDefinition) {
            val paramList = when (scope) {
                is CrystalMethodDefinition -> scope.parameterList
                is CrystalMacroDefinition -> scope.parameterList
                else -> null
            }
            paramList?.parameterList?.forEach { param ->
                val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
                if (paramIdent?.text == name) return paramIdent.psi
            }
            break // Don't look beyond method boundaries for locals
        }
        // Check block parameters (e.g., |ola| in each do |ola| ... end)
        if (scope is CrystalBlock) {
            val paramList = scope.parameterList
            paramList?.parameterList?.forEach { param ->
                val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
                if (paramIdent?.text == name) return paramIdent.psi
            }
            // Don't break — continue walking up to find enclosing method scope
        }
        scope = scope.parent
    }
    return null
}
```

Key differences from the method/macro check:
1. **No `break`** after finding block params — continue walking up to find enclosing method scope
2. Block parameters are local to the block, but usages inside the block should still resolve

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `./gradlew test`
Expected: All existing tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/psi/CrystalReference.kt
git commit -m "fix: resolve block parameter references for rename and go-to-definition"
```

---

### Task 2: Add unit tests for block parameter resolution

**Files:**
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt`

**Interfaces:**
- Consumes: Fixed `CrystalReference.resolveLocal()` from Task 1
- Produces: Tests verifying rename works for block parameters

- [ ] **Step 1: Add test for renaming block parameter from definition site**

Add test method to `CrystalRenamePsiNameIdentifierOwnerTest`:

```kotlin
fun testRenameBlockParameterFromDefinition() {
    val file = myFixture.configureByText("test.cr", """
        [1, 2, 3].each do |<caret>ola|
          puts ola
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("element")
    myFixture.checkResult("""
        [1, 2, 3].each do |element|
          puts element
        end
    """.trimIndent())
}
```

- [ ] **Step 2: Add test for renaming block parameter from usage site**

```kotlin
fun testRenameBlockParameterFromUsage() {
    val file = myFixture.configureByText("test.cr", """
        [1, 2, 3].each do |ola|
          puts <caret>ola
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("element")
    myFixture.checkResult("""
        [1, 2, 3].each do |element|
          puts element
        end
    """.trimIndent())
}
```

- [ ] **Step 3: Add test for renaming block parameter with multiple params**

```kotlin
fun testRenameBlockParameterMultipleParams() {
    val file = myFixture.configureByText("test.cr", """
        [1, 2].each_with_index do |<caret>elem, idx|
          puts elem
          puts idx
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("item")
    myFixture.checkResult("""
        [1, 2].each_with_index do |item, idx|
          puts item
          puts idx
        end
    """.trimIndent())
}
```

- [ ] **Step 4: Add test for nested blocks**

```kotlin
fun testRenameBlockParameterNestedBlocks() {
    val file = myFixture.configureByText("test.cr", """
        [1, 2].each do |outer|
          [3, 4].each do |<caret>inner|
            puts outer
            puts inner
          end
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("x")
    myFixture.checkResult("""
        [1, 2].each do |outer|
          [3, 4].each do |x|
            puts outer
            puts x
          end
        end
    """.trimIndent())
}
```

- [ ] **Step 5: Add test for curly brace blocks**

```kotlin
fun testRenameBlockParameterCurlyBrace() {
    val file = myFixture.configureByText("test.cr", """
        [1, 2, 3].map { |<caret>n| n * 2 }
    """.trimIndent())
    myFixture.renameElementAtCaret("num")
    myFixture.checkResult("""
        [1, 2, 3].map { |num| num * 2 }
    """.trimIndent())
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest"`
Expected: All new tests pass

- [ ] **Step 7: Commit**

```bash
git add src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt
git commit -m "test: add rename tests for block parameters"
```

---

### Task 3: Run full test suite and verify

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass (no regressions)

- [ ] **Step 2: Run build**

Run: `./gradlew build`
Expected: Build succeeds

- [ ] **Step 3: Final commit if needed**

Only if build/test revealed additional fixes needed.
