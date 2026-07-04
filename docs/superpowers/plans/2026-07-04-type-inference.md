# Type Inference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `CrystalTypeInference` and `CrystalExpressionTypeResolver` to infer types for scalar literals, trivial expressions, collection literals, control-flow unions, multi-assignment/instance vars, and operator result types.

**Architecture:** Two systems are extended independently: `CrystalTypeInference` (variable-level, used by completion/goto) gets literal assignment inference, and `CrystalExpressionTypeResolver` (expression-level, used by type-check inspection) gets collection/control-flow/operator inference. Both share regex-based pattern matching for method calls (existing code). Tests use `BasePlatformTestCase` for inspection tests and pure JUnit 4 for unit tests.

**Tech Stack:** Kotlin, JUnit 4, IntelliJ Platform SDK (`BasePlatformTestCase`), PsiTreeUtil

## Global Constraints

- JDK 21, Gradle 9.4.1 (wrapper committed)
- Tests: `./gradlew test` — all must pass before commit
- Generated sources in `src/main/gen/` are committed; regenerate after `.flex`/`.bnf` changes
- No comments in code unless requested
- English is the project language

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt` | Modify | Extend `inferTypeFromExpression` for scalar literals |
| `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt` | Modify | Extend `resolveType` for collections, control-flow, trivial expressions, operators |
| `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt` | Create | Unit tests for `CrystalTypeInference` literal inference |
| `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt` | Modify | Add inspection tests for new expression types |

---

### Task 1: Scalar Literal Inference in CrystalTypeInference

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt:96-129`
- Create: `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt`

**Interfaces:**
- Consumes: `CrystalAssignment.expression` PSI element
- Produces: `inferTypeFromExpression` returns type name string for literal RHS

- [ ] **Step 1: Create test class with failing tests for scalar literals**

Create `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt`:

```kotlin
package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.completion.CrystalTypeInference

class CrystalTypeInferenceTest : BasePlatformTestCase() {

    fun testInferIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1")
        val expr = myFixture.file.children.last().children.last() // assignment expression
        // Use the public API: inferType looks for assignment in file scope
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferSuffixedIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1_i64")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int64", type)
    }

    fun testInferFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1.0")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float64", type)
    }

    fun testInferSuffixedFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1_f32")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float32", type)
    }

    fun testInferStringLiteral() {
        myFixture.configureByText("test.cr", "x = \"hello\"")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("String", type)
    }

    fun testInferCharLiteral() {
        myFixture.configureByText("test.cr", "x = 'a'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferSymbolLiteral() {
        myFixture.configureByText("test.cr", "x = :foo")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Symbol", type)
    }

    fun testInferTrueLiteral() {
        myFixture.configureByText("test.cr", "x = true")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferFalseLiteral() {
        myFixture.configureByText("test.cr", "x = false")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferNilLiteral() {
        myFixture.configureByText("test.cr", "x = nil")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Nil", type)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalTypeInferenceTest" 2>&1 | tail -20`
Expected: All tests FAIL — `inferTypeFromExpression` doesn't handle literals yet.

- [ ] **Step 3: Add scalar literal inference to `inferTypeFromExpression`**

In `CrystalTypeInference.kt`, add literal detection at the start of `inferTypeFromExpression` (before the existing regex patterns at line 99):

```kotlin
private fun inferTypeFromExpression(expr: PsiElement, project: Project): String? {
    val text = expr.text.trim()

    // Scalar literals
    val type = inferFromLiteral(expr)
    if (type != null) return type

    // Pattern: Klasse.new (existing code continues...)
```

Add new private method:

```kotlin
private fun inferFromLiteral(expr: PsiElement): String? {
    val type = expr.node?.elementType ?: return null
    return when (type) {
        CrystalTypes.INTEGER_LITERAL -> resolveIntegerLiteralType(expr.text)
        CrystalTypes.FLOAT_LITERAL -> resolveFloatLiteralType(expr.text)
        CrystalTypes.STRING_LITERAL -> "String"
        CrystalTypes.CHAR_LITERAL -> "Char"
        CrystalTypes.SYMBOL_LITERAL -> "Symbol"
        CrystalTypes.TRUE, CrystalTypes.FALSE -> "Bool"
        CrystalTypes.NIL -> "Nil"
        else -> null
    }
}

private fun resolveIntegerLiteralType(text: String): String {
    val lower = text.lowercase().replace("_", "")
    return when {
        lower.endsWith("i8") -> "Int8"
        lower.endsWith("i16") -> "Int16"
        lower.endsWith("i32") -> "Int32"
        lower.endsWith("i64") -> "Int64"
        lower.endsWith("i128") -> "Int128"
        lower.endsWith("u8") -> "UInt8"
        lower.endsWith("u16") -> "UInt16"
        lower.endsWith("u32") -> "UInt32"
        lower.endsWith("u64") -> "UInt64"
        lower.endsWith("u128") -> "UInt128"
        else -> "Int32"
    }
}

private fun resolveFloatLiteralType(text: String): String {
    val lower = text.lowercase().replace("_", "")
    return when {
        lower.endsWith("f32") -> "Float32"
        lower.endsWith("f64") -> "Float64"
        else -> "Float64"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalTypeInferenceTest" 2>&1 | tail -20`
Expected: All 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt \
        src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt
git commit -m "feat: add scalar literal type inference to CrystalTypeInference"
```

---

### Task 2: Scalar Literal Inference in CrystalExpressionTypeResolver

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt:40-48`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalExpression` PSI element
- Produces: `resolveType` returns `ResolvedType` for literal expressions

- [ ] **Step 1: Write failing inspection test for literal argument type checking**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testIntLiteralWhereStringExpectedStillWorks() {
    myFixture.configureByText("test.cr", """
        def greet(name : String)
        end
        greet(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testStringLiteralWhereStringExpectedNoError() {
    myFixture.configureByText("test.cr", """
        def greet(name : String)
        end
        greet("hello")
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testNilLiteralWhereNilableExpectedNoError() {
    myFixture.configureByText("test.cr", """
        def greet(name : String?)
        end
        greet(nil)
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testStringLiteralWhereStringExpectedNoError" 2>&1 | tail -20`
Expected: FAIL — `resolveType` doesn't handle `CrystalStringExpression` in all contexts, or literal types not fully resolved.

- [ ] **Step 3: Add `CrystalStringExpression` handling to `resolveType`**

The existing code at line 52 already handles `CrystalStringExpression`. Verify that the literal token types at lines 40-48 cover all scalar cases. If the test fails due to a specific gap, add the missing case. The existing code should already handle this — the test is a regression guard.

- [ ] **Step 4: Run full inspection test suite**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest" 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/test/kotlin/de.magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add scalar literal type tests to CrystalExpressionTypeResolver"
```

---

### Task 3: Trivial Expression Types in CrystalExpressionTypeResolver

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt:29-82`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalRegexExpression`, `CrystalCommandExpression`, `CrystalHeredocLiteral`, `CrystalSymbolStringExpression`, `CrystalSizeofExpression`, `CrystalInstanceSizeofExpression`, `CrystalOffsetofExpression` PSI elements
- Produces: `resolveType` returns `ResolvedType` with fixed type name

- [ ] **Step 1: Write failing tests**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testRegexLiteralType() {
    myFixture.configureByText("test.cr", """
        def foo(r : Regex)
        end
        foo(/pattern/)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testCommandExpressionType() {
    myFixture.configureByText("test.cr", """
        def foo(s : String)
        end
        foo(`ls`)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testHeredocLiteralType() {
    myFixture.configureByText("test.cr", """
        def foo(s : String)
        end
        foo(<<-HEREDOC
        hello
        HEREDOC)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testSizeofExpressionType() {
    myFixture.configureByText("test.cr", """
        def foo(n : UInt64)
        end
        foo(sizeof(Int32))
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testRegexLiteralType" 2>&1 | tail -20`
Expected: FAIL — `resolveType` doesn't handle these PSI types.

- [ ] **Step 3: Add trivial expression handling to `resolveType`**

In `CrystalExpressionTypeResolver.kt`, add after the `CrystalStringExpression` check (line 52):

```kotlin
// Trivial expression types — always resolve to a fixed type
if (expr is CrystalRegexExpression) return ResolvedType("Regex")
if (expr is CrystalCommandExpression) return ResolvedType("String")
if (expr is CrystalHeredocLiteral) return ResolvedType("String")
if (expr is CrystalSymbolStringExpression) return ResolvedType("Symbol")
if (expr is CrystalSizeofExpression) return ResolvedType("UInt64")
if (expr is CrystalInstanceSizeofExpression) return ResolvedType("UInt64")
if (expr is CrystalOffsetofExpression) return ResolvedType("UInt64")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest" 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add trivial expression type resolution (Regex, heredoc, sizeof)"
```

---

### Task 4: Collection Literal Inference — Arrays

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalArrayLiteral` PSI element, `CrystalExpressionTypeResolver.resolveType`
- Produces: `ResolvedType("Array(T)")` or `null` if elements can't be resolved

- [ ] **Step 1: Write failing tests for array literal type checking**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testArrayLiteralOfTypeAnnotation() {
    myFixture.configureByText("test.cr", """
        def foo(a : Array(Int32))
        end
        foo([] of Int32)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArrayLiteralHomogeneousNoError() {
    myFixture.configureByText("test.cr", """
        def foo(a : Array(Int32))
        end
        foo([1, 2, 3])
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testArrayLiteralHeterogeneousNoError() {
    myFixture.configureByText("test.cr", """
        def foo(a : Array(Int32 | String))
        end
        foo([1, "hi"])
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Write failing unit tests for array inference**

Add to `CrystalTypeInferenceTest.kt`:

```kotlin
fun testInferArrayLiteralWithOfType() {
    myFixture.configureByText("test.cr", "x = [] of Int32")
    val type = CrystalTypeInference.inferType("x", myFixture.file, project)
    assertEquals("Array", type)
}

fun testInferArrayLiteralHomogeneous() {
    myFixture.configureByText("test.cr", "x = [1, 2, 3]")
    val type = CrystalTypeInference.inferType("x", myFixture.file, project)
    assertEquals("Array", type)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testArrayLiteralHomogeneousNoError" 2>&1 | tail -20`
Expected: FAIL.

- [ ] **Step 4: Implement array literal resolution in `CrystalExpressionTypeResolver`**

In `CrystalExpressionTypeResolver.kt`, add after the trivial expression checks:

```kotlin
if (expr is CrystalArrayLiteral) return resolveArrayLiteral(expr)
```

Add new method:

```kotlin
private fun resolveArrayLiteral(expr: CrystalArrayLiteral): ResolvedType? {
    // Check for "of Type" annotation
    val typeRef = expr.typeReference
    if (typeRef != null) {
        val typeName = typeRef.text.trim().split("|").first().trim()
            .replace(Regex("""\(.*\)"""), "").trim()
        return ResolvedType("Array($typeName)")
    }

    // Infer from elements
    val elements = expr.expressionList?.expressionList ?: emptyList()
    if (elements.isEmpty()) return null

    val elementTypes = elements.mapNotNull { resolveType(it) }
    if (elementTypes.size != elements.size) return null

    val firstType = elementTypes.first().typeName
    return if (elementTypes.all { it.typeName == firstType }) {
        ResolvedType("Array($firstType)")
    } else {
        val union = elementTypes.joinToString(" | ") { it.typeName }
        ResolvedType("Array($union)")
    }
}
```

- [ ] **Step 5: Implement array literal inference in `CrystalTypeInference`**

In `CrystalTypeInference.inferTypeFromExpression`, add before the method-call regex patterns:

```kotlin
// Array literal
if (text.startsWith("[")) return "Array"
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt \
        src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt \
        src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add array literal type inference"
```

---

### Task 5: Collection Literal Inference — Hash & Tuple

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalHashLiteral`, `CrystalTupleLiteral` PSI elements
- Produces: `ResolvedType("Hash(K, V)")`, `ResolvedType("Tuple(T1, T2)")`, or `null`

- [ ] **Step 1: Write failing tests**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testHashLiteralOfTypeAnnotation() {
    myFixture.configureByText("test.cr", """
        def foo(h : Hash(String, Int32))
        end
        foo({} of String => Int32)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testHashLiteralShorthandNoError() {
    myFixture.configureByText("test.cr", """
        def foo(h : Hash(Symbol, Int32))
        end
        foo({a: 1})
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testTupleLiteralNoError() {
    myFixture.configureByText("test.cr", """
        def foo(t : Tuple(Int32, String))
        end
        foo({1, "hi"})
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testHashLiteralOfTypeAnnotation" 2>&1 | tail -20`
Expected: FAIL.

- [ ] **Step 3: Implement hash literal resolution**

In `CrystalExpressionTypeResolver.kt`, add after array literal handling:

```kotlin
if (expr is CrystalHashLiteral) return resolveHashLiteral(expr)
```

Add new method:

```kotlin
private fun resolveHashLiteral(expr: CrystalHashLiteral): ResolvedType? {
    // Check for "of K => V" annotation
    val ofType = expr.children.firstOrNull { it.node?.elementType == CrystalTypes.OF }
    if (ofType != null) {
        // Parse "of K => V" — find CONSTANT or type reference after OF
        val rest = expr.text.substringAfter("of").trim()
        val arrowIndex = rest.indexOf("=>")
        if (arrowIndex > 0) {
            val keyType = rest.substring(0, arrowIndex).trim().split("|").first().trim()
                .replace(Regex("""\(.*\)"""), "").trim()
            val valueType = rest.substring(arrowIndex + 2).trim().split("|").first().trim()
                .replace(Regex("""\(.*\)"""), "").trim()
            return ResolvedType("Hash($keyType, $valueType)")
        }
    }

    // Infer from entries
    val entries = expr.hashEntryList?.hashEntryList ?: emptyList()
    if (entries.isEmpty()) return null

    val keyTypes = entries.mapNotNull { it.expressionList.firstOrNull()?.let { e -> resolveType(e) } }
    val valueTypes = entries.mapNotNull { it.expressionList.getOrNull(1)?.let { e -> resolveType(e) } }
    if (keyTypes.size != entries.size || valueTypes.size != entries.size) return null

    val keyType = keyTypes.first().typeName
    val valueType = valueTypes.first().typeName
    return if (keyTypes.all { it.typeName == keyType } && valueTypes.all { it.typeName == valueType }) {
        ResolvedType("Hash($keyType, $valueType)")
    } else {
        val keyUnion = keyTypes.joinToString(" | ") { it.typeName }
        val valueUnion = valueTypes.joinToString(" | ") { it.typeName }
        ResolvedType("Hash($keyUnion, $valueUnion)")
    }
}
```

- [ ] **Step 4: Implement tuple literal resolution**

In `CrystalExpressionTypeResolver.kt`, add after hash literal handling:

```kotlin
if (expr is CrystalTupleLiteral) return resolveTupleLiteral(expr)
```

Add new method:

```kotlin
private fun resolveTupleLiteral(expr: CrystalTupleLiteral): ResolvedType? {
    val elements = expr.expressionList.expressionList
    if (elements.isEmpty()) return null

    val types = elements.mapNotNull { resolveType(it) }
    if (types.size != elements.size) return null

    val typeList = types.joinToString(", ") { it.typeName }
    return ResolvedType("Tuple($typeList)")
}
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add hash and tuple literal type inference"
```

---

### Task 6: Control-Flow Union Inference — Ternary & If Expressions

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalIfStatement` PSI element (ternary is parsed as if_expression in Crystal)
- Produces: `ResolvedType("T1 | T2")` union of branch types

- [ ] **Step 1: Write failing tests**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testTernaryExpressionType() {
    myFixture.configureByText("test.cr", """
        def foo(n : Int32?)
        end
        foo(true ? 1 : nil)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testIfExpressionType() {
    myFixture.configureByText("test.cr", """
        def foo(n : Int32 | String)
        end
        foo(if true; 1; else; "hi"; end)
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testTernaryExpressionType" 2>&1 | tail -20`
Expected: FAIL.

- [ ] **Step 3: Implement control-flow expression resolution**

In `CrystalExpressionTypeResolver.kt`, add after tuple literal handling:

```kotlin
if (expr is CrystalIfStatement) return resolveIfExpression(expr)
if (expr is CrystalCaseStatement) return resolveCaseExpression(expr)
```

Add new methods:

```kotlin
private fun resolveIfExpression(expr: CrystalIfStatement): ResolvedType? {
    val branches = mutableListOf<ResolvedType>()

    // Collect all then-clause expressions
    val thenStatements = expr.statementList.statementList
    val lastThen = thenStatements.lastOrNull()
    if (lastThen != null) {
        val thenType = resolveType(lastThen)
        if (thenType != null) branches.add(thenType)
    }

    // Collect else-clause expressions
    val elseClause = expr.elseClause
    if (elseClause != null) {
        val elseStatements = elseClause.statementList.statementList
        val lastElse = elseStatements.lastOrNull()
        if (lastElse != null) {
            val elseType = resolveType(lastElse)
            if (elseType != null) branches.add(elseType)
        }
    } else {
        // Implicit else returns Nil
        branches.add(ResolvedType("Nil"))
    }

    if (branches.isEmpty()) return null
    if (branches.size == 1) return branches.first()

    val typeList = branches.joinToString(" | ") { it.typeName }
    return ResolvedType(typeList)
}

private fun resolveCaseExpression(expr: CrystalCaseStatement): ResolvedType? {
    val branches = mutableListOf<ResolvedType>()

    for (whenClause in expr.whenClauseList) {
        val thenStatements = whenClause.statementList.statementList
        val lastThen = thenStatements.lastOrNull()
        if (lastThen != null) {
            val thenType = resolveType(lastThen)
            if (thenType != null) branches.add(thenType)
        }
    }

    // Else clause
    val elseClause = expr.elseClause
    if (elseClause != null) {
        val elseStatements = elseClause.statementList.statementList
        val lastElse = elseStatements.lastOrNull()
        if (lastElse != null) {
            val elseType = resolveType(lastElse)
            if (elseType != null) branches.add(elseType)
        }
    }

    if (branches.isEmpty()) return null
    if (branches.size == 1) return branches.first()

    val typeList = branches.joinToString(" | ") { it.typeName }
    return ResolvedType(typeList)
}
```

- [ ] **Step 4: Implement ternary/if inference in `CrystalTypeInference`**

In `CrystalTypeInference.inferTypeFromExpression`, add before the method-call regex patterns:

```kotlin
// If expression / ternary
if (expr is CrystalIfStatement) return inferFromIfExpression(expr, project)
```

Add new method:

```kotlin
private fun inferFromIfExpression(expr: CrystalIfStatement, project: Project): String? {
    val branches = mutableListOf<String>()

    val thenStatements = expr.statementList.statementList
    val lastThen = thenStatements.lastOrNull()
    if (lastThen != null) {
        val thenType = inferTypeFromExpression(lastThen, project)
        if (thenType != null) branches.add(thenType)
    }

    val elseClause = expr.elseClause
    if (elseClause != null) {
        val elseStatements = elseClause.statementList.statementList
        val lastElse = elseStatements.lastOrNull()
        if (lastElse != null) {
            val elseType = inferTypeFromExpression(lastElse, project)
            if (elseType != null) branches.add(elseType)
        }
    } else {
        branches.add("Nil")
    }

    if (branches.isEmpty()) return null
    if (branches.size == 1) return branches.first()
    return branches.joinToString(" | ")
}
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt \
        src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt \
        src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add control-flow union type inference (ternary, if, case)"
```

---

### Task 7: Instance Variable & Multi-Assignment Inference

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt`

**Interfaces:**
- Consumes: `CrystalAssignment` with `CrystalInstanceVarAccess` child
- Produces: Type name for `@var` assignments

- [ ] **Step 1: Write failing tests**

Add to `CrystalTypeInferenceTest.kt`:

```kotlin
fun testInferInstanceVariableFromAssignment() {
    myFixture.configureByText("test.cr", "@x = \"hello\"")
    val type = CrystalTypeInference.inferType("@x", myFixture.file, project)
    assertEquals("String", type)
}

fun testInferInstanceVariableFromIntegerAssignment() {
    myFixture.configureByText("test.cr", "@count = 42")
    val type = CrystalTypeInference.inferType("@count", myFixture.file, project)
    assertEquals("Int32", type)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalTypeInferenceTest.testInferInstanceVariableFromAssignment" 2>&1 | tail -20`
Expected: FAIL — `inferFromAssignment` checks for `@name` but `inferTypeFromExpression` doesn't handle the `@` prefix properly for literals.

- [ ] **Step 3: Verify and fix instance variable inference**

The existing code at `CrystalTypeInference.kt:76` already checks `varName != "@$name"`. The issue is likely that the literal inference from Task 1 needs to work for instance variable context too. Verify the fix — the `inferFromLiteral` method added in Task 1 should already handle this since it works on the expression PSI, not the variable name.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalTypeInferenceTest" 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalTypeInference.kt \
        src/test/kotlin/de/magynhard/crystal/CrystalTypeInferenceTest.kt
git commit -m "feat: add instance variable type inference from literal assignments"
```

---

### Task 8: Operator Result Type Inference

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt`

**Interfaces:**
- Consumes: `CrystalExpression` with operator children
- Produces: `ResolvedType` for binary/unary operator results

- [ ] **Step 1: Write failing tests**

Add to `CrystalTypeCheckInspectionTest.kt`:

```kotlin
fun testIntegerAdditionType() {
    myFixture.configureByText("test.cr", """
        def foo(n : Int32)
        end
        foo(1 + 2)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testComparisonType() {
    myFixture.configureByText("test.cr", """
        def foo(b : Bool)
        end
        foo(1 == 2)
    """.trimIndent())
    myFixture.checkHighlighting()
}

fun testStringConcatType() {
    myFixture.configureByText("test.cr", """
        def foo(s : String)
        end
        foo("a" + "b")
    """.trimIndent())
    myFixture.checkHighlighting()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "de.magynhard.crystal.inspections.CrystalTypeCheckInspectionTest.testIntegerAdditionType" 2>&1 | tail -20`
Expected: FAIL.

- [ ] **Step 3: Implement operator result type resolution**

In `CrystalExpressionTypeResolver.kt`, add after control-flow handling:

```kotlin
// Binary operator expressions — CrystalExpression wrapping operator children
if (expr is CrystalExpression) {
    val result = resolveOperatorType(expr)
    if (result != null) return result
}
```

Add new method:

```kotlin
private fun resolveOperatorType(expr: CrystalExpression): ResolvedType? {
    val children = expr.children.filter { it !is PsiWhiteSpace }.toList()
    if (children.size < 3) return null

    val left = children[0]
    val operator = children[1]
    val right = children[2]

    val opType = operator.node?.elementType ?: return null

    return when (opType) {
        CrystalTypes.EQ, CrystalTypes.NEQ,
        CrystalTypes.LT, CrystalTypes.LTE,
        CrystalTypes.GT, CrystalTypes.GTE,
        CrystalTypes.SPACESHIP, CrystalTypes.CASE_EQ,
        CrystalTypes.MATCH_OP -> ResolvedType("Bool")

        CrystalTypes.PLUS, CrystalTypes.MINUS,
        CrystalTypes.STAR, CrystalTypes.SLASH,
        CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT,
        CrystalTypes.DOUBLE_STAR -> {
            val leftType = resolveType(left)
            val rightType = resolveType(right)
            if (leftType != null && leftType.typeName == rightType?.typeName) leftType
            else null
        }

        CrystalTypes.AND_AND, CrystalTypes.OR_OR,
        CrystalTypes.BANG -> ResolvedType("Bool")

        else -> null
    }
}
```

Note: The `CrystalTypes` constants for operators need to be verified against the actual generated token types. Adjust operator token names to match what's in `CrystalTypes.java`.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/inspections/CrystalExpressionTypeResolver.kt \
        src/test/kotlin/de/magynhard/crystal/inspections/CrystalTypeCheckInspectionTest.kt
git commit -m "feat: add operator result type inference (arithmetic, comparison, logical)"
```

---

### Task 9: Update TODO.md and Final Verification

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: Mark completed items in TODO.md**

Update the Type Inference section:

```markdown
## Type Inference (Issue #1)

- [x] **Extend CrystalTypeInference for literal assignments** — `x = "hello"` → String,
  `x = 1` → Int32, `x = :sym` → Symbol.
- [x] **Add array/hash/named-tuple literal inference** — `x = [1, 2]` → Array(Int32)
- [x] **Add control-flow union inference** — `x = cond ? 1 : nil` → Int32?
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add TODO.md
git commit -m "docs: update TODO.md — mark type inference tasks as completed"
```
