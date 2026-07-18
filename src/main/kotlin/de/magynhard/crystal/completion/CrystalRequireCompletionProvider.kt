package de.magynhard.crystal.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver

/**
 * Completion support for Crystal's `require` statement.
 *
 * Two modes (see `docs/specs/require.md`):
 *
 * 1. **Keyword mode** — caret at top-level position with a `r…` prefix that
 *    matches `require`. Emits a single keyword lookup; selecting it inserts
 *    `require ""` and schedules the path-completion popup.
 * 2. **Path mode** — caret inside the string of a `require_statement`.
 *    Dispatches by the first typed character:
 *    - `.` or `/` → relative mode (list `.cr` files / dirs in the current
 *      file's directory; subdirectory traversal via re-triggered popup).
 *    - otherwise → shard/stdlib mode (list directories under
 *      `<project>/lib/` and the stdlib root).
 *
 * `require` is a compiler pseudo-keyword in Crystal — there is no `def
 * require` in stdlib. The keyword lookup is synthesized here, not produced
 * from the method index.
 */
object CrystalRequireCompletionProvider {

    private const val KEYWORD = "require"

    // ==================== Keyword mode ====================

    /**
     * Builds the synthesized `require` keyword lookup. Selecting it inserts
     * `require ""<caret>` and schedules the path-completion popup.
     */
    fun getKeywordLookup(): LookupElement =
        LookupElementBuilder.create(KEYWORD)
            .withIcon(AllIcons.Nodes.Include)
            .withTailText("(name)", true)
            .withTypeText("keyword", true)
            .withInsertHandler(CrystalRequireInsertHandler)

    /**
     * `true` iff the caret is at a top-level statement position where a
     * `require "..."` statement may legitimately appear.
     *
     * `require_statement` is only a `top_level_statement` alternative in the
     * BNF ( Crystal.bnf:199 ), so the keyword lookup is suppressed inside
     * method bodies, class bodies, blocks, and macro bodies.
     */
    fun isAtTopLevel(position: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(
            position,
            de.magynhard.crystal.psi.CrystalMethodDefinition::class.java,
            de.magynhard.crystal.psi.CrystalClassBody::class.java,
            de.magynhard.crystal.psi.CrystalBlock::class.java,
            de.magynhard.crystal.psi.CrystalMacroBody::class.java,
        ) == null

    // ==================== Path mode ====================

    /**
     * Produces path-completion lookup elements based on the prefix typed
     * inside the require string. See `getPathPrefixInsideRequireString`.
     *
     * Returns an empty list when neither the project's `lib/` nor the stdlib
     * root can be resolved (best-effort; never throws).
     */
    fun getPathLookups(
        position: PsiElement,
        project: Project,
        pathPrefix: String,
        originalFile: com.intellij.psi.PsiFile?,
    ): List<LookupElement> {
        if (pathPrefix.startsWith(".") || pathPrefix.startsWith("/")) {
            return getRelativePathLookups(position, originalFile, pathPrefix)
        }
        return getShardStdlibPathLookups(project, pathPrefix)
    }

    /**
     * Reads the path text typed so far inside a `require "..."` string — the
     * substring of the document between the opening `"` and the caret.
     *
     * Returns `null` if [position] is not inside a `require_statement`'s
     * string (callers gate path mode on this).
     */
    fun getPathPrefixInsideRequireString(
        position: PsiElement,
        editor: Editor,
        offset: Int,
    ): String? {
        val requireStmt = PsiTreeUtil.getParentOfType(
            position, CrystalRequireStatement::class.java, false
        ) ?: return null
        // Confirm the caret is inside the require's string_expression.
        val stringExpression = requireStmt.stringExpression ?: return null
        if (!stringExpression.textRange.containsOffset(offset)) return null
        // Find the opening `"`. The string_expression's text starts with `"`,
        // but the caret might be on the dummy identifier injected by the
        // completion framework; we read the raw document text.
        val document = editor.document.charsSequence
        val requireStart = requireStmt.textRange.startOffset
        // The first `"` after the REQUIRE keyword — scan forward from requireStart.
        var quoteOffset = -1
        for (i in requireStart until offset) {
            if (document[i] == '"') { quoteOffset = i; break }
        }
        if (quoteOffset < 0) return ""
        // Exclude the opening quote from the returned prefix.
        return document.subSequence(quoteOffset + 1, offset).toString()
    }

    // ---------------- Relative mode ----------------

    private fun getRelativePathLookups(
        position: PsiElement,
        originalFile: com.intellij.psi.PsiFile?,
        pathPrefix: String,
    ): List<LookupElement> {
        // Use the original (non-dummy) file to locate the VirtualFile — the
        // completion framework's `position.containingFile` may be an in-memory
        // copy without a VirtualFile binding. Fall back to position's
        // containingFile (which is reliable for the PSI tree structure, just
        // not for VFS lookups) when originalFile is unavailable.
        val containingFile = originalFile?.virtualFile
            ?: position.containingFile?.virtualFile
            ?: return emptyList()
        // Resolve the directory of the current file.
        var dir = containingFile.parent ?: return emptyList()

        // Character-by-character walk of pathPrefix. A trailing `/` on a
        // segment means "walk INTO that directory" — the next popup lists its
        // children. A segment without a trailing `/` is the partially-typed
        // segment being filtered.
        val n = pathPrefix.length
        var i = 0
        while (i < n) {
            val c = pathPrefix[i]
            when {
                c == '/' -> {
                    i++  // bare slash separator at start (e.g. `/foo` — rare)
                }
                c == '.' -> {
                    if (i + 1 < n && pathPrefix[i + 1] == '.') {
                        // ".." — walk up one level
                        dir = dir.parent ?: return emptyList()
                        i += 2
                        if (i < n && pathPrefix[i] == '/') i++  // skip the trailing '/'
                    } else {
                        // "." — current directory (no-op)
                        i++
                        if (i < n && pathPrefix[i] == '/') i++  // skip the trailing '/'
                    }
                }
                else -> {
                    // Read segment up to next '/' (or end of prefix).
                    val sb = StringBuilder()
                    while (i < n && pathPrefix[i] != '/') {
                        sb.append(pathPrefix[i])
                        i++
                    }
                    val seg = sb.toString()
                    if (i < n && pathPrefix[i] == '/') {
                        // Trailing slash — walk INTO this segment
                        val child = dir.findChild(seg)
                        dir = child ?: return emptyList()
                        if (!dir.isDirectory) return emptyList()
                        i++  // skip the '/'
                    } else {
                        // No trailing slash — `seg` is the partially-typed
                        // segment; filter the current directory's children.
                        return listChildren(dir, seg, pathPrefix, currentFile = containingFile)
                    }
                }
            }
        }
        // Path was fully consumed (ending with a path indicator like `./` or
        // `../`, or with a trailing `/` after a real segment) — list all
        // children of the final directory with no prefix filter.
        return listChildren(dir, "", pathPrefix, currentFile = containingFile)
    }

    // ---------------- Shard/Stdlib mode ----------------

    private fun getShardStdlibPathLookups(
        project: Project,
        pathPrefix: String,
    ): List<LookupElement> {
        val result = mutableListOf<LookupElement>()
        val seen = mutableSetOf<String>()

        // Compute the directory to list and the basename filter, walking into
        // trailing-slash segments so `require "json/parser"` lists children of
        // `lib/json/` (or `stdlib/json/`) filtered by `parser`.
        val (relativeDir, lastSegment) = walkShardStdlibPrefix(pathPrefix)

        // Root A: project's lib/ directory (shards installed via `shards install`).
        val basePath = project.basePath
        if (basePath != null) {
            val libDir = LocalFileSystem.getInstance().findFileByPath("$basePath/lib")
            if (libDir != null && libDir.isDirectory) {
                val target = resolveSubdir(libDir, relativeDir)
                if (target != null) {
                    for (lookup in listChildren(target, lastSegment, pathPrefix, currentFile = null)) {
                        val name = lookup.lookupString
                        if (seen.add(name)) result.add(lookup)
                    }
                }
            }
        }

        // Root B: Crystal stdlib (json, ostruct, base64, …).
        // Use CrystalStdlibRoots.enumerate to skip the compiler/, lib_c/,
        // llvm/, etc. subtrees from the distribution — see docs/specs/require.md
        // and the recent over-indexing fix.
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        if (stdlibRoot != null && stdlibRoot.isDirectory) {
            for (stdlibSourceRoot in de.magynhard.crystal.sdk.CrystalStdlibRoots.enumerate(stdlibRoot)) {
                val target = resolveSubdir(stdlibSourceRoot, relativeDir)
                if (target != null) {
                    for (lookup in listChildren(target, lastSegment, pathPrefix, currentFile = null)) {
                        val name = lookup.lookupString
                        if (seen.add(name)) result.add(lookup)
                    }
                }
            }
        }

        return result
    }

    /**
     * Walks a shard/stdlib prefix and splits it into the subdirectory path
     * (with `/` separators) and the basename filter.
     *
     * `pathPrefix` does not start with `.` or `/` (the dispatch in
     * [getPathLookups] has already routed it to shard/stdlib mode). Examples:
     *
     | pathPrefix    | relativeDir | lastSegment |
     |---------------|-------------|-------------|
     | `json`        | `""`        | `json`      |
     | `json/`       | `json`      | `""`        |
     | `json/parser` | `json`      | `parser`    |
     | `json/pa`     | `json`      | `pa`        |
     */
    private fun walkShardStdlibPrefix(pathPrefix: String): Pair<String, String> {
        val lastSlash = pathPrefix.lastIndexOf('/')
        return if (lastSlash < 0) {
            "" to pathPrefix
        } else {
            pathPrefix.substring(0, lastSlash) to pathPrefix.substring(lastSlash + 1)
        }
    }

    /**
     * Resolves a relative subdirectory path (e.g. `"json"`, `""`) against
     * [root] and returns the corresponding VirtualFile, or `null` if any
     * segment is missing or not a directory.
     */
    private fun resolveSubdir(root: VirtualFile, relativeDir: String): VirtualFile? {
        if (relativeDir.isEmpty()) return root
        var current: VirtualFile = root
        for (seg in relativeDir.split('/').filter { it.isNotEmpty() }) {
            current = current.findChild(seg) ?: return null
            if (!current.isDirectory) return null
        }
        return current
    }

    /**
     * Computes the text that should be inserted between the opening `"` of a
     * require string and the caret, given the [pathPrefix] the user has
     * already typed and the [childName] they just selected.
     *
     * This is the prefix of [pathPrefix] up to and including the last `/`,
     * followed by [childName]. Special cases: when [pathPrefix] is `.` or
     * `..` (with no trailing slash), we synthesize `./` / `../` so the
     * inserted text starts with the path indicator — e.g. for `require "."`
     * the lookup `src` inserts as `./src/`.
     *
     * Examples (pathPrefix → inserted text for childName `src`):
     *   `.`     → `./src`
     *   `.`     → `./src/` (with isDirectory=true, caller appends `/`)
     *   `./sr`  → `./src`
     *   `./src/` → `./src/main` (for childName `main`)
     *   `../`   → `../src`
     *   `json/` → `json/parser` (for childName `parser`)
     *   `json`  → `json` (top-level shard name, no slash prepended)
     */
    private fun computeFullInsertPath(pathPrefix: String, childName: String): String {
        val lastSlash = pathPrefix.lastIndexOf('/')
        val prefixUp = if (lastSlash >= 0) {
            pathPrefix.substring(0, lastSlash + 1)
        } else if (pathPrefix == "." || pathPrefix == "..") {
            pathPrefix + "/"
        } else {
            ""
        }
        return prefixUp + childName
    }

    /**
     * Lists children of [dir] filtered by [segmentPrefix]; returns lookups
     * suitable for completion.
     *
     * Each lookup's `lookupString` is the child's **barename** (e.g. `src`,
     * `main`) so IntelliJ's default word-character prefix matcher filters
     * correctly. When the user selects a lookup, IntelliJ's pre-insert
     * replaces only the matched-prefix range (which ends at the last `/`)
     * with the barename — then our `InsertHandler` rewrites the require-path
     * content to the full form (e.g. `./src/`), restoring the path-component
     * indicators (`.` `..` `/`) that IntelliJ discarded.
     *
     * Directories are displayed with a trailing `/` in `tailText` (UX cue);
     * selecting them inserts the full pathname including the trailing `/` and
     * re-arms the popup for traversal.
     *
     * The current file is excluded from listings (you wouldn't
     * `require "./this_file"`).
     */
    private fun listChildren(
        dir: VirtualFile,
        segmentPrefix: String,
        pathPrefix: String,
        currentFile: VirtualFile?,
    ): List<LookupElement> {
        val result = mutableListOf<LookupElement>()
        val children = try {
            dir.children
        } catch (_: Throwable) {
            return emptyList()
        }

        for (child in children) {
            if (child.name.startsWith(".")) continue  // skip dotfiles
            if (child.isDirectory) {
                if (!child.name.startsWith(segmentPrefix)) continue
                val fullInsertPath = computeFullInsertPath(pathPrefix, child.name) + "/"
                result.add(
                    LookupElementBuilder.create(child.name)
                        .withIcon(AllIcons.Nodes.Folder)
                        .withTailText("/", true)
                        .withTypeText("directory", true)
                        .withInsertHandler(CrystalRequirePathInsertHandler(fullInsertPath, isDirectory = true))
                )
            } else if (child.extension == "cr") {
                val baseName = child.nameWithoutExtension
                if (!baseName.startsWith(segmentPrefix)) continue
                if (child == currentFile) continue  // don't suggest requiring the current file
                val fullInsertPath = computeFullInsertPath(pathPrefix, baseName)
                result.add(
                    LookupElementBuilder.create(baseName)
                        .withIcon(AllIcons.FileTypes.Text)
                        .withTypeText("file", true)
                        .withInsertHandler(CrystalRequirePathInsertHandler(fullInsertPath, isDirectory = false))
                )
            }
        }
        return result
    }
}

// ==================== Insert handlers ====================

/**
 * Inserts `require ""` and places the caret between the quotes, then
 * schedules the path-completion popup.
 */
private object CrystalRequireInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val editor = context.editor
        val document = editor.document
        // The 'require' keyword has just been inserted at [startOffset, tailOffset).
        val end = context.tailOffset
        document.insertString(end, " \"\"")
        // Caret between the two `"`.
        editor.caretModel.moveToOffset(end + 2)
        AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
    }
}

/**
 * Path-mode insert handler: rewrites the require-path content between the
 * opening `"` (after `require`) and the caret to [fullInsertPath].
 *
 * IntelliJ's standard completion has already pre-inserted the barename
 * `lookupString` at the matched-prefix range — but that matched range stops
 * at the last `/` (since `.` and `/` are not word characters), losing the
 * path-component indicators. We restore them by rewriting the full path
 * content from the opening `"` to the caret with [fullInsertPath]
 * (e.g. `./src/`, `./src/main`, `json/parser`).
 *
 * For directory entries, [fullInsertPath] already ends with `/`; we also
 * schedule another auto-popup so the user can descend into the subdirectory.
 */
private class CrystalRequirePathInsertHandler(
    private val fullInsertPath: String,
    private val isDirectory: Boolean,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val editor = context.editor
        val document = editor.document
        val caret = editor.caretModel.offset

        // Find the opening `"` of the require string by scanning backward from
        // the caret. The pre-inserted barename may have moved text around, but
        // the opening quote is always before it.
        val text = document.charsSequence
        var quoteOffset = -1
        var i = caret - 1
        while (i >= 0) {
            if (text[i] == '"') { quoteOffset = i; break }
            i--
        }
        if (quoteOffset < 0) return  // shouldn't happen — we're inside a require string

        // Rewrite [pathStart, caret) with fullInsertPath.
        val pathStart = quoteOffset + 1
        document.deleteString(pathStart, caret)
        document.insertString(pathStart, fullInsertPath)
        val newCaret = pathStart + fullInsertPath.length
        editor.caretModel.moveToOffset(newCaret)

        if (isDirectory) {
            // Re-arm the popup so the user can descend into this subdirectory.
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
        }
    }
}