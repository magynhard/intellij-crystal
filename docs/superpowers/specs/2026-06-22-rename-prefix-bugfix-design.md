# Rename Prefix Handling Bugfix

## Problem Statement

When renaming instance variables (`@name`) or class variables (`@@name`) in the Crystal IntelliJ plugin, the `setName()` method in `CrystalInstanceVarAccessMixin` and `CrystalClassVarAccessMixin` does not strip existing `@`/`@@` prefixes before re-applying them. This causes incorrect results when the user explicitly includes the prefix in the new name.

### Reproduction Steps

**Scenario 1: Class Variable**
1. Define `@@example` in a class
2. Trigger rename (Shift+F6) on `@@example`
3. Type `@@other` (with explicit `@@` prefix)
4. **Expected:** All occurrences become `@@other`
5. **Actual:** Definition becomes `@` (corrupted), usages become `@@other`

**Scenario 2: Instance Variable**
1. Define `@ini` in a class
2. Trigger rename (Shift+F6) on `@ini`
3. Type `@other_ini` (with explicit `@` prefix)
4. **Expected:** All occurrences become `@other_ini`
5. **Actual:** One occurrence becomes `@@other_ini` (wrong type), others become `@other_ini`

## Root Cause Analysis

### Primary Bug: Missing Prefix Stripping

**File:** `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalInstanceVarAccessMixin.kt`

```kotlin
// CURRENT (BUGGY):
override fun setName(name: String): PsiElement {
    val newName = "@$name"  // Always adds @, even if name already has @
    // ...
}
```

When user types `@other_ini`:
- `newName = "@" + "@other_ini"` = `"@@other_ini"` (CLASS_VAR syntax!)
- This creates a class variable instead of an instance variable

**File:** `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalClassVarAccessMixin.kt`

```kotlin
// CURRENT (BUGGY):
override fun setName(name: String): PsiElement {
    val newName = "@@$name"  // Always adds @@, even if name already has @@
    // ...
}
```

When user types `@@other`:
- `newName = "@@" + "@@other"` = `"@@@@other"` (5 @ signs)
- Lexer tokenizes this incorrectly, causing parse errors

### Secondary Bug: Fragile Leaf Extraction

Both methods use `createFileFromText(...).firstChild?.node` which:
- Returns a composite wrapper node (e.g., `top_level_statement`), not the leaf token
- `replaceChild` then replaces the leaf with a composite, corrupting the PSI tree

### Comparison with Working Code

Other `setName()` implementations in the codebase correctly strip prefixes:

**CrystalParameterMixin.setName():**
```kotlin
val bareName = name.removePrefix("@").removePrefix("@")
val fixedName = when (tokenType) {
    CrystalTypes.INSTANCE_VAR -> "@$bareName"
    CrystalTypes.CLASS_VAR -> "@@$bareName"
    else -> bareName
}
```

**CrystalAssignmentMixin.setName():**
```kotlin
val bareName = name.removePrefix("@").removePrefix("@")
val fixedName = when (tokenType) {
    CrystalTypes.INSTANCE_VAR -> "@$bareName"
    CrystalTypes.CLASS_VAR -> "@@$bareName"
    else -> bareName
}
```

**CrystalVariableReferenceMixin.setName():**
```kotlin
val bareName = name.removePrefix("@").removePrefix("@")
val fixedName = when (tokenType) {
    CrystalTypes.INSTANCE_VAR -> "@$bareName"
    CrystalTypes.CLASS_VAR -> "@@$bareName"
    else -> bareName
}
```

## Proposed Solution

### Changes

#### 1. Fix `CrystalInstanceVarAccessMixin.setName()`

**File:** `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalInstanceVarAccessMixin.kt`

Replace the current `setName()` implementation with:

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

**Key changes:**
1. Strip `@`/`@@` prefix before re-applying (consistent with other `setName()` methods)
2. Use `createLeafFromText()` instead of `createFileFromText().firstChild?.node`
3. Replace only the leaf child, not the entire composite

#### 2. Fix `CrystalClassVarAccessMixin.setName()`

**File:** `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalClassVarAccessMixin.kt`

Replace the current `setName()` implementation with:

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

**Key changes:**
1. Strip `@`/`@@` prefix before re-applying (consistent with other `setName()` methods)
2. Use `createLeafFromText()` instead of `createFileFromText().firstChild?.node`
3. Replace only the leaf child, not the entire composite

#### 3. Add Tests

**File:** `src/test/kotlin/de/magynhard/crystal/CrystalRenamePsiNameIdentifierOwnerTest.kt`

Add new test methods:

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

## Verification

### Test Plan

1. Run existing rename tests to ensure no regressions:
   ```bash
   ./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest"
   ```

2. Run new tests to verify the fix:
   ```bash
   ./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameClassVarWithExplicitPrefix"
   ./gradlew test --tests "de.magynhard.crystal.CrystalRenamePsiNameIdentifierOwnerTest.testRenameInstanceVarWithExplicitPrefix"
   ```

3. Run full test suite:
   ```bash
   ./gradlew test
   ```

### Manual Verification

1. Open Crystal project in IntelliJ
2. Create a test file with instance and class variables
3. Trigger rename on each type with and without explicit prefix
4. Verify all occurrences are updated correctly
5. Verify variable types are preserved (instance vs class)

## Scope

### In Scope
- Fix `setName()` in `CrystalInstanceVarAccessMixin`
- Fix `setName()` in `CrystalClassVarAccessMixin`
- Add unit tests for explicit prefix scenarios

### Out of Scope
- Cross-file rename for local variables
- Method definition → call-site rename
- Custom conflict detection
- Undo/rollback on compiler errors

## Risk Assessment

**Low Risk:**
- Changes are isolated to two files
- Follow existing patterns in codebase
- Add tests to prevent regressions
- No architectural changes

## References

- `CrystalInstanceVarReference.handleElementRename()` - correct implementation to follow
- `CrystalParameterMixin.setName()` - correct implementation to follow
- `CrystalAssignmentMixin.setName()` - correct implementation to follow
- `CrystalVariableReferenceMixin.setName()` - correct implementation to follow
