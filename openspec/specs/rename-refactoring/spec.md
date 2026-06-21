# Rename Refactoring — Technical Spec

This document is a comprehensive analysis of how rename refactoring works in
IntelliJ and how Crystal's PSI architecture interacts with it. It is the result
of decompiling IntelliJ 2026.1 internals, attempting a `renamePsiElementProcessor`
approach that broke rename entirely, and reverting to the original state.

**Goal**: Provide a complete reference for anyone implementing scope-aware rename
in the Crystal plugin, so that the same mistakes are not repeated.

---

## 1. IntelliJ Rename Architecture (IntelliJ 2026.1)

### 1.1 Handler Chain

When the user triggers rename (Shift+F6 or right-click → Rename), IntelliJ
iterates registered `RenameHandler`s and invokes the first one where
`isAvailableOnDataContext` returns true.

The default handlers (registered by the platform, not by plugins) are:

| Handler | Purpose | Condition for activation |
|---------|---------|------------------------|
| `MemberInplaceRenameHandler` | In-place rename (edit name directly in editor) for `PsiNameIdentifierOwner` elements | `element instanceof PsiNameIdentifierOwner` AND `provider.isMemberInplaceRenameAvailable(element, nameAtCaret)` |
| `PsiElementRenameHandler` | Dialog-based rename (opens a rename dialog with preview) | `element != null` (resolved from data context) |

There is **no** `TokenInplaceRenameHandler` in IntelliJ 2026.1 — this class was
removed/merged in recent versions.

### 1.2 Element Resolution

The `element` parameter passed to handlers comes from
`PsiElementRenameHandler.getElement(dataContext)`:

```
LangDataKeys.PSI_ELEMENT.getData(dataContext)
```

This is resolved by the editor's data provider. In the editor context:
- For `PsiNamedElement` composites (CrystalMethodDefinition, CrystalClassDefinition):
  `PSI_ELEMENT` resolves to the composite itself.
- For elements with a `PsiReference` (CrystalVariableReference):
  `PSI_ELEMENT` may resolve to the reference target (via `TargetElementUtil`).
- For leaf tokens without a reference target (IDENTIFIER in a local variable):
  `PSI_ELEMENT` may be `null` or the leaf token itself.

### 1.3 `VariableInplaceRenameHandler.isAvailable` (base class)

Decompiled from `intellij.platform.lang.impl.jar`:

```java
protected boolean isAvailable(PsiElement element, Editor editor, PsiFile file) {
    PsiElement nameAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null || !element.isValid()) return false;
    RefactoringSupportProvider provider = LanguageRefactoringSupport.forContext(element);
    return editor.getSettings().isVariableInplaceRenameEnabled()
        && provider != null
        && provider.isInplaceRenameAvailable(element, nameAtCaret);
}
```

**Key**: This calls `isInplaceRenameAvailable`, NOT `isMemberInplaceRenameAvailable`.
Crystal does NOT override `isInplaceRenameAvailable` — the default returns `false`.

### 1.4 `MemberInplaceRenameHandler.isAvailable` (extends Variable)

Decompiled from `intellij.platform.lang.impl.jar`:

```java
protected boolean isAvailable(PsiElement element, Editor editor, PsiFile file) {
    PsiElement nameAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
    // If element is null but there's an active lookup, try to find PsiNamedElement parent
    if (element == null && LookupManager.getActiveLookup(editor) != null) {
        element = PsiTreeUtil.getParentOfType(nameAtCaret, PsiNamedElement.class);
    }
    RefactoringSupportProvider provider = element != null
        ? LanguageRefactoringSupport.forContext(element) : null;
    return editor.getSettings().isVariableInplaceRenameEnabled()
        && provider != null
        && element instanceof PsiNameIdentifierOwner      // <-- CRITICAL CHECK
        && provider.isMemberInplaceRenameAvailable(element, nameAtCaret);
}
```

**Key**: The `element instanceof PsiNameIdentifierOwner` check at offset 131 means
`MemberInplaceRenameHandler` ONLY activates for elements that implement
`PsiNameIdentifierOwner`. Leaf tokens (IDENTIFIER, INSTANCE_VAR) do NOT implement
this interface, so `MemberInplaceRenameHandler` never activates for them.

### 1.5 `MemberInplaceRenameHandler.doRename` (the actual rename)

```java
public InplaceRefactoring doRename(PsiElement element, Editor editor, DataContext ctx) {
    if (element instanceof PsiNameIdentifierOwner) {
        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
        if (processor.isInplaceRenameSupported()) {
            PsiElement substituted = processor.substituteElementToRename(element, editor);
            // ... proceed with inplace rename using the processor
        }
    }
    // ... fallback to dialog rename
}
```

**Key**: `RenamePsiElementProcessor.forElement(element)` is called with the composite
element (e.g., CrystalMethodDefinition), not the leaf token. If a custom processor
is registered and `canProcessElement` returns true for this element, the custom
processor takes over the rename flow. This is where the `renamePsiElementProcessor`
approach broke things.

### 1.6 `RefactoringSupportProvider` Methods

| Method | Called by | Default | Crystal overrides? |
|--------|----------|---------|-------------------|
| `isInplaceRenameAvailable(element, nameIdentifier)` | `VariableInplaceRenameHandler` | `false` | **No** |
| `isMemberInplaceRenameAvailable(element, nameIdentifier)` | `MemberInplaceRenameHandler` | `false` | **Yes** — returns true for IDENTIFIER, CONSTANT |

**Critical**: Crystal only overrides `isMemberInplaceRenameAvailable`, but
`VariableInplaceRenameHandler` calls `isInplaceRenameAvailable` (which defaults
to `false`). This means `VariableInplaceRenameHandler` never activates for Crystal.
Rename works via `PsiElementRenameHandler` (dialog-based) instead.

---

## 2. Crystal PSI Architecture — Rename-Relevant Properties

### 2.1 Element Classification

| PSI Type | Implements PsiNameIdentifierOwner? | Has PsiReference? | Has getName/setName? | Current rename behavior |
|----------|-----------------------------------|--------------------|---------------------|----------------------|
| `CrystalMethodDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalClassDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalModuleDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalStructDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalEnumDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalMacroDefinition` | YES (via `CrystalNamedElement`) | YES (via StubIndex) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalInstanceVarAccess` | YES (via mixin) | YES (via `CrystalInstanceVarReference`) | YES | Works via `PsiElementRenameHandler` (dialog) |
| `CrystalVariableReference` | **NO** | YES (via `CrystalReference`) | **NO** | **Broken** — no PsiNameIdentifierOwner → handler can't activate |
| `CrystalParameter` | **NO** | **NO** | **NO** | **Broken** — same reason |
| `CrystalAssignment` (local var) | **NO** | **NO** | **NO** | **Broken** — same reason |
| IDENTIFIER leaf token | **NO** | **NO** | **NO** | **Broken** — leaf token, no PsiNameIdentifierOwner |

### 2.2 `CrystalNamedElement` Interface

```kotlin
interface CrystalNamedElement : PsiNameIdentifierOwner
```

Implemented by: `CrystalStubbedClassDefinitionImpl`, `CrystalStubbedModuleDefinitionImpl`,
`CrystalStubbedStructDefinitionImpl`, `CrystalStubbedEnumDefinitionImpl`,
`CrystalStubbedMethodDefinitionImpl`, `CrystalStubbedMacroDefinitionImpl`.

Each implements `getNameIdentifier()`, `getName()`, `setName()`.

### 2.3 `CrystalInstanceVarAccessMixin`

```kotlin
abstract class CrystalInstanceVarAccessMixin(node: ASTNode)
    : ASTWrapperPsiElement(node), CrystalInstanceVarAccess {
    override fun getName(): String = text
    override fun setName(name: String): PsiElement { /* replaces INSTANCE_VAR token */ }
    override fun getNameIdentifier(): PsiElement? = node.findChildByType(CrystalTypes.INSTANCE_VAR)?.psi
    override fun getReference(): PsiReference? = CrystalInstanceVarReference(this)
}
```

This is the pattern for making a composite element renameable.

### 2.4 `CrystalVariableReferenceMixin` (NOT PsiNameIdentifierOwner)

```kotlin
abstract class CrystalVariableReferenceMixin(node: ASTNode)
    : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
```

Does NOT implement `PsiNameIdentifierOwner`. Only provides `getReference()` for
Go to Definition. This is the main gap for local variable rename.

### 2.5 `CrystalReference` Resolution

```kotlin
class CrystalReference(...) : PsiReferenceBase<PsiElement>(...) {
    override fun resolve(): PsiElement? {
        // 1. Project-wide StubIndex lookup (methods, classes)
        val definitions = CrystalDefinitionFinder.findDefinitions(name, element.project)
        if (definitions.isNotEmpty()) return definitions.first()
        // 2. Local scope fallback (walk up PSI tree)
        return resolveLocal()
    }
}
```

The `resolveLocal()` method walks up the PSI tree looking for:
- Sibling assignments with matching IDENTIFIER name
- Method/macro parameter lists with matching name

This is already partially scope-aware for local variables, but only for resolution
(go to definition), not for rename target collection.

### 2.6 `CrystalRenameVerifier` (post-rename safety net)

Registered as a `RefactoringEventListener` in plugin.xml. After any rename completes,
runs `crystal build --no-codegen` on the file and shows a warning notification if
the compiler reports errors. This works regardless of which rename handler was used.

---

## 3. Current Rename Behavior (what works)

### 3.1 What works

| Scenario | How it works | Mechanism |
|----------|-------------|-----------|
| Method definition (`def tanzen`) | Dialog rename opens, renames method | `PsiElementRenameHandler` resolves `CrystalMethodDefinition` (PsiNameIdentifierOwner) |
| Class/module/struct/enum name | Dialog rename opens, renames type | Same as methods |
| Instance variable (`@name`) | Dialog rename opens, renames all `@name` in class | `CrystalInstanceVarAccess` implements PsiNameIdentifierOwner |
| Macro definition | Dialog rename opens | `CrystalMacroDefinition` implements PsiNameIdentifierOwner |

### 3.2 What does NOT work

| Scenario | Why it fails |
|----------|-------------|
| Local variable (`x = 1`) | `CrystalVariableReference` is NOT PsiNameIdentifierOwner → no handler activates |
| Parameter (`def foo(loud : Int32)`) | `CrystalParameter` is NOT PsiNameIdentifierOwner → no handler activates |
| Method call on instance (`a.tanzen`) | The IDENTIFIER leaf resolves to the method definition via reference, but the rename dialog may not open correctly |
| Scope-aware rename (renaming `x` in method A should not affect `x` in method B) | Not possible with current token-based approach — all same-text tokens are renamed |

---

## 4. Failed Approach: `renamePsiElementProcessor`

### 4.1 What was attempted

Registered a `CrystalRenameProcessor` extending `RenamePsiElementProcessor` via:
```xml
<renamePsiElementProcessor
    implementation="de.magynhard.crystal.refactoring.CrystalRenameProcessor"/>
```

With `canProcessElement` returning true for `CrystalNamedElement` and
`CrystalInstanceVarAccess` composites.

### 4.2 Why it broke everything

1. `RenamePsiElementProcessor.forElement(element)` now returns `CrystalRenameProcessor`
   instead of `DEFAULT` for Crystal composites.
2. `MemberInplaceRenameHandler.doRename` calls `processor.isInplaceRenameSupported()`
   → our processor returned `true` → tried inplace rename → but the element
   setup was wrong → failed.
3. `PsiElementRenameHandler` also calls `forElement(element)` → gets our processor
   → calls `processor.substituteElementToRename(element, editor)` → this changes
   the element being renamed → the dialog opens with wrong element or fails.
4. For leaf tokens (IDENTIFIER, INSTANCE_VAR): `canProcessElement` returns false
   → `forElement` returns DEFAULT → but the overall flow was already disrupted
   by the processor being registered for composites.

### 4.3 Root cause

Registering `renamePsiElementProcessor` changes the rename flow for ALL elements
in the language, not just the ones `canProcessElement` returns true for. The
processor's `substituteElementToRename` and `isInplaceRenameSupported` methods
affect the handler selection and dialog creation in ways that are incompatible
with Crystal's PSI architecture (where most renameable elements are leaf tokens
without `PsiNameIdentifierOwner`).

### 4.4 Lesson learned

**Do NOT register `renamePsiElementProcessor` for Crystal.** This extension point
is designed for languages where all renameable elements are `PsiNameIdentifierOwner`
composites (like Java, Kotlin). For Crystal, where local variables and parameters
are leaf tokens or non-PsiNameIdentifierOwner composites, this approach is
fundamentally incompatible.

---

## 5. Viable Approaches for Scope-Aware Rename

### 5.1 Approach A: Make CrystalVariableReference implement PsiNameIdentifierOwner

**Concept**: Update `CrystalVariableReferenceMixin` to implement `PsiNameIdentifierOwner`
(same pattern as `CrystalInstanceVarAccessMixin`). This makes `CrystalVariableReference`
a composite that IntelliJ can rename via `MemberInplaceRenameHandler`.

**Changes required**:
- `CrystalVariableReferenceMixin`: add `getName()`, `setName()`, `getNameIdentifier()`
- `CrystalAssignment`: add mixin implementing `PsiNameIdentifierOwner` for local variable assignments
- `CrystalParameter`: add mixin implementing `PsiNameIdentifierOwner`
- `CrystalRefactoringSupportProvider`: override `isInplaceRenameAvailable` (not just `isMemberInplaceRenameAvailable`) to return true for these new types

**Scope-awareness**: With proper `PsiReference` on `CrystalVariableReference` that
resolves to the correct scope-local definition, `ReferencesSearch` would only find
scope-relevant references. The existing `CrystalReference.resolveLocal()` already
does partial scope-aware resolution.

**Risk**: LOW — does not change any existing handler registration or processor logic.
Only adds `PsiNameIdentifierOwner` to composites that already have the other pieces
(reference, mixin). The `CrystalInstanceVarAccessMixin` is the proven template.

**Compatibility**: HIGH — follows the same pattern already working for instance variables.

**Testing**: Can be tested via `BasePlatformTestCase` using `myFixture.renameElementAtCaret()`.

### 5.2 Approach B: Custom RenameHandler

**Concept**: Register a custom `RenameHandler` via `lang.renameHandler` that handles
Crystal-specific rename logic. The handler would:
1. Check if the element at the caret is a Crystal element
2. Collect scope-aware occurrences using a scope resolver
3. Open the rename dialog with the custom occurrence set

**Changes required**:
- New `CrystalRenameHandler` implementing `RenameHandler`
- Register in plugin.xml via `lang.renameHandler`
- Implement scope-aware occurrence collection

**Risk**: MEDIUM — custom `RenameHandler` replaces the default handler chain for
Crystal elements. Must correctly handle all element types and edge cases. The
`RenameHandler.invoke` method must open the rename dialog correctly.

**Compatibility**: MEDIUM — requires deep understanding of IntelliJ's rename dialog
API. If the handler doesn't properly delegate to `RenamePsiElementProcessor`, some
features (like preview, undo) may not work.

### 5. Approach C: Scope-aware PsiReference (no handler changes)

**Concept**: Keep the existing handler chain unchanged. Make `CrystalReference.resolve()`
scope-aware for local variables — resolve to the correct definition within the same
method scope. Then `PsiElementRenameHandler` would use the scope-aware reference to
find rename targets.

**Changes required**:
- Make `CrystalReference.resolve()` scope-aware for local variables
- Make `CrystalVariableReference` implement `PsiNameIdentifierOwner` (so the handler can activate)
- No handler or processor registration changes

**Risk**: LOW — minimal changes to the rename framework. The scope-awareness is in
the reference resolution, not in the rename handler logic.

**Compatibility**: HIGH — works with the existing `PsiElementRenameHandler` (dialog-based).

**Note**: This is essentially Approach A with scope-aware reference resolution added.

### 5.4 Approach D: No scope-awareness, just fix PsiNameIdentifierOwner

**Concept**: Make `CrystalVariableReference`, `CrystalParameter`, and `CrystalAssignment`
implement `PsiNameIdentifierOwner` so that rename works at all for these elements.
Keep the existing token-based approach (rename all same-text tokens in the file).
Scope-awareness is a separate future task.

**Changes required**:
- `CrystalVariableReferenceMixin`: add `PsiNameIdentifierOwner`
- `CrystalParameter`: add mixin
- `CrystalAssignment`: add mixin
- `CrystalRefactoringSupportProvider`: override `isInplaceRenameAvailable`

**Risk**: LOWEST — only adds interfaces to existing composites. No handler changes.
No scope-awareness (token-based rename continues).

**Compatibility**: HIGHEST — follows the proven `CrystalInstanceVarAccessMixin` pattern.

**Testing**: Simple — verify rename opens for local variables, parameters, and assignments.

---

## 6. Recommendation

**Start with Approach D** (fix PsiNameIdentifierOwner, no scope-awareness).

This is the lowest-risk path that gets rename working for all element types. Once
rename works for local variables and parameters, scope-awareness can be added
incrementally via Approach C (scope-aware PsiReference) in a separate change.

### 6.1 Implementation order

1. Make `CrystalVariableReferenceMixin` implement `PsiNameIdentifierOwner`
2. Add mixin for `CrystalParameter` implementing `PsiNameIdentifierOwner`
3. Add mixin for `CrystalAssignment` implementing `PsiNameIdentifierOwner`
4. Override `isInplaceRenameAvailable` in `CrystalRefactoringSupportProvider`
5. Tests: verify rename works for local vars, params, assignments, methods, instance vars
6. ~~(Future)~~ Make `CrystalReference.resolveLocal()` scope-aware — **Done**: `resolveLocal()` now finds variable assignments via recursive subtree search (`findAssignmentWithName`), stops at method/macro/class boundaries
7. (Future) Full scope-aware rename: collect all references in same scope for rename target set

### 6.2 What to NEVER do

- **Never register `renamePsiElementProcessor`** — this extension point is incompatible
  with Crystal's PSI architecture. It was tested and broke rename entirely.
- **Never override `isInplaceRenameAvailable` to return true without making elements
  PsiNameIdentifierOwner first** — `MemberInplaceRenameHandler` requires both.
- **Never change `isMemberInplaceRenameAvailable` to accept composite types without
  also handling the `doRename` flow** — the handler will activate but then fail.

---

## 7. resolveLocal() Fix — Variable Assignment Discovery

### 7.1 Problem

`resolveLocal()` could not find variable assignments like `x = 1` when resolving
a reference to `x`. The sibling-walking logic checked `sibling.firstChild`, but
sibling nodes in the PSI tree are wrapped in `CrystalStatement` composites —
`firstChild` returns the statement wrapper, not the IDENTIFIER leaf.

### 7.2 Solution

Added `findAssignmentWithName()` — a recursive subtree search that:

1. Walks the sibling's PSI subtree looking for `CrystalAssignment` nodes
2. Uses `(element as PsiNameIdentifierOwner).name` to match the variable name
3. **Stops at scope boundaries**: `CrystalMethodDefinition`, `CrystalMacroDefinition`,
   `CrystalClassDefinition`, `CrystalModuleDefinition`, `CrystalStructDefinition`,
   `CrystalEnumDefinition` — prevents resolving across method/class boundaries

### 7.3 Scope boundary behavior

The scope boundary check in `findAssignmentWithName` means:

```crystal
def other
  x = 99  # ← NOT visible from greet()
end

def greet
  puts x   # ← resolveLocal() returns null (not found locally)
end
```

This is correct behavior: `x` in `greet()` should not resolve to the assignment
in `other()`. If `x` is undefined in `greet()`, it falls through to StubIndex
lookup (which also returns null for local variables), resulting in an unresolved
reference — which is the expected behavior for an undefined variable.

---

## 8. Existing Infrastructure (reuse these)

| Component | File | Purpose |
|-----------|------|---------|
| `CrystalInstanceVarAccessMixin` | `psi/impl/CrystalInstanceVarAccessMixin.kt` | Template for PsiNameIdentifierOwner mixin |
| `CrystalNamedElement` | `psi/CrystalNamedElement.kt` | Interface for PsiNameIdentifierOwner elements |
| `CrystalReference` | `psi/CrystalReference.kt` | PsiReference with local scope fallback + `findAssignmentWithName` |
| `CrystalRenameVerifier` | `refactoring/CrystalRenameVerifier.kt` | Post-rename compiler verification |
| `CrystalNamesValidator` | `refactoring/CrystalNamesValidator.kt` | Identifier validation |
| `CrystalRefactoringSupportProvider` | `refactoring/CrystalRefactoringSupportProvider.kt` | Refactoring availability |
| `setNameOnIdentifier` | `psi/impl/CrystalStubbedElements.kt:66` | Helper for setName implementation |
| `CrystalInstanceVarReference` | `psi/CrystalInstanceVarReference.kt` | Instance var reference (scope-aware) |

---

## 9. Test Strategy

### 8.1 Existing tests (do not break)

- `CrystalGotoDeclarationTest` — Go to Definition for methods, instance vars
- `CrystalCompletionTest` — completion for methods, variables, types
- `CrystalParameterInfoHandlerTest` — parameter info at call sites
- All inspection tests — type check, argument count, unused variables

### 8.2 Tests in `CrystalRenamePsiNameIdentifierOwnerTest`

| Test | What it verifies | Status |
|------|-----------------|--------|
| `testVariableReferenceImplementsPsiNameIdentifierOwner` | CrystalVariableReference is PsiNameIdentifierOwner | Done |
| `testVariableReferenceGetNameIdentifier` | nameIdentifier returns IDENTIFIER leaf | Done |
| `testVariableReferenceGetName` | getName returns identifier text | Done |
| `testVariableReferenceConstantGetNameIdentifier` | Constants (Foo) have nameIdentifier | Done |
| `testParameterImplementsPsiNameIdentifierOwner` | CrystalParameter is PsiNameIdentifierOwner | Done |
| `testParameterGetNameIdentifier` | nameIdentifier returns last IDENTIFIER (internal name) | Done |
| `testParameterGetName` | getName returns parameter name | Done |
| `testParameterWithExternalNameUsesInternalName` | `def foo(user_name name)` returns "name" | Done |
| `testAssignmentImplementsPsiNameIdentifierOwner` | CrystalAssignment is PsiNameIdentifierOwner | Done |
| `testAssignmentGetNameIdentifier` | nameIdentifier returns LHS IDENTIFIER | Done |
| `testAssignmentGetName` | getName returns variable name | Done |
| `testResolveParameterReturnsComposite` | resolve() returns CrystalParameter (not IDENTIFIER leaf) | Done |
| `testResolveMethodReturnsMethodDefinition` | resolve() finds method via StubIndex | Done |
| `testResolveClassConstantReturnsClassDefinition` | resolve() finds class via StubIndex | Done |
| `testResolveLocalFindsVariableAssignment` | resolve() finds CrystalAssignment for local variable | Done |
| `testResolveLocalFindsAssignmentBeforeMultipleStatements` | resolve() finds assignment across intervening statements | Done |
| `testResolveLocalDoesNotCrossMethodBoundary` | resolve() does not find assignment in sibling method | Done |
| `testRenameUsesDefaultProcessorForAllElements` | No custom renamePsiElementProcessor registered | Done |

### 8.3 Remaining test gaps (future)

| Test | What it verifies |
|------|-----------------|
| Rename local variable end-to-end | `myFixture.renameElementAtCaret()` renames `x` to `y` |
| Rename parameter end-to-end | `myFixture.renameElementAtCaret()` renames param |
| Scope-aware rename isolation | Renaming `x` in method A doesn't change `x` in method B |
