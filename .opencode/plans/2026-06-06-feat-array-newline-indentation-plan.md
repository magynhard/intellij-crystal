# Plan: Implement Smart Array/Hash Newline Indentation

**Objective:** Enhance the `CrystalEnterHandler` to provide Ruby-like indentation when pressing Enter inside arrays (`[...]`) and hashes (`{...}`).

## Requirements

1. **Array Elements:** When pressing Enter after a comma inside an array, the new line should be indented by 2 spaces relative to the array's opening bracket.
2. **Closing Bracket Alignment:** When pressing Enter before the closing bracket (`]`), the bracket should move to a new line aligned with the variable name or equals sign that started the array definition.
3. **Hashes:** Apply the same logic to curly braces `{...}` used for hashes.

## Technical Analysis

The `CrystalEnterHandler.postProcessEnter` method currently handles:
- Block keywords (`def`, `if`, `do`, etc.) adding `end`
- Bracket enter (when `nextTrimmed` starts with `]` or `}`)

### Missing Logic:
- **Continuation Indentation:** The handler doesn't recognize that a line ending in `,` (comma) inside a collection should trigger a 2-space indent.
- **Context-Aware Closing:** The handler aligns `]` with the *previous line's indent*, not the *opening bracket's context* (e.g., `a = [` should align `]` under `a`, not under `[`).

## Implementation Strategy

### Step 1: Enhance `postProcessEnter` to handle Comma Continuation

Modify the `postProcessEnter` method to detect if the previous line ends with `,` and we are currently inside a bracket scope.

- **Logic:**
  1. Check if `trimmed.endsWith(",")`.
  2. Find the unclosed opening bracket (`[` or `{`) by scanning backwards.
  3. Determine indentation of the opening bracket's line.
  4. Set new line indent = `Array/Hash Start Indent + 2 spaces`.

### Step 2: Refine Closing Bracket Alignment

Modify the existing logic that handles `nextTrimmed.startsWith("]") || nextTrimmed.startsWith("}")`.

- **Logic:**
  1. When moving `]` or `}` down, scan backwards to find the line containing the opening `[` or `{`.
  2. Extract the indentation of that *opener line*, NOT the bracket itself.
  3. Example:
     ```
     a = [   <-- Opener line (Indent 0)
       1,    <-- Element (Indent 2)
     ]       <-- Should align with 'a', so Indent 0
     ```
  4. Apply the opener line's indentation to the closing bracket.

### Step 3: Edge Cases

- **Nested Brackets:** `[[1], [2]]` - Ensure we find the *immediate* unclosed bracket.
- **Hash Rockets:** `{ :a => 1 }` - Same logic applies.

## File Changes

- `src/main/kotlin/de/magynhard/crystal/CrystalEnterHandler.kt`: Modify `postProcessEnter` and add helper methods for bracket scanning.
- `src/test/kotlin/de/magynhard/crystal/CrystalEnterHandlerTest.kt`: Add test cases for array and hash indentation.

## Tests

- `testArrayNewlineIndent`: `a = [1, <caret>]` -> `a = [1,\n  <caret>]`
- `testArrayClosingBracketAlignment`: `a = [1, 2<caret>]` -> `a = [1, 2\n<caret>]`
- `testHashNewlineIndent`: `h = {:a => 1, <caret>}` -> `h = {:a => 1,\n  <caret>}`

## Detailed Implementation Plan

1. **Add `isInsideUnclosedBracket` helper (at the end of `CrystalEnterHandler.kt`):**
   - Scans backwards from `caretLine` to find a `[` or `{` that isn't closed.
   - Tracks `]` and `}` counts to correctly handle nested brackets.
   - Returns `true` if an unclosed bracket is found.

2. **Add `findOpeningBracketIndent` helper (at the end of `CrystalEnterHandler.kt`):**
   - Similar to `findOpeningBlockIndent`, but searches for `[` or `{`.
   - Returns the indentation of the line containing the bracket.

3. **Modify `postProcessEnter` in `CrystalEnterHandler.kt`:**
   - After checking for `trimmed.endsWith("{")`, check if `trimmed.endsWith(",")`.
   - If `trimmed.endsWith(",")` and `isInsideUnclosedBracket(document, caretLine)`:
     - Use `findOpeningBracketIndent` to get `arrayStartIndent`.
     - Set `newIndent = "$arrayStartIndent  "`.
     - Apply indent to current line.
     - Return `Result.Stop`.
   - Refine the existing `nextTrimmed.startsWith("]") || nextTrimmed.startsWith("}")` logic:
     - Instead of `baseIndent` (prev line), use `findOpeningBracketIndent`.
     - Use this opener indent for the closing bracket `]`.

### 4. Exact Changes in `CrystalEnterHandler.kt`

1. **Inside `postProcessEnter`:**
   - **Add** the `trimmed.endsWith(",")` check *after* the `trimmed.endsWith("{")` check (around line 142).
   - **Modify** the `nextTrimmed.startsWith("]") || nextTrimmed.startsWith("}")` logic (around line 115-128):
     - Change `val baseIndent = prevLineText.takeWhile { it == ' ' || it == '\t' }` to `val baseIndent = findOpeningBracketIndent(document, prevLineNumber) ?: (prevLineText.takeWhile { it == ' ' || it == '\t' })`.

2. **Add `isInsideUnclosedBracket`:**
```kotlin
private fun isInsideUnclosedBracket(document: com.intellij.openapi.editor.Document, currentLine: Int): Boolean {
    var closeSquareCount = 0
    var closeCurlyCount = 0
    for (line in (currentLine) downTo 0) {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val text = document.getText(TextRange(lineStart, lineEnd))
        val trimmed = text.trimEnd()

        for (i in (trimmed.length - 1) downTo 0) {
            val c = trimmed[i]
            if (c == ']') closeSquareCount++
            else if (c == '[') {
                if (closeSquareCount > 0) closeSquareCount--
                else return true
            }
            else if (c == '}') closeCurlyCount++
            else if (c == '{') {
                if (closeCurlyCount > 0) closeCurlyCount--
                else return true
            }
        }
    }
    return false
}
```

3. **Add `findOpeningBracketIndent`:**
```kotlin
private fun findOpeningBracketIndent(document: com.intellij.openapi.editor.Document, currentLine: Int): String? {
    var closeSquareCount = 0
    var closeCurlyCount = 0
    for (line in (currentLine) downTo 0) {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val text = document.getText(TextRange(lineStart, lineEnd))
        val trimmed = text.trimEnd()

        for (i in (trimmed.length - 1) downTo 0) {
            val c = trimmed[i]
            if (c == ']') closeSquareCount++
            else if (c == '[') {
                if (closeSquareCount > 0) closeSquareCount--
                else return text.takeWhile { it == ' ' || it == '\t' }
            }
            else if (c == '}') closeCurlyCount++
            else if (c == '{') {
                if (closeCurlyCount > 0) closeCurlyCount--
                else return text.takeWhile { it == ' ' || it == '\t' }
            }
        }
    }
    return null
}
```
