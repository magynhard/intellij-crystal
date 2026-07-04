# Block Highlighting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend brace highlighting to all Crystal keyword blocks (def...end, if...end, class...end, etc.) matching RubyMine behavior.

**Architecture:** Two independent mechanisms — `PairedBraceMatcher` for simple token-based pairs (EDT), `CodeBlockSupportHandler` for PSI-based multi-block expressions with all related markers.

**Tech Stack:** IntelliJ Platform 2026.1+, Kotlin, Gradle

## Global Constraints

- IntelliJ Platform build 261+
- All keyword pairs use `END` as closing token
- `ELSE`/`ELSIF`/`WHEN`/`RESCUE`/`ENSURE` are NOT registered as brace tokens
- `CodeBlockSupportHandler` registered via `com.intellij.codeBlockSupportHandler` EP

---

### Task 1: Extend CrystalBraceMatcher with keyword pairs

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/CrystalBraceMatcher.kt`

- [ ] **Step 1: Add keyword BracePair entries**

```kotlin
package de.magynhard.crystal

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.psi.CrystalTypes

class CrystalBraceMatcher : PairedBraceMatcher {

    companion object {
        private val PAIRS = arrayOf(
            // Brackets/braces (existing)
            BracePair(CrystalTypes.LPAREN, CrystalTypes.RPAREN, false),
            BracePair(CrystalTypes.LBRACKET, CrystalTypes.RBRACKET, false),
            BracePair(CrystalTypes.LBRACE, CrystalTypes.RBRACE, true),
            BracePair(CrystalTypes.PERCENT_LITERAL_BEGIN, CrystalTypes.PERCENT_LITERAL_END, false),
            // Keyword blocks — structural (new)
            BracePair(CrystalTypes.DEF, CrystalTypes.END, true),
            BracePair(CrystalTypes.CLASS, CrystalTypes.END, true),
            BracePair(CrystalTypes.MODULE, CrystalTypes.END, true),
            BracePair(CrystalTypes.STRUCT, CrystalTypes.END, true),
            BracePair(CrystalTypes.ENUM, CrystalTypes.END, true),
            BracePair(CrystalTypes.ANNOTATION, CrystalTypes.END, true),
            BracePair(CrystalTypes.LIB, CrystalTypes.END, true),
            BracePair(CrystalTypes.MACRO, CrystalTypes.END, true),
            BracePair(CrystalTypes.VERBATIM, CrystalTypes.END, true),
            BracePair(CrystalTypes.IF, CrystalTypes.END, true),
            BracePair(CrystalTypes.UNLESS, CrystalTypes.END, true),
            BracePair(CrystalTypes.WHILE, CrystalTypes.END, true),
            BracePair(CrystalTypes.UNTIL, CrystalTypes.END, true),
            BracePair(CrystalTypes.FOR, CrystalTypes.END, true),
            BracePair(CrystalTypes.CASE, CrystalTypes.END, true),
            BracePair(CrystalTypes.DO, CrystalTypes.END, true),
            BracePair(CrystalTypes.BEGIN, CrystalTypes.END, true),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
```
All keyword pairs use `matchable = true` (structural).

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `./gradlew test`
Expected: all tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/CrystalBraceMatcher.kt
git commit -m "feat: add keyword block pairs to CrystalBraceMatcher"
```

---

### Task 2: Create CrystalCodeBlockSupportHandler

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/CrystalCodeBlockSupportHandler.kt`

- [ ] **Step 1: Write the handler implementation**

```kotlin
package de.magynhard.crystal

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import de.magynhard.crystal.psi.*

class CrystalCodeBlockSupportHandler : CodeBlockSupportHandler {

    override fun getCodeBlockMarkerRanges(elementAtCursor: PsiElement): List<TextRange> {
        if (elementAtCursor.node?.elementType !in MARKER_TOKENS) return emptyList()
        
        val ranges = findBlockMarkers(elementAtCursor) ?: return emptyList()
        return ranges
    }

    override fun getCodeBlockRange(elementAtCursor: PsiElement): TextRange {
        val parent = findEnclosingBlock(elementAtCursor) ?: return TextRange.EMPTY_RANGE
        return parent.textRange
    }

    private fun findBlockMarkers(element: PsiElement): List<TextRange>? {
        val block = findEnclosingBlock(element) ?: return null
        return collectMarkers(block)
    }

    private fun findEnclosingBlock(element: PsiElement): PsiElement? {
        val parents = generateSequence(element.parent) { it.parent }
        return parents.firstOrNull { it.node?.elementType in BLOCK_TYPES }
    }

    private fun collectMarkers(block: PsiElement): List<TextRange> {
        val blockType = block.node?.elementType ?: return emptyList()
        return when (blockType) {
            CrystalTypes.IF_EXPRESSION -> collectIfMarkers(block)
            CrystalTypes.UNLESS_EXPRESSION -> collectUnlessMarkers(block)
            CrystalTypes.CASE_EXPRESSION -> collectCaseMarkers(block)
            CrystalTypes.BEGIN_EXPRESSION -> collectBeginMarkers(block)
            else -> listOf(block.firstChild.textRange, lastEndChild(block)?.textRange).filterNotNull()
        }
    }

    private fun collectIfMarkers(block: PsiElement): List<TextRange> {
        val markers = mutableListOf<TextRange>()
        addChildTokenRange(block, CrystalTypes.IF)?.let { markers.add(it) }
        markers.addAll(collectChildTokenRanges(block, CrystalTypes.ELSIF))
        addChildTokenRange(block, CrystalTypes.ELSE)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.END)?.let { markers.add(it) }
        markers.sortBy { it.startOffset }
        return markers
    }

    private fun collectUnlessMarkers(block: PsiElement): List<TextRange> {
        val markers = mutableListOf<TextRange>()
        addChildTokenRange(block, CrystalTypes.UNLESS)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.ELSE)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.END)?.let { markers.add(it) }
        markers.sortBy { it.startOffset }
        return markers
    }

    private fun collectCaseMarkers(block: PsiElement): List<TextRange> {
        val markers = mutableListOf<TextRange>()
        addChildTokenRange(block, CrystalTypes.CASE)?.let { markers.add(it) }
        markers.addAll(collectChildTokenRanges(block, CrystalTypes.WHEN))
        addChildTokenRange(block, CrystalTypes.ELSE)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.END)?.let { markers.add(it) }
        markers.sortBy { it.startOffset }
        return markers
    }

    private fun collectBeginMarkers(block: PsiElement): List<TextRange> {
        val markers = mutableListOf<TextRange>()
        addChildTokenRange(block, CrystalTypes.BEGIN)?.let { markers.add(it) }
        markers.addAll(collectChildTokenRanges(block, CrystalTypes.RESCUE))
        addChildTokenRange(block, CrystalTypes.ELSE)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.ENSURE)?.let { markers.add(it) }
        addChildTokenRange(block, CrystalTypes.END)?.let { markers.add(it) }
        markers.sortBy { it.startOffset }
        return markers
    }

    private fun addChildTokenRange(parent: PsiElement, type: IElementType): TextRange? {
        for (child in parent.children) {
            if (child.node?.elementType == type) {
                // Find the actual token leaf (not the PSI composite)
                val leaf = findLeafToken(child, type) ?: child
                return leaf.textRange
            }
        }
        return null
    }

    private fun collectChildTokenRanges(parent: PsiElement, type: IElementType): List<TextRange> {
        return parent.children
            .filter { it.node?.elementType == type }
            .mapNotNull { child -> findLeafToken(child, type)?.textRange ?: child.textRange }
    }

    private fun findLeafToken(element: PsiElement, type: IElementType): PsiElement? {
        return element.children.firstOrNull { it.node?.elementType == type }
            ?: element.firstChild?.let { findLeafToken(it, type) }
            ?: element
    }

    private fun lastEndChild(block: PsiElement): PsiElement? {
        for (child in block.children.reversed()) {
            if (child.node?.elementType == CrystalTypes.END) {
                val leaf = findLeafToken(child, CrystalTypes.END) ?: child
                return leaf
            }
        }
        return null
    }

    companion object {
        private val MARKER_TOKENS = setOf(
            CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
            CrystalTypes.STRUCT, CrystalTypes.ENUM, CrystalTypes.ANNOTATION,
            CrystalTypes.LIB, CrystalTypes.MACRO, CrystalTypes.VERBATIM,
            CrystalTypes.IF, CrystalTypes.UNLESS, CrystalTypes.WHILE,
            CrystalTypes.UNTIL, CrystalTypes.FOR, CrystalTypes.CASE,
            CrystalTypes.DO, CrystalTypes.BEGIN,
            CrystalTypes.END, CrystalTypes.ELSE, CrystalTypes.ELSIF,
            CrystalTypes.WHEN, CrystalTypes.RESCUE, CrystalTypes.ENSURE,
        )

        private val BLOCK_TYPES = setOf(
            CrystalTypes.DEF_DEFINITION, CrystalTypes.CLASS_DEFINITION,
            CrystalTypes.MODULE_DEFINITION, CrystalTypes.STRUCT_DEFINITION,
            CrystalTypes.ENUM_DEFINITION, CrystalTypes.ANNOTATION_DEFINITION,
            CrystalTypes.LIB_DEFINITION, CrystalTypes.MACRO_DEFINITION,
            CrystalTypes.VERBATIM_DEFINITION,
            CrystalTypes.IF_EXPRESSION, CrystalTypes.UNLESS_EXPRESSION,
            CrystalTypes.WHILE_EXPRESSION, CrystalTypes.UNTIL_EXPRESSION,
            CrystalTypes.FOR_EXPRESSION, CrystalTypes.CASE_EXPRESSION,
            CrystalTypes.DO_BLOCK, CrystalTypes.BEGIN_EXPRESSION,
        )
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/de/magynhard/crystal/CrystalCodeBlockSupportHandler.kt
git commit -m "feat: add CrystalCodeBlockSupportHandler for keyword block markers"
```

---

### Task 3: Register in plugin.xml

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add codeBlockSupportHandler and heavyBracesHighlighter extensions**

Find the `<lang.braceMatcher>` extension block and add the `codeBlockSupportHandler` and `heavyBracesHighlighter` entries after it:

```xml
        <lang.braceMatcher
                language="Crystal"
                implementationClass="de.magynhard.crystal.CrystalBraceMatcher"/>

        <codeBlockSupportHandler
                language="Crystal"
                implementationClass="de.magynhard.crystal.CrystalCodeBlockSupportHandler"/>
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests**

Run: `./gradlew test`
Expected: all tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/META-INF/plugin.xml
git commit -m "feat: register CrystalCodeBlockSupportHandler in plugin.xml"
```

---

### Task 4: Write unit tests

**Files:**
- Create: `src/test/kotlin/de/magynhard/crystal/CrystalCodeBlockSupportHandlerTest.kt`

- [ ] **Step 1: Write tests**

This test uses `BasePlatformTestCase` with editor fixture to verify marker ranges.

```kotlin
package de.magynhard.crystal

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalTypes

class CrystalCodeBlockSupportHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/highlighting"

    fun testDefBlock() {
        myFixture.configureByText("test.cr", """
            <caret>def foo
              bar
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(2, ranges.size)
        assertEquals("def", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
    }

    fun testIfBlock() {
        myFixture.configureByText("test.cr", """
            <caret>if true
              bar
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(2, ranges.size)
        assertEquals("if", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
    }

    fun testIfElseBlock() {
        myFixture.configureByText("test.cr", """
            <caret>if true
              bar
            else
              baz
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(3, ranges.size)
        assertEquals("if", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("else", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[2].startOffset, ranges[2].endOffset))
    }

    fun testIfElsifElseBlock() {
        myFixture.configureByText("test.cr", """
            <caret>if true
              bar
            elsif false
              baz
            else
              qux
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(4, ranges.size)
        assertEquals("if", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("elsif", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
        assertEquals("else", myFixture.file.text.substring(ranges[2].startOffset, ranges[2].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[3].startOffset, ranges[3].endOffset))
    }

    fun testCaseBlock() {
        myFixture.configureByText("test.cr", """
            <caret>case x
            when 1
              one
            when 2
              two
            else
              other
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(5, ranges.size)
        assertEquals("case", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertTrue(myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset).startsWith("when"))
        assertTrue(myFixture.file.text.substring(ranges[2].startOffset, ranges[2].endOffset).startsWith("when"))
        assertEquals("else", myFixture.file.text.substring(ranges[3].startOffset, ranges[3].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[4].startOffset, ranges[4].endOffset))
    }

    fun testBeginBlock() {
        myFixture.configureByText("test.cr", """
            <caret>begin
              bar
            rescue ex
              handle
            ensure
              cleanup
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(4, ranges.size)
        assertEquals("begin", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("rescue", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
        assertEquals("ensure", myFixture.file.text.substring(ranges[2].startOffset, ranges[2].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[3].startOffset, ranges[3].endOffset))
    }

    fun testCursorOnElse() {
        myFixture.configureByText("test.cr", """
            if true
              bar
            <caret>else
              baz
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(3, ranges.size)
        assertEquals("if", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("else", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[2].startOffset, ranges[2].endOffset))
    }

    fun testCursorOnEndOnIfBlock() {
        myFixture.configureByText("test.cr", """
            if true
              bar
            <caret>end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(2, ranges.size)
        assertEquals("if", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
    }

    fun testNestedBlocks() {
        myFixture.configureByText("test.cr", """
            <caret>def foo
              if true
                bar
              end
            end
        """.trimIndent())
        // Cursor on def — should find ONLY the enclosing def block markers
        val ranges = getMarkerRanges()
        assertEquals(2, ranges.size)
        assertEquals("def", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
    }

    fun testClassBlock() {
        myFixture.configureByText("test.cr", """
            <caret>class Foo
              def bar
              end
            end
        """.trimIndent())
        val ranges = getMarkerRanges()
        assertEquals(2, ranges.size)
        assertEquals("class", myFixture.file.text.substring(ranges[0].startOffset, ranges[0].endOffset))
        assertEquals("end", myFixture.file.text.substring(ranges[1].startOffset, ranges[1].endOffset))
    }

    private fun getMarkerRanges(): List<TextRange> {
        val offset = myFixture.editor.caretModel.offset
        val element = myFixture.file.findElementAt(offset)
        return CodeBlockSupportHandler.findMarkersRanges(element)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "de.magynhard.crystal.CrystalCodeBlockSupportHandlerTest"`
Expected: all tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/de/magynhard/crystal/CrystalCodeBlockSupportHandlerTest.kt
git commit -m "test: add tests for CrystalCodeBlockSupportHandler"
```

---

### Task 5: Full build and verify

- [ ] **Step 1: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Full test suite**

Run: `./gradlew test`
Expected: all tests pass

- [ ] **Step 3: Commit any remaining changes**

```bash
git add -A
git commit -m "chore: finalize block highlighting implementation"
```
