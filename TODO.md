# TODO — IntelliJ Crystal Plugin

## Heredoc Embedded-Language Injection Follow-up

- [ ] **Write back fragment-editor edits for interpolated injected heredocs/strings** — single-place
  bodies (raw heredocs, interpolation-free heredocs/strings) write fragment-editor edits back
  exactly (`updateText` re-encodes for strings); multi-place (interpolated) bodies ignore edits
  rather than corrupting interpolations, because the flat fragment text cannot be reconstructed
  into per-place ranges. A correct implementation needs place-boundary tracking in the injected
  document (e.g. placeholder sentinel scanning or a DocumentWindow-aware write path).
- [ ] **Injection intentions and settings UI** — "Inject language or reference" intention,
  `# language=` comment completion, and a Language-Injections-style settings page are not
  implemented; only heredoc-marker and `# language=` comment injection exist.
- [ ] **`# language=` comments for percent literals and `:"symbol"` strings** — percent literals
  (`%q(…)` etc.) and `symbol_string_expression` are not injection hosts; only `heredoc_literal`
  and `string_expression` hosts exist.

## Unused Assignment Inspection Follow-up

- [ ] **Analyze destructuring assignment targets independently** — represent each local target in tuple,
  parenthesized, and nested destructuring assignments as its own binding definition, preserve ignored
  underscore targets, and map subsequent reads to the correct target without treating the complete
  destructuring expression as one assignment.
- [ ] **Model `case ... in` pattern bindings as clause-local symbols** — distinguish identifiers that bind
  matched values from ordinary expression references, including nested tuple/named-tuple patterns and guards,
  so they neither read nor shadow an unrelated outer local incorrectly.

## Inlay Hints (Issue #2)

- [ ] **Implement InlayHintsProvider** — show inferred types on variables inline
  in the editor. Depends on type inference (Issue #1).

## Crystal Shards (Issue #3)

- [ ] **Parse shard.yml** — extract dependency declarations
- [ ] **Index lib/ directory** — include shard sources in StubIndex
- [ ] **Dependency-aware completion** — suggest types/methods from installed shards
- [ ] **Include arbitrary custom `CRYSTAL_PATH` roots in bare requires** — preserve environment order across every
  absolute custom source root while keeping the first root containing `prelude.cr` as the core foundation. Acceptance
  requires exact and wildcard bare requires plus path completion to search custom roots before/after stdlib as ordered,
  deduplicate collisions deterministically, invalidate root changes without compiler processes on completion paths,
  and retain the existing project-`lib/` and stdlib-traversal shadowing guarantees.

## Implement Members (Issue #5)

- [ ] **Discover abstract methods** from parent classes/modules
- [ ] **Generate implementing stubs** with correct method signatures
- [ ] **Register OverrideImplement action** in plugin.xml

## Indexed Declaration Follow-up

- [ ] **Add constant declaration stubs and indexes** (`CrystalConstantIndex` and `CrystalConstantByClassIndex`) only after the grammar separates constant definitions from ordinary statement assignment contexts.
- [ ] **Design instance/class-variable declaration indexing** only if a valid stubbed declaration model can represent declarations without indexing arbitrary usages or assignments.

## Parser Recovery Follow-up

- [ ] **Preserve declarations after incomplete binary operators** — malformed prefix/postfix forms such as
  `value = !~ other` and `value = other !~` produce a `PsiErrorElement` but can consume a following
  declaration during pinned assignment recovery. Add boundary-aware recovery without `recoverWhile` or
  weakening valid consecutive-statement parsing, then assert the trailing declaration remains structured.
- [ ] **Keep postfix-rescue bare calls from consuming heredoc body openers** — valid code such as
  `value = <<-TEXT rescue puts fallback` currently treats the newline `HEREDOC_START` body opener as another
  bare argument of `puts`, leaving the body content detached. Preserve the marker/body pairing while keeping
  ordinary closeless heredoc-call arguments valid.

## Call Argument Inspection Follow-up

- [ ] **Validate `lib fun` calls** — add indexed FFI function declaration resolution, then apply argument-count and argument-type diagnostics to calls such as `LibC.exit`, `LibC.exit()`, and `LibC.exit(value)`.
- [ ] **Model named-only parameter boundaries** — preserve bare `*` and positional-splat boundaries in parameter metadata so positional arguments cannot satisfy parameters that must be passed by name.
- [ ] **Validate signature parameter ordering** — report invalid required positional parameters declared after optional positional parameters while preserving valid named-only parameters after splats.
- [ ] **Expand unqualified call applicability** — support inherited unqualified methods and other unqualified calls that cannot yet resolve to one exact applicable overload set, without introducing name-only fallbacks.
- [ ] **Make overload tie diagnostics deterministic** — define stable ranking when equally close overloads omit different required parameter names.
- [ ] **Model complete call precedence** — distinguish all remaining declaration kinds that can share call syntax so argument inspections can suppress or resolve aliases and other non-method declarations without name-only guesses.
- [ ] **Resolve record instance methods through the shared call resolver** — exact simple, qualified, and absolute record constructors already use shared constructor identity and precedence; model generated record instance signatures before enabling diagnostics for record values.
- [ ] **Resolve class-variable receivers** — model exact class-variable types and assignment conflicts so DOT-calls on class variables can use the shared resolver without name-only guesses.
- [ ] **Narrow union and nilable receivers** — use control-flow facts to reduce union or nilable receiver types to one exact non-nil type before resolving DOT-calls.
- [ ] **Resolve macro-interpolated call targets (partially done, v13)** — unqualified call names inside `{{ … }}`
  and macro bodies now resolve to project macros or builtin `Crystal::Macros` macro-methods (macros.cr is indexed
  as a single-file root), and argument diagnostics are suppressed in macro context. Still open: macro-interpolated
  RECEIVERS (`{{ x.method }}` with receiver types) and macro-argument arity checking against macro parameter lists.
- [ ] **Include inherited instance-variable type evidence** — define deterministic hierarchy precedence for inherited instance-variable declarations and assignments before using inherited type bodies for DOT-call receiver inference.
- [ ] **Finish migrating call consumers to the shared resolver** — completion and DOT navigation now consume neutral receiver, hierarchy, constructor, and overload metadata; move the remaining type-checking and parameter-info paths to the same semantics.
- [ ] **Evaluate type-check overloads as complete calls, not independent slots** — `CrystalTypeCheckInspection` currently accepts each argument when any overload accepts that slot, so different slots can be validated by different overloads. Combined constructor pools make this especially visible: a call such as `Deque(Int32).new([1], 2)` can pass per-slot checks even though no single `new` overload accepts the complete call. Reuse one applicability result per overload across arity, names, and all resolved argument types before deciding whether to report.
- [ ] **Index Crystal load order for cross-file type reopenings** — preserve require-graph order when identical methods or include/extend edges are reopened across files; until load order is indexed, the shared resolver suppresses identical cross-file signatures and multiple relevant cross-file edges whose precedence cannot be proven, retains callable-distinct overloads, and keeps exact same-file source precedence.
- [ ] **Investigate operator-call arity on inferred stdlib values** — the headless kemal audit reports "Too many arguments: expected at most 0, got 1" for `(Time.monotonic - start).total_milliseconds` in `spec/run_spec.cr`. Reproduce the exact highlighted PSI and determine why `Time::Span#-(Time::Span)` is replaced by an implicit zero-argument constructor before changing resolver fallback semantics.
- [ ] **Suppress diagnostics for unresolved macro-generated constructors** — the headless kemal audit checks the ten-argument `new(...)` call inside `Kemal::ExceptionPage.new(context, exception)` against the visible two-argument overload even though the ExceptionPage shard generates the real constructor through a macro. Model constructor uncertainty from relevant macro expansions, or suppress only when the written overload set can be proven incomplete; add a regression shaped like `src/kemal/helpers/exception_page.cr`.
- [ ] **Investigate chained `status(...).json(...)` argument binding** — the headless kemal audit reports missing `status_code` for valid `env.status(:not_found).json(...)` calls. Capture the parsed call arguments and exact resolution result to distinguish a parser-binding defect from wrong-overload selection, then cover both the chained and standalone forms.
- [ ] **Preserve generic owner arguments for nested receiver types** — the headless kemal audit reports `Node(K, V)` versus `Kemal::LRUCache::Node` at `src/kemal/route_handler.cr:56`. Trace the local assignment and nested generic receiver through exact DOT resolution so the qualified type does not lose its owner arguments before compatibility checking.

## IDE / Incremental Lexing Follow-up

- [ ] **Restore heredoc delimiter queue across incremental relexes** — the v12 lexer queues same-line heredoc
  delimiters in a runtime `ArrayDeque<PendingHeredoc>` (fields are NOT part of the int lexer state IntelliJ
  stores per line via `CrystalLexerAdapter.getState()`). After an incremental relex restart inside/below a
  multi-heredoc header chain, the queue is empty and remaining bodies are lexed as ordinary code until the file
  is re-parsed from the top. Parser/batch behavior is correct (see `MultiHeredocBodies` fixture: 4 bodies, 0
  errors). COLORING is solved independently via PSI-enforced annotator
  attributes (v12.2, see docs/specs/heredoc-calls.md) — remaining impact is
  limited to token-level consumers of the LAYER lexer. Fix direction if that
  ever matters: encode the pending delimiter sequence into the adapter state
  (like the existing `interpolationDepth` encoding) or re-derive remaining
  bodies from the already-emitted HEREDOC_START markers; also verify
  `heredocId` restoration for body relexes.

## Parser Follow-up

- [ ] **Reject unsupported trailing `while` and `until` modifiers** — Crystal 1.21
  rejects forms such as `puts value while condition` and `target[index] = value
  until condition`, but the historical `postfix_modifier` rule and parser goldens
  still accept them. Restrict the rule to compiler-supported modifiers and migrate
  the old positive fixtures to invalid-syntax tests without weakening valid block
  `while`/`until` parsing.
- [ ] **Finish the Crystal 1.21.0 parser compatibility gates** — reduce the indexed
  `stdlibParseAudit` corpus from the current 133 errors in 97 of 461 files to zero,
  then parse all 1,625 distribution sources without errors. Once both are green, add
  mandatory CI jobs that download the pinned official archive, verify SHA-256
  `cc407bd071915cc7b5d9348281e669a911d20a1f4b9fac52a62088660eb22208`, and run both
  scopes. Keep raw `PsiErrorElement` collection and exact file counts; do not add an
  error allowlist or accepted nonzero threshold. See `docs/specs/stdlib-parser-compatibility.md`.
- [ ] **Enforce named-argument ordering in call grammar** — once the first named argument appears,
  Crystal rejects later positional, splat, and positional `out` arguments. The current generic
  `argument_list` also accepts this pre-existing invalid ordering for ordinary named arguments;
  model the positional-to-named transition without breaking macro trivia or heredoc markers.
- [ ] **Parse string-literal external parameter names** — Crystal accepts non-interpolated
  strings such as `def fetch("http-header" internal)`, but the parameter grammar currently
  supports identifier external names only. Add a delimiter-safe non-interpolating string-name
  rule, reject empty/interpolated names, and preserve the decoded call-site label separately
  from the internal binding.
- [ ] **Parse comma-separated assignments inside parenthesized calls (`compute(x = 5, y = 6)`)** — valid Crystal
  (verified: compiles and evaluates both assignments in order), but neither the bare-argument path (grouped
  expressions hold at most one assignment) nor `argument_list` (`argument` cannot consume `id = expr`) accepts it.
  PRE-EXISTING gap, verified against the baseline grammar while landing heredoc marker support (v12). Fix likely:
  extend `argument` with an assignment alternative mirroring grouped-expression semantics, or route multi-group
  lists through a dedicated `assignment_argument` element — careful with `named_argument {pin=2}` interplay.
  Single-assignment form `consume(value = "ready")` works (see docs/specs/heredoc-calls.md binding matrix).
- [ ] **Support brace blocks after `&.` shorthand (`f &.m { }`)** — `implicit_object_call` accepts no
  trailing `[block]`, so the unparenthesized proc-plus-block form (`select &.even? { }`) fails to parse.
  Parenthesized usage (`select(&.even?)`) is unaffected. Rare in real code; extend the rule with a
  `[block]` tail (and cover it in a parser test) when a real-world case appears.
- [ ] **Handle `Foo::bar` with lowercase identifiers as method calls** — `namespace_access` only matches
  `DOUBLE_COLON CONSTANT`, so `Foo::bar` (lowercase) parses as variable reference + orphaned global-scope
  call. Standalone `::ident args` calls are fixed (see `[DOUBLE_COLON]` on `method_call_expression`);
  the receiver-postfixed `::method` form needs a postfix operator or `dot_call_access` extension.
- [ ] **Investigate flaky `CrystalIndexServiceTest` scope tests** — twice now a full-suite run failed
  (`testExcludedScopeTypeIsFiltered` / `testProcessesTypeNameCandidatesOutsideProvidedScope`) with
  StubIndex results missing just-added fixture types (`expected:<[ExcludedType]> but was:<[]>`), while
  the tests passed in isolation and on immediate full-suite rerun. Suspected platform indexing race
  (VFS refresh vs. StubIndex query) plus cross-project name bleed in the shared test index; both
  failures predate and are unrelated to recent grammar changes.
- [ ] **Close the cold-cache stdlib window in `CrystalRequireGraphService`** — the production
  constructor wires its stdlib-root supplier to `cachedStdlibPath` only, so until some other component
  (typically the async library provider) publishes a discovered root, bare stdlib requires resolve to
  nothing and stdlib symbols stay invisible. The state self-heals via the null→root generation bump in
  `captureGeneration`, but there is an early window after project open where resolution silently fails.
  Consider triggering discovery from the graph (without blocking read actions on `crystal env`) or
  publishing the root earlier during project startup.

## Type Inference Follow-up

- [ ] **Model expression-position `return`/`break`/`next` as abrupt in variable flow** — an
  expression-position exit such as the RHS of `values[0] ||= return x = 1` currently flows its
  value assignment as fall-through, so the write leaks past the exit in type resolution. Flow
  `control_flow_expression` values first and preserve the abrupt continuation instead of
  merging the exited write into the fall-through state.
- [ ] **Resolve operator overload return types** — replace the conservative `Unknown`
  result for `<=>`, `=~`, and `!~` with exact receiver-aware overload resolution.
  Standard implementations are not uniformly boolean (`String#=~` returns
  `Int32 | Nil`), and Crystal permits custom methods with arbitrary return types,
  so token-based result heuristics are unsafe.

## Completion Follow-up

- [ ] **Resolve generic include edges for primitive receivers** — `struct Int`/`struct Float`
  include `Comparable(Precise)` / `Comparable(Number)` with generic arguments; the hierarchy
  walk cannot resolve generic include edges, so comparison methods (`>`, `<`, `<=`, `clamp`
  overloads) inherited through Comparable are not offered on numeric literal receivers yet.
- [ ] **Type-shaped macro arguments that bare_expression half-matches** — the new
  `named_type_bare_argument` alternative only triggers when `bare_expression` fails entirely.
  Shapes where the expression parse "succeeds" but is semantically a type stay mis-shaped:
  `property level : Severity? = nil` (log/broadcast_backend.cr) parses `Severity` as an
  expression, the `?` as a stray ternary QUESTION and `= nil` as an assignment tail, and
  `property select_context : SelectContext(Nil)?` (channel.cr) parses the generic type as a
  bare method call. Consequence: the nilability/generic information is lost from the PSI (no
  parse error, so no canary signal). A correct fix needs type-aware macro-argument parsing
  (e.g. preferring type_reference when the value starts with CONSTANT/LPAREN-type shapes)
  without breaking expression-valued macro args; pinned by NamedTypeBareArguments.txt.
- [ ] **Property default-value blocks in macro arguments** — `getter root_context :
  RootContext { RootContext.new(self) }` (spec/context.cr:150) leaves the trailing
  `{ ... }` default block unconsumed and currently breaks the bare-argument list. Accept an
  optional block tail on type-shaped macro arguments (or bare arguments generally) and verify
  the block does not swallow blocks belonging to an enclosing call.
- [ ] **Design an explicit opt-in for project-root recursive require wildcards** — retain suppression for recursive targets equal to or containing the project root (`./**`, `../**`, and deeper ancestors) until an implementation can prove a bounded traversal root, expose cancellation/progress, avoid `FileTypeIndex` and project-wide index scans, and cover large projects without completion latency regressions.
- [ ] **Bound require-graph root caches and compose prelude source sets** — add per-root LRU or lifecycle eviction with deterministic invalidation, preserve active closure ownership and retry semantics, and represent effective sources as a shared prelude plus root-local set without eagerly copying the prelude for every cached root. Acceptance requires bounded memory under many queried roots, unchanged membership/snapshot coherence, and concurrency tests for eviction during dirty validation.
- [ ] **Evaluate repeated DOT reference result caching independently** — do not broaden the current completion-session optimization into cross-invocation or PSI-reference caches until invalidation and identity semantics have a dedicated design and measurements.
- [ ] **Evaluate require-listener granularity independently** — the current targeted closure ownership fixes effective-snapshot validation cost without redesigning PSI listeners; consider listener changes only with separate diagnostics and correctness coverage.
- [ ] **Model caller-aware ECR project/shard/host load context** — injected ECR currently receives only the configured prelude closure. Define an explicit compiling caller or entrypoint model before exposing project, shard, or host forward requires, so templates gain real caller context without inferring reverse dependencies or leaking unrelated siblings.
- [ ] **Select completed-call overloads by arguments** — reuse exact argument applicability and overload ranking for expression receiver completion before inferring a completed call's return type. Until then, receiver analysis must return `Unknown` whenever multiple exact overload candidates remain.
- [ ] **Support cross-file record type-object completion** — the shared constructor classifier scopes record definitions to the containing file (`CrystalPsiUtils.findRecordDefinitions`), so `Config.` in a file that does not itself declare `record Config` resolves to `Unknown` and offers no completions (pinned by `CrystalCompletionTest.testCrossFileRecordTypeObjectOffersNoCompletions`). Requires indexing record definitions or making the classifier project-scope aware; update the pinning test when support lands.
- [ ] **Support index/bracket receiver components in DOT completion chains** — postfix receivers containing `[]` access (e.g. `First.new.second[0].`) currently resolve the whole receiver to `Unknown` and offer no candidates (pinned by `CrystalCompletionReceiverResolverTest.testRejectsUnsupportedPostfixTail`). Requires resolving the indexed element type (e.g. from an `Array(T)`/`Hash(K, V)` receiver or one exact `def [](...)` overload) before the chain can continue.
- [ ] **`::` completion enumerates candidates from unrequired files** — `CrystalTypeCompletionProvider.getEnclosingTypeLookups` and `CrystalSymbolCompletionProvider.addClassConstants` currently enumerate nested types and class constants from the whole project index without an effective-source filter. Give them a `PsiElement` context and filter candidates through the require-graph effective-source snapshot so `Namespace::` completion only offers types/constants visible to the context file's forward require closure.
