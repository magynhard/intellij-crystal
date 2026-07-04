# Block Highlighting

Extend brace highlighting to keyword-based blocks (`def...end`, `if...end`, `class...end`, etc.) matching RubyMine's behavior where all related structural keywords are highlighted when cursor is on any of them.

## Terminology

- **PairedBraceMatcher** — IntelliJ's token-based `BraceMatcher` for fast EDT matching of `()`, `[]`, `{}`, and simple keyword pairs.
- **CodeBlockSupportHandler** — IntelliJ's PSI-based extension point (`com.intellij.codeBlockSupportHandler`) for complex multi-block expressions like `if/elsif/else/end`. Returns a `List<TextRange>` of ALL related marker keywords.

## Architecture

Two mechanisms run independently:

### 1. PairedBraceMatcher — Token-based pairs (EDT)

All opening keywords are registered as `BracePair` entries with `END` as the closing token. Keywords with intermediate markers (`ELSE`, `ELSIF`, `WHEN`, `RESCUE`, `ENSURE`) are intentionally NOT registered as brace tokens — they are transparent to the token scanner, so depth counting from `IF` correctly reaches `END`.

**Registered pairs:**

| Left | Right | Notes |
|------|-------|-------|
| `DEF` | `END` | |
| `CLASS` | `END` | |
| `MODULE` | `END` | |
| `STRUCT` | `END` | |
| `ENUM` | `END` | |
| `ANNOTATION` | `END` | |
| `LIB` | `END` | |
| `MACRO` | `END` | |
| `VERBATIM` | `END` | |
| `IF` | `END` | ELSIF/ELSE are transparent |
| `UNLESS` | `END` | |
| `WHILE` | `END` | |
| `UNTIL` | `END` | |
| `FOR` | `END` | |
| `CASE` | `END` | WHEN/ELSE are transparent |
| `DO` | `END` | |
| `BEGIN` | `END` | RESCUE/ELSE/ENSURE are transparent |

All pairs are structural (higher priority than non-structural braces).

### 2. CodeBlockSupportHandler — PSI-based markers (background)

For each keyword block type, the handler:

1. Checks if `elementAtCursor` is a known structural marker keyword
2. Walks the PSI tree up to find the enclosing block
3. Collects all marker keywords within that block (e.g., `if`, `elsif`, `else`, `end`)
4. Returns their `TextRange`s

**Block types covered:**

| Enclosing PSI element | Markers collected |
|---|---|
| `if_expression` | `if`, `elsif`*, `else`*, `end` |
| `unless_expression` | `unless`, `else`*, `end` |
| `case_expression` | `case`, `when`*, `else`*, `end` |
| `begin_expression` | `begin`, `rescue`*, `else`*, `ensure`*, `end` |
| `def_definition` | `def`, `end` |
| `class_definition` | `class`, `end` |
| `module_definition` | `module`, `end` |
| `struct_definition` | `struct`, `end` |
| `enum_definition` | `enum`, `end` |
| `annotation_definition` | `annotation`, `end` |
| `lib_definition` | `lib`, `end` |
| `macro_definition` | `macro`, `end` |
| `verbatim_definition` | `verbatim`, `end` |
| `while_expression` | `while`, `end` |
| `until_expression` | `until`, `end` |
| `for_expression` | `for`, `end` |
| `do_block` | `do`, `end` |

## Highlighting behavior

| Cursor on | BraceMatcher marks | CodeBlockSupportHandler adds | Total marked |
|---|---|---|---|
| `if` | `if`, `end` | `elsif`*, `else`* | ALL |
| `elsif` | — | `if`, `elsif`, `else`, `end` | ALL |
| `else` | — | `if`, `elsif`*, `else`, `end` | ALL |
| `end` | `end`, matching opening | intermediate markers | ALL |
| `def` | `def`, `end` | — | `def`, `end` |
| `class` | `class`, `end` | — | `class`, `end` |

## Files to change

| File | Change |
|------|--------|
| `CrystalBraceMatcher.kt` | Add keyword `BracePair` entries |
| `CrystalCodeBlockSupportHandler.kt` (new) | Implement `CodeBlockSupportHandler` |
| `plugin.xml` | Register `codeBlockSupportHandler` extension |
| Test file(s) | Unit tests for handler, parser tests if needed |

## Bidirectional Name Resolution

Clicking on a definition name (e.g., `module Kann`, `class Foo`, `def bar`) highlights all usages in the current file and enables Rename (Shift+F6). Previously only the usage side worked (clicking on a usage highlights the definition).

### Architecture

Two extension points handle different scopes:

| Extension Point | Scope | Triggered By |
|----------------|-------|-------------|
| `HighlightUsagesHandlerFactory` | Current file only | Ctrl+Shift+F7, auto-caret highlighting |
| `FindUsagesHandlerFactory` | Entire project | Alt+F7, Rename (Shift+F6) |

### How it works

**Problem**: The `CONSTANT`/`IDENTIFIER` leaf inside a definition (e.g., `Kann` in `module Kann`) has no `PsiReference` — `createCrystalReference` returns null for `CrystalNamedElement` parents. The platform's default `TargetElementUtilBase.findTargetElement()` also fails because the leaf itself is not a `PsiNamedElement`.

**Solution**: Register custom factories that detect the definition-name case and delegate to handlers using `ReferencesSearch`:

1. `CrystalHighlightUsagesHandlerFactory` — detects when caret is on a `CONSTANT`/`IDENTIFIER` leaf whose parent is a `CrystalNamedElement`, creates `CrystalHighlightUsagesHandler`
2. `CrystalHighlightUsagesHandler` — uses `ReferencesSearch.search(target, LocalSearchScope(file))` to find all usages in the current file
3. `CrystalFindUsagesHandlerFactory` — creates `CrystalFindUsagesHandler` for project-wide usage search and rename
4. `CrystalFindUsagesHandler` — uses `ReferencesSearch.search(target, scope)` with configurable scope

### Behavior

| Cursor on | Highlighting | Rename | Find Usages |
|-----------|-------------|--------|-------------|
| `module Kann` (definition name) | All usages in file | Yes | Yes |
| `Kann` (usage) | Definition + all usages | Yes | Yes |
| `class` (keyword) | No highlighting | No | No |
| `def` (keyword) | No highlighting | No | No |

## Dependencies

- IntelliJ 2026.1+ (already targeted, build 261+)
- `PairedBraceMatcher` — standard API, no new deps
- `CodeBlockSupportHandler` — new EP `com.intellij.codeBlockSupportHandler`
- `HighlightUsagesHandlerFactory` — EP `com.intellij.highlightUsagesHandlerFactory`
- `FindUsagesHandlerFactory` — EP `com.intellij.findUsagesHandlerFactory`
