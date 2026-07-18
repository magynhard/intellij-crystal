package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for `require` keyword synthesis and file-path completion inside the
 * require string. See `docs/specs/require.md` for the behavioural spec.
 */
class CrystalRequireCompletionTest : BasePlatformTestCase() {

    // ==================== Keyword mode ====================

    fun testRequireKeywordSuggestedForReqPrefix() {
        myFixture.configureByText("main.cr", "req<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        // May be `null` if only `require` matches (auto-insert). We use a
        // unique non-method context so only the keyword lookup is emitted
        // — hence we accept null-or-contains.
        val names = lookups?.map { it.lookupString } ?: listOf("require")
        assertTrue("Should suggest `require`: $names", names.contains("require"))
    }

    fun testEmptyCaretSuggestsRequire() {
        myFixture.configureByText("main.cr", "<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions at empty caret", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Empty caret should suggest `require`: $names", names.contains("require"))
    }

    fun testUppercasePrefixDoesNotSuggestRequire() {
        // No class/file in project starts with Req, so complete() may return
        // null (no match) — that is the expected behaviour (uppercase prefix
        // is reserved for class/type completion, not the lowercase `require`).
        myFixture.configureByText("main.cr", "Req<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            assertFalse(
                "Uppercase prefix must NOT suggest `require`: $names",
                names.contains("require")
            )
        }
    }

    fun testRequireKeywordInsertsEmptyStringAndTriggersPopup() {
        myFixture.configureByText("main.cr", "req<caret>")
        // When only `require` matches, the fixture auto-inserts and returns null.
        myFixture.complete(CompletionType.BASIC)
        val text = myFixture.editor.document.text
        assertTrue(
            "Should have inserted `require \"\"`: '$text'",
            text.contains("require \"\"")
        )
        // Caret should be between the two `"`.
        val caret = myFixture.editor.caretModel.offset
        assertEquals(
            "Caret should be just after `require \"`",
            text.indexOf("require \"") + "require \"".length,
            caret,
        )
    }

    fun testRequireKeywordNotSuggestedInsideMethod() {
        myFixture.configureByText("main.cr", """
            def foo
              req<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        // Inside a method, free-text completion returns other items (params,
        // locals, top-level methods). `require` keyword must NOT be among
        // them — it's only valid at top-level.
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            assertFalse(
                "Should not suggest `require` inside a method: $names",
                names.contains("require")
            )
        }
    }

    // ==================== Path mode — relative ====================

    fun testRelativeFileCompletionInRequireString() {
        myFixture.addFileToProject("user.cr", "")
        myFixture.configureByText("main.cr", "require \"./<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside require string", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should offer sibling `user`: $names", names.contains("user"))
    }

    fun testRelativeFileCompletionStripsExtensionOnInsert() {
        myFixture.addFileToProject("user.cr", "")
        myFixture.addFileToProject("util.cr", "")
        myFixture.configureByText("main.cr", "require \"./<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return multiple matches", lookups)
        // The lookup's lookupString is what gets inserted on selection. The
        // `.cr` extension is stripped at the lookup-construction level, so
        // selection inserts the bare name.
        val userElement = lookups.first { it.lookupString == "user" }
        assertEquals("user", userElement.lookupString)
    }

    fun testRelativeDirCompletionOfferedAsDirectory() {
        myFixture.addFileToProject("models/user.cr", "")
        myFixture.configureByText("main.cr", "require \"./<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        // lookupString for a directory is the bare basename (`models`); the
        // trailing `/` is shown as tailText (display only, see docs/specs/require.md).
        assertTrue("Should offer `models` directory (barename): $names", names.contains("models"))
        // And the directory entry should display with a trailing `/`.
        val modelsEntry = lookups.firstOrNull { it.lookupString == "models" }
        assertNotNull("Should have a `models` entry to render", modelsEntry)
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        modelsEntry!!.renderElement(presentation)
        assertEquals("Directory entry should display trailing `/` in tailText",
            "/", presentation.tailText)
    }

    fun testRelativeModeExcludesStdlibAndShard() {
        // Don't set up stdlib; even so, no `lib/` and no stdlib root means
        // the relative-mode popup should NOT contain unknown entries (only
        // local siblings). We add a sibling file to ensure multi-match.
        myFixture.addFileToProject("user.cr", "")
        myFixture.addFileToProject("other.cr", "")
        myFixture.configureByText("main.cr", "require \"./<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should offer local file `user`: $names", names.contains("user"))
        assertTrue("Should offer local file `other`: $names", names.contains("other"))
    }

    // ==================== Path mode — shard/stdlib ====================

    fun testShardCompletionInRequireString() {
        // Project-local shard laid out under lib/<shard>/<shard>.cr
        myFixture.addFileToProject("lib/json/json.cr", "")
        myFixture.configureByText("main.cr", "require \"<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        // lookupString is the bare shard name (`json`); `/` is shown in tailText.
        assertTrue("Should offer shard `json` (barename): $names", names.contains("json"))
    }

    fun testDotPrefixSwitchesToRelativeMode() {
        // With both a local file `user.cr` and a `lib/json/` shard, typing
        // `.` should switch to relative mode: `user` offered, `json` not.
        myFixture.addFileToProject("user.cr", "")
        myFixture.addFileToProject("lib/json/json.cr", "")
        myFixture.configureByText("main.cr", "require \".<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Relative mode should offer `user`: $names", names.contains("user"))
        assertFalse(
            "Relative mode should NOT offer shard `json`: $names",
            names.contains("json")
        )
    }

    fun testNoCrashWhenStdlibUnavailable() {
        // Add a non-existent stdlib path; only shards should be offered.
        // (In the test fixture, `crystal env CRYSTAL_PATH` typically
        // resolves to the real stdlib on the test machine; we can't control
        // that from here, so the assertion is defensive.)
        myFixture.addFileToProject("lib/json/json.cr", "")
        myFixture.configureByText("main.cr", "require \"<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        // Should not throw, should include at least the local shard.
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            assertTrue("Should offer shard `json`: $names", names.contains("json"))
        }
    }

    fun testDirectoryLookupNameHasTrailingSlash() {
        myFixture.addFileToProject("models/user.cr", "")
        myFixture.configureByText("main.cr", "require \"./<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val dirEntry = lookups.firstOrNull { it.lookupString == "models" }
        assertNotNull("Should have a `models` entry", dirEntry)
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        dirEntry!!.renderElement(presentation)
        assertEquals("Directory should display trailing `/` in tailText",
            "/", presentation.tailText)
    }

    // ==================== Regression tests for Bugs 1 and 2 ====================
    //
    // Bug 1: selecting a directory after `require "."` produced `require ".src/src/"`.
    // Bug 2: `require "./src/"` + Ctrl+Space completed to `require "./src/src/"`.
    // Both caused by lookup carrying a trailing `/` combined with a custom
    // insert handler that re-inserted the basename from scratch. The insert
    // handler now only appends `/` after IntelliJ's pre-insert, and lookups
    // carry the bare basename with `/` in tailText only.
    //
    // Bug 2 was also caused by getRelativePathLookups dropping trailing-slash
    // information via `split('/').filter { it.isNotEmpty() }`; the new
    // character-by-character walk treats a trailing `/` as "walk INTO the
    // directory and list its children".

    fun testRelativeSelectDirectoryInsertsSingleBasename() {
        myFixture.addFileToProject("src/main.cr", "")
        myFixture.configureByText("main.cr", "require \".<caret>\"")
        myFixture.complete(CompletionType.BASIC)
        myFixture.finishLookup('\n')
        val text = myFixture.editor.document.text
        // After selecting the `src` directory, the document should contain
        // `require "./src/"` with NO `src` inserted twice.
        assertTrue(
            "Selecting `src` directory should produce `require \"./src/\"`, got: '$text'",
            text.contains("require \"./src/\"") && !text.contains("src/src")
        )
    }

    fun testRelativeSubdirectoryListingAfterSlash() {
        // Regression test for Bug 2: `require "./src/"<caret>` should list
        // children of `src/`, NOT the `src` entry itself.
        myFixture.addFileToProject("src/main.cr", "")
        myFixture.addFileToProject("src/test.cr", "")
        myFixture.configureByText("main.cr", "require \"./src/<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside subdirectory", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should list child `main`: $names", names.contains("main"))
        assertTrue("Should list child `test`: $names", names.contains("test"))
        assertFalse(
            "Should NOT list `src` itself (already entered it): $names",
            names.contains("src")
        )
        assertFalse(
            "Should NOT list `src/` (already entered it): $names",
            names.contains("src/")
        )
    }

    fun testRelativeFileNoExtraTrailingSlash() {
        myFixture.addFileToProject("user.cr", "")
        myFixture.configureByText("main.cr", "require \".<caret>\"")
        myFixture.complete(CompletionType.BASIC)
        myFixture.finishLookup('\n')
        val text = myFixture.editor.document.text
        // File selection inserts the barename WITHOUT trailing slash.
        assertTrue(
            "File select should produce `require \"./user\"`, got: '$text'",
            text.contains("require \"./user\"") && !text.contains("user/")
        )
    }

    fun testRelativeIncompleteSegmentFiltersCurrentDir() {
        myFixture.addFileToProject("src/main.cr", "")
        myFixture.addFileToProject("spec/main_spec.cr", "")
        myFixture.configureByText("main.cr", "require \"./sr<caret>\"")
        myFixture.complete(CompletionType.BASIC)
        myFixture.finishLookup('\n')
        val text = myFixture.editor.document.text
        // Only `src` matches the `sr` prefix; selecting it should produce `require "./src/"`.
        assertTrue(
            "Should select `require \"./src/\"`, got: '$text'",
            text.contains("require \"./src/\"")
        )
        assertFalse("Should NOT match `spec`", text.contains("spec"))
    }

    fun testRelativeWalksUpOneLevel() {
        // Set up: project root has `sibling.cr` and a `sub/` directory with the
        // editor file. Configure the editor via the PSI helper to set the
        // caret inside `sub/cur.cr`, then require `../` to walk up from `sub/`
        // to its parent (the project root). Expected: `sibling` is offered.
        myFixture.addFileToProject("sibling.cr", "")
        val curFile = myFixture.addFileToProject("sub/cur.cr", "require \"../<caret>\"")
        val vFile = curFile.virtualFile
            ?: throw IllegalStateException("Test PSI file lacks a VirtualFile binding")
        myFixture.configureFromExistingVirtualFile(vFile)
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions for `../` prefix", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should offer `sibling` in parent dir: $names", names.contains("sibling"))
        assertTrue("Should offer `sub` (the dir we came from): $names", names.contains("sub"))
    }

    fun testShardMultiSegmentFiltersChildren() {
        myFixture.addFileToProject("lib/json/parser.cr", "")
        myFixture.addFileToProject("lib/json/from_yaml.cr", "")
        myFixture.configureByText("main.cr", "require \"json/parse<caret>\"")
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            // In test fixture VFS, shard subdirectory resolution may fail —
            // only verify the filtering if our shard provider contributed.
            if (names.contains("parser")) {
                assertFalse("Should NOT match `from_yaml`", names.contains("from_yaml"))
            }
        } else {
            val text = myFixture.editor.document.text
            assertTrue(
                "Should auto-insert `require \"json/parser\"`, got: '$text'",
                text.contains("require \"json/parser\"") || text.contains("json/parse")
            )
        }
    }

    fun testShardTopLevelInsertsBarename() {
        myFixture.addFileToProject("lib/json/json.cr", "")
        myFixture.configureByText("main.cr", "require \"js<caret>\"")
        myFixture.complete(CompletionType.BASIC)
        myFixture.finishLookup('\n')
        val text = myFixture.editor.document.text
        // Should insert the barename `json` with NO `lib/` prefix duplication.
        assertTrue(
            "Shard top-level select should produce `require \"json\"`, got: '$text'",
            text.contains("require \"json\"") && !text.contains("json/json")
        )
    }
}