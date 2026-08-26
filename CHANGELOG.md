# Changelog

All notable changes to the Crystal Language Plugin for JetBrains IDEs will be documented in this file.

## [0.2.8] — 2026-xx-xx

### Bug Fixes

- **Accept initializer-backed constructor overloads beside explicit `self.new` (`Deque(Int32).new`)** — parameterless `Deque(Int32).new` was incorrectly marked "Missing required argument(s): 'array'" even though it compiles. Crystal adds implicit `new` forwarders for every `initialize` overload and keeps callable-distinct forwarders alongside explicit `def self.new` overloads, but constructor resolution previously selected the explicit `self.new` definition set and discarded every initializer. Resolution, navigation, hover, and argument inspection now share the combined overload pool; explicit `self.new` targets remain first for stable presentation and shadow only initializer forwarders with an overlapping default-expanded dispatch signature, while implicit zero-argument construction is used only when the resulting pool is empty. Covered by synthetic generic-constructor tests, merged polyvariant navigation tests, explicit-shadowing coverage, and a real-stdlib Deque integration test.
- **Resolve constructors in stdlib types that define setters (`URI.new("http", "localhost", 9999)`)** — the arguments were flagged with "Too many arguments: expected at most 0, got 3" and hover on `new` showed only "Any (Variable)". Root cause: the grammar had no setter form for method definitions (`def host=`, `def query_params=`), so real stdlib files like `uri.cr` failed to parse and stub indexing lost members of the surrounding type — including its `initialize`, which made constructor resolution fall back to the zero-argument implicit-constructor rule. Setters now parse (instance, `self.`, and abstract forms), are indexed under their Crystal name `host=`, nested bare calls nest like Crystal (`exec new_request m, p` → `exec(new_request(m, p))`), and macro-interpolated callees accept bare/named arguments and blocks (`{{method.id}} path, form: body`), which unbreaks the generated methods in `http/client.cr`. The file stub version was bumped so persisted indexes rebuild. Covered by new `SetterMethodDefinition` and `MacroInterpolatedCallee` parser tests, a stdlib source parse canary (`CrystalStdlibSourceParseTest`), and constructor argument-count regression tests.
- **Resolve constructors on stdlib types that generate methods through macros (`HTTP::Client.new`)** — hover on `new` still showed "Any (Variable)", Go to definition reported "Can not find declaration to go to", and argument-count checks were silently skipped, although http/client.cr explicitly defines `def self.new(uri, tls)` overloads. Cause: any type containing macro-interpolated method names (`def {{method.id}}` inside `{% for %}` loops) was treated as fully uncertain, suppressing every named lookup against it — including textually written definitions. Macro uncertainty now suppresses only names that are not explicitly defined; a written `def self.new`/`initialize` resolves normally. Covered by new goto-declaration and method-hierarchy tests plus an integration test against the real local Crystal stdlib (skipped when Crystal is not installed).
- **Parse char literals inside string interpolation and macro control blocks** — `"cat-#{rule_id.split('-').last.rjust(3, '0')}"` flagged both `'-'` and `'0'` with "Unterminated char literal" and `<expression> expected, got '''` because the `<INTERPOLATION>` lexer state had no `CHAR_LITERAL` rule. Char literals (including escape forms) now lex correctly in every interpolating literal, the invalid multi-character `'ab'` guard is mirrored from top level, and `macro_control_token` was extended with `CHAR_LITERAL`/`GLOBAL_VAR` so `{% if x == 'a' %}` no longer aborts parsing of the surrounding macro definition. Covered by the new `CharLiteralInterpolation` parser test and four lexer unit tests.
- **Parse assignments in postfix modifier conditions** — `return [] of Result unless target = PAIRS[node.name]?` produced `<…> expected, got '='` because `postfix_modifier` only accepted a plain expression while block-level conditions already allowed assignments. Postfix `if`/`unless`/`while`/`until`/`rescue` now share the block-level `condition_assignment` alternative (flat private rule, no PSI shape change for existing code), and the speculative `in`-clause guard accepts assignments too. Covered by the new `PostfixModifierAssignment` parser test.
- **Parse special global variables inside string interpolation** — `"#{$1}"` (e.g. in regex-replacement blocks like `str.sub(/\.map(...)\s*\.sum/) { ".sum#{$1}" }`) produced `<expression> expected, got '$'` because the `<INTERPOLATION>` lexer state had no `{GLOBAL_VAR}` rule, so `$` fell through to the `BAD_CHARACTER` fallback. `$1`, `$~`, `$?`, and named globals now lex as `GLOBAL_VAR` inside interpolation; the same rule was added to the `<MACRO_INTERPOLATION>` and `<MACRO_CONTROL>` states to keep them mirrored. Covered by the new `GlobalVarInterpolation` parser test across string, regex, command, heredoc, percent-literal, nested-interpolation, and top-level contexts.

## [0.2.7] — 2026-08-25

### Bug Fixes

- **Restore ECR Structure View and spec test locator through main-descriptor registrations** — 0.2.5 moved these extensions into `plugin-smRunner.xml` and `plugin-structureView.xml`, where they silently disappeared. A first 0.2.6 attempt wired the fragments with `<depends optional="true" config-file="...">`, but a real `runIde` session proved that product module names are not resolvable as plugin IDs at runtime (`plugin intellij.platform.structureView is not resolved`; both configs excluded), even though the Marketplace verifier understands them. `EcrStructureViewFactory` and `CrystalTestLocator` are therefore registered unconditionally in the main `plugin.xml`, matching the already-working `CrystalStructureViewFactory`; both fragment files are removed.
- **Fix 14 Marketplace verifier binary-incompatibility problems against RubyMine RM-262** — without a declared module dependency, the verifier could not resolve any SM Test Runner class referenced by the spec test runner (`OutputToGeneralTestEventsConverter`, `GeneralTestEventsProcessor`, `SMTestLocator`, `SMTRunnerConsoleProperties`, `SMCustomMessagesParsing`, `SMTestRunnerConnectionUtil`, and the `sm.runner.events` package), reporting `NoSuchClassError` risks. DAP, SM Test Runner, and Structure View are now explicit dependencies through the modern 2026.2 `<dependencies><module name="..."/></dependencies>` mechanism, which both runtime and verifier resolve without the optional-dependency configuration defect.
- **Consolidate the DAP debug adapter registration** — the `debugAdapterSupportProvider` extension lived in an orphaned `intellij.platform.dap.xml` fragment since its introduction; the debugger only worked because `CrystalDebugProgramRunner` registers the provider programmatically at runtime. The provider is now declared in the main plugin descriptor (the DAP module is a hard dependency there), the orphaned fragment file is deleted, and the runtime registration remains as a guarded fallback that becomes a no-op once the declarative registration loads.

### Added

- **Descriptor and runtime registration regression coverage** — `PluginDescriptorConsistencyTest` parses `src/main/resources/META-INF/plugin.xml` and fails the build when the ECR factory or spec test locator disappears, a required product module is missing or incorrectly declared through legacy `<depends>`, a `config-file` reference dangles, or a fragment is orphaned. `EcrStructureViewTest` now obtains its builder through `LanguageStructureViewBuilder` (the real extension-point path) instead of constructing `EcrStructureViewFactory` directly, so the original production regression can no longer pass tests.

## [0.2.5] — 2026-08-25

### Changed

- **Compile target raised to IntelliJ 2026.2 (build 262)** — the plugin now compiles against and requires IntelliJ 2026.2 or later (`sinceBuild=262`), matching the current platform release. The Gradle build uses the final 2026.2 GA build (262.10315.19) and the Kotlin/Java toolchain moved to JDK 25, as required by the 2026.2 platform jars.
- **README reorganized around installation and core user features** — streamlined the public documentation while retaining platform-specific dependency setup, documented compiler configuration and project creation, clarified ECR and type-inference limits, removed incorrect platform guidance, and moved implementation details out of the primary user flow. Contributor setup now reflects the JDK 25 build toolchain.

### Bug Fixes

- **Zero scheduled-for-removal and zero deprecated API usages** — the Marketplace verifier reported one scheduled-for-removal and six deprecated API usages; all are fixed:
  - `RunLineMarkerContributor.Info(Icon, util.Function, vararg AnAction)` replaced with the non-deprecated `(Icon, Array<AnAction>, java.util.function.Function)` constructor.
  - `XDebuggerManager.startSession(environment, starter)` plus `XDebugSession.getRunContentDescriptor()` replaced with the session builder API (`newSessionBuilder(starter).environment(env).startSession()` returning `XSessionStartedResult`, whose descriptor is used directly) — exactly what the platform's own implementation delegates to.
  - `MarkdownParser(flavour)` replaced with `MarkdownParser(flavour, false, CancellationToken.NonCancellable)` and `buildMarkdownTreeFromString(String)` switched to the CharSequence overload (requires an `@OptIn` for the markdown library's new `@ExperimentalApi`).
  - `ProjectTopics.PROJECT_ROOTS` replaced with `ModuleRootListener.TOPIC`.
- **Optional module dependencies moved into extension fragments** — the `testLocator` extension and the ECR Structure View factory moved into `plugin-smRunner.xml` and `plugin-structureView.xml`. Note: 0.2.5 shipped these fragments without links from `plugin.xml`; a subsequent attempt to wire them exposed that product module names cannot activate `config-file` fragments at runtime. Both registrations were restored directly in the main descriptor in 0.2.6.

### Added

- **Structure view stub element types comply with the 2026.2 holder contract** — `CrystalStubElementTypeHolder` became a Java interface with static fields so the platform can enumerate stub element types without class initialization (`externalIdPrefix="crystal."`), fixing `ExceptionInInitializerError` crashes during inspections on 2026.2.

## [0.2.4] — 2026-08-25

### Bug Fixes

- **Replace internal Structure View API so Marketplace upload passes verification** — uploading 0.2.3 was blocked by the plugin verifier reporting four internal API violations (`StructureViewComposite`, `StructureViewComposite.StructureViewDescriptor`, and both constructors) in `EcrStructureViewFactory`. The factory now returns a `TemplateLanguageStructureViewBuilder` — public platform API designed exactly for template languages like ECR — which composes the composite view itself from the base language model (ECR tags) and every other view-provider language's registered builder (HTML). No internal classes are referenced anymore; the verifier reports zero internal API usages.
- **Make the `intellij.platform.smRunner` module dependency optional** — the hard `<depends>intellij.platform.smRunner</depends>` added for RubyMine compatibility disabled the entire plugin in environments where that module is not installed as a standalone plugin (e.g. the platform test application), silently dropping every extension point and breaking all editor features under test. The depends is now `optional="true"`; every target IDE bundles the module, so the spec test-runner feature remains fully available in production IDEs while the plugin degrades gracefully where it is absent.

### Changed

- **ECR Structure View: three sections merged into two tabs** — with the switch to `TemplateLanguageStructureViewBuilder`, the former separate "Crystal" tab (`@instance_variables`) became an `@instance_variables` group node inside the ECR tab (shown only when instance variables exist), keeping navigation to each variable's first occurrence. Tab titles are unchanged ("ECR", "HTML"); tab icons now come from the respective file types.

## [0.2.3] — 2026-08-24

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

### Fixed

- **Declare SM Test Runner dependency for binary compatibility with RubyMine and other IDEs** — the Marketplace verifier reported 13 unresolved-class problems against RubyMine RM-262 (`OutputToGeneralTestEventsConverter`, `GeneralTestEventsProcessor`, `SMTestLocator`, `SMTRunnerConsoleProperties`, `SMCustomMessagesParsing`, `SMTestRunnerConnectionUtil`, and the `sm.runner.events` package): the test-runner feature uses these APIs but `plugin.xml` never declared the module providing them. The plugin now declares `intellij.platform.smRunner`, and the Gradle build lists it as an explicit bundled module alongside the DAP module.

- **Type-check splat and double-splat expansion against real parameters** — `add(*args)` flagged `*args` with "expected 'Int32', got 'Tuple(Int32, Int32)'" and `setup(**options)` flagged `**options` with "expected 'String', got 'NamedTuple(…)'": unqualified-call type checking compared the splatted expression as a single value against the first parameter, with no expansion at all. Argument lists are now materialized into effective slots — `*tuple` expands to positional slots from the tuple's element types, `**namedTuple` to named slots from its entries — and every slot is checked sequentially against each overload's parameters (rest parameters absorb remaining positionals; unknown named keys skip conservatively). This yields real validation: wrong element/value types report precisely ("expected 'Int32', got 'String'"), arity mismatches surface through the argument-count check's existing tuple-size resolution, and non-tuple splats leave the call unchecked.
- **Infer crystal NamedTuples for `{k: v}` literals and match them against `{k: T, ...}` parameters** — `greet({name: "Smith", title: "Dr."})` against `def greet(user : {name: String, title: String})` was falsely flagged "Type mismatch … got 'Hash(Symbol, String)'": colon-syntax braces are NamedTuple literals in crystal, but inference classified them as Hash with symbol keys and the compatibility check had no named-tuple comparison. Inference now returns `NamedTuple(name: T, …)` for identifier-keyed colon literals (`=>` forms remain Hash; empty `{}` unchanged), and the type-compatibility check compares the inferred entries structurally with the parameter's brace notation in both directions — same key set, per-value compatibility, key order irrelevant.
- **Stop binding chained dot expressions as bare arguments** — in a multi-line parameter list like `workers : Int32 = System.cpu_count.to_i,` the dot-call rule's bare-argument alternative swallowed the following chain and parameter lines as arguments of `.cpu_count` (its first bare argument may be an implicit-object call), producing false "Too many arguments: expected at most 0, got 2" markers on unrelated tokens such as `.to_i` or a later type annotation. The bare-argument alternative now requires `!DOT` — the same guard ordinary method calls already carry. This also corrects long-standing mis-shaped PSI for unparenthesized chains like `name.downcase.gsub(" ", "_")` and `x.not_nil!.next` (previously parsed as calls with implicit-call arguments). Compiler-checked semantics: crystal treats `m .x` as a receiver continuation on `m`'s result ("wrong number of arguments … given 0"), never as passing `.x` as an argument; the corresponding resolver pin was updated to match.
- **Restore constructor resolution for classes containing `private module`/`private enum`** — `Channel(Nil).new(3)` was flagged "Too many arguments: expected at most 0, got 1" and hovering `new` showed "Any (Variable)" instead of the constructor: the visibility modifier rule did not accept `module_definition` (nor enum/annotation/alias definitions), so `channel.cr` broke parsing at its `private module SenderReceiverCloseAction`, every method of the enclosing class dropped out of the method-by-class index, and the constructor resolved as implicit-with-no-parameters. The rule now covers all definition forms; stub index version bumped so persisted indexes rebuild — `Channel#initialize(@capacity = 0)` resolves and argument counts check against it again.
- **Flag multi-line unions without backslash continuation at the dangling pipe** — `rescue ex : ArgumentError |` followed by a newline compiles nowhere ("expecting token 'CONST', not 'NEWLINE'"), but reporting it through the parser pinned GrammarKit's generic error marker to the enclosing keyword. The parser stays permissive; a new annotator flags each dangling pipe in type positions precisely, with a compiler-style message and an "Add backslash line continuation" quick fix (inserts `\` directly before the newline). Valid single-line and backslash-continued unions are untouched; bitwise-or expressions spanning lines are not affected.
- **Respect require-graph visibility for unqualified calls and name fallbacks** — a bare `process` call flagged "Missing required argument(s): 'items'" against a top-level `process(items)` living in an unrelated, unrequired file (while `crystal run` correctly reports `undefined local variable or method`). All three name-based lookup paths now filter their candidates through the caller's effective source set: the argumentless-call check in the argument-count inspection, both inspections' unqualified-calls-with-arguments paths, and the `CrystalReference` fallback used by hover/Go to Definition — which additionally no longer picks an arbitrary first match from all-scope search (the source of unrelated signatures like `HTTP::Server::RequestProcessor#process` appearing on undefined names). Calls into required files and prelude helpers (`puts`, …) keep being checked; unknown names now fall back to plain identifier/type-info hovers instead of foreign signatures.
- **Stop leaking stdlib overloads into dot-call type checks** — `handler.call(*@args)` on a lowercase receiver was type-checked against every indexed method sharing the callee name (the old path searched the whole project by name and only filtered constant receivers), producing false "Type mismatch: expected … 'HTTP::Server::Context'" errors on valid code like variadic-generic `Event(*Args).call`. Dot-call type checking now resolves through the shared authoritative resolver: exact receiver resolutions are checked against their real overloads, splat arguments are skipped (they expand to N elements, not one value), and unknown/suppressed/ambiguous receivers stay silent. Migrates one of the remaining call-consumer paths named in the shared-resolver TODO; record and constructor checks keep working through the resolver's record fallback.
- **Stop flagging bracket access after dot-calls as arguments** — `Tools.config["envs"]` was parsed as `config(["envs"])`: the dot-call's bare-argument alternative swallowed the bracket sequence as an Array-literal argument, producing a false "Too many arguments: expected at most 0, got 1" on the bracket access (and silently mis-shaping every chained index read). The dot-call rule now carries the same `!LBRACKET` guard as ordinary method calls, so brackets parse as the index postfix.
- **Restore full `String` completion and indexing** — seven parser/lexer gaps broke parsing inside real stdlib sources, silently dropping most of `string.cr` from stub indexing (only 41 of 311 methods were indexed, hiding `upcase`, `downcase`, `capitalize`, `strip`, `gsub`, …). Fixed constructs: newlines and brace blocks inside `{% … %}` macro scripts (the lexer state was missing `{`, `}`, `->`, `=>`, `;`, `#`, `@[`, `@` — now mirroring the parser's token list), multi-line bare calls with trailing commas (`record ToUnsignedInfo(T),` across lines), expression-positioned control flow (`x || return nil`, `cond && break`, bare `return yield`), C pointer suffixes in generic type arguments (`Pointer(UInt8*)`), another object's ivar access (`other.@length`) including the `&.` shorthand with keyword methods (`match.try &.begin(0)`), implicit-subject case comparisons (`when .< 0`), indexed assignment targets (`chars[index] = carry = 'a'`), `self` as a standalone/bare argument expression (`sprintf self, other`), and `%(` after `def` or `.` lexing as the operator method name instead of a percent literal. Stub index version bumped so persisted IDE indexes rebuild; `String` now exposes ~180 unique method names through require-aware dot-completion.
- **Show method documentation for DOT-calls on literal receivers** — hovering over `max` in `[1, 2, 3, 4].max` now shows the resolved method's documentation instead of falling back to "Any (Variable)"; the same applies to Go to Definition and argument inspections on such calls. Literal receivers (`[...]`, `%w[...]`, `"abc"`, numbers, hashes, tuples, `cond ? a : b` with same-class branches) are typed through the neutral inference session and must agree on one bare receiver class; element unions like `[1, "a"]` still resolve to their single `Array` root. Cross-class unions (`cond ? [1] : {1 => 2}`) and operator composites (`(a || b).run`) remain conservatively suppressed per the exact-receiver contract.
- **Infer types for percent literals (`%w`, `%i`, `%r`, `%q`, …)** — `%w[test fest]` now hovers as `Array(String)` instead of `Any`. The lexer gives word arrays their own `PERCENT_WORD_ARRAY_BEGIN/END` tokens (previously indistinguishable from `%q`/`%Q`/plain `%`), adds the missing interpolating variants `%W` and `%I`, and allows interpolation inside symbol arrays. Type resolution dispatches percent literals by token kind — `%w`/`%W` → `Array(String)`, `%i`/`%I` → `Array(Symbol)` (also when empty), `%r` → `Regex`, everything else → `String`. Stub index version bumped so persisted IDE indexes rebuild.
- **Restore full `Enumerable` method completion on arrays and collections** — six parser and lexer gaps broke parsing inside real stdlib sources, silently dropping most of `enumerable.cr` from stub indexing (only 8 of 142 methods were indexed, hiding `sum`, `max`, `min`, `map`, `select`, `reduce`, `find`, …). Fixed constructs: global-scope calls (`::raise "msg"`), assignments as `case` subjects (`case v = yield e`), multi-argument `yield` in expression position (`memo = cond ? (yield a, b) : c`), named block parameters with comma type lists (`&block : T, U -> R`), macro-control directives between tuple entries (`yield({elem, {% for %} x{{i}}, {% end %}})`), multi-line `{{ }}` interpolations, `&.as(T)` shorthand without receiver, and — root cause of several cascades above — newlines inside `{{ }}`/`{% %}` previously lexed as bad characters instead of NEWLINE tokens. The stub index version was bumped so persisted IDE indexes rebuild; `Enumerable` now exposes ~131 indexed methods through require-aware completion.
- **Preserve trailing stdlib wildcard dependencies after macro-generated call arguments** — parenthesized argument lists now retain compile-time macro-control directives as structured PSI, so parsing continues through files such as `indexable.cr` and require-aware completion can load its trailing `indexable/*` extensions, including `Indexable::Mutable` methods for array literals.
- **Resolve remaining adversarial require and SDK edge cases** — unresolved exact and wildcard targets behind symlinked prefixes now retain canonical intended paths, so external shard-cache create/delete/rename/move events rebuild effective sources immediately; `CRYSTAL_PATH` discovery skips custom roots without `prelude.cr`, supports optional `src/`, and recognizes Unix, drive-letter, UNC, and extended-length Windows paths.
- **Close canonical wildcard and Windows SDK release gates** — wildcard owners now match structural changes through both symlink aliases and safe canonical external targets without weakening project-root traversal protection, unresolved targets retain lexical nearest-parent watches, `CRYSTAL_PATH` uses the platform path separator without splitting Windows drive letters, and symlink-cycle tests skip only concrete unsupported or permission failures.
- **Close final require release gates** — classify every dot-prefixed filename as relative, accept escaped-newline continuation whitespace at a closing quote, prevent stale SDK discovery from overwriting newer settings or overrides, and canonicalize recursive wildcard traversal so project-root symlink aliases cannot expose project sources.
- **Match Crystal 1.20.3 require loading exactly** — preserve compiler candidate order across ordinary, shard, namespaced, nested, and explicit-extension forms; decode static require escapes with lexer-identical numeric and Unicode validation; reject stale PSI; and make recursive wildcard traversal ancestor-safe, cycle-safe, depth-first, and cancellation-aware.
- **Make SDK-backed tests deterministic** — synchronized stack overrides now survive out-of-order disposal without restoring inactive callbacks, and general completion, inspection, expression, and Find Usages suites use project-local synthetic stdlib roots instead of the machine SDK.
- **Harden require-aware analysis for real source edits and path syntax** — explicit `.cr` paths and decoded Crystal string escapes now resolve and invalidate correctly; full unsaved macro-controlled require removal/replacement updates immediately; arbitrary nonphysical PSI no longer receives prelude visibility; recursive wildcards are iterative, cancellable, and cannot scan the project root.
- **Keep require graph readers coherent under concurrency and shared dependencies** — dirty dependency reasons are cleared across every owning closure without losing simultaneous changes, cold prelude readers share one retryable resolution, failed SDK replacement cannot revive the old stdlib, and tests use project-local collaborators instead of POSIX subprocesses or stale global VFS state.
- **Preserve lexical DOT receiver identity across unrequired collisions** — local, parameter, assignment, and instance-variable receiver evidence now resolves through the caller's require-aware type session, keeping completion, navigation, references, and inspections aligned.
- **Restore realistic stdlib DOT completion** — core `String`, numeric, and collection methods now resolve through the configured `prelude.cr` closure, including implicit `Int32 -> Int` and `Array -> Indexable -> Enumerable` hierarchy edges. Optional stdlib extensions, shard methods, and project reopenings appear only through direct or transitive forward requires, including top-level macro-control and wildcard requires. Injected ECR fragments receive the same prelude foundation for core literals without gaining project, shard, reverse, or host context.
- **Protect the prelude from project shadowing** — requires originating under the configured stdlib root now stay within that root, so project `lib/string.cr`, `lib/int.cr`, and collection reopenings cannot replace core prelude dependencies or leak globally into Crystal and ECR completion. Disposable fixture graph overrides are now atomically stacked and restored without exposing the production SDK-backed service between replacements.
- **Preserve prelude resolution across escaped relative dependencies** — stdlib-only resolution is now traversal provenance rather than a per-file path classification. Support files reached outside the configured stdlib root continue resolving bare exact and wildcard dependencies from that stdlib when traversed by the prelude, while independent project traversals of the same file retain project-first semantics.
- **Fix `def self.<keyword>` rendering as method body source** — `def self.require(path)`, `def self.class(x)`, `def self.end(x)`, etc. (any `def self.<keyword>` using the `keyword_as_method` BNF alternative) no longer appear in the completion popup as `def require(path)end(path)` (or pollute the stub index with that body text as a key). `getNameFromMethodName` now stops its fallback name-composition loop at the parameter list (`LPAREN`) and method body (`METHOD_BODY`), and skips the `DEF` and `SELF` header tokens, producing the correct name (`"require"`, `"class"`, etc.). As a side effect, Go to Definition, Find Usages, and Rename now also work for `def self.<keyword>` methods (previously silently broken because `findNameIdentifierInMethodName` only matched `IDENTIFIER`/`CONSTANT` leaves).
- **Avoid repeated require-closure traversal on clean completion queries** — clean effective-source snapshots now return in constant time. Reverse closure ownership limits dirty validation to roots that contain the changed dependency, preserving node, closure, and snapshot identity after ordinary body edits while rebuilding changed require edges.
- **Avoid require-graph sessions outside DOT completion** — free-text, annotation, class-body, type, namespace, and require completion no longer construct a type-resolution session or query effective sources. DOT receiver, method, and constructor phases still share exactly one session.
- **Preserve pre-existing require-completion fixture paths** — test cleanup now snapshots the initial temporary fixture tree and removes only paths created by the current test instead of deleting hard-coded roots such as `src`.
- **Avoid repeated require fingerprint PSI walks on analysis hot paths** — materialized clean require-graph nodes are now reused directly. Saved dirty nodes validate lazily inside the current query read action, preserving all cache identities for ordinary body edits while rebuilding changed require edges and reverse dependents.
- **Reject compound require paths during dependency collection** — adjacent double-quoted literals such as `require "foo" "bar"` no longer produce a bogus static require edge, while escaped quotes and backslashes inside one complete literal remain valid.
- **Preserve qualified constructor identity in navigation fallback** — recovery PSI shapes without a `CrystalDotCallAccess` reference now pass complete simple, qualified, or absolute constant paths to the shared exact constructor resolver. Incomplete receiver paths are suppressed instead of falling through to an unrelated type with the same simple name.
- **Make expression DOT completion usable with real Crystal stdlib metadata** — body-level macro interpolation no longer marks method names unknown, macro-controlled methods are filtered without hiding unrelated certain methods, and compiler-implicit numeric hierarchy edges expose parent methods such as `Int#times` on `Int32` literals. Unions now require every branch to resolve exactly, ranges complete as `Range` rather than their left endpoint, lexical `.new` chains retain qualified identity, and radix literals preserve unsuffixed autocasting metadata.
- **Navigate every exact DOT-call overload** — `CrystalDotCallReference` is now polyvariant for regular static, instance, and explicit constructor overloads, including arbitrarily grouped static constants, qualified or absolute paths, locals, typed parameters, instance variables, and constructors. IntelliJ receives all exact targets for its chooser, while single-target resolution succeeds only when unique. Empty exact results are authoritative, preventing incomplete or ambiguous receivers from falling through to unrelated same-simple-name constructors, and constructor precedence remains unchanged.
- **Apply shared constructor precedence to DOT completion type objects** — a record now wins over an indexed class at the same exact identity, so `Config.` renders the record's `new(record_value : String)` signature instead of the colliding class constructor, while nearer lexical identities keep their established record-vs-class precedence. Synthetic `new` eligibility and initializer signatures are resolved through the shared constructor metadata of the exact selected identity, so a lexically selected `Service` inside `Right` can no longer render `Left::Service`'s `initialize` tail.
- **Keep expression DOT completion exact across namespace collisions** — static candidates now come from the neutral hierarchy API using the receiver's resolved qualified identity, including lexically resolved simple constants. Qualified records retain their exact constructor PSI and render the selected record's fields/type instead of the first same-simple-name record. Direct unwrapped operator receivers, long mixed call chains, explicit `self.new` overloads, and injected ECR DOT completion are covered end to end.
- **Make expression receiver analysis follow Crystal execution and scope semantics** — source-ordered flow now composes ternary/logical assignments, postfix modifiers, loop intermediate states, exception-aware rescue/else/ensure, and nearest-type instance variables. Call arguments retain post-assignment state at the enclosing call throw point without narrowing failures during argument evaluation, while structured `if`/`unless`/`case` reachability excludes terminating assignment arms. Logical expressions use truthiness, all-branch termination removes unreachable state/returns/tails, and assignment provenance preserves full generic/union rendering after typed parameters are reassigned. Nearest lexical constants shadow outer/global identities, generic hierarchy edges normalize consistently, named method caches track macro uncertainty by name, and one neutral session owns concrete class/struct/record constructor precedence plus named lookup for completion, PSI references, and DOT-call consumers. Ambiguous constructor references remain unresolved instead of choosing an arbitrary overload.
- **Resolve transparent parenthesized and generic DOT-call receivers** — calls such as `(service).run`, `(Outer::Service).run`, `(::Service).run`, and `Box(Int32).new` now preserve one exact receiver identity and validate the selected declaration. Parenthesized constructors retain record-collision suppression, direct generic constructors use normal `self.new`/`initialize`/implicit precedence, and downstream assignments retain the same generic root. Grouped control flow, unions, nilable or call-valued generic arguments, class variables, and other inexact receivers remain suppressed.
- **Validate exact DOT-calls against method signatures** — parenthesized, bare, and argumentless calls now share exact receiver and hierarchy resolution before reporting missing or excess arguments. This covers inferred instance receivers and constructor precedence through `self.new`, `initialize`, implicit zero-argument construction, or an exact simple record fallback. Record/class collisions follow nearest lexical identity and macro-controlled records suppress uncertain diagnostics. Ambiguous, incomplete, union, nilable, record-instance, and otherwise unresolved targets remain suppressed. Unqualified calls retain their existing shadowing and overload behavior.
- **Type-check constructor shorthand parameters** — named constructor arguments now match direct instance-variable parameters such as `initialize(@age : Int32)`, so `Boo.new age: "wrong"` reports the expected `Int32` type mismatch.
- **Exclude file-level self-receiver methods from global completion** — definitions such as `def self.require(path)` outside a type remain available to general method lookup but are no longer indexed or suggested as callable top-level methods.
- **Parse and diagnose nested `require` expressions correctly** — `require` inside runtime `if` branches, blocks, assignments, arguments, conditions, interpolations, type declarations, `def`, or `fun` now produces structured `CrystalRequireStatement` PSI instead of a generic parser error or method-call PSI. A dedicated inspection highlights the keyword with Crystal's matching semantic error (`Can't require dynamically`, `Can't execute Require in a macro`, `Can't require inside def/fun`, or `Can't require inside type declarations`), while direct file-scope and compile-time macro-conditional requires remain valid.
- **Prevent exponential parser replay in nested Spec DSL files** — expression, bare-expression, and range rules now parse their common prefixes once, keeping deeply nested `describe`/`context`/`it`-style blocks responsive while preserving postfix `?`, right-associative ternaries, and all bounded or omitted-endpoint range forms.
- **Restore responsive Go to Symbol searches** — name enumeration now streams StubIndex keys directly instead of loading PSI elements once per candidate name; actual navigation results remain constrained to the requested search scope.
- **Block parameter rename/reference resolution** — block parameters (e.g. `|ola|` in `each do |ola| ... end`) are now resolved as references, so Rename (from definition or usage site), Go to Definition, and Find Usages work and stay in sync across the block body. Previously they were only highlighted (Annotator) and suggested (Completion) but had no reference link, so renaming one occurrence did not rename the other. Fixed by teaching `CrystalReference.resolveLocal()` to check `CrystalBlock.parameterList`, mirroring the existing method/macro parameter resolution.


### Changed

- **Keep each DOT completion on one source snapshot** — receiver inference, instance/static method collection, and synthetic constructor classification now share one type-resolution session. Nonphysical ECR fragments use only the prelude source set, and same-fragment records do not bypass physical source membership to offer `new`.
- **Make shared type and hierarchy resolution require-aware** — each neutral resolution session now captures one immutable effective-source snapshot and filters indexed types, named methods, class methods, reopenings, hierarchy edges, and duplicate-signature checks before resolving identities or completeness. DOT completion passes the actual PSI position for static and instance receivers, so only the current file, its forward require closure, and the configured prelude contribute methods; unrequired optional files no longer leak and there is no project-wide fallback.
- **Cache require-aware effective source snapshots** — a project-level require DAG now combines a dependency-version-validated `prelude.cr` foundation with each file's forward-only transitive require closure. Immutable snapshots are reused across ordinary body edits, changed or deleted prelude dependencies rebuild before publication, and one read boundary prevents atomic edits from producing mixed-version closures. Unsaved require edits and required-file changes invalidate only reverse dependents; exact edges own unresolved and higher-precedence candidate paths, while typed wildcard watches distinguish direct from recursive targets and missing targets from unrelated siblings. Create/copy/delete/rename/move and containing-directory events are matched without PSI work in VFS callbacks; saved content marks materialized nodes dirty and validates only nodes reached by the next query inside its existing read-consistent traversal, with no executor barrier or unrelated-closure gating. Shard metadata, relevant `lib/` roots, and project roots invalidate globally and rebuild lazily. Cached stdlib-root identity is part of graph validity, so cold bare exact and wildcard requires rebuild on `null`/root/different-root transitions regardless of which component populated the cache. SDK replacement invalidates once after clearing the old root and again from `finally` after resolution/publication. Deep cycles use stack-safe iterative traversal, unresolved or invalid files cannot leak stale sources, and completion never starts `crystal env` discovery.
- **Extract static require dependencies from PSI** — valid file-scope and top-level macro-conditional `require` paths now produce ordered, stable fingerprints for require-aware analysis, while dynamic, interpolated, incomplete, method, type, runtime, and macro-interpolation contexts remain excluded.
- **Complete DOT expressions through shared receiver analysis** — completion now gives `Foo.`, `(Foo).`, and `((Foo)).` identical results and supports grouped/qualified type objects, runtime variables, scalar literals, collections, operators, and annotated or inferred method results. Direct `3.` and `3.14.` member access bypass ordinary numeric-literal suppression, union receivers merge methods from every branch without collapsing overloads, runtime values never receive static constructors, and unknown DOT receivers no longer fall through to unrelated project methods.
- **Prepare union receiver method candidates for shared dispatch** — instance-method lookup now accepts ordered exact type roots, uses one cached neutral hierarchy session, and merges duplicate canonical signatures while retaining overloads and namespace isolation. Primitive and generic outer roots use the same indexed path.
- **Centralize scoped expression receiver analysis** — a neutral memoized type-set resolver now owns lexical variable flow, complete control-flow sets, exact receiver/implicit-self method returns, overload conservatism, and recursion safety using cached StubIndex lookups. Completion retains only receiver/postfix decomposition and type-object handling; legacy inspection and string inference APIs are compatibility adapters.
- **Share conservative DOT receiver normalization through neutral PSI infrastructure** — inspections and completion use `CrystalReceiverExpression` for transparent single-expression grouping and exact constant/generic roots while assignments, multi-expression groups, dynamic namespace roots, and ambiguous receivers remain opaque.
- **Split completion responsibilities into focused providers** — `CrystalCompletionContributor` now retains only registration and ordered dispatch policy, while context classification, local candidates, and class/constant candidates live in dedicated components. Completion ordering, ranking, deduplication, Dumb Mode guards, and results remain unchanged.
- **Use scoped StubIndex navigation and centralized lookups** — Go to Class/Symbol no longer perform project-wide `FileTypeIndex` scans, and alias, annotation, and lib definitions are now indexed and navigable. Production StubIndex access goes through the typed `CrystalIndexService`; indexed hot paths use requested or narrower scopes and stop processing early where semantics allow. Type navigation retains distinct class, module, struct, and enum icons.
- **Strengthen indexed navigation regression coverage** — direct tests now protect call extraction, Go to Class/Symbol contributors, and current-file-aware completion type lookup ahead of the index service migration. The coverage also fixes current-file type preference stopping at the first indexed match and constructor extraction for the composite DOT-call PSI shape.
- **Correct constructor fallback ambiguity coverage** — navigation tests now distinguish an unavailable top-level type from a genuinely ambiguous exact identity declared with the same constructor signature in separate files, and explicitly verify that recovery PSI bypasses the normal polyvariant DOT reference.
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
- **Instance/class variable `@` completion** — typing `@` (or `@@`) inside a method now suggests all instance (`@name`) and class (`@@name`) variables of the enclosing class, and the auto-popup appears as the sigil is typed. Previously a bare `@`/`@@` produced no suggestions because the lexer emits a standalone `AT` token and the default prefix matcher ignored the sigil; variables defined in a different method (e.g. `initialize`) were also missing because collection was limited to the current method. Fixed by a sigil-aware prefix matcher, file-level class variable collection (walks all file children including raw `INSTANCE_VAR`/`CLASS_VAR` tokens from error-recovery parse states), nested-class isolation, and auto-popup triggering via `TypedHandlerDelegate.checkAutoPopup`.
- **Prevent endless first-open stdlib indexing** — the filtered synthetic library is now the sole stdlib root source. A directory-index exclusion policy shields known non-user-facing distribution directories while an exact legacy `Crystal StdLib` module library still exists, and an idempotent post-startup cleanup removes that library without creating a replacement. Projects that intentionally open the Crystal compiler source are unaffected when no legacy library exists. SDK/reindex actions refresh only filtered `.cr` roots through IntelliJ's additional-library API; Force Re-index is ignored outside Crystal projects and honors cancellation while collecting and scheduling files.
- **`require` statement and path completion** — typing `req<caret>` at an independent file-scope statement now offers one canonical method-style `require(path)` lookup, including inside top-level compile-time macro conditions. Local or indexed methods cannot duplicate this bare candidate, while real methods named `require` remain available through normal DOT completion and insertion. The synthesized statement is excluded from runtime control flow, blocks, type/callable/macro-definition bodies, DOT calls, namespace access, type annotations, assignment values, arguments, conditions, and unfinished multiline expressions. Selecting it inserts `require ""` and immediately opens path completion. A leading `.` or `/` lists relative `.cr` files and directories, while other prefixes list project shards and cached Crystal stdlib entries, with multi-segment directory traversal in both modes.
- **Suggest class constants after `::`** — typing `ClassName::<caret>` now shows class-level constants (e.g. `WEIGHT`) alongside nested types.


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
