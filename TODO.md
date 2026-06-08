# TODO — IntelliJ Crystal Plugin

## Open (prioritized for upcoming releases)

### Mixed ideas

- [ ] **Ameba/Crystalline integration** — Crystal linter inspections from `ameba` or direct integration/support of `crystalline` which uses ameba implicitly.
- [ ] **Scope-aware rename** — improve token-based rename with scope analysis
- [ ] **Inlay hints** — show inferred types on variables
- [ ] **Implement members** — generate stubs for abstract methods
- [ ] **Crystal Shards support** — `shard.yml` parsing, dependency completion
- [ ] **New file templates** — create class, module, spec files
- [ ] **Better operator precedence** — Pratt parsing or precedence climbing

## Completed

### Parser

- [x] Full generics, macros, union types, proc/lambdas, pattern matching, lib bindings
- [x] Annotations, heredocs, percent literals, string interpolation, operators
- [x] Type restrictions, default params, splat/double-splat, visibility modifiers
- [x] Wrapping operators, loop do, previous_def, out parameters
- [x] Trailing commas, &.method shorthand, external parameter names
- [x] Named arguments, empty collections with `of`, command literals

### IDE Features

- [x] Reference resolution via StubIndex + fallback
- [x] Instance/class variable navigation
- [x] Code completion (dot, free-text, type)
- [x] Type inference (basic)
- [x] Semantic highlighting
- [x] Type checking, argument count validation
- [x] Unused variable detection
- [x] Quick documentation (Ctrl+Q)
- [x] Test runner (SMTRunner with real-time parsing, gutter icons, folder-level)
- [x] Debugger (LLDB DAP with Crystal formatters)
- [x] Project SDK detection
- [x] Parser tests (gold-file based)
- [x] Platform tests (EnterHandler)
- [x] Plugin Marketplace publication
- [x] Plugin icon
