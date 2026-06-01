# TODO — IntelliJ Crystal Plugin

## Parser Extensions (BNF Grammar)

The current grammar covers the most common constructs. The following extensions are planned:

- [ ] Full generics (`Array(T)`, `forall T`, constraints)
- [ ] Macro body parsing (`{% %}`, `{{ }}`, `{% for %}`)
- [x] Union types as type annotations (`Int32 | String`)
- [ ] Proc/Lambda types (`-> Int32`, `Proc(Int32, String)`)
- [ ] Proc literals (`->{ }`, `->(x) { }`)
- [ ] Pattern matching (`case...in`, Crystal 1.x+)
- [ ] Multi-assignment (`a, b = 1, 2`)
- [ ] Splat parameters (`*args`, `**kwargs`)
- [x] Annotation usage parsing (`@[Deprecated]`, `@[JSON::Serializable]`, `@[Link("sqlite3")]`)
- [x] `asm` blocks
- [ ] Named tuples (`{name: "foo", age: 42}`)
- [ ] `select` statement (concurrency)
- [x] Heredocs as expressions in parser (lexer already supports them)
- [ ] Better operator precedence (Pratt parsing or precedence climbing)
- [x] Type restrictions on parameters (`def foo(x : Int32)`)
- [ ] Default parameter values (full expressions)
- [ ] Visibility modifiers as modifier nodes on PSI elements
- [ ] `with...yield` blocks
- [x] `pointerof`, `offsetof` as expressions
- [x] String interpolation as nested expressions in parser
- [x] Suffix if/unless/while (`expr if condition`)
- [ ] Ternary operator (partially exists: `? :` in expression rule)
- [x] Typed variable declarations (`x : String | Nil`) as statements
- [x] Generic type arguments in type references (`Array(String)`, `Hash(String, Int32)`)

## IDE Features (require parser improvements)

- [x] Reference resolution — resolve variables/methods to their declarations (via StubIndex + FileTypeIndex fallback + local scope; works cross-file)
- [x] Instance/class variable navigation — Go to Definition (@name/@@name → property declaration/getter/assignment) + Find Usages within class
- [x] Code completion — context-aware suggestions (dot-completion with type inference, free-text with classes/methods/locals, type completion in annotations/generics/union types, stdlib types)
- [x] Type inference (basic) — deduce variable type from assignment (`x = Klasse.new`) and parameter annotations (`x : Type`)
- [ ] Scope-aware rename — improve current token-based rename with scope analysis
- [x] Semantic highlighting — visually distinguish variables, methods, types, and parameters
- [ ] Inlay hints — show inferred types on variables
- [x] Type checking — validate argument types against method parameter type annotations (e.g. passing `String` to a parameter typed `Int32` shows an error). Supports numeric autocasting, union types, nilable types, overloads, named args, splat skip. Phase 2: inheritance hierarchy, generics, array/hash literals.
- [x] Argument count checking — validate number of arguments against method parameters. Reports missing required arguments (warning on method name) and excess arguments (warning on each extra arg). Supports named args, splat/double-splat, block params, default values, overloads, DOT-calls, bare calls.
- [x] Unused variable detection — reports local variables that are assigned but never read (WEAK_WARNING). Supports reassignment analysis (each overwritten-before-read assignment warned individually), compound assignments (treated as read), underscore-prefix convention. Ignores method parameters, instance/class vars.
- [x] Quick documentation — display doc comment above `def` (Ctrl+Q / F1 / hover; shows syntax-highlighted signature + Markdown-rendered doc comments with code blocks)
- [ ] Implement members — generate stubs for abstract methods

> **Note:** Parameter Info and Structure View are already implemented using the PSI parser and StubIndex.
> Parameter Info supports parenthesized calls, bare (parenthesis-free) calls, dot-calls, and class method calls.
> It works correctly when the cursor is after a comma (with or without trailing whitespace/argument),
> and also when no argument has been typed yet (e.g. `foo ` with cursor after the space).

## IDE Features (independent of parser)

- [ ] Ameba integration (Crystal linter) — show inspections from `ameba`
- [ ] Crystal Shards support — `shard.yml` parsing, dependency completion
- [x] Test runner — connect `crystal spec` to IntelliJ's test UI (SMTRunner with real-time output parsing, gutter run icons, single-test execution via file:line, re-run failed tests)
- [x] Debugger integration — LLDB via DAP (Debug Adapter Protocol) with lldb-dap, Crystal formatters bundled, supports debugging both programs and specs
- [x] Project SDK — detect and configure Crystal version
- [ ] New file templates — create class, module, spec files
- [ ] Spell checking in strings and comments
- [ ] Markdown rendering for doc comments

## Infrastructure

- [ ] More lexer tests (edge cases: nested interpolation, regex vs. division)
- [x] Parser tests (gold-file based)
- [x] Platform tests (EnterHandler — 16 tests covering end-insertion, balance, indentation)
- [ ] CI/CD pipeline (GitHub Actions)
- [x] Plugin Marketplace publication
- [x] Plugin icon for Marketplace
- [ ] Automated changelog

## Key Decisions

- **Crystalline LSP removed** — unmaintained, replaced by plugin-native Go to Definition via CrystalReference + StubIndex + FileTypeIndex fallback.
- **Two-tier definition lookup** — CrystalDefinitionFinder uses StubIndex (fast) with FileTypeIndex + PSI tree walk fallback (always works regardless of index state). This makes Go to Definition robust against stale or incomplete stub caches.
- **Crystalline does not support rename** — there is no `textDocument/rename` handler. The hybrid approach (token-based + preview dialog + compiler verification) is the only viable option.
- **Crystal formatter has no options** — `crystal tool format` is canonical; no settings panel needed.
- **StubIndex chosen over FileBasedIndex** — industry standard for IntelliJ plugins, provides instant project-wide navigation with proper PSI element access.
- **Generated files are committed** — standard convention for GrammarKit plugins to ensure reproducible builds without requiring specific tool versions.
- **Parser subset approach** — the grammar is intentionally incomplete and will be extended incrementally. Unsupported constructs degrade gracefully (error recovery, tokens remain highlighted).
- **LiveTemplateContextBean requires contextId** — IntelliJ 2025.1+ requires `contextId` attribute on `liveTemplateContext` registration; without it, platform tests and potentially IDE startup fail.
