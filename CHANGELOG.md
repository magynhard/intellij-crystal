# Changelog

All notable changes to the Crystal Language Plugin for JetBrains IDEs will be documented in this file.

## [0.1.18] — 2026-xx-yy

### Added

- **Embedded Crystal (ECR) support** — first-class `.ecr` and `.html.ecr` template language support, mirroring RubyMine's ERB handling:
  - **JFlex outer-splitter lexer** splits ECR files into `ECR_OUTER` (HTML/text), `ECR_TAG_BEGIN`/`ECR_TAG_END` (tag delimiters), and `ECR_RAW` (Crystal code inside tags)
  - **BNF parser** builds ECR PSI tree (`ecrFile → ecrPart → ecrText | ecrTag`)
  - **Layered editor highlighter** (`LayeredLexerEditorHighlighter`) with 3 layers: ECR tag delimiters (base), Crystal syntax highlighting inside `ECR_RAW` (`CrystalSyntaxHighlighter`), HTML syntax highlighting in `ECR_OUTER` (`HtmlFileHighlighter` from bundled HtmlTools plugin)
  - **Template language infrastructure**: `EmbeddedCrystalLanguage` implements `TemplateLanguage` marker interface; `EcrHtmlFile` uses `TemplateDataElementType` with `EmbeddedCrystalLanguage` as lexer-source to re-parse HTML regions with the HTML parser
  - **3-section Structure View** via `StructureViewComposite`: **ECR** (all `<% %>` tag snippets), **HTML** (full HTML element hierarchy), **Crystal** (all `@instance_variables` with navigation to first occurrence)
  - **HTML always activated** — every `.ecr` file gets implicit HTML as template data language (not just `.html.ecr`), matching RubyMine ERB behaviour
  - **`<%>` file icon** — custom SVG glyph for `.ecr` and `.html.ecr` files
  - **Lexer fix for `%` in tag content** — `([^%]|"%"[^>])+` pattern matches everything up to `%>` as a single `ECR_RAW` token, so Crystal strings like `"%Y-%m-%d"` no longer trigger parser errors
  - **Crystal language injection inside `<% %>` tags** — full Crystal code intelligence is now available inside ECR tags in both `.ecr` and `.html.ecr` files: code completion (class names like `Int32`, methods, locals, dot-completion), syntax highlighting (keywords, strings, numbers — no longer gray/comment-colored), Go to Definition, Parameter Info, hover type info, Quick Documentation, Find Usages, and inspections. Implemented via `MultiHostInjector` (`CrystalEcrInjector`) injecting `CrystalLanguage` into `ecrBody` PSI elements, which implement `PsiLanguageInjectionHost` via the `EcrBodyInjectionHost` mixin. This fixes the highlighting disparity where `.ecr` files showed Crystal code inside `<% %>` as gray while `.html.ecr` files showed proper Crystal colors.

### Changed

- **Split completion responsibilities into focused providers** — `CrystalCompletionContributor` now retains only registration and ordered dispatch policy, while context classification, local candidates, and class/constant candidates live in dedicated components. Completion ordering, ranking, deduplication, Dumb Mode guards, and results remain unchanged.
- **Use scoped StubIndex navigation and centralized lookups** — Go to Class/Symbol no longer perform project-wide `FileTypeIndex` scans, and alias, annotation, and lib definitions are now indexed and navigable. Production StubIndex access goes through the typed `CrystalIndexService`; indexed hot paths use requested or narrower scopes and stop processing early where semantics allow. Type navigation retains distinct class, module, struct, and enum icons.
- **Strengthen indexed navigation regression coverage** — direct tests now protect call extraction, Go to Class/Symbol contributors, and current-file-aware completion type lookup ahead of the index service migration. The coverage also fixes current-file type preference stopping at the first indexed match and constructor extraction for the composite DOT-call PSI shape.
- **Split Parameter Info call analysis into focused helpers** — `CrystalParameterInfoHandler` now coordinates the IntelliJ lifecycle while dedicated locators handle call discovery, bare and DOT-call analysis, and current-parameter indexing. Existing handler entry points and behavior remain unchanged.
- **Replace deprecated `DefaultLiveTemplatesProvider`** — replaced the deprecated `DefaultLiveTemplatesProvider` class-based implementation with the declarative `<defaultLiveTemplates file="..."/>` extension point, aligning with current IntelliJ Platform API conventions.
- **Replace deprecated `supportsPossessiveQuantifiers()`** — updated `CrystalRegExpLanguageHost` to use the new `supportsPossessiveQuantifiers(RegExpElement)` overload, replacing the deprecated no-args version.
- **Replace deprecated `FileChooserDescriptorFactory.createSingleFileDescriptor()`** — migrated to `singleFile()` and `singleFile().withExtensionFilter()` across settings, project wizard, and run configuration.
- **Replace deprecated `addBrowseFolderListener(title, desc, project, descriptor)`** — migrated to `TextBrowseFolderListener(descriptor.withTitle(...).withDescription(...), project)` pattern.
- **Replace deprecated `ReadAction.run()`** — migrated to `ReadAction.runBlocking()` in Find Usages handler.
- **Replace deprecated `GeneratorPeerImpl.getComponent()`** — moved panel construction logic into `getComponent(TextFieldWithBrowseButton, Runnable)`.
- **Remove Object fallback from dot-completion** — dot-completion on instance variables and local variables (e.g. `@apfel.`, `a.`) no longer includes generic `Object` methods (`to_s`, `inspect`, `hash`, `nil?`, etc.) as fallback suggestions. Only methods from the inferred type and its explicit parent classes/modules are shown, matching Go to Definition behavior and reducing lookup noise.
- **Show parameter signatures for class methods in free-text completion** — typing inside a class method now shows parameter signatures (e.g. `essen(speed, anders)`) in the autocomplete popup for sibling methods, matching the behavior of dot-completion.
- **Suggest `initialize` in class method completion** — typing inside a class method now also suggests the constructor method `initialize`, which was previously filtered out.
- **Suggest file-level constants in free-text completion** — typing with an uppercase prefix at the top level (e.g. `B<caret>`) now also suggests file-level constants like `BREZEL_SIZE`, alongside class names and stdlib types.
- **Suggest top-level (global) methods in free-text completion** — typing a lowercase prefix (e.g. `k<caret>`) now suggests top-level `def` methods (e.g. `kung(foo : String)`) defined outside any class/module/struct/enum, including their parameter signatures. Stdlib top-level helpers (`puts`, `pp`, `p`, `print`, …) are included once indexed. Available in every context (top-level, inside class methods, inside blocks). Class methods (`def self.xxx`) are excluded — they only appear via dot-completion on their enclosing class. Local variables, parameters, and enclosing-class methods take priority and dedup against same-named global methods. Implemented via a new dedicated `CrystalTopLevelMethodIndex` StubIndex.

- **`require` keyword and path completion** — typing `req<caret>` at a top-level statement position now offers the `require` compiler pseudo-keyword (no `def require` exists in the stdlib — it is a language-level keyword). Selecting it inserts `require ""` and immediately opens a path-completion popup inside the string. Inside a `require "..."` string, the popup dispatches by the typed prefix: a leading `.` or `/` switches to **relative mode** (lists `.cr` files and subdirectories in the current file's directory, with `.cr` extensions stripped on insert and subdirectory traversal via re-triggered popups — mirroring RubyMine's `require_relative`); any other prefix enters **shard/stdlib mode** (lists shards under `<project>/lib/` and Crystal stdlib entries such as `json`, `ostruct`, `securerandom`, also with subdirectory traversal for `require "json/parser"`-style paths). The stdlib path resolution is now cached project-scoped (invalidated when the user changes the Crystal SDK path in settings), so each completion invocation no longer spawns a `crystal env CRYSTAL_PATH` subprocess.

- **Fix `def self.<keyword>` rendering as method body source** — `def self.require(path)`, `def self.class(x)`, `def self.end(x)`, etc. (any `def self.<keyword>` using the `keyword_as_method` BNF alternative) no longer appear in the completion popup as `def require(path)end(path)` (or pollute the stub index with that body text as a key). `getNameFromMethodName` now stops its fallback name-composition loop at the parameter list (`LPAREN`) and method body (`METHOD_BODY`), and skips the `DEF` and `SELF` header tokens, producing the correct name (`"require"`, `"class"`, etc.). As a side effect, Go to Definition, Find Usages, and Rename now also work for `def self.<keyword>` methods (previously silently broken because `findNameIdentifierInMethodName` only matched `IDENTIFIER`/`CONSTANT` leaves).
- **Suggest class constants after `::`** — typing `ClassName::<caret>` now shows class-level constants (e.g. `WEIGHT`) alongside nested types.

### Fixed

- **Restore responsive Go to Symbol searches** — name enumeration now streams StubIndex keys directly instead of loading PSI elements once per candidate name; actual navigation results remain constrained to the requested search scope.
- **Block parameter rename/reference resolution** — block parameters (e.g. `|ola|` in `each do |ola| ... end`) are now resolved as references, so Rename (from definition or usage site), Go to Definition, and Find Usages work and stay in sync across the block body. Previously they were only highlighted (Annotator) and suggested (Completion) but had no reference link, so renaming one occurrence did not rename the other. Fixed by teaching `CrystalReference.resolveLocal()` to check `CrystalBlock.parameterList`, mirroring the existing method/macro parameter resolution.

- **Instance/class variable `@` completion** — typing `@` (or `@@`) inside a method now suggests all instance (`@name`) and class (`@@name`) variables of the enclosing class, and the auto-popup appears as the sigil is typed. Previously a bare `@`/`@@` produced no suggestions because the lexer emits a standalone `AT` token and the default prefix matcher ignored the sigil; variables defined in a different method (e.g. `initialize`) were also missing because collection was limited to the current method. Fixed by a sigil-aware prefix matcher, file-level class variable collection (walks all file children including raw `INSTANCE_VAR`/`CLASS_VAR` tokens from error-recovery parse states), nested-class isolation, and auto-popup triggering via `TypedHandlerDelegate.checkAutoPopup`.

- **Prevent endless first-open stdlib indexing** — the filtered synthetic library is now the sole stdlib root source. A pre-load project converter removes legacy persisted `Crystal StdLib` module entries before Workspace Model can queue the broad `/usr/lib/crystal` root, while SDK/reindex actions refresh only filtered `.cr` roots through IntelliJ's additional-library API. The post-startup module-library migration and indefinite smart-mode waiter have been removed.

## [0.1.17] — 2026-07-05

### Enhancements

- **Keyword block highlighting** — cursor on `if`, `else`, `elsif`, `end`, `begin`, `rescue`, `ensure`, `case`, `when`, `def`, `class`, `module`, etc. now highlights all related structural keywords of the enclosing block (e.g. `if`/`elsif`/`else`/`end`). Uses IntelliJ's `CodeBlockSupportHandler` extension point with `AbstractCodeBlockSupportHandler` and a declarative TokenSet-based tree structure for reliable multi-marker highlighting.
- **Variable hover type info** — hovering over a variable (definition or usage) now shows the inferred type in a two-line popup: `String (Variable)` / `my_variable`. Works for local variables, instance variables (`@var`), and in method arguments (`puts x`, `foo(x)`). Uses `CrystalTypeInference` for type resolution.
- **Numeric type linking** — integer types (`Int32`, `Int8`, `UInt64`, etc.) and float types (`Float32`, `Float64`) are now linked to their parent type documentation (`Int` or `Float`) in hover popups, since they don't have individual documentation pages.
- **Hash/tuple type inference** — hash literals (`{"a" => 1}` → `Hash(String, Int32)`) and tuple literals (`{1, "hi"}` → `Tuple(Int32, String)`) now show detailed type parameters in hover popups.
- **Array type deduplication** — mixed arrays like `[1, 2, 3, "lol"]` now show `Array(Int32 | String)` instead of `Array(Int32 | Int32 | Int32 | String)`.
- **Ternary type inference** — ternary expressions (`true ? 1 : nil` → `Int32 | Nil`) now correctly infer types for variable hover, including complex conditions (`true == true ? 123 : "lol"`).
- **Method return type inference from body** — methods without an explicit return type annotation now have their return type inferred from `return` statements and implicit last-expression returns. E.g. `def foo; return "hi"; end` infers `String`, `def bar(x : Int32); x + 1; end` infers `Int32`. Used by variable hover to show the correct type when the variable holds a method's return value.

### Bug Fixes

- **Fix false argument count with operators in call arguments** — `write_to_second_line(cmd + ".ps1", %Q{text})` no longer falsely reports "Missing required argument(s): 'line'" when the first argument contains a binary operator with a literal (e.g. `+ ".ps1"`, `* 2`, `/ 3`). The `binary_op_lookahead` rule now covers all expression-starting token types, preventing the parser from misinterpreting `var + ".ext"` inside parenthesized calls as a bare method call that greedily consumes subsequent arguments.
- **Fix false "Value assigned never used" with conditional reassignment** — `x = ""; if cond; x = "v"; end; puts x` no longer falsely reports the first assignment as unused. The inspection now recognizes that assignments inside conditional branches may not execute, making the initial value a fallback. Applies to `if`, `unless`, `while`, `until`, `for`, `case`, `select`, and `begin+rescue` constructs.
- **Fix false positive "Too many arguments" on parameter variables** — using a parameter variable in a binary expression (e.g. `count + 87` inside `def dance(count : Int32)`) no longer falsely reports "Too many arguments" because the inspection now checks if the method name resolves to a local variable or parameter before validating argument count against StubIndex methods
- **Fix `::` namespace not recognized inside string interpolation** — `#{Foo::Bar.method}` and `{{ RvmCli::Tools.config }}` no longer cause parse errors; the lexer now produces `DOUBLE_COLON` tokens inside `INTERPOLATION` and `MACRO_INTERPOLATION` states
- **Fix range with omitted start in bracket access** — `arr[..2]`, `arr[...2]`, `arr[1..]`, `arr[..]` etc. now parse correctly; `range_expression` and `bare_range_expression` now allow the left-hand side to be omitted
- **Fix Parameter Info (Ctrl+P) for DOT-calls after method calls** — `puts Tesa.hika<caret>` and `puts Tesa.hika <caret>` now show `hika`'s parameters instead of `puts`'s; the Quick-Check for DOT-call method names now runs before the generic args-holder lookup, and `findMethodNameInLeaves` iterates backwards to find the correct method name when multiple identifiers are present
- **Fix type checking for bare DOT-call arguments** — `puts Foo.bar 123` where `bar` expects `String` now correctly marks `123` as type mismatch; `bare_postfix_op` now allows `bare_argument_list` in addition to `call_args` for DOT-calls, matching `postfix_op` behavior
- **Fix Go to Definition (Ctrl+B) freezing on top-level bare method calls** — `sahne` after `def sahne(bonbon : String) … end` no longer hangs the IDE for 40+ seconds with a "Resolving reference..." popup; `CrystalReference.resolveLocal()` now stops walking up the PSI tree at the containing file boundary instead of climbing into `PsiDirectory` and lazily parsing every sibling file (including `.sh` build scripts via the Shell plugin). `findAssignmentWithName()` also gains a defensive `PsiFile`/`PsiDirectory` guard so a future regression cannot cascade across the project tree. The diagnostic `CRYSTAL RESOLVE #N` logging introduced for this investigation has been removed.
- **Hover documentation for DOT-call methods** — hovering over (or pressing Ctrl+Q on) `Apfel.tanzen`, `a.essen`, or `Senf.new` now shows the target method's signature and doc comment, just like for top-level calls such as `sahne`. `CrystalDocumentationProvider.getCustomDocumentationElement` now falls back to `CrystalGotoDeclarationHandler.getGotoDeclarationTargets` when no `PsiReference` is available on the context element.
- **Unified DOT-call resolution via `CrystalDotCallReference`** — DOT-call identifiers (`Apfel.tanzen`, `a.essen`) now have a real `PsiReference` backed by the new `dot_call_access` BNF rule. The reference resolves via `CrystalMethodByClassIndex` + `CrystalTypeInference` for instance methods. When the receiver type is unknown, resolution returns `null` (no name-only guessing, no false positives). This also gives DOT-call method names proper identifier highlighting in the editor.
- **Parameter hover popups** — hovering over a parameter name (e.g. `bonbon` in `def butter(bonbon : String)` or in the method body `return bonbon`) now shows a parameter-specific popup with type (hyperlinked) and name, instead of the enclosing method's popup. Untyped parameters show `Any` with a runtime evaluation note.
- **Definition hover popups** — hovering over a definition name (e.g. `butter` in `def butter`, `Foo` in `class Foo`) now shows the documentation popup, matching the behavior at call sites.
- **Namespace access Go to Definition and hover** — hovering over intermediate namespace segments (e.g. `Inner` in `Outer::Inner.method`) now shows the class popup and supports Go to Definition. The new `namespace_access` BNF rule creates a real PSI composite with `CrystalNamespaceReference` that reconstructs the full path and resolves via `CrystalClassIndex`. Supports `::Foo` (leading), `A::B` (nested), and `A::B::C` (multi-level) patterns.
- **Disambiguation for nested classes with same name** — `Foo::Sub.space` now correctly resolves to `Foo::Sub`'s `space` method, not `Bar::Sub`'s. References filter candidates by comparing the full qualified name chain (built via `CrystalPsiUtils.buildQualifiedName`) against the expected path. Completion of `Foo::<caret>` shows only types nested inside `Foo` via the new `CrystalClassByEnclosingIndex`. Completion of `Foo::Sub.<caret>` shows only methods from `Foo::Sub`, filtering out methods from other classes with the same simple name.
- **Auto-completion popup for `::`** — typing `::` after a CONSTANT (e.g. `Foo::<caret>`) now triggers the completion popup automatically, matching the behavior of `.` for method completion. No Ctrl+Space needed.
- **Fix postfix `?` after bracket access on method calls** — `RvmCli::Tools.config["default"]?` now parses correctly as a postfix `?` operator. The `expression` and `bare_expression` rules now try the ternary operator first, then fall back to postfix `?`, preventing `?` from being consumed as a postfix operator when a ternary is intended.
- **Fix rescue clause parsing for typed rescue** — `rescue JSON::ParseException`, `rescue SomeError | OtherError`, and `rescue e : SomeError | OtherError` now parse correctly. The `rescue_clause` BNF rule now uses a `rescue_spec` sub-rule that handles all Crystal rescue clause forms: bare, variable binding, typed, variable + type, and union types.
- **Fix implicit object bracket access (`&.[]`)** — `&.[1]`, `&.[]`, `&.[]?`, `&.[1, 2]`, `&.[0..1]`, and `&.[0] = 99` now parse correctly. The `implicit_object_call` BNF rule now supports `DOT LBRACKET argument_list RBRACKET [QUESTION] [assign_op expression]` for bracket-style implicit object calls. `bare_primary_expression` now includes `implicit_object_call` and `bare_argument` now supports `AMPERSAND bare_expression`, enabling `&.method` and `&.[]` as bare arguments (e.g. `match.try &.[1]`).
- **Fix string interpolation in percent literals** — `%(hello #{name})`, `%Q(hello #{name})`, `%r(pattern #{name})`, and `%x(echo #{name})` now correctly parse `#{expression}` as Crystal code with proper `STRING_INTERPOLATION_BEGIN`/`STRING_INTERPOLATION_END` tokens and full PSI tree structure. The `PERCENT_LITERAL` lexer state now has a `#{` rule that transitions to the `INTERPOLATION` state (guarded by a `percentInterpolation` flag). Non-interpolating forms (`%q`, `%w`, `%i`) remain unaffected. `percent_literal_content` in the parser now includes `STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END`.
- **Fix string interpolation in regex literals** — `/hello #{name}/` and `/pattern #{expr}/i` now correctly parse `#{expression}` as Crystal code. New `REGEX` lexer state handles `#{` → `INTERPOLATION` transitions, escape sequences, and closing `/` with flags. `regex_expression` in the parser now supports `REGEX_BEGIN (content | interpolation)* REGEX_END`.
- **Fix string interpolation in backtick command literals** — `` `echo #{name}` `` and `` `cmd #{arg1} #{arg2}` `` now correctly parse `#{expression}` as Crystal code. New `BACKTICK` lexer state handles `#{` → `INTERPOLATION` transitions, escape sequences, newlines, and closing backtick. `command_expression` in the parser now supports `COMMAND_BEGIN (content | interpolation)* COMMAND_END`. Non-interpolating `%x(...)` literals remain unaffected via existing `percentInterpolation` flag.

### Changed

- **Improved documentation hover format for DOT-call methods and class types** — hovering on `Tesa.hika` now displays `Tesa` (hyperlinked, blue) on the first line and `hika(params) : ReturnType` on the second line, with parameter and return types themselves hyperlinked to their class documentation (e.g. clicking `Foo` opens `Foo`'s class doc in the same popup). Hovering on a class itself (`class Tesa < Object`) links the superclass; a class's own name is not self-linked. Top-level methods show `Object` as the enclosing class (matching RubyMine's Ruby convention). Clicking any link in the popup replaces the popup content with the linked element's documentation via `getDocumentationElementForLink`. Non-resolvable type names render as plain text.
- **Remove FileTypeIndex fallback from Go to Definition** — removed the project-wide `.cr` file scan that caused 90+ second delays on right-click/hover; StubIndex is now the only lookup mechanism for definition resolution

## [0.1.16] — 2026-06-25

### Added

- **Colon spacing inspection** — reports missing space after `:` in type annotations (e.g. `x:Int32` → warning)
- **`@param` shorthand support** — `@param` in method bodies is recognized for completion, type inference, and inspections
- **Record macro support** — `record Config, host : String, port : Int32` works for completion, parameter info (`Ctrl+P`), and argument inspections
- **Type checking for record macro parameters** — validates argument types against record parameters in inspections
- **Parameter completion priority boost** — parameters appear higher in the completion popup with bold styling
- **Keywords as method/macro names** — `macro require(...)`, `def if(...)`, `def self.require(...)` etc. now parse correctly; all Crystal keywords are valid as method names
- **Annotation definitions inside module/class bodies** — `annotation GeneratedWrapper ... end` inside `module`/`class` now parses correctly
- **Global namespace prefix in expressions** — `::Bytes.new(...)`, `::Foo::Bar.new(x)` etc. now parse correctly
- **Overloaded methods in completion** — multiple overloads of the same method (e.g. `ENV.fetch` with 3 different signatures) now appear as separate entries in the code completion popup, each showing its parameter signature

### Bug Fixes

- **Fix rename prefix handling for instance/class variables** — renaming `@var` to `@new_name` or `@@var` to `@@new_name` (with explicit prefix) now correctly preserves the variable type instead of creating wrong prefixes (`@@` for instance vars or corrupted names for class vars)
- **Fix macro body depth tracking for postfix control flow** — `if`/`unless`/`while`/`until` used as postfix modifiers (e.g. `return x if condition`) no longer cause the macro body `end` detection to be off-by-one, which broke parsing of code after macros containing postfix control flow
- **Fix type check and parameter info for DOT-calls on constants** — `ENV.fetch("PER_PAGE", "25").to_i` no longer shows false positive type mismatch; both type check inspection and `Ctrl+P` parameter info now filter methods by the receiver's class/module, preventing stdlib calls from showing params of unrelated user-defined overloads
- **Fix false positive "Unknown named argument" on class constructor DOT-calls** — `::Bytes.new(ptr, length, read_only: true)` no longer falsely reports `read_only` as unknown; both argument count and type check inspections now filter by receiver class/module for DOT-calls
- **Fix `case ... end.tap do` parse error** — `case`/`when`/`end` followed by a method call with block (e.g. `.tap do`) now parses correctly; `case_statement` is now processed through `expression_statement` to support postfix method calls with blocks
- **Fix `?`/`!` suffix after macro interpolation** — `{{ expr }}?` and `{{ expr }}!` (Crystal method name suffixes) now parse correctly; the lexer consumes `?`/`!` immediately following `}}` as part of the interpolation end token
- **Fix multi-level pointer types in lib bindings** — `BaseInfo***`, `UInt8**` etc. (arbitrary pointer depth) in `fun` parameter types now parse correctly; type suffix now allows multiple `*`/`**` tokens
- **Fix postfix modifier after compound assignment** — `@data[key] += 1 if condition` now parses correctly; `expression_statement` now allows `postfix_modifier` after `expression_assign_suffix`
- **Fix greedy IDENTIFIER consumption in argument and bare_argument rules** — `Vector2D.new(x * scalar, y * scalar)` now correctly parses as two separate arguments; binary expressions like `x * scalar` inside calls are no longer misparsed as bare method calls with binary args
- **Fix splat prefix detection in argument extraction** — only the first child node is checked for splat prefix, preventing false positives on wrapped arguments
- **Fix record macro parameter priority** — record macro parameters are now checked before class `initialize` in inspections, preventing false "wrong argument type" errors
- **Fix colon spacing inspection scope** — only scans within method definitions, not the entire file body
- **Fix binary `+`/`-` parsed as bare method arguments** — `+`/`-` followed by an identifier no longer parsed as bare method call arguments
- **Fix Go to Definition on `.new`** — `Senf.new` now jumps to the correct target following Crystal's constructor resolution order (`def self.new` → `record` → `def initialize`), instead of showing every `new` method project-wide

### Changed

- **Method lookup elements use PSI object identity** — `LookupElementBuilder.create(method)` replaces `LookupElementBuilder.create(name)`, enabling IntelliJ to distinguish overloaded methods with the same name
- **Force Re-index button fixed** — now properly removes the library before re-adding it, ensuring stale stub index data is cleared before fresh indexing
- **Force Re-index shows progress** — background progress bar in status bar with "Removing old index..." / "Indexing..." states, plus balloon notification on completion

### Bug Fixes

- **Fix operator method name resolution** — `def self.[]?` etc. now parse correctly; operator method names (`[]`, `[]?`, `[]=`) are composed from tokens when no IDENTIFIER is found
- **Fix ENV.fetch completion** — all stdlib methods inside `module ENV` are now indexed; the BNF fix for `def self.[]?` prevents cascading parse failures that previously skipped all subsequent methods in `env.cr`

## [0.1.15] — 2026-06-14

### Changed

- **Parameter info (Ctrl+P) for `ClassName.new`** — now shows `initialize` method parameters when calling `new` on a class
- **Type check for `ClassName.new`** — argument type validation now works for `new` calls, resolving to `initialize` parameters

### Bug Fixes

- **Fix `new` resolution using wrong class** — `findTypeByName` now prefers the class from the current file when multiple classes with the same name exist in the project

## [0.1.13] — 2026-06-14

### Added

- **Code folding for multi-line arrays and hashes** — `[...]` and `{...}` blocks spanning multiple lines can now be collapsed, matching RubyMine behavior
- **Improved code folding for all block constructs** — keyword and signature remain visible when collapsed, placeholder shows ` ... end` (or `[ ... ]` / `{ ... }` for arrays/hashes), matching RubyMine behavior
- Supported: `if`/`unless`/`while`/`until` (with condition), `def` (with name + params), `class`/`module`/`struct`/`enum` (with name), `begin`, `do`, `case`/`for`, `macro`, `lib`, `annotation`, `verbatim do`

### Changed

- **Suppress completion after numeric literals** — typing a number (e.g. `a = 1`) no longer triggers variable/method suggestions, matching RubyMine behavior

### Bug Fixes

- **Fix nil-safe index (`?`) breaking parser** — `item["states"]?` inside `each do`/`if` blocks no longer causes false parse errors; the bracket access was incorrectly parsed as a method call with array literal

## [0.1.12] — 2026-06-12

### Changed

- **Minimum IDE version** — raised `sinceBuild` from 251 to 261 (IntelliJ 2026.1+) to match the target platform

## [0.1.11] — 2026-06-12

### Bug Fixes

- **Improved debugger compatibility on Windows** — added Windows-specific lldb-dap binary discovery, `.exe` suffix for compiled binaries, and fixed formatter path handling for Windows

## [0.1.9] — 2026-06-12

### Bug Fixes

- **Windows support for debugger** — patched `crystal_formatters.py` to run on Windows, enabling the debugger on Windows as well

## [0.1.6] — 2026-06-08

First official release of the Crystal Language Plugin. This is an early beta (Proof of Concept) providing comprehensive Crystal language support for JetBrains IDEs without requiring an external language server.

### Parser

- **GrammarKit BNF parser** — full Crystal syntax coverage including classes, modules, structs, enums, methods, macros, control flow, and expressions
- **Generics** — variadic generics (`*T`), default types (`T = Int32`), bounded generics, and `forall` constraints
- **Macros** — full macro body parsing with `{% %}` / `{{ }}` / `{% for %}`, hooks (`inherited`, `included`, `extended`, `finished`, `method_added`, `method_missing`), fresh variables (`%fresh_var`), and `verbatim` blocks
- **Union types** — type annotations (`Int32 | String`) and union type resolution in expressions
- **Proc/Lambda** — types (`-> Int32`, `Proc(Int32, String)`) and literals (`->{ }`, `->(x) { }`)
- **Pattern matching** — `case...in` with tuple destructuring (`in {x, y}`), pin operator (`^var`), and guard clauses (`in pattern if cond`)
- **Lib bindings** — `fun`, `union`, `enum`, external variables (`$errno`), varargs, and top-level `fun`
- **Annotations** — usage parsing (`@[Deprecated]`, `@[JSON::Serializable]`), multiple annotations on parameters
- **Operators** — wrapping operators (`&+`, `&-`, `&*`, `&**`), ternary (`? :`), suffix if/unless/while, `responds_to?`, `is_a?`, `nil?`
- **String handling** — interpolation as nested expressions, heredocs, percent literals (`%w[]`, `%i[]`, `%x()`), regex
- **Parameters** — type restrictions, default values, splat (`*args`), double splat (`**kwargs`), external parameter names (`def move(to destination : String)`), block-pass (`&block`)
- **Other** — `asm` blocks, named tuples, `select` statement (concurrency), `with...yield` blocks, `pointerof`, `offsetof`, `uninitialized`, `loop do`, `previous_def`, `out` parameters
- **Error-tolerant** — pin/recovery rules ensure the parser works with incomplete code while typing

### Syntax Highlighting

- **60+ keywords** — all Crystal keywords including reserved words, pseudo-variables (`self`, `typeof`, `_`), and special literals
- **Operators** — full operator support including compound assignment, range, bitwise, and comparison operators
- **Strings** — syntax highlighting with interpolation support
- **Heredocs** — highlighted as multi-line string constructs
- **Percent literals** — `%w[]`, `%i[]`, `%r()`, `%x()`, `%q()`, `%Q()` with correct delimiter matching
- **Symbols** — `:symbol` and `:"string symbol"` highlighting
- **Regex** — `/pattern/` with character classes and quantifiers
- **Annotations** — `@[Annotation]` syntax highlighting
- **Macros** — `{% %}` and `{{ }}` highlighted differently from regular code

### Semantic Highlighting

- **PSI annotator** — visually distinguishes variables, methods, types, parameters, and macro fresh vars
- **Instance variables** — `@name` styled differently from local variables
- **Class variables** — `@@name` with distinct styling
- **Constants** — recognized and highlighted as types

### Code Intelligence

#### Navigation

- **Go to Definition** (Ctrl+B / Ctrl+Click) — jump to class, module, struct, enum, method, and variable declarations across the entire project
- **Go to Symbol** (Ctrl+Alt+Shift+N) — find any symbol in the project via StubIndex
- **Go to Class** (Ctrl+N) — find classes, modules, structs, enums
- **Find Usages** (Alt+F7) — locate all references to methods, classes, instance variables (`@name`), and class variables (`@@name`)
- **Structure View** — PSI-based tree showing classes, modules, methods, macros, constants, and nested definitions
- **Parameter Info** (Ctrl+P) — method signatures at call sites; supports parenthesized calls, bare calls, dot-calls, class method calls, and overloads; works correctly with cursor after comma

#### Code Completion

- **Dot-completion** — context-aware suggestions on classes (static methods) and variables (instance methods via type inference)
- **Free-text completion** — classes, methods, locals, and stdlib types
- **Type completion** — after `:` in annotations, inside generics (`Array(<caret>)`), and in union types (`String | <caret>`)
- **Stdlib types** — built-in Crystal standard library types included in completions

#### Type Inference

- **Basic type deduction** — variable type inferred from assignment (`x = Klasse.new`) and parameter annotations (`x : Type`)
- **Instance variable type** — inferred from `@name` declarations in the class

### Code Quality

- **Type checking** — validates argument types against method parameter annotations; supports numeric autocasting, union types, nilable types, overloads, named args, splat skip
- **Argument count validation** — reports missing required arguments and excess arguments; supports named args, splat/double-splat, block params, default values, overloads, DOT-calls, bare calls
- **Unused variable detection** — warns on local variables that are assigned but never read; supports reassignment analysis, compound assignments, underscore-prefix convention, variables in method call expressions, and string interpolation
- **Lib fun type annotation** — reports missing type annotations in lib function definitions (ERROR level)

### Editor Features

- **Code Formatting** (Ctrl+Alt+L) — delegates to `crystal tool format` via stdin/stdout; no configuration needed
- **Rename Refactoring** (Shift+F6) — in-place rename with preview dialog and automatic compiler verification (`crystal build --no-codegen`)
- **Smart Enter** — automatically inserts `end` after def/class/module/if/do/unless/rescue and handles correct indentation
- **Code Folding** — collapse methods, classes, blocks, comments, and heredocs
- **Brace Matching** — highlights matching pairs for parentheses, brackets, braces, and do/end
- **TODO Indexing** — Crystal TODO/FIXME comments appear in the TODO tool window
- **Live Templates** — 21 code snippets for common Crystal patterns (def, class, module, struct, spec, describe, it, context, etc.)

### Run & Debug

#### Run Configurations

- **Crystal Run** — run Crystal programs with configurable arguments, environment variables, and working directory
- **Crystal Build** — compile with custom flags (release, static, target, etc.)
- **Crystal Spec** — run specs with file/line targeting and tag filters

#### Test Runner

- **SMTRunner integration** — connected to IntelliJ's test UI for familiar test execution experience
- **Gutter run icons** — run individual specs directly from `describe` and `it` blocks
- **Single-test execution** — via `file:line` targeting for precise test isolation
- **Real-time output** — live parsing of Crystal verbose output with pass/fail/error/pending states
- **Re-run failed tests** — one-click re-execution of failed specs
- **Folder-level running** — run all specs in a directory recursively
- **Navigate to source** — double-click on test node to jump to source location
- **Failure propagation** — parent suites marked as failed when children fail
- **Per-test timing** — execution duration from JUnit XML output
- **Duplicate test names** — handles multiple tests with the same name in different describe blocks

#### Debugger

- **LLDB DAP integration** — full debugging via Debug Adapter Protocol
- **Breakpoints** — set breakpoints with hit counts and conditions
- **Stepping** — step over / into / out of code
- **Variable inspection** — locals, instance variables, globals
- **Expression evaluation** — evaluate expressions during debugging
- **Crystal formatters** — bundled LLDB type formatters for readable variable display
- **Debug both** — supports debugging both `crystal run` and `crystal spec` targets

### Infrastructure

- **StubIndex** — project-wide index for classes and methods (instant navigation even in large projects)
- **Generated files committed** — standard convention for GrammarKit plugins to ensure reproducible builds
- **Error-tolerant parsing** — parser works with incomplete code while typing

### Requirements

- **IntelliJ Platform** 2026.1 or later
- **Crystal** installed and available in PATH (for formatting, compiler verification, and running programs)
- **lldb-dap** (optional) — required for debugging; install via your system package manager

### Compatibility

- **JetBrains IDEs** — IntelliJ IDEA, RubyMine, WebStorm, CLion, and other JetBrains IDEs
- **Incompatible with** — legacy Crystal plugin (`net.kenro.ji.jin.intellij.crystal-2`)

---

*This changelog follows [Keep a Changelog](https://keepachangelog.com/) format.*
