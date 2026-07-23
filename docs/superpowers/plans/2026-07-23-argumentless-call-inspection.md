# Argumentless Call Inspection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Report missing required arguments for argumentless `def` calls without parentheses while suppressing ambiguous, shadowed, or inapplicable declarations.

**Architecture:** Extend `CrystalArgumentCountInspection` with two zero-argument discovery paths that feed the existing overload evaluator an empty argument list. Direct references use only `CrystalTopLevelMethodIndex` after local/declaration shadowing; constant DOT-calls use only exact receiver `def self.<name>` overloads extracted through `CrystalCallExtractor`. Existing argument-bearing calls, constructors, records, inferred instance receivers, and `lib fun` calls remain unchanged.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2026.1, PSI visitors, StubIndex, JUnit 4 `BasePlatformTestCase`, Gradle 9.4.1, JDK 21.

## Global Constraints

- Follow `docs/specs/call-argument-inspections.md` exactly.
- Use `CrystalTypes` as the single source of truth for token and element types.
- Runtime lookup must use `CrystalIndexService`; never scan project files through `FileTypeIndex`.
- Direct argumentless calls query only `CrystalTopLevelMethodIndex`.
- Constant DOT-calls query only exact receiver `def self.<name>` overloads.
- `.new`, records, inferred instance receivers, inherited-only receivers, ambiguous receivers, and `lib fun` are outside this change.
- Preserve parenthesized, bare-argument, excess-argument, type-checking, constructor, and record behavior.
- Use `ProblemHighlightType.GENERIC_ERROR` and highlight the innermost method-name element.
- No serialized stub fields or index emission semantics change, so the file stub version remains unchanged.
- Every implementation change requires regression coverage.
- Update `CHANGELOG.md` after implementation.
- Run `./gradlew test` before the final commit.
- The user's `go` explicitly authorizes implementation commits.

---

### Task 1: Validate Direct Argumentless Top-Level Calls

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspection.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/psi/CrystalReference.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspectionTest.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/CrystalReferenceTest.kt`

**Interfaces:**
- Consumes: `CrystalIndexService.findTopLevelMethods(name, project, scope)` and `CrystalIndexService.findMacros(name, project, scope)`.
- Produces: a `CrystalVariableReference` visitor path that invokes existing `checkArgumentCount(methods, emptyList(), nameElement, holder)` only for applicable unqualified calls.
- Produces: local parameter resolution based on `CrystalParameter.nameIdentifier`, preserving the internal name from `external internal` declarations.

- [ ] **Step 1: Add failing required/optional classification tests**

Add argumentless no-parentheses highlighting cases to `CrystalArgumentCountInspectionTest`:

```kotlin
fun testArgumentlessDirectCallReportsRequiredParameters() {
    myFixture.configureByText("test.cr", """
        def process(untyped, typed : Int32, nilable : String?)
        end
        <error descr="Missing required argument(s): 'untyped', 'typed', 'nilable'">process</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArgumentlessDirectCallAcceptsOptionalParameters() {
    myFixture.configureByText("test.cr", """
        def configure(value : String? = nil, *rest, **options)
        end
        configure
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArgumentlessDirectCallReportsRequiredParametersAroundVariadics() {
    myFixture.configureByText("test.cr", """
        def configure(nilable : String?, optional = 1, *rest, named_required, named_optional = 2, **options)
        end
        <error descr="Missing required argument(s): 'nilable', 'named_required'">configure</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Add failing overload and expression-position tests**

Cover an accepting zero-argument overload, a uniquely closest rejecting overload, and nested expression positions:

```kotlin
fun testArgumentlessDirectCallAcceptsMatchingOverload() {
    myFixture.configureByText("test.cr", """
        def process(value)
        end
        def process
        end
        process
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArgumentlessDirectCallUsesUniquelyClosestOverload() {
    myFixture.configureByText("test.cr", """
        def process(value)
        end
        def process(first, second)
        end
        <error descr="Missing required argument(s): 'value'">process</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArgumentlessDirectCallInsideExpressionIsChecked() {
    myFixture.configureByText("test.cr", """
        def load(path)
        end
        result = <error descr="Missing required argument(s): 'path'">load</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 3: Add failing false-positive and ownership tests**

Add tests proving that prior local assignments, regular parameters, block parameters, internal parameter names, type declarations, same-named macros, unrelated enclosed methods, unknown names, parenthesized calls, and bare-argument calls do not produce a zero-argument diagnostic. Use at least these collision forms:

```crystal
def callback(required)
end

def wrapper(external callback)
  callback
end
```

```crystal
def refresh(required)
end

macro refresh
end

refresh
```

```crystal
class Other
  def refresh(required)
  end
end

refresh
```

Keep existing `greet()`, `greet "Hans"`, and argument-count tests in the fixture so duplicate visitor ownership would fail highlighting.

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalArgumentCountInspectionTest" --tests "de.magynhard.crystal.CrystalReferenceTest"
```

Expected: new argumentless direct-call tests fail because `CrystalVariableReference` is not visited; the internal-name reference test fails because local parameter resolution uses the first identifier.

- [ ] **Step 5: Implement internal-name parameter resolution**

In both method/macro and block parameter loops in `CrystalReference.resolveLocal()`, replace first-token extraction with the PSI owner's internal name:

```kotlin
val owner = param as? PsiNameIdentifierOwner
owner?.nameIdentifier?.takeIf { owner.name == name }?.let { return it }
```

Keep all existing scope boundaries and file traversal guards unchanged.

- [ ] **Step 6: Implement direct argumentless call discovery**

Extend the inspection visitor with `CrystalVariableReference`. Add a focused helper that:

1. Extracts the direct `IDENTIFIER` or `CONSTANT` method-name leaf.
2. Rejects receiver and namespace positions and any PSI already owned by explicit call handling.
3. Resolves local/declaration shadowing and returns when the target is not a method.
4. Suppresses the candidate when `CrystalIndexService.findMacros(name, project, scope)` is non-empty.
5. Queries `CrystalIndexService.findTopLevelMethods(name, project, scope)` only.
6. Calls `checkArgumentCount(methods, emptyList(), methodNameElement, holder)` when candidates exist.

Do not use `CrystalMethodIndex` or any file scan in this path.

- [ ] **Step 7: Run focused tests and verify GREEN**

Run the command from Step 4. Expected: all direct-call and reference tests pass.

- [ ] **Step 8: Commit Task 1**

Inspect `git status`, `git diff`, and `git log --oneline -10`; stage only Task 1 files and commit:

```bash
git commit -m "fix(inspections): validate argumentless direct calls"
```

### Task 2: Validate Exact Constant DOT-Calls

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalCallExtractor.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspection.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalArgumentCountInspectionTest.kt`

**Interfaces:**
- Produces: `ArgumentlessDotCallInfo(receiverName: String, qualifiedReceiverName: String, methodName: String, methodNameElement: PsiElement)`.
- Produces: `CrystalCallExtractor.detectArgumentlessConstantDotCall(access: CrystalDotCallAccess): ArgumentlessDotCallInfo?`.
- Consumes: `CrystalIndexService.findMethodsByClass(receiverName, project, scope)`, `CrystalPsiUtils.isSelfMethod()`, `CrystalPsiUtils.getEnclosingType()`, and `CrystalPsiUtils.buildQualifiedName()`.

- [ ] **Step 1: Add failing positive DOT-call tests**

Add tests for direct and qualified constant receivers:

```kotlin
fun testArgumentlessClassMethodReportsRequiredParameter() {
    myFixture.configureByText("test.cr", """
        class Factory
          def self.create(name : String?)
          end
        end
        Factory.<error descr="Missing required argument(s): 'name'">create</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArgumentlessQualifiedClassMethodReportsRequiredParameter() {
    myFixture.configureByText("test.cr", """
        module Outer
          class Factory
            def self.create(name)
            end
          end
        end
        Outer::Factory.<error descr="Missing required argument(s): 'name'">create</error>
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Add failing applicability and suppression tests**

Cover all exact-candidate rules:

- An unrelated `Other::Factory` or another simple-name receiver cannot affect `Outer::Factory.create`.
- An instance `def create` cannot satisfy or invalidate `Factory.create`.
- A top-level `def create` cannot satisfy or invalidate `Factory.create`.
- All exact `def self.create` overloads are evaluated, including one accepting zero arguments.
- Lowercase/inferred receivers, inherited-only methods, unknown receivers, and `.new` remain unchanged without new diagnostics.
- Existing `Factory.create()`, `Factory.create value`, and argument-bearing DOT tests remain single-owned and produce no duplicate diagnostic.

- [ ] **Step 3: Run focused inspection tests and verify RED**

Run:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalArgumentCountInspectionTest"
```

Expected: new argumentless DOT-call tests fail because only argument-list PSI currently triggers `checkDotCall()`.

- [ ] **Step 4: Implement constant DOT-call extraction**

In `CrystalCallExtractor`, add the immutable `ArgumentlessDotCallInfo` model and `detectArgumentlessConstantDotCall()` that:

1. Returns `null` when `callArgs` or `bareArgumentList` exists.
2. Extracts the method-name leaf and rejects `new`.
3. Reads the preceding constant or `CrystalNamespaceAccess` receiver.
4. Uses `CrystalPsiUtils.buildNamespacePath()` for qualified receivers.
5. Returns both the simple receiver key used by `CrystalMethodByClassIndex` and the full qualified receiver identity.
6. Returns `null` for lowercase, inferred, malformed, or unknown receiver shapes.

- [ ] **Step 5: Implement exact DOT candidate filtering**

Visit `CrystalDotCallAccess` directly. For extracted argumentless info:

```kotlin
val methods = CrystalIndexService.findMethodsByClass(info.receiverName, project, scope)
    .filter { it.name == info.methodName }
    .filter(CrystalPsiUtils::isSelfMethod)
    .filter { method ->
        CrystalPsiUtils.getEnclosingType(method)
            ?.let(CrystalPsiUtils::buildQualifiedName) == info.qualifiedReceiverName
    }
```

Pass all exact candidates and `emptyList()` to the existing evaluator. Do not retain instance, top-level, inherited, record, initialize, or unrelated qualified candidates.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the command from Step 3. Expected: all argument-count inspection tests pass.

- [ ] **Step 7: Commit Task 2**

Inspect status/diff/log, stage only Task 2 files, and commit:

```bash
git commit -m "fix(inspections): validate argumentless class calls"
```

### Task 3: Document And Verify The Completed Behavior

**Files:**
- Modify: `CHANGELOG.md`
- Verify: `docs/specs/call-argument-inspections.md`
- Verify: `TODO.md`
- Include: `docs/superpowers/plans/2026-07-23-argumentless-call-inspection.md`

**Interfaces:**
- Consumes: completed direct and DOT argumentless call inspection behavior.
- Produces: a current-version changelog entry and complete verification evidence.

- [ ] **Step 1: Update the changelog**

Add one bullet under the current `### Fixed` section explaining that argumentless `greet` and exact constant `Factory.create` calls now report missing signature-required parameters, while defaults, nilable annotations, splats, overloads, and shadowing follow the documented rules.

- [ ] **Step 2: Check spec and TODO alignment**

Verify every implementation behavior appears in `docs/specs/call-argument-inspections.md` and every deferred item remains in `TODO.md`. Do not broaden implementation to deferred `lib fun`, named-only positional enforcement, external call-site names, signature ordering, inherited/inferred calls, overload ties, or complete call precedence.

- [ ] **Step 3: Run focused tests freshly**

Run:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalArgumentCountInspectionTest" --tests "de.magynhard.crystal.CrystalReferenceTest" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 4: Run the full suite freshly**

Run:

```bash
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failed tests. Existing unrelated compiler warnings may remain.

- [ ] **Step 5: Review the complete diff**

Run `git status --short`, `git diff --check`, `git diff 6cd0aa4..HEAD`, and `git log --oneline -10`. Confirm generated files and stub version are unchanged and no temporary artifacts are tracked.

- [ ] **Step 6: Commit Task 3**

Stage only `CHANGELOG.md` and the implementation plan, then commit:

```bash
git commit -m "docs(inspections): document argumentless call checks"
```
