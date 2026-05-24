# TODO — IntelliJ Crystal Plugin

## Parser Extensions (BNF Grammar)

The current grammar covers the most common constructs. The following extensions are planned:

- [ ] Full generics (`Array(T)`, `forall T`, constraints)
- [ ] Macro body parsing (`{% %}`, `{{ }}`, `{% for %}`)
- [ ] Union types as type annotations (`Int32 | String`)
- [ ] Proc/Lambda types (`-> Int32`, `Proc(Int32, String)`)
- [ ] Proc literals (`->{ }`, `->(x) { }`)
- [ ] Pattern matching (`case...in`, Crystal 1.x+)
- [ ] Multi-assignment (`a, b = 1, 2`)
- [ ] Splat parameters (`*args`, `**kwargs`)
- [ ] Annotation bodies (`@[JSON::Field(key: "x")]`)
- [ ] `asm` blocks
- [ ] Named tuples (`{name: "foo", age: 42}`)
- [ ] `select` statement (concurrency)
- [ ] Heredocs as expressions in parser (lexer already supports them)
- [ ] Better operator precedence (Pratt parsing or precedence climbing)
- [ ] Type restrictions on parameters (`def foo(x : Int32)`)
- [ ] Default parameter values (full expressions)
- [ ] Visibility modifiers as modifier nodes on PSI elements
- [ ] `with...yield` blocks
- [ ] `pointerof`, `offsetof` as expressions
- [ ] String interpolation as nested expressions in parser
- [ ] Suffix if/unless/while (`expr if condition`)
- [ ] Ternary operator (partially exists: `? :` in expression rule)

## IDE Features (require parser improvements)

- [x] Reference resolution — resolve variables/methods to their declarations (via StubIndex + FileTypeIndex fallback + local scope; works cross-file)
- [ ] Code completion — context-aware suggestions
- [ ] Type inference (basic) — deduce variable type from assignment
- [ ] Scope-aware rename — improve current token-based rename with scope analysis
- [x] Semantic highlighting — visually distinguish variables, methods, types, and parameters
- [ ] Inlay hints — show inferred types on variables
- [ ] Quick documentation — display doc comment above `def`
- [ ] Implement members — generate stubs for abstract methods

> **Note:** Parameter Info and Structure View are already implemented using the PSI parser and StubIndex.

## IDE Features (independent of parser)

- [ ] Ameba integration (Crystal linter) — show inspections from `ameba`
- [ ] Crystal Shards support — `shard.yml` parsing, dependency completion
- [ ] Test runner — connect `crystal spec` to IntelliJ's test UI
- [ ] Debugger integration — GDB/LLDB for Crystal binaries
- [x] Project SDK — detect and configure Crystal version
- [ ] New file templates — create class, module, spec files
- [ ] Spell checking in strings and comments
- [ ] Markdown rendering for doc comments

## Infrastructure

- [ ] More lexer tests (edge cases: nested interpolation, regex vs. division)
- [x] Parser tests (gold-file based)
- [x] Platform tests (EnterHandler — 13 tests covering end-insertion, balance, indentation)
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
