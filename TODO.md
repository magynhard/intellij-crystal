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

- [ ] **Support bare callees followed by whitespace-separated regex arguments** — valid Crystal
  such as `y = match /abc/` cannot be disambiguated lexically: after a plain identifier the
  slash must stay division for `a /b/ c`, and Crystal resolves the call-vs-division conflict
  with parser-level backtracking that a PEG lexer/parser split cannot reproduce. The dotted
  (`range.match /re/`), nested-callee (`x.should match /re/`), and keyword (`when /^get_/`)
  contexts are covered by lexer heuristics; a correct general solution needs parse-context
  feedback into lexing.
- [ ] **Preserve declarations after incomplete binary operators** — malformed prefix/postfix forms such as
  `value = !~ other` and `value = other !~` produce a `PsiErrorElement` but can consume a following
  declaration during pinned assignment recovery. Add boundary-aware recovery without `recoverWhile` or
  weakening valid consecutive-statement parsing, then assert the trailing declaration remains structured.
- [ ] **Keep postfix bare calls from consuming heredoc body openers** — valid code such as
  `value = <<-TEXT rescue puts fallback` and `VALUE = <<-TEXT if enabled` currently treats the newline
  `HEREDOC_START` body opener as another bare argument of `puts` or `enabled`, leaving the body content
  detached. Preserve the marker/body pairing while keeping ordinary closeless heredoc-call arguments valid.

## Call Argument Inspection Follow-up

- [ ] **Resolve `Pointer(T).malloc(size, value)` overloads for generic-type receivers** —
  headless audit of stdlib array.cr:156 flags "Too many arguments: expected at most 1,
  got 2" for `Pointer(T).malloc(size, value)`, although pointer.cr declares the 2-arg
  version. The receiver resolution apparently loses generic Pointer's unary
  `def self.malloc(size : Int)` overload pairing — needs the dot-call method pool for
  instantiated generics to contribute class methods with their arity.
- [ ] **De-fuse binary operand mismatch from untyped-parameter constants** — stdlib
  array.cr:2175 (`offset = @capacity - old_capacity` with both sides derived from untyped
  parameters) produces "Type mismatch: expected 'UInt64', got 'Int32'". Track
  instantiation-derived integer width propagation for assignments through method bodies,
  or restrict definite numeric-width verdicts to cases with typed evidence on both legs.
- [ ] **Deepen generic include-edge leaf comparison** — the include-edge traversal (CrystalGenericIncludeCompat) accepts `Array(TestHeaderHandler)` against `Enumerable(HTTP::Handler)` structurally and leaves leaf comparisons to the existing user-type leniency; when the hierarchy gains concrete user-subclass relations for the type checker, wire the leaf comparison through it so genuinely wrong element types inside include-compatible generics are reported.
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
- [ ] **Accessor rename follow-ups** — the bidirectional decorator rename is implemented (define the full scope boundary in `docs/specs/accessor-rename.md`); remaining readers/setters: a receiver chain `obj.nested.foo = v` participates only when a single-level receiver resolves exactly (deeper chain-shape shape matching is future work); the same-name accessor of a re-opened body in another file resolves through the exact type identity, but the word-based scan小结 participants — cross-file setter call sites only participate when the receiver resolves in the same file; rename of a REOPENED type's accessor from its own argument only (class args across relocated files are follow-up work with the crystal class index). The unused-variable analysis now skips assignment-shaped accessor declaration arguments (the API-surface argument; see `docs/specs/accessor-rename.md`'s unused-variable section).
- [ ] **Remaining indexed-audit parse errors** — the escape fix removed the escaped `-\{% / \{{`-statements in primitives.cr (lines 101/146); after the lib-body constant repair the indexed corpus keeps 173 raw errors across 126 files and the crystal-lang/crystal external checkout 1,029 errors across 510 files. They need per-cause traces, each Sym family treated like the escape fix.
- [ ] **Repair remaining lib-body member families** — after the `constant_assignment` fix the
  dominant remaining lib gaps, ranked by remaining file count in the crystal external audit:
  untyped positional fun parameters (`fun strerror_r(Int, Char*, SizeT) : Int`) are done
  (external audit 1,005/494 → 900/397) and external `fun name = symbol` aliases are done
  (external audit 900/397 → 881/358); uppercase `lib fun` names are done
  (external audit 881/358 → 810/323); keyword `fun` names are done
  (external audit 810/323 → 778/307); `$var = symbol` external vars are done
  (external audit 778/307 → 776/306; indexed 163/119 → 161/118);
  `@[...]` annotations inside `lib` bodies are done
  (external audit 776/306 → 432/272; indexed 161/118 → 159/117);
  `{% %}` / `{{ }}` macro-control/interpolation in `lib` and `enum` bodies are done
  (external audit 432/272 → 292/236; indexed 159/117 → 136/109);
  macro-spliced member names are done
  (external audit 292/236 → 280/230; indexed 136/109 → 131/105).
- [ ] **Repair macro interpolation in code positions** — after the spliced-name fix the external
  audit keeps 280 errors in 230 files (max 3 per file, long tail); 110 of them mention macro
  tokens, e.g. interpolated parameters (`def initialize({{` in `src/macros.cr:91`), case/when
  conditions (`in {{member.id}}`, `when {{name}}.to_slice`), and interpolated receivers/calls.
  Treat each position like the spliced-name fix: compiler behavior first, narrowest grammar
  admission, golden plus boundary tests, audit file-set comparison. Expression positions are done
  (private `macro_content_expression` in both primary rules plus operator parity in the macro
  lexer states; external audit 280/230 → 211/183; indexed 131/105 → 100/87); structured
  `{% if %} A {% else %} B {% end %}` envelopes with expression branches are done
  (new `CrystalMacroIfEnvelope` node, no stub change; external audit 211/183 → 203/175;
  indexed 100/87 → 97/84); string-colon named args are done (private `string_label` in
  `named_argument`/`named_bare_argument` plus label unquoting; external audit 203/175 → 193/164;
  indexed unchanged at 97/84); nested `lib` definitions in type bodies are done
  (`lib_definition` joins `class_member`, covering class/struct/module bodies; no lexer, stub,
  or index-key change, so no stub-version bump; external audit 193/164 → 188/162 with
  `elf.cr`/`mach_o.cr` fully clean and `lib_ffi.cr` 2 → 1; indexed 97/84 → 96/84 via the same
  `lib_ffi.cr` advance);
  receiver-qualified ivar `pointerof` targets are done (private `pointerof_target`
  admitting exactly `.@ivar`-terminal chains via `dot_call_access`; ordinary calls stay
  rejected; external audit 188/162 → 174/154 with 8 files fully clean and `scheduler.cr`
  2 → 1 onto the unrelated `&->@stack_pool.collect_loop` proc shape;   indexed 96/84 → 92/82);
  proc pointers with variable receivers are done (dedicated `proc_literal`
  alternative for `ARROW (INSTANCE_VAR | CLASS_VAR) DOT name` plus optional type list;
  raw leaves, no PSI/stub change; external audit 174/154 → 173/153 with `scheduler.cr`
  fully clean; indexed unchanged at 92/82);
  macro control in `case` clause sequences is done (private `case_clause_trivia`
  before the first and between `when`/`in` clauses; at least one real clause still
  required; external audit 173/153 → 163/149 with 4 files fully clean and both
  interpreter sources 2 → 1 onto unrelated macro gaps; indexed 92/82 → 84/79);
  macro-generated splat parameters are done (`do`/`end` in the interpolation lexer
  states plus a splat-gated `parameter` branch; bare fragments still rejected;
  external audit 163/149 → 160/147 with `macros.cr` and `interpreter/compiler.cr`
  fully clean; indexed 84/79 → 81/77);
  chained indexed assignments are done (indexed RHS recursion, dotted receivers,
  assignments in indices; external audit 160/147 → 154/141 with 6 files fully clean;
  indexed 81/77 → 77/73);
  macro fresh variables are done (YYINITIAL `%ident` with an operand-position `freshVariableAllowed`
  guard, `macro_fresh_variable` PSI with braces key in `variable`/primaries/`{% %}` soup; external
  audit 154/141 → 152/139 with `iterator.cr` and `math_spec.cr` fully clean; indexed 77/73 → 76/72;
  newly stopped cascades: `NamedTuple.new(` interior macro-controlled named args, `run_op_tests...`
  macro-args in int_spec);
  indexed and `self` multi-assign targets are done (private `multi_assign_indexed_target` from the
  indexed-assignment helpers, `SELF` in `assignment_target` and the member root; external audit
  152/139 → 149/136 with `pointer.cr:322`, `slice/sort.cr:28`, compiler `syntax/lexer.cr:1988`,
  `crypto/bcrypt.cr:140`/`:137`, and `samples/sudoku.cr` recovered; indexed 76/72 → 74/70;
  newly stopped cascades: unary `&-boundary` in pointer.cr:481, `out = v.to_unsafe` keyword-name
  family in slice/sort.cr:348);
  unary wrapping operators are done (`WRAP_PLUS`/`WRAP_MINUS` in both private unary rules, prefix
  exclusive to `&+`/`&-`; external audit 149/136 → 145/132 with `pointer.cr`, `big/big_int.cr`,
  `uint_spec.cr`, and `crystal/hasher_spec.cr` fully clean; indexed 74/70 → 72/68);
  prefix `!` precedence is done (`BANG` in the twin unary chains, `not_expression` level removed;
  external audit 145/132 → 142/129 with compiler semantic `method_lookup.cr`, `restrictions.cr`,
  and `suggestions.cr` fully clean; indexed 72/68 → 69/65);
  `.!` pseudo-method suffix is done (private `bang_suffix` in both postfix rules + implicit-object
  shorthand, `!LPAREN`-stricter `!()`; external audit 142/129 → 140/127 with
  `time/location_spec.cr` and compiler semantic `new.cr` fully clean; indexed 69/65 → 68/64);
  macro-generated symbols are done (second `symbol_string_expression` alternative `COLON` +
  tightness + `macro_interpolation`, existing element type reused; external audit 140/127 →
  139/126 with colorize.cr fully clean — both `:{{name.id}}` comparison sites; indexed 68/64 →
  67/63);
  keyword identifiers as local variables are done (compiler-verified subset `of`, `union`,
  `uninitialized`, `forall`, `previous_def` in `variable` and rvalue `variable_reference`,
  `out`/`uninitialized` rvalue excluded; external audit 139/126 → 137/120 with `codegen/call.cr`,
  semantic `ast.cr`, `literal_expander.cr`, `syntax/transformer.cr`, `regex.cr`, `spec/parser_spec.cr`
  fully clean; indexed 67/63 → 66/58; same-cause visibility inflation on the pending
  keyword-parameter family: `type_intersect.cr:254`, `syntax/ast.cr:557`, `syntax/parser.cr:6164`
  report 2 same-line errors, `yaml/lib_yaml.cr` exposes `alias : AliasEvent`);
  keyword identifiers as parameter names are done (compiler-verified subset in a lone-name
  parameter alternative; external audit 137/120 → 130/117 with `type_intersect.cr`,
  `syntax/ast.cr`, `syntax/parser.cr` fully clean; indexed 66/58 → 59/55; newly exposed cascade:
  lone `uninitialized` as bare `.new` argument — `TypeDeclarationWithLocation.new(...,
  uninitialized, nil)` in type_declaration_visitor.cr:277);
  multiple `typeof` arguments are done (shared private `typeof_argument_list` in expression and
  type contexts; trailing comma/newlines supported; external audit 130/117 → 128/115 with
  `src/class.cr` and `spec/std/class_spec.cr` clean; indexed 59/55 → 58/54; assignment-level
  operands `typeof(value = 1, other = 2)` remain separate parse_op_assign parity);
  lone `uninitialized` as a bare call argument is done (private
  `uninitialized_variable_reference ::= UNINITIALIZED !type_reference` in `variable_reference`,
  so `TypeDeclarationWithLocation.new(..., uninitialized, nil)` binds the keyword-named value
  while `uninitialized UInt32` keeps the type expression; keyword variables resolve and rename
  end to end via the shared `KEYWORD_VARIABLES` set — reference creation, name identifiers,
  `setName`, parameter names, local usage analysis, word-scanner indexing, and
  `handleElementRename` rewriting the keyword leaf to `IDENTIFIER`; external audit 128/115 →
  127/114 with `type_declaration_visitor.cr` clean; indexed 58/54 → 57/53, zero newly failing
  files by file-set comparison);
  macro header lexer state is done (`macroHeaderSeen` only activates for a line-start macro
  declaration with optional `private`/`protected`, and waits for a multiline header's closing
  parenthesis; dotted `.macro`, `def macro`, and macro-named fields/parameters stay ordinary
  code; external audit 127/114 → 118/105; indexed 57/53 → 48/44, exactly nine compiler files
  repaired and zero newly failing files by file-set comparison);
  typed tuple collections and expression-position `with … yield` are done (`type_path{...}` now
  chooses existing hash entries first or an expression list for `Deque{1, 2}`/`Set{"a", "b"}`;
  `with_yield_statement` accepts bare/parenthesized yield arguments and is admitted as a primary
  expression while preserving its PSI element; external audit 118/105 → 105/94, eleven files
  clean and zero newly failing files; indexed remains 48/44);
  constant-assignment heredocs and spaced index ternaries are done (queued bodies attach to
  `CONSTANT = <<-BODY`; only tight `values[i]?` is nil-safe while `values[i] ? a : b` is ternary;
  additive constant heredoc PSI accessor, no stub/index change; external audit 105/94 → 96/85 and
  indexed 48/44 → 39/35, nine repaired files and zero newly failing files);
  C-FFI aggregate fields and typed keyword parameters are done (struct/union-only grouped fields
  and keyword names; outer lib fields remain invalid; all typed keyword lib-fun parameters are
  normal PSI names; external audit 96/85 → 80/75 and indexed 39/35 → 33/31, ten repaired files and
  zero newly failing files);
  block-only `super`/`previous_def` calls are done (trailing block without arguments;
  external audit 80/75 → 78/73 and indexed 33/31 → 31/29, `range` and `slice` repaired with zero
  newly failing files);
  semicolon-terminated annotation definitions are done (`annotation Field; end`;
  external audit 78/73 → 76/72 and indexed 31/29 → 29/28, `serializable` repaired with zero newly
  failing files);
  double-splat parameter restrictions are done (`**options : **T` with strict splat pairing;
  the line-59 site is repaired but an independent macro-hash-key error at line 71 keeps
  `named_tuple.cr` failing, so totals stay 76/72 and 29/28 with zero newly failing files);
  multiline ternaries are done (newlines around `?` and `:`; external audit 76/72 → 75/71 and
  indexed 29/28 → 28/27, `bsearch` repaired with zero newly failing files);
  keyword setter method names are done (`def private=` with any word keyword, operators excluded;
  external audit 75/71 → 74/70 and indexed 28/27 → 27/26, `types` repaired with zero newly failing
  files);
  macro-generated receiver ivar access is done (`other.@{{ivar.id}}` in `dot_call_access`;
  indexed 27/26 → 26/25, `struct` repaired with zero newly failing files);
  macro-generated multi-assignment targets and named-argument labels are done
  (`multi_assign_member_name`, `named_argument`, `named_bare_argument`, and `string_label`
  accept `macro_interpolation`; no new indexed repairs yet because the disassembler.cr target
  stays macro-body content and the json/yaml label sites advance to later gaps);
  newly exposed cascades: macro-args in int_spec, interpolated named-argument labels in
  json/yaml from_json/from_yaml);
  pre-existing plugin gap recorded: `def self.!` still parses without error);
  next: (a) remaining macro code positions (case/when conditions,
  interpolated receivers/calls), (b) lib-external-var `pointerof` targets
  (`pointerof(LibFFI.ffi_type_void)`) — needs a semantic distinction from ordinary calls,
  separate approach.
- [ ] **Trace the last shard argument-count finding** — the bidirectional decorator rename is implemented (define the full scope boundary in `docs/specs/accessor-rename.md`); remaining readers/setters: a receiver chain `obj.nested.foo = v` participates only when a single-level receiver resolves exactly (deeper chain-shape shape matching is future work); the same-name accessor of a re-opened body in another file resolves through the exact type identity, but the word-based scan小结 participants — cross-file setter call sites only participate when the receiver resolves in the same file; rename of a REOPENED type's accessor from its own argument only (class args across relocated files are follow-up work with the crystal class index).
- [ ] **Trace the last shard argument-count finding** — kemal's own sources (src/ + spec/, including static_file_handler_spec.etag_with_coding:135), ameba's typos.cr `as:` DSL finding, and the ameba `as_node <<-CRYSTAL` pool (variable_spec:102) are clear. The one remaining finding is `arg` in excessive_allocations_spec:10 ("expected at most 0, got 1"), needing its own trace. (Fixed along the way: extractArguments now handles the bare (parenthesis-free) argument-list form of CrystalBareMethodCallExpression — the heredoc-header marker argument of `as_node <<-CRYSTAL` used to vanish, so the def saw zero arguments and reported "Missing 'source'"; earlier: macro-invocation block bodies are macro data via CrystalMacroContext.isInsideMacroCallBlock, the tight bracket after a dot-call method name always binds as the receiver's index postfix, `Reference.new(node, scope)` resolves the sibling `Ameba::AST::Reference` through the program closure, macro-call arguments are no longer checked as runtime calls, and proc-literal parameters resolve as local declarations.)
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

- [ ] **Finish the Crystal 1.21.0 parser compatibility gates** — reduce the indexed
  `stdlibParseAudit` corpus (pinned 650 production-indexed sources, compiler tree
  included) from the current 175 errors in 126 files to zero, then parse the whole
  distribution without errors. Once both are green, add
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
