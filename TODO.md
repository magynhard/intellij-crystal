# TODO — IntelliJ Crystal Plugin

## Unused Assignment Inspection Follow-up

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

- [ ] **Support bare callees followed by whitespace-separated regex arguments** — valid Crystal
  such as `y = match /abc/` cannot be disambiguated lexically: after a plain identifier the
  slash must stay division for `a /b/ c`, and Crystal resolves the call-vs-division conflict
  with parser-level backtracking that a PEG lexer/parser split cannot reproduce. The dotted
  (`range.match /re/`), nested-callee (`x.should match /re/`), and keyword (`when /^get_/`)
  contexts are covered by lexer heuristics; a correct general solution needs parse-context
  feedback into lexing.

## Call Argument Inspection Follow-up

- [ ] **De-fuse binary operand mismatch from untyped-parameter constants** — stdlib
  array.cr:2175 (`offset = @capacity - old_capacity` with both sides derived from untyped
  parameters) produces "Type mismatch: expected 'UInt64', got 'Int32'". Track
  instantiation-derived integer width propagation for assignments through method bodies,
  or restrict definite numeric-width verdicts to cases with typed evidence on both legs.
- [ ] **Deepen generic include-edge leaf comparison** — the include-edge traversal (CrystalGenericIncludeCompat) accepts `Array(TestHeaderHandler)` against `Enumerable(HTTP::Handler)` structurally and leaves leaf comparisons to the existing user-type leniency; when the hierarchy gains concrete user-subclass relations for the type checker, wire the leaf comparison through it so genuinely wrong element types inside include-compatible generics are reported.
- [ ] **Validate `lib fun` calls** — add indexed FFI function declaration resolution, then apply argument-count and argument-type diagnostics to calls such as `LibC.exit`, `LibC.exit()`, and `LibC.exit(value)`.
- [ ] **Expand unqualified call applicability** — support inherited unqualified methods and other unqualified calls that cannot yet resolve to one exact applicable overload set, without introducing name-only fallbacks.
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
- [ ] **Accessor rename follow-ups** — the bidirectional decorator rename is implemented (define the full scope boundary in `docs/specs/accessor-rename.md`); remaining readers/setters: a receiver chain `obj.nested.foo = v` participates only when a single-level receiver resolves exactly (deeper chain-shape shape matching is future work); the same-name accessor of a re-opened body in another file resolves through the exact type identity, but the word-based scan小结 participants — cross-file setter call sites only participate when the receiver resolves in the same file; rename of a REOPENED type's accessor from its own argument only (class args across relocated files are follow-up work with the crystal class index). The unused-variable analysis now skips assignment-shaped accessor declaration arguments (the API-surface argument; see `docs/specs/accessor-rename.md`'s unused-variable section).
- [ ] **Cover real-world ECR templates (`content_for ... do`, HTML/code interleave)** — the 2026-09-17 kemal re-audit reports 17 parse errors plus 5 unused-variable findings in the shipped `exception_page` shard template (`lib/exception_page/src/exception_page/exception_page.ecr`) and 1 parse error in kemal's own `spec/asset/hello_with_content_for.ecr`. Block-form `content_for "meta" do ... end` and surrounding markup exceed current ECR support. Reproduce with minimized fixtures, extend the ECR grammar/states narrowly, and keep existing ECR goldens green.
- [ ] **Standalone chained-call argument checks** — the resolved-env.status(...).json(...) chains are clean now (the hash-key fix); when chained-call receivers gain exact typing, wire the standalone chain forms into the same argument checks with the regression shape `env.status(:not_found).json({error: "User not found"})`.
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

- [ ] **Enforce named-argument ordering in call grammar** — once the first named argument appears,
  Crystal rejects later positional, splat, and positional `out` arguments. The current generic
  `argument_list` also accepts this pre-existing invalid ordering for ordinary named arguments;
  model the positional-to-named transition without breaking macro trivia or heredoc markers.
- [ ] **Investigate flaky `CrystalIndexServiceTest` scope tests** — recurring full-suite
  failures (`testProcessesTypeNameCandidatesOutsideProvidedScope`) with StubIndex results
  missing just-added fixture types (`expected:<[ExcludedType]> but was:<[]>`), while the
  tests pass in isolation. Observed both with and without grammar changes and with/without
  a stub-version bump (green 1/1, red 1/1 after the bump) — the bump is NOT a causal fix and
  the failure predates the empty-brackets landing. Suspected platform indexing race
  (VFS refresh vs. StubIndex query) plus cross-project name bleed in the shared test index.
  Reproduction filter to keep at hand: `./gradlew test --rerun-tasks` (full suite) reproduces
  roughly every other run; isolation always green.
- [ ] **Wire resolution/navigation for zero-arity `X[]` empty-call expressions** — the tight
  empty-bracket postfix (`Int64[]`, `foo[]`) now parses and infers (Number-family receivers
  resolve to `Array(X)`; spec: docs/specs/empty-collection-inspection.md) but produces plain
  token children without a reference composite; `X[]` does not resolve to the `Number` `[]`
  macro / matching `def self.[]` for navigation, hover-on-call, or argument-count inspection
  routing. Reuse the shared exact DOT-target resolver used by DOT-calls if a composite shape
  is introduced.
- [ ] **Infer the `Slice`/`StaticArray` `[]` families** — `Slice[1, 2]` / `StaticArray[1, 2]`
  are their own stdlib class `[]` constructors (not `Number` receivers), so the bracket-call
  typing gate (Number-family walk) leaves them Unknown. Add per-family gates with slice
  element casts (Slice uses `new!`, so reading `.to_i`-style values needs care) once their
  PSI shape is covered by navigation.
- [ ] **Close the cold-cache stdlib window in `CrystalRequireGraphService`** — the production
  constructor wires its stdlib-root supplier to `cachedStdlibPath` only, so until some other component
  (typically the async library provider) publishes a discovered root, bare stdlib requires resolve to
  nothing and stdlib symbols stay invisible. The state self-heals via the null→root generation bump in
  `captureGeneration`, but there is an early window after project open where resolution silently fails.
  Consider triggering discovery from the graph (without blocking read actions on `crystal env`) or
  publishing the root earlier during project startup.

## Type Inference Follow-up

- [ ] **Refine non-element index results and index writes** — range and multi-argument
  indexing currently returns the container as-is; `Array#[](start, count)` and
  `StaticArray`/`Slice` range overloads could report their exact return types, and
  literal `Tuple` indexes outside `-size..size-1` should not silently keep the tuple
  type. Indexed-assignment getter typing (`arr[i] += 1`, `h[k] ||= v`) and the
  `String#[](String | Char)` / regex overloads (`String?` results) remain unresolved.

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
- [ ] **Design an explicit opt-in for project-root recursive require wildcards** — retain suppression for recursive targets equal to or containing the project root (`./**`, `../**`, and deeper ancestors) until an implementation can prove a bounded traversal root, expose cancellation/progress, avoid `FileTypeIndex` and project-wide index scans, and cover large projects without completion latency regressions.
- [ ] **Bound require-graph root caches and compose prelude source sets** — add per-root LRU or lifecycle eviction with deterministic invalidation, preserve active closure ownership and retry semantics, and represent effective sources as a shared prelude plus root-local set without eagerly copying the prelude for every cached root. Acceptance requires bounded memory under many queried roots, unchanged membership/snapshot coherence, and concurrency tests for eviction during dirty validation.
- [ ] **Evaluate repeated DOT reference result caching independently** — do not broaden the current completion-session optimization into cross-invocation or PSI-reference caches until invalidation and identity semantics have a dedicated design and measurements.
- [ ] **Evaluate require-listener granularity independently** — the current targeted closure ownership fixes effective-snapshot validation cost without redesigning PSI listeners; consider listener changes only with separate diagnostics and correctness coverage.
- [ ] **Model caller-aware ECR project/shard/host load context** — injected ECR currently receives only the configured prelude closure. Define an explicit compiling caller or entrypoint model before exposing project, shard, or host forward requires, so templates gain real caller context without inferring reverse dependencies or leaking unrelated siblings.
- [ ] **Select completed-call overloads by arguments** — reuse exact argument applicability and overload ranking for expression receiver completion before inferring a completed call's return type. Until then, receiver analysis must return `Unknown` whenever multiple exact overload candidates remain.
- [ ] **Support cross-file record type-object completion** — the shared constructor classifier scopes record definitions to the containing file (`CrystalPsiUtils.findRecordDefinitions`), so `Config.` in a file that does not itself declare `record Config` resolves to `Unknown` and offers no completions (pinned by `CrystalCompletionTest.testCrossFileRecordTypeObjectOffersNoCompletions`). Requires indexing record definitions or making the classifier project-scope aware; update the pinning test when support lands.
