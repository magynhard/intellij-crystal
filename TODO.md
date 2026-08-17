# TODO — IntelliJ Crystal Plugin

## Rename Refactoring — Follow-up Tasks

The scope-aware rename infrastructure is in place (PsiNameIdentifierOwner on
CrystalVariableReference, CrystalParameter, CrystalAssignment; resolve() promotion
logic; CrystalRefactoringSupportProvider). These follow-up tasks complete the work:

- [x] **Fix resolveLocal() to find variable assignments** — now uses recursive
  `findAssignmentWithName()` that walks sibling subtrees to find `CrystalAssignment`
  composites. Stops at method/macro/class boundaries to prevent cross-scope resolution.

- [x] **Add rename tests for PsiNameIdentifierOwner composites** — 18 tests in
  `CrystalRenamePsiNameIdentifierOwnerTest` covering CrystalParameter, CrystalAssignment,
  CrystalVariableReference PsiNameIdentifierOwner implementation, resolve() promotion,
  and resolveLocal() scope boundary behavior.

- [x] **Update rename spec** — documented resolveLocal() fix (section 7), updated test
  matrix (section 9.2), added known limitations. See openspec/specs/rename-refactoring/spec.md.

- [x] **Fix handleElementRename() for INSTANCE_VAR/CLASS_VAR** — CrystalReference,
  CrystalInstanceVarReference, and all setName() mixins now handle @/@@ prefixed tokens
  and ensure the prefix is preserved during rename.

- [x] **Fix CrystalParameterMixin for instance var parameters** — getNameIdentifier()
  now recognizes INSTANCE_VAR_ACCESS composites (e.g. `def initialize(@x : Int32)`).

### Instance Variable Rename — Remaining Issues

The `@`/`@@` prefix is always preserved from the original token type. The user
only types the bare name. The prefix is never changed during rename.

| Scenario | User types | Result | Status |
|----------|-----------|--------|--------|
| `@var` → `foo` | `foo` | `@foo` | ✅ Works |
| `@var` → `@foo` | `@foo` | `@foo` | ✅ Works |
| `@@var` → `cool` | `cool` | `@@cool` | ✅ Works |
| `@@var` → `@cool` | `@cool` | `@@cool` | ✅ Works |
| `my_var` → `@my_var` | — | Not supported | N/A (different types) |
| `@var` → `var` | — | Not supported | N/A (different types) |

Type changes (`IDENTIFIER` ↔ `INSTANCE_VAR` ↔ `CLASS_VAR`) are intentionally
not supported — they are fundamentally different variable types.

### Root Cause Analysis

~~The core problem is that `CrystalNamesValidator.isIdentifier()` uses a simple~~
~~character check that rejects `@`-prefixed names.~~ **Fixed**: validator now
accepts `@`/`@@`-prefixed identifiers.

~~The remaining issues (token type changes when adding/removing `@`) are deep~~
~~structural problems~~ **Fixed**: `createLeafFromText()` helper now properly walks
the parsed PSI tree to find the correct leaf token, instead of using `firstChildNode`
which returned wrapper composites (statement/expression_statement).

**Fixed**: All `setName()` and `handleElementRename()` methods now always strip
any `@`/`@@` prefix from the user input and re-apply it from the original token
type. This ensures consistent behavior regardless of what the user types.

## Type Inference (Issue #1)

- [x] **Extend CrystalTypeInference for literal assignments** — currently only
  handles `Klasse.new`, `Klasse.method`, bare method_call. Add inference for
  literal assignments (`x = "hello"` → String, `x = 1` → Int32, `x = :sym` → Symbol).
- [x] **Add array/hash/named-tuple literal inference** — `x = [1, 2]` → Array(Int32)
- [x] **Add control-flow union inference** — `x = cond ? 1 : nil` → Int32?

## Inlay Hints (Issue #2)

- [ ] **Implement InlayHintsProvider** — show inferred types on variables inline
  in the editor. Depends on type inference (Issue #1).

## Crystal Shards (Issue #3)

- [ ] **Parse shard.yml** — extract dependency declarations
- [ ] **Index lib/ directory** — include shard sources in StubIndex
- [ ] **Dependency-aware completion** — suggest types/methods from installed shards

## Implement Members (Issue #5)

- [ ] **Discover abstract methods** from parent classes/modules
- [ ] **Generate implementing stubs** with correct method signatures
- [ ] **Register OverrideImplement action** in plugin.xml

## Indexed Declaration Follow-up

- [ ] **Add constant declaration stubs and indexes** (`CrystalConstantIndex` and `CrystalConstantByClassIndex`) only after the grammar separates constant definitions from ordinary statement assignment contexts.
- [ ] **Design instance/class-variable declaration indexing** only if a valid stubbed declaration model can represent declarations without indexing arbitrary usages or assignments.

## Type Resolution Unification

- [x] **Unify type resolution ownership** — neutral `de.magynhard.crystal.analysis` structured type-set resolution now owns scoped expression, variable-flow, and method-return inference. `CrystalTypeInference` and `CrystalExpressionTypeResolver` are compatibility adapters rather than mutually dependent owners.

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
- [ ] **Resolve macro-interpolated call targets** — resolve macro-interpolated receivers, method names, and constructor targets to one exact declaration before enabling call-argument diagnostics for those calls.
- [ ] **Include inherited instance-variable type evidence** — define deterministic hierarchy precedence for inherited instance-variable declarations and assignments before using inherited type bodies for DOT-call receiver inference.
- [ ] **Finish migrating call consumers to the shared resolver** — completion and DOT navigation now consume neutral receiver, hierarchy, constructor, and overload metadata; move the remaining type-checking and parameter-info paths to the same semantics.
- [ ] **Index Crystal load order for cross-file type reopenings** — preserve require-graph order when identical methods or include/extend edges are reopened across files; until load order is indexed, the shared resolver suppresses identical cross-file signatures and multiple relevant cross-file edges whose precedence cannot be proven, retains callable-distinct overloads, and keeps exact same-file source precedence.

## Completion Follow-up

- [ ] **Design an explicit opt-in for project-root recursive require wildcards** — retain suppression for recursive targets equal to or containing the project root (`./**`, `../**`, and deeper ancestors) until an implementation can prove a bounded traversal root, expose cancellation/progress, avoid `FileTypeIndex` and project-wide index scans, and cover large projects without completion latency regressions.
- [ ] **Bound require-graph root caches and compose prelude source sets** — add per-root LRU or lifecycle eviction with deterministic invalidation, preserve active closure ownership and retry semantics, and represent effective sources as a shared prelude plus root-local set without eagerly copying the prelude for every cached root. Acceptance requires bounded memory under many queried roots, unchanged membership/snapshot coherence, and concurrency tests for eviction during dirty validation.
- [ ] **Evaluate repeated DOT reference result caching independently** — do not broaden the current completion-session optimization into cross-invocation or PSI-reference caches until invalidation and identity semantics have a dedicated design and measurements.
- [ ] **Evaluate require-listener granularity independently** — the current targeted closure ownership fixes effective-snapshot validation cost without redesigning PSI listeners; consider listener changes only with separate diagnostics and correctness coverage.
- [ ] **Model caller-aware ECR project/shard/host load context** — injected ECR currently receives only the configured prelude closure. Define an explicit compiling caller or entrypoint model before exposing project, shard, or host forward requires, so templates gain real caller context without inferring reverse dependencies or leaking unrelated siblings.
- [ ] **Select completed-call overloads by arguments** — reuse exact argument applicability and overload ranking for expression receiver completion before inferring a completed call's return type. Until then, receiver analysis must return `Unknown` whenever multiple exact overload candidates remain.
- [ ] **Support cross-file record type-object completion** — the shared constructor classifier scopes record definitions to the containing file (`CrystalPsiUtils.findRecordDefinitions`), so `Config.` in a file that does not itself declare `record Config` resolves to `Unknown` and offers no completions (pinned by `CrystalCompletionTest.testCrossFileRecordTypeObjectOffersNoCompletions`). Requires indexing record definitions or making the classifier project-scope aware; update the pinning test when support lands.
- [ ] **Support index/bracket receiver components in DOT completion chains** — postfix receivers containing `[]` access (e.g. `First.new.second[0].`) currently resolve the whole receiver to `Unknown` and offer no candidates (pinned by `CrystalCompletionReceiverResolverTest.testRejectsUnsupportedPostfixTail`). Requires resolving the indexed element type (e.g. from an `Array(T)`/`Hash(K, V)` receiver or one exact `def [](...)` overload) before the chain can continue.
- [ ] **`::` completion enumerates candidates from unrequired files** — `CrystalTypeCompletionProvider.getEnclosingTypeLookups` and `CrystalSymbolCompletionProvider.addClassConstants` currently enumerate nested types and class constants from the whole project index without an effective-source filter. Give them a `PsiElement` context and filter candidates through the require-graph effective-source snapshot so `Namespace::` completion only offers types/constants visible to the context file's forward require closure.
