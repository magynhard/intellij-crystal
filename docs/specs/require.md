# `require` Keyword and Path Completion

Behavioural specification for completion of Crystal's `require` statement
(`require "json"`, `require "./user"`, …). Covers two related work items:

1. **Bug fix** — `def self.require(path)` (and other `def self.<keyword>`)
   rendered as `def require(path)end(path)` in the completion popup.
2. **Feature** — file-path completion inside the `require "..."` string,
   mirroring RubyMine's `require_relative` behaviour.

## Background

`require` is a **Crystal compiler pseudo-keyword**, not a real method. There
is no `def require`, `def self.require`, or `macro require` anywhere in the
Crystal 1.20 stdlib source. It is handled as an AST node `Crystal::Require`
by `Crystal::SemanticVisitor#require_file`.

The plugin's BNF already handles `require` specially:

```
require_statement ::= REQUIRE string_expression {pin=1}
```

`REQUIRE` is a dedicated lexer token (`Crystal.flex:165`), distinct from
`IDENTIFIER`. This means a `def self.require(path)` definition legitimately
parses via the `keyword_as_method` alternative of `method_name`, producing a
`CrystalMethodDefinition` whose name-leaf is a `REQUIRE` token, not an
`IDENTIFIER`.

---

## Section 1 — Bug Fix: `def self.<keyword>` Name Resolution

### Root cause

`CrystalStubbedElements.kt`'s helpers were written assuming method names are
always `IDENTIFIER` or `CONSTANT`:

- `findNameIdentifierInMethodName` (line 27) returns `null` for
  `def self.require(path) end` because it only matches `IDENTIFIER`/`CONSTANT`
  children, and `require` is a `REQUIRE` token.
- `getNameFromMethodName` (line 45) then enters its fallback path, which
  concatenates every child's text **except `SELF` and `DOT`**. The fallback has
  **no stop condition** at the parameter list or method body, so it walks the
  entire node and produces:
  ```
  "def require(path)\nend"
  ```
  This string becomes both the stub index key (polluting
  `CrystalTopLevelMethodIndex` / `CrystalMethodIndex` with an unusable key) and
  the `lookupString` of `LookupElementBuilder.create(psiElement)`, visible as
  `def require(path)end(path)` in the popup.

### Fix

Edit `src/main/kotlin/de/magynhard/crystal/psi/impl/CrystalStubbedElements.kt`:

1. **`findNameIdentifierInMethodName`** — advance past `DEF`, `SELF`, and
   `DOT` tokens; return the next token of **any** type as the name
   identifier. This recognises `REQUIRE`, `CLASS`, `END`, `IF`, `PLUS`, …
   as valid method names (matching the `keyword_as_method` BNF alternative
   at `Crystal.bnf:572`). As a defensive guard, stop the search and return
   `null` if `LPAREN` or `METHOD_BODY` appears before a non-skipped token
   (this should not happen for valid Crystal methods, but prevents the
   search from accidentally returning a parameter token).

2. **`getNameFromMethodName` fallback** — when the new
   `findNameIdentifierInMethodName` returns `null` (truly malformed input):
   concatenate child token text as today, but stop at the first `LPAREN`
   or `METHOD_BODY` element (the parameter list and body are NOT part of
   the method name) and skip the `DEF` token (currently included
   erroneously in the concatenated string). This path is now unreachable
   for valid Crystal methods, but kept as a defensive fallback.

### Result

| Definition | `getName()` before | `getName()` after |
|------------|---------------------|--------------------|
| `def kung` | `"kung"` | `"kung"` (unchanged) |
| `def self.tanzen` | `"tanzen"` | `"tanzen"` (unchanged) |
| `def self.require(path)` | `"def require(path)\nend"` ❌ | `"require"` ✓ |
| `def self.+(x)` | `"+"` | `"+"` (unchanged) |
| `def self.class(x)` | `"def class(x)\nend"` ❌ | `"class"` ✓ |

Side effects (all desirable):

- Stub key for `def self.require(path)` is now `"require"` (not the body text),
  so it stops polluting `CrystalMethodIndex` and `CrystalTopLevelMethodIndex`.
- Go to Definition, Find Usages, and Rename now work for
  `def self.<keyword>` methods (previously silently broken).
- The stray `require` entry the user observed disappears automatically once
  the dev project is reindexed (because the new stub key no longer matches
  `req…` against the broken string).

### Tests

New `src/test/kotlin/de/magynhard/crystal/CrystalKeywordMethodNameTest.kt`
(`BasePlatformTestCase`):

- `testKeywordMethodName_Require` — `def self.require(path) end` →
  `method.name == "require"`.
- `testKeywordMethodName_Class` — `def self.class(x) end` →
  `method.name == "class"`.
- `testKeywordMethodName_End` — `def self.end(x) end` →
  `method.name == "end"`.
- `testKeywordMethodName_OperatorUnchanged` — `def self.+(x) end` →
  `method.name == "+"`.
- `testKeywordMethodName_IdentifierUnchanged` — `def kung end` →
  `method.name == "kung"`.
- `testKeywordMethodNotRenderedAsBody` — configure `req<caret>` in a file
  containing `def self.require(path) end`, call `myFixture.complete(BASIC)`,
  assert no lookup's `lookupString` contains `"def"` or `"end"`.

Regression: `CrystalRenamePsiNameIdentifierOwnerTest`,
`CrystalGotoDeclarationTest`, `CrystalReferenceTest` must still pass.

### Out of scope

- Excluding `src/test/testData/` from the dev project's StubIndex is a
  separate concern. The name-resolution bug exists for any
  `def self.<keyword>` in user code too, so the fix is necessary regardless
  of the test-data leak.
- Whether to treat `require` as a "do not index even if defined explicitly"
  pseudo-keyword (analogous to `initialize` filter) is left to a future
  task; no user would benefit from defining `def require` themselves.

---

## Section 2 — Feature: `require` Keyword Lookup + Path Completion

### UX

Two independent completion contexts share one provider file:

#### 2A. Keyword context — typing `req<caret>` at a valid statement start

- Lookup: `require` with icon `AllIcons.Nodes.Include`, tail text `(name)`,
  type text `keyword`.
- On selection (via custom `InsertHandler`):
  1. Replace the just-typed prefix (`req…`) with `require`.
  2. Insert ` ""` after `require` (i.e. the document becomes
     `require ""`).
  3. Place the caret between the two `"`.
  4. Schedule an auto-popup so the path-completion window opens immediately.
- This mirrors the user's chosen behaviour ("Insert `require ""` + Datei-Popup").

The lookup is emitted only when **all** of these hold:

1. The prefix starts an **independent statement**. The completion position is
   resolved to its direct statement child under the nearest statement
   container (`CrystalFile`, `CrystalStatementList`, or a type body). That
   child's start offset must equal the completion prefix's start offset. This
   admits `require` at file level, in type bodies, and in top-level control-flow
   or block bodies while rejecting a prefix embedded in a larger expression:

   ```crystal
   Foo.req<caret>       # rejected: DOT call target
   Foo::req<caret>      # rejected: namespace continuation
   value = req<caret>   # rejected: assignment value
   foo(req<caret>)      # rejected: argument
   if req<caret>        # rejected: condition
   ```

   This is intentionally PSI-based rather than a blacklist of preceding
   characters. It also handles whitespace and multiline expression
   continuations correctly.
2. The caret is not inside a `def`, `fun`, or macro definition. Crystal 1.20's
   parser accepts `require` as an atomic expression but rejects it while the
   parser is nested in a `def` or `fun` (`check_not_inside_def("can't
   require")`). Macro-definition bodies remain excluded because completion in
   generated macro source is outside this provider's scope.
3. Crystal's `require` is lowercase; an uppercase `R` prefix
   (e.g. `Req<caret>`) must **not** trigger this lookup (uppercase
   prefixes are reserved for class/type completion).
4. `result.prefixMatcher.prefixMatches("require")` returns true (in
   practice, when the prefix is empty or starts with a lowercase `r`).

The context gate applies only to the synthesized keyword lookup. If user code
defines a real method named `require`, normal DOT completion may still offer
that method for `Foo.req<caret>` or `value.req<caret>`; it uses the method
lookup and must not receive the keyword's `require ""` insert handler.

Implementation gate:

```kotlin
val prefix = computeCompletionPrefix(parameters.editor, parameters.offset)
val lowercase = prefix.isEmpty() || prefix[0].isLowerCase()
val prefixStart = parameters.offset - prefix.length
val validContext = CrystalRequireCompletionProvider.isKeywordContext(
    position,
    prefixStart,
)
if (validContext && lowercase && result.prefixMatcher.prefixMatches("require")) {
    result.addElement(CrystalRequireCompletionProvider.getKeywordLookup())
}
```

Empty caret (Ctrl+Space without prefix) also emits the lookup, so users can
discover the keyword when the caret starts a valid independent statement.

#### 2A.1 Parser support in nested statement containers

The plugin grammar currently lists `require_statement` only in
`top_level_statement`, even though Crystal accepts it in contexts such as a
top-level `if` branch. Add `require_statement` to `statement` before the more
general expression alternatives. The existing explicit
`top_level_statement -> require_statement` alternative remains first and
continues to parse the common file-level form directly.

This parser change deliberately preserves a structured `CrystalRequireStatement`
even in semantically invalid source such as a `require` inside `def`. The
completion context gate remains responsible for not suggesting that invalid
construct; semantic diagnostics are separate from error-tolerant PSI parsing.

Regenerate the committed parser output after changing `Crystal.bnf`. A parser
golden test must cover `require` in an `if` branch, a type body, and a top-level
block body without any `PsiErrorElement`.

#### 2B. Path context — caret inside the string of a `require_statement`

```
require "<caret>"
require "./<caret>"
require "../models/<caret>"
```

Detection: `isInsideStringLiteral(position)` (the existing helper) returns
`true` (which already excludes interpolation `#{…}`), **and**
`PsiTreeUtil.getParentOfType(position, CrystalRequireStatement::class.java, false) != null`.
This check runs **before** the existing
`if (isInsideStringLiteral(position)) return` early-return in
`addCompletions` (line 70), so require-string completion bypasses the
global string-literal suppression. Positions inside an interpolation
inside a require string are NOT handled by this provider — they fall
through to normal expression completion (because `isInsideStringLiteral`
returns `false` for interpolation positions).

Input: the path text typed so far (substring of the document from the opening
`"` to the caret, excluding the quote itself).

#### Mode dispatch (based on the first character of the typed path)

| First char(s) | Mode | Root |
|----------------|------|------|
| `.` or `/` (any path starting with a path separator) | **Relative** | Directory of the current `.cr` file |
| (anything else, including empty caret) | **Shard/Stdlib** | `<project.basePath>/lib` + stdlib root |

This matches Crystal's own resolution rule: a leading `.`/`/` makes the path
file-relative, otherwise Crystal searches `CRYSTAL_PATH` (`lib:/usr/lib/crystal`).

#### Relative mode

- Root = `containingFile.virtualFile.parent`.
- List children of root (filtered by the typed prefix, processed through the
  `CompletionResultSet`'s prefix matcher):
  - `.cr` files → lookup name is the file name **without** `.cr`.
  - Directories → lookup name is the directory name plus a trailing `/`.
- Insert handler:
  - Replaces the path segment after the last `/` with the chosen entry.
  - For a file entry → completes the path; no re-trigger.
  - For a directory entry → appends `/`, moves the caret after the `/`, and
    re-triggers the auto-popup so the user can descend.
- Files matching the current file are excluded (you wouldn't
  `require "./this_file"`).

#### Shard/Stdlib mode

- Root A: `<project.basePath>/lib` if it exists as a directory.
- Root B: `CrystalStdlibResolver.resolveStdlibPath(project)` (may be `null`).
- List children of each root:
  - Directories → shard name (e.g. `json`, `ostruct`, `securerandom`).
  - `.cr` files at the root of stdlib (e.g. `base64.cr`) → file name
    without `.cr`. Files inside shard subdirectories are NOT listed at the
    top level (they are reached via traversal, not directly).
- Caching: the resolved `VirtualFile` for root B must be cached
  project-scoped (see "Risks and Open Points" below) — calling
  `crystal env CRYSTAL_PATH` on every completion invocation would impose
  ~150-250 ms subprocess latency and stall the popup.
- Subdirectory traversal: Crystal allows `require "json/parser"`, so
  choosing a directory entry appends `/` and re-triggers the popup, listing
  the subdirectory's contents (same listing rule: `.cr` files stripped of
  extension, subdirectories suffixed with `/`).
- Dedup by name (a shard named `json` and a project file named `json.cr` in
  lib/ collide — prefer the shard directory).
- Insert handler: replaces the entire typed prefix (from the opening `"` to
  the caret) with the chosen entry; for directories, appends `/` and
  re-triggers.

#### Performance

- All directory listings use `VirtualFile.getChildren()` and
  `LocalFileSystem.getInstance().findFileByPath()`. No `FileTypeIndex` (per
  §AGENTS.md). The stdlib root is resolved once per completion call via the
  existing cached `CrystalStdlibResolver`.
- The popup typically contains <100 entries (stdlib has ~110 top-level
  files + dirs), so no chunking is needed.

### Files

- New `src/main/kotlin/de/magynhard/crystal/completion/CrystalRequireCompletionProvider.kt`
  — object with `getKeywordLookup(): LookupElement` and
  `getPathLookups(position: PsiElement, project: Project, prefix: String): List<LookupElement>`.
- `CrystalCompletionContributor.kt` — register both at the top of
  `addCompletions`:
  1. **Before** `isInsideStringLiteral` suppression: check
     `getParentOfType(position, CrystalRequireStatement::class.java) != null`
     and, if true, delegate to `getPathLookups` and `return`.
   2. **After** the general suppression guards (string literals, numeric
      literals): before context-specific completion dispatch, emit
      `getKeywordLookup()` only when `isKeywordContext(position, prefixStart)`
      and `result.prefixMatcher.prefixMatches("require")` both succeed.
- `Crystal.bnf` — add `require_statement` to `statement`, then regenerate the
  committed parser output in `src/main/gen/`.

### Insert handler

`CrystalRequireInsertHandler` (private object in the new provider file):

```kotlin
object CrystalRequireInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val editor = context.editor
        val document = editor.document
        // The 'require' text has just been inserted at context.startOffset..context.tailOffset
        // Append ' ""' and move the caret between the quotes.
        val end = context.tailOffset
        document.insertString(end, " \"\"")
        editor.caretModel.moveToOffset(end + 2) // caret between the two '"'
        AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
    }
}
```

`CrystalRequirePathInsertHandler(pathSegmentStart: Int, isDirectory: Boolean)`
handles the path-mode inserts: replaces the text in
`[pathSegmentStart, current caret)` with the chosen entry, appends `/` for
directories, and re-triggers the popup.

### Tests

New `src/test/kotlin/de/magynhard/crystal/CrystalRequireCompletionTest.kt`.
Tests that need stdlib indexed must call `setupStdlib()` (the same helper
used by `CrystalStdlibIndexDiagnosticTest`: add a `main.cr` containing
`puts 1`, resolve and filter the stdlib roots, then notify the synthetic
library refresh through `CrystalStdlibIndexRefresher`). For relative-mode tests, `myFixture.addFileToProject`
suffices because completion uses the in-project VFS directly.

- `testRequireKeywordSuggestedForReqPrefix` — `req<caret>` at statement
  position → lookups contain `require` with tail `(name)`.
- `testRequireKeywordInsertsEmptyStringAndTriggersPopup` — selecting `require`
  → document text contains `require ""`, caret at offset between the two `"`.
  (Auto-popup side effect is validated by structuring the test around the
  inserted text, not by mocking `AutoPopupController` — too brittle.)
- `testUppercasePrefixDoesNotSuggestRequire` — `Req<caret>` → `require` NOT
  in lookups (lowercase-only keyword).
- `testEmptyCaretSuggestsRequire` — `<caret>` with no prefix → `require` in
  lookups.
- `testRequireKeywordSuggestedAfterSemicolon` — `foo; req<caret>` → keyword
  lookup is offered as a new statement.
- `testRequireKeywordSuggestedInIfBranch` — a standalone `req<caret>` in a
  top-level `if` body → keyword lookup is offered.
- `testRequireKeywordSuggestedInTypeBody` — a standalone `req<caret>` in a
  class body → keyword lookup is offered.
- `testRequireKeywordSuggestedInTopLevelBlock` — a standalone `req<caret>` in
  a top-level block body → keyword lookup is offered.
- Context rejection tests cover `Foo.req<caret>`, `value.req<caret>`,
  `Foo::req<caret>`, `x : req<caret>`, `x = req<caret>`,
  `foo(req<caret>)`, and `if req<caret>`. None may insert or render the
  synthesized keyword lookup.
- `testRealRequireMethodStillSuggestedAfterDot` — a real
  `def self.require(path)` remains available through DOT method completion and
  does not use the keyword insert handler.
- Existing method-body rejection is extended to `fun` and macro-definition
  bodies.
- A parser golden test covers nested statement containers (`if`, type body,
  and top-level block) and contains no `PsiErrorElement`.
- `testRelativeFileCompletionInRequireString` — file has sibling `user.cr`;
  `require "./<caret>"` → `user` offered; selecting inserts `require "./user"`
  (no `.cr` extension).
- `testRelativeDirCompletionInRequireString` — subdirectory `models/`
  offered as `models/`; selecting inserts `require "./models/"` and
  re-triggers popup (validated by checking the popup re-opens via a second
  `myFixture.complete(BASIC)` call returning directory contents).
- `testRelativeModeExcludesStdlib` — `require "./<caret>"` → `json` (stdlib)
  NOT offered.
- `testStdlibCompletionInRequireString` — `require "<caret>"` with stdlib
  indexed → `json` offered; selecting inserts `require "json"`.
- `testShardCompletionInRequireString` — `lib/json/json.cr` in project →
  `require "<caret>"` offers `json`.
- `testDotPrefixSwitchesToRelativeMode` — combines the two above: with both
  stdlib and a local `user.cr`, `require ".<caret>"` only offers `user`, not
  `json`.
- `testNoCrashWhenStdlibUnavailable` — when
  `CrystalStdlibResolver.resolveStdlibPath(project)` returns `null`, only
  shards from `lib/` offered (best-effort, no exception).
- `testDirectoryChoiceAppendsSlash` — selecting a directory inserts
  `<name>/` and re-arms the popup.

Existing tests that must still pass:
- `CrystalParserTest.testRequireStatement`
- `CrystalCompletionTest.*` (free-text, dot, etc.)
- `CrystalStdlibIndexDiagnosticTest.*`

### Out of scope (future work)

- Completion inside `require {{ "..." }}` macro interpolation (deferred —
  macro-interpolation completion is its own spec).
- ECR template file path completion (different file type, not part of this
  spec).
- Validation / inspection of unresolved `require` paths (e.g. squiggle when
  `require "jsn"` is a typo of `json`). Tracked separately.
- Cross-project shard dependencies not in `lib/` (e.g. via `shard.yml`
  resolution). This spec only reads the flat `lib/` directory.
- `require_relative` — Crystal has no such keyword; `require "./..."` is the
  relative form.

---

## Implementation Order

1. **Section 1 (bug fix)** — small, isolated change to
   `CrystalStubbedElements.kt`. Land first; it is a prerequisite for the
   keyword lookup in Section 2A (otherwise `def self.require` would still
   pollute the popup).
2. **Section 2A (keyword lookup)** — depends on §1 being merged. Produces a
   working `require ""` insert handler.
3. **Section 2B (path completion)** — depends on 2A (the keyword lookup
   drives the entry into the string). The larger chunk: relative + shard +
   stdlib mode dispatch, traversal, re-trigger.
4. **Spec/changelog** — written alongside the spec sections above; CHANGELOG
   entry will be added under `[0.1.18]` Added in the same commit:

   > **`require` keyword and path completion** — typing `req<caret>` now
   > offers the `require` keyword; selecting it inserts `require ""` and
   > opens a path-completion popup. Inside the require string, the popup
   > offers `.cr` files (relative mode, prefix `.` or `/`) or shards and
   > stdlib entries (default mode, mirroring RubyMine's
   > `require_relative`). Subdirectory traversal is supported.
   >
   > Also fixes `def self.<keyword>` (e.g. `def self.require(path)`) being
   > rendered as `def require(path)end(path)` in the completion popup — the
   > name-identifier lookup now recognises keyword-as-method tokens, and the
   > fallback name-composition loop stops at the parameter list instead of
   > walking the entire method body.

---

## Risks and Open Points

- **Re-trigger mechanism for subdirectory traversal.** IntelliJ's
  `AutoPopupController.scheduleAutoPopup` is the idiomatic way; existing
  code uses it in `CrystalTypedHandler.kt:26` for `::`. Consistent.
- **Stdlib path resolution cost.** `CrystalStdlibResolver.resolveStdlibPath`
  runs `crystal env CRYSTAL_PATH` via `ProcessBuilder`. It is called when the
  synthetic stdlib provider is queried and by path completion. **For path completion inside `require`, the
  resolver is called once per completion invocation.** If the subprocess
  takes >200ms (cold), the popup may lag on first use. Mitigation: cache
  the resolved VirtualFile in `CrystalStdlibResolver` (project-scoped,
  invalidated on settings change). This caching is a prerequisite task in
  the implementation plan, not part of this design.
- **Shard name collision.** A user file `lib/json/foo.cr` would collide
  with the `json` shard. The dedup rule prefers the shard directory; this
  matches Crystal's own resolution (longest `lib/` path first).
- **Project root detection.** `project.basePath` is the IDE's open project
  root. For a Crystal project with a `shard.yml`, this is the directory
  containing `shard.yml`; we rely on the project being opened at the right
  root. No extra detection logic here — if a user opens the wrong
  directory, `lib/` won't be found and the popup falls back to stdlib-only.
  Acceptable for v1.
