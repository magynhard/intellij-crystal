# TODO — IntelliJ Crystal Plugin

## Parser Extensions (BNF Grammar)

The current grammar covers the most common constructs. The following extensions are planned:

- [x] Full generics (`Array(T)`, `forall T`, constraints) — basic support done
- [x] Macro body parsing (`{% %}`, `{{ }}`, `{% for %}`)
- [x] Union types as type annotations (`Int32 | String`)
- [x] Proc/Lambda types (`-> Int32`, `Proc(Int32, String)`)
- [x] Proc literals (`->{ }`, `->(x) { }`, `->method_name(Type)`)
- [x] Pattern matching (`case...in`, Crystal 1.x+) — basic `in_clause` done
- [x] Multi-assignment (`a, b = 1, 2`)
- [x] Splat parameters (`*args`, `**kwargs`)
- [x] Annotation usage parsing (`@[Deprecated]`, `@[JSON::Serializable]`, `@[Link("sqlite3")]`)
- [x] `asm` blocks
- [x] Named tuples (`{name: "foo", age: 42}`)
- [x] `select` statement (concurrency)
- [x] Heredocs as expressions in parser (lexer already supports them)
- [ ] Better operator precedence (Pratt parsing or precedence climbing)
- [x] Type restrictions on parameters (`def foo(x : Int32)`)
- [x] Default parameter values (full expressions)
- [x] Visibility modifiers (`private`/`protected` standalone and as prefix)
- [x] `with...yield` blocks
- [x] `pointerof`, `offsetof` as expressions
- [x] String interpolation as nested expressions in parser
- [x] Suffix if/unless/while (`expr if condition`)
- [x] Ternary operator (partially exists: `? :` in expression rule)
- [x] Typed variable declarations (`x : String | Nil`) as statements
- [x] Generic type arguments in type references (`Array(String)`, `Hash(String, Int32)`)
- [x] Block-pass (`&block`) not counted as argument

### Missing Syntax (not yet implemented)

#### Completely missing

- [x] Wrapping operators (`&+`, `&-`, `&*`, `&**`) — overflow-safe arithmetic
- [x] `loop do ... end` — infinite loop construct
- [x] `previous_def` — call the previously defined method in redefinition
- [x] `out` parameter in arguments (C-Bindings: `LibC.foo(out result)`)
- [x] Lib `union` — union definitions inside `lib` blocks
- [x] Lib `enum` — enum definitions inside `lib` blocks
- [x] Lib `$external_var` — global variables in `lib` (`$errno : Int32`)
- [x] Lib varargs — `fun printf(format : UInt8*, ...) : Int32`
- [x] Top-level `fun` — exported C functions outside of `lib`
- [x] Macro hooks (`macro inherited`, `included`, `extended`, `finished`, `method_added`, `method_missing`)
- [x] Macro `%fresh_var` — fresh variables in macros (highlighted as local variable)
- [x] `verbatim do ... end` — macro construct (highlighted as keyword)
- [x] Variadic generics (`class Foo(*T)`, `Tuple(*T)`)
- [x] Generic default types (`T = Int32`) and bounded generics

#### Partially implemented / gaps

- [x] `self` / `typeof` / `_` as type in type_reference position
- [x] Pattern matching: tuple destructuring (`in {x, y}`), pin operator (`^var`), guard clauses (`in pattern if cond`)
- [x] Percent literal contents (`%w[foo bar]`, `%i[a b]`) — full parsing with inner tokens
- [x] Indexer assignment as expression (`obj[key] = value`)
- [x] `responds_to?(:method)` as implicit object call (`.responds_to?`, `.is_a?`, `.nil?`)
- [x] Rescue with union types (`rescue ex : Foo | Bar`), inline rescue (`expr rescue default`), rescue in method body
- [x] Annotations on parameters (`def foo(@[MyAnn] param : Int32)`) — multiple annotations supported

#### Potential gaps (untested)

- [x] `with ... yield` blocks — scoped yield syntax (was already implemented)
- [x] `select`/`spawn`/Fibers — concurrency constructs (was already implemented)
- [x] Macro `for` with Tuple/NamedTuple iteration (`{% for key, value in ... %}`) — added `DOUBLE_COLON`/`PERCENT` to macro_control_token
- [x] `uninitialized` keyword as expression (`x = uninitialized Int32`) — was already implemented
- [x] `pointerof`/`sizeof`/`instance_sizeof` — NLS in parens + `T.class` metaclass type
- [x] Tuple destructuring in assignment (`a, b = tuple`) — NLS in multi-assignment targets
- [x] Operator precedence edge cases — backslash line continuation, method chaining across newlines, range NLS, condition assignments

#### Syntax3 round (tested with real-world examples)

- [x] Trailing commas in parameter/argument/type lists
- [x] `&.method` shorthand — operator tokens in `keyword_as_method` (`.+`, `.>`, etc.)
- [x] Proc/Lambda multiline params — NLS in proc_literal LPAREN/RPAREN
- [x] `%x(...)` command literals — lexer support + COMMAND_LITERAL in percent_literal_content
- [x] `$?` global variable — added `?` to GLOBAL_VAR lexer pattern
- [x] External parameter names (`def move(to destination : String)`) — IDENTIFIER IDENTIFIER alternative in parameter rule
- [x] Named arguments, double splat (`**kwargs`) — parser test verified
- [x] Empty collections with `of` (`[] of Type`, `{} of K => V`) — parser test verified

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
- [x] Unused variable detection — reports local variables that are assigned but never used (LIKE_UNUSED_SYMBOL = grayed out + strikethrough). Supports reassignment analysis, compound assignments (treated as read), underscore-prefix convention. Handles variables used in method call expressions (e.g. `x + 2` where `x` is the method name) and string interpolation (`"#{name}"`). Ignores method parameters, instance/class vars.
- [x] Missing type annotation in lib fun — reports parameters without type annotations in lib fun definitions (ERROR).
- [x] Quick documentation — display doc comment above `def` (Ctrl+Q / F1 / hover; shows syntax-highlighted signature + Markdown-rendered doc comments with code blocks)
- [ ] Implement members — generate stubs for abstract methods

> **Note:** Parameter Info and Structure View are already implemented using the PSI parser and StubIndex.
> Parameter Info supports parenthesized calls, bare (parenthesis-free) calls, dot-calls, and class method calls.
> It works correctly when the cursor is after a comma (with or without trailing whitespace/argument),
> and also when no argument has been typed yet (e.g. `foo ` with cursor after the space).

## IDE Features (independent of parser)

- [ ] Ameba integration (Crystal linter) — show inspections from `ameba`
- [ ] Crystal Shards support — `shard.yml` parsing, dependency completion
- [x] Test runner — connect `crystal spec` to IntelliJ's test UI (SMTRunner with real-time output parsing, gutter run icons, single-test execution via file:line, re-run failed tests, navigate to source on double-click via CrystalSpecFileIndexer + CrystalTestLocator with crystal_spec:// protocol, folder-level spec running, failure propagation to parent suites via two-pass architecture, cache invalidation on file changes, comment-safe indexing, `it(...)` support, duplicate test name handling)
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
