# Instance/Class Variable `@` Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the caret is inside a method of a class and the user types `@`, suggest all instance variables (`@name`) and class variables (`@@name`) of the enclosing class — and likewise for `@@` (class vars only). Works both via manual Ctrl+Space and via auto-popup as `@` is typed.

**Architecture:** Two fixes in the completion layer plus one new extension point:
1. **Sigil-aware prefix matcher** — derive the completion prefix from the raw document so a leading `@`/`@@` is treated as part of the variable name. Route all free-text completions through this matcher so `@`/`@@` vars match even when only the sigil is typed.
2. **Class-scoped variable collection** — instance/class variables are collected from the *enclosing class* (all its methods), not just the current method; local variables stay method/block-scoped with the existing forward-reference guard.
3. **CompletionConfidence** — force the auto-popup to appear when the char before the caret is `@` (outside string literals and annotation context).

**Tech Stack:** Kotlin, IntelliJ Platform PSI + Completion API, JUnit 4 (BasePlatformTestCase)

## Global Constraints

- Project language: English (code, comments, commits, docs)
- Build: `./gradlew build` / `./gradlew test`
- All extensions registered in `src/main/resources/META-INF/plugin.xml`
- Never add `recoverWhile` to BNF rules
- AGENTS.md: every change needs a CHANGELOG.md entry; update `docs/specs/completion.md`

---

### Task 1: Sigil-aware prefix matcher

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt` (addCompletions, ~lines 155-168)

**Interfaces:**
- Consumes: `parameters.editor`, `parameters.offset`
- Produces: an effective `CompletionResultSet` whose prefix matcher includes leading `@`/`@@`

- [ ] **Step 1: Add a helper to compute the sigil-aware prefix**

Add a file-level (top-level, `internal`) function in `CrystalCompletionContributor.kt`:

```kotlin
/**
 * Computes the completion prefix from the raw document text before the caret,
 * treating a leading `@` or `@@` as part of the variable name.
 *
 * Examples (text before caret → returned prefix):
 *   "@"          → "@"
 *   "@@"         → "@@"
 *   "@@vari"     → "@@vari"
 *   "@foo"       → "@foo"
 *   "mein"       → "mein"
 *   "Str"        → "Str"
 *   "foo.bar"    → "bar"
 */
internal fun computeCompletionPrefix(editor: com.intellij.openapi.editor.Editor, offset: Int): String {
    val text = editor.document.charsSequence.subSequence(0, offset).toString()
    val match = "([@]@?[A-Za-z0-9_]*)$".toRegex().find(text)
    return match?.groupValues?.get(1) ?: ""
}
```

- [ ] **Step 2: Use the sigil-aware prefix in addCompletions**

Replace the existing block (lines 155-168):

```kotlin
            // Case 3: Free-text completion — scope items + classes (uppercase only)
            val prefix = result.prefixMatcher.prefix
            val isUppercase = prefix.isNotEmpty() && prefix[0].isUpperCase()

            addLocalCompletions(position, result)

            // Only suggest classes and stdlib types when prefix starts with uppercase
            // (Crystal class names start with uppercase, e.g. Int32, String, Array)
            if (prefix.isEmpty() || isUppercase) {
                for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                    result.addElement(lookup)
                }
                addAllClasses(project, result)
            }
```

with:

```kotlin
            // Case 3: Free-text completion — scope items + classes (uppercase only)
            // Treat a leading @ / @@ as part of the variable name so instance (@foo)
            // and class (@@bar) variables are suggested even when only the sigil is typed.
            val actualPrefix = computeCompletionPrefix(parameters.editor, parameters.offset)
            val isVarPrefix = actualPrefix.startsWith("@")
            val effectiveResult = if (isVarPrefix) {
                result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(actualPrefix))
            } else {
                result
            }
            val isUppercase = actualPrefix.isNotEmpty() && actualPrefix[0].isUpperCase()

            addLocalCompletions(position, effectiveResult)

            // Only suggest classes and stdlib types when prefix starts with uppercase.
            // When the prefix is a variable sigil (@ / @@), skip classes entirely.
            if (actualPrefix.isEmpty() || isUppercase) {
                for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                    effectiveResult.addElement(lookup)
                }
                addAllClasses(project, effectiveResult)
            }
```

- [ ] **Step 3: Route class-method completions through effectiveResult**

In Case 5 (lines 250-265), `addClassMethods(... result ...)` is called twice. Change both to `effectiveResult` so they are filtered by the sigil matcher (methods never start with `@`, so they're hidden for the `@` prefix — correct):

```kotlin
            if (method != null) {
                val enclosingClassName = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosingClassName != null) {
                    val project = position.project
                    val searchScope = GlobalSearchScope.allScope(project)
                    addClassMethods(enclosingClassName, 30.0, searchScope, project, seen, effectiveResult)

                    val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
                    val superClassName = enclosingClass?.superclassClause?.typeReference?.text
                    if (superClassName != null && superClassName != enclosingClassName) {
                        addClassMethods(superClassName, 20.0, searchScope, project, seen, effectiveResult)
                    }
                }
            }
```

Note: `effectiveResult` is declared inside the `addCompletions` body before Case 5, so it is in scope here (no early `return` between Case 3 and Case 5 in the current code path — Case 5 is reached only after the dot/colon early-returns, and for the `@` prefix none of those early-returns fire).

- [ ] **Step 4: Run existing completion tests to verify no regression**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalCompletionTest"`
Expected: All existing tests pass (the sigil-aware prefix equals the old prefix for non-`@` inputs)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt
git commit -m "fix: sigil-aware completion prefix so @/@@ vars match when only sigil typed"
```

---

### Task 2: Class-scoped instance/class variable collection

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt` (addLocalCompletions, lines 218-248)

**Interfaces:**
- Consumes: `CrystalClassDefinition`, `CrystalInstanceVarAccess`, `CrystalClassVarAccess`
- Produces: instance/class vars from the enclosing class added to `result`

- [ ] **Step 1: Replace the local-variable/var collection block**

Replace the block (lines 218-248):

```kotlin
            // 4. Local variables + instance/class variables (scope-aware)
            //    Scope: method → block → file (Crystal is method-scoped)
            val scope = findCompletionScope(position)
            if (scope != null) {
                val assignments = PsiTreeUtil.findChildrenOfType(scope, CrystalAssignment::class.java)
                for (assignment in assignments) {
                    if (assignment.textOffset >= position.textOffset) continue
                    val child = assignment.firstChild ?: continue
                    // Handle raw IDENTIFIER tokens (local variables)
                    if (child.node?.elementType == CrystalTypes.IDENTIFIER) {
                        val text = child.text ?: continue
                        if (seen.add(text)) {
                            result.addElement(prioritizedLookup(text, AllIcons.Nodes.Variable, "local", 50.0))
                        }
                    }
                    // Handle INSTANCE_VAR inside CrystalInstanceVarAccess composite
                    if (child is CrystalInstanceVarAccess) {
                        val name = child.name
                        if (name != null && seen.add(name)) {
                            result.addElement(prioritizedLookup(name, AllIcons.Nodes.Variable, "instance variable", 40.0))
                        }
                    }
                    // Handle CLASS_VAR inside CrystalClassVarAccess composite
                    if (child is CrystalClassVarAccess) {
                        val name = child.name
                        if (name != null && seen.add(name)) {
                            result.addElement(prioritizedLookup(name, AllIcons.Nodes.Variable, "class variable", 40.0))
                        }
                    }
                }
            }
```

with a split implementation — locals stay method/block scoped with the forward-reference guard; instance/class vars come from the enclosing class (all methods) without the offset guard (they are class fields, available regardless of definition order):

```kotlin
            // 4. Local variables (method/block scoped, forward-reference excluded)
            val scope = findCompletionScope(position)
            if (scope != null) {
                val assignments = PsiTreeUtil.findChildrenOfType(scope, CrystalAssignment::class.java)
                for (assignment in assignments) {
                    if (assignment.textOffset >= position.textOffset) continue
                    val child = assignment.firstChild ?: continue
                    if (child.node?.elementType == CrystalTypes.IDENTIFIER) {
                        val text = child.text ?: continue
                        if (seen.add(text)) {
                            result.addElement(prioritizedLookup(text, AllIcons.Nodes.Variable, "local", 50.0))
                        }
                    }
                }
            }

            // 4b. Instance + class variables of the enclosing class (all methods).
            //     These are class fields, available throughout the class regardless
            //     of which method defines them or the caret position.
            val enclosingClass = PsiTreeUtil.getParentOfType(position, CrystalClassDefinition::class.java)
            val varScope = enclosingClass ?: position.containingFile
            collectClassVariables(varScope, enclosingClass != null) { name, typeText ->
                if (seen.add(name)) {
                    result.addElement(prioritizedLookup(name, AllIcons.Nodes.Variable, typeText, 40.0))
                }
            }
```

- [ ] **Step 2: Add the collectClassVariables helper**

Add this `private` method to `CrystalCompletionProvider`:

```kotlin
        /**
         * Collects @instance and @@class variables from [root].
         *
         * When [rootIsClass] is true, recursion stops at nested class/module/struct/enum
         * definitions so that variables of an inner class don't leak into the outer class.
         */
        private fun collectClassVariables(
            root: PsiElement,
            rootIsClass: Boolean,
            add: (name: String, typeText: String) -> Unit
        ) {
            fun visit(element: PsiElement) {
                // Stop at nested class-defining elements (but always process the root class)
                if (rootIsClass && element !== root &&
                    (element is CrystalClassDefinition || element is CrystalModuleDefinition ||
                     element is CrystalStructDefinition || element is CrystalEnumDefinition)) {
                    return
                }
                when (element) {
                    is CrystalInstanceVarAccess -> {
                        val name = element.name
                        if (name != null) add(name, "instance variable")
                    }
                    is CrystalClassVarAccess -> {
                        val name = element.name
                        if (name != null) add(name, "class variable")
                    }
                }
                for (child in element.children) visit(child)
            }
            for (child in root.children) visit(child)
        }
```

- [ ] **Step 3: Run completion tests**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalCompletionTest"`
Expected: Pass

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt
git commit -m "fix: collect instance/class variables from enclosing class, not just current method"
```

---

### Task 3: CompletionConfidence to auto-show popup on `@`

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/completion/CrystalAtCompletionConfidence.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (register `completion.confidence` EP)

**Interfaces:**
- Consumes: `isInsideStringLiteral` (existing private function in `CrystalCompletionContributor.kt` — promote to file-level `internal`)
- Produces: auto-popup triggered after typing `@`

- [ ] **Step 1: Promote isInsideStringLiteral to file-level internal**

In `CrystalCompletionContributor.kt`, move the existing `private fun isInsideStringLiteral(position: PsiElement): Boolean` (defined further down in the file) to a **file-level** `internal fun` so it can be reused. Keep its body unchanged.

- [ ] **Step 2: Create CrystalAtCompletionConfidence**

```kotlin
package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionConfidence.Resolution
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Forces the completion auto-popup to appear when the char immediately before
 * the caret is `@`. By default IntelliJ only auto-shows the popup on identifier
 * characters, so typing `@` (the instance/class variable sigil) would not open it.
 *
 * Annotation context (`@[`) is handled by the contributor's early return and is
 * excluded here (string literals never trigger; `@[` will be taken over by the
 * annotation provider once `[` is typed).
 */
class CrystalAtCompletionConfidence : CompletionConfidence() {
    override fun shouldShowLookups(editor: Editor, file: PsiFile, offset: Int): Resolution {
        if (offset <= 0) return Resolution.undefined()
        val charBefore = editor.document.charsSequence[offset - 1]
        if (charBefore != '@') return Resolution.undefined()
        val element = file.findElementAt(offset - 1) ?: return Resolution.undefined()
        if (isInsideStringLiteral(element)) return Resolution.undefined()
        return Resolution(true)
    }
}
```

- [ ] **Step 3: Register in plugin.xml**

Add inside the `<extensions>` block of `src/main/resources/META-INF/plugin.xml`:

```xml
<completion.confidence implementation="de.magynhard.crystal.completion.CrystalAtCompletionConfidence"/>
```

- [ ] **Step 4: Build to verify registration compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalAtCompletionConfidence.kt src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt src/main/resources/META-INF/plugin.xml
git commit -m "fix: auto-show completion popup after @ for instance/class variables"
```

---

### Task 4: Tests

**Files:**
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt`

**Interfaces:**
- Consumes: the three fixes above

- [ ] **Step 1: Test `@` suggests class instance + class vars (defined in another method)**

```kotlin
fun testAtPrefixSuggestsClassInstanceAndClassVars() {
    myFixture.configureByText("main.cr", """
        class Apfel
          def initialize
            @name = "x"
            @@count = 1
          end

          def foo
            @<caret>
          end
        end
    """.trimIndent())
    val lookups = myFixture.complete(CompletionType.BASIC)
    assertNotNull("Should return completions", lookups)
    val names = lookups.map { it.lookupString }
    assertTrue("Should contain instance var @name", names.contains("@name"))
    assertTrue("Should contain class var @@count", names.contains("@@count"))
}

fun testAtAtPrefixSuggestsOnlyClassVars() {
    myFixture.configureByText("main.cr", """
        class Apfel
          def initialize
            @name = "x"
            @@count = 1
          end

          def foo
            @@<caret>
          end
        end
    """.trimIndent())
    val lookups = myFixture.complete(CompletionType.BASIC)
    assertNotNull("Should return completions", lookups)
    val names = lookups.map { it.lookupString }
    assertTrue("Should contain class var @@count", names.contains("@@count"))
    assertFalse("Should NOT contain instance var @name for @@ prefix", names.contains("@name"))
}
```

- [ ] **Step 2: Test typing a name part still works (regression guard)**

```kotlin
fun testAtPrefixWithNamePartMatchesClassVar() {
    myFixture.configureByText("main.cr", """
        class Apfel
          def initialize
            @@variata = 1
          end

          def foo
            @<caret>var
          end
        end
    """.trimIndent())
    val lookups = myFixture.complete(CompletionType.BASIC)
    assertNotNull("Should return completions", lookups)
    val names = lookups.map { it.lookupString }
    assertTrue("Should contain @@variata", names.contains("@@variata"))
}
```

- [ ] **Step 3: Test that no class names pollute the `@` popup**

```kotlin
fun testAtPrefixExcludesClassNames() {
    myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
    myFixture.configureByText("main.cr", """
        class Birne
          def foo
            @<caret>
          end
        end
    """.trimIndent())
    val lookups = myFixture.complete(CompletionType.BASIC)
    val names = lookups?.map { it.lookupString } ?: emptyList()
    assertFalse("Should NOT suggest class names for @ prefix", names.contains("Apfel"))
}
```

- [ ] **Step 4: Test nested class isolation**

```kotlin
fun testAtPrefixDoesNotLeakNestedClassVars() {
    myFixture.configureByText("main.cr", """
        class Outer
          def initialize
            @outer_var = 1
          end

          class Inner
            def initialize
              @inner_var = 2
            end
          end

          def foo
            @<caret>
          end
        end
    """.trimIndent())
    val lookups = myFixture.complete(CompletionType.BASIC)
    assertNotNull("Should return completions", lookups)
    val names = lookups.map { it.lookupString }
    assertTrue("Should contain @outer_var", names.contains("@outer_var"))
    assertFalse("Should NOT contain nested @inner_var", names.contains("@inner_var"))
}
```

- [ ] **Step 5: Run completion tests**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalCompletionTest"`
Expected: All pass

- [ ] **Step 6: Commit**

```bash
git add src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt
git commit -m "test: add @/@@ completion tests for instance/class variables"
```

---

### Task 5: Docs + CHANGELOG + full verification

- [ ] **Step 1: Update docs/specs/completion.md**

In the "Instance and Class Variables" section (lines 142-157), replace the prose with the precise behaviour and add notes about the sigil-aware prefix and class scope:

```markdown
#### Instance and Class Variables

Instance variables (`@var`) and class variables (`@@var`) of the **enclosing class** are suggested as soon as `@` (or `@@`) is typed — not only after a name character. They are collected from **all methods of the enclosing class** (e.g. `@name` defined in `initialize` is offered inside `greet`), because they are class fields available throughout the class.

- Typing `@`  → both `@instance` and `@@class` variables are offered.
- Typing `@@` → only `@@class` variables are offered.
- Typing `@na` → only `@name` (and any `@@name`-style) matches the prefix.

The auto-popup appears on `@` (via `CrystalAtCompletionConfidence`). String literals and `@[` annotation context are excluded.

```crystal
class Foo
  def initialize
    @name = "hello"
    @@count = 0
  end

  def greet
    @  # ← @name, @@count suggested (both)
    @@ # ← @@count suggested (class vars only)
  end
end
```

Nested classes are isolated: an inner class's `@vars` are not offered in the outer class.

**Priority:** 40
```

- [ ] **Step 2: Add CHANGELOG.md entry under [0.1.18]

Add a "Fixed" bullet (the section already exists from the block-parameter fix):

```
- **Instance/class variable `@` completion** — typing `@` (or `@@`) inside a method now suggests all instance (`@name`) and class (`@@name`) variables of the enclosing class, with the auto-popup appearing as the sigil is typed. Previously a bare `@`/`@@` produced no suggestions because the lexer emits a standalone `AT` token and the default prefix matcher ignored the sigil; variables defined in a different method (e.g. `initialize`) were also missing because collection was limited to the current method. Fixed by a sigil-aware prefix matcher, class-scoped variable collection, and a `CrystalAtCompletionConfidence` extension point.
```

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass (note: 18 pre-existing failures in CrystalArgumentCountInspectionTest / CrystalTypeCheckInspectionTest are unrelated — verify they match the baseline via `git stash` before/after if unsure)

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add docs/specs/completion.md CHANGELOG.md
git commit -m "docs: document @/@@ instance/class variable completion behaviour"
```
