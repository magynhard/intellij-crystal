# Rename Prefix Handling Bugfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the rename prefix handling bug in `CrystalInstanceVarAccessMixin.setName()` and `CrystalClassVarAccessMixin.setName()` to correctly strip `@`/`@@` prefixes before re-applying them.

**Architecture:** Modify two mixin files to use the same prefix-stripping pattern already used by `CrystalParameterMixin.setName()`, `CrystalAssignmentMixin.setName()`, and `CrystalVariableReferenceMixin.setName()`. Add unit tests to verify the fix.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, JUnit 4, BasePlatformTestCase

## Global Constraints

- JDK 21 required
- Gradle 9.4.1 (wrapper committed)
- Run `./gradlew test` to verify all tests pass
- Follow existing code patterns in the codebase

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalInstanceVarAccessMixin.kt` | Modify | Fix `setName()` prefix handling |
| `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalClassVarAccessMixin.kt` | Modify | Fix `setName()` prefix handling |
| `src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt` | Modify | Add tests for explicit prefix scenarios |

---

### Task 1: Add Tests for Explicit Prefix Rename

**Files:**
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt`

**Interfaces:**
- Consumes: `myFixture.renameElementAtCaret()`, `myFixture.checkResult()`
- Produces: Two new test methods that will fail until the fix is implemented

- [ ] **Step 1: Add test for class variable rename with explicit prefix**

Add this test method to `CrystalRenamePsiNameIdentifierOwnerTest`:

```kotlin
fun testRenameClassVarWithExplicitPrefix() {
    val file = myFixture.configureByText("test.cr", """
        class Foo
          @@example = 1
          def self.test
            puts @@example
          end
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("@@other")
    myFixture.checkResult("""
        class Foo
          @@other = 1
          def self.test
            puts @@other
          end
        end
    """.trimIndent())
}
```

- [ ] **Step 2: Add test for instance variable rename with explicit prefix**

Add this test method to `CrystalRenamePsiNameIdentifierOwnerTest`:

```kotlin
fun testRenameInstanceVarWithExplicitPrefix() {
    val file = myFixture.configureByText("test.cr", """
        class Bar
          def initialize(@ini : Int32)
            @other = @ini + 1
          end
        end
    """.trimIndent())
    myFixture.renameElementAtCaret("@other_ini")
    myFixture.checkResult("""
        class Bar
          def initialize(@other_ini : Int32)
            @other = @other_ini + 1
          end
        end
    """.trimIndent())
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameClassVarWithExplicitPrefix"`
Expected: FAIL (definition becomes corrupted)

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameInstanceVarWithExplicitPrefix"`
Expected: FAIL (wrong variable type)

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt
git commit -m "test: add tests for rename with explicit @/@@ prefix"
```

---

### Task 2: Fix CrystalInstanceVarAccessMixin.setName()

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalInstanceVarAccessMixin.kt:21-33`

**Interfaces:**
- Consumes: `CrystalTypes.INSTANCE_VAR`, `createLeafFromText()`
- Produces: Fixed `setName()` that correctly handles explicit `@` prefix

- [ ] **Step 1: Replace the setName() implementation**

Replace lines 21-33 in `CrystalInstanceVarAccessMixin.kt` with:

```kotlin
override fun setName(name: String): PsiElement {
    val identNode = node.findChildByType(CrystalTypes.INSTANCE_VAR) ?: return this
    val bareName = name.removePrefix("@").removePrefix("@")
    val fixedName = "@$bareName"
    val newNode = de.magynhard.crystal.psi.createLeafFromText(project, fixedName, CrystalTypes.INSTANCE_VAR) ?: return this
    identNode.treeParent.replaceChild(identNode, newNode)
    return this
}
```

- [ ] **Step 2: Run the instance variable test**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameInstanceVarWithExplicitPrefix"`
Expected: PASS

- [ ] **Step 3: Run all rename tests to check for regressions**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest"`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalInstanceVarAccessMixin.kt
git commit -m "fix: strip @ prefix in CrystalInstanceVarAccessMixin.setName()"
```

---

### Task 3: Fix CrystalClassVarAccessMixin.setName()

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalClassVarAccessMixin.kt:20-31`

**Interfaces:**
- Consumes: `CrystalTypes.CLASS_VAR`, `createLeafFromText()`
- Produces: Fixed `setName()` that correctly handles explicit `@@` prefix

- [ ] **Step 1: Replace the setName() implementation**

Replace lines 20-31 in `CrystalClassVarAccessMixin.kt` with:

```kotlin
override fun setName(name: String): PsiElement {
    val identNode = node.findChildByType(CrystalTypes.CLASS_VAR) ?: return this
    val bareName = name.removePrefix("@").removePrefix("@")
    val fixedName = "@@$bareName"
    val newNode = de.magynhard.crystal.psi.createLeafFromText(project, fixedName, CrystalTypes.CLASS_VAR) ?: return this
    identNode.treeParent.replaceChild(identNode, newNode)
    return this
}
```

- [ ] **Step 2: Run the class variable test**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameClassVarWithExplicitPrefix"`
Expected: PASS

- [ ] **Step 3: Run all rename tests to check for regressions**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest"`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalClassVarAccessMixin.kt
git commit -m "fix: strip @@ prefix in CrystalClassVarAccessMixin.setName()"
```

---

### Task 4: Full Test Suite Verification

**Files:**
- No new files

**Interfaces:**
- Consumes: All changes from Tasks 1-3
- Produces: Confirmation that all tests pass

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 2: Verify no compilation errors**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit if needed**

If any additional changes were made:
```bash
git add -A
git commit -m "chore: verify all tests pass after rename prefix fix"
```

---

## Verification Checklist

After completing all tasks, verify:

- [ ] `testRenameClassVarWithExplicitPrefix` passes
- [ ] `testRenameInstanceVarWithExplicitPrefix` passes
- [ ] All existing rename tests still pass
- [ ] Full test suite passes
- [ ] No compilation errors
- [ ] Code follows existing patterns in the codebase
