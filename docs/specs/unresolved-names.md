# Unresolved Names

Spec for the `CrystalUnresolvedName` inspection and the matching hover text:
names that resolve to nothing report `Cannot find 'name'` instead of
silently falling back to `Any (Variable)` hovers.

Severity splits by cause: truly unknown names warn (`WARNING`), names indexed
only outside the current file's require closure warn weakly (`WEAK WARNING`).
Hover shows the same `Cannot find 'name'` text for both.

## Rule

A bare identifier, call callee, DOT method name, constant, or type-path
segment is **known** when any of the following holds; otherwise the
inspection reports `Cannot find '<name>'` on the name element and hovering
shows the same text. Type paths (`x : Helper`, `Array(Helper)`, unions) use
the constant rule per segment, with the root rule for qualified paths.
1. A local binding is visible: preceding assignment, `for` variable, `rescue`
   binding, grouped `(x = …)` or multi `x, y = …` target, enclosing `def` /
   `macro` parameter, block or proc-literal parameter. Forward references do
   not bind (locals), except hoisted top-level methods.
   Assignments in conditions (`if ucid = channel.receive`, and the corresponding
   `unless`/loop forms) bind their local target in the body. The target does not
   bind its own right-hand side or leak across method boundaries. References
   navigate to the target in the condition even though the parser stores it in
   a `condition` node rather than an ordinary assignment node.
   Local lookup includes preceding siblings of the reference itself, not only
   its parent wrappers. This makes `(base_name = value) && base_name != other`
   resolve in flat logical expressions, including `if` conditions; `||`
   follows the same lexical binding rule without implying a truthy/non-nil type.
   Reads before the assignment and on its own right-hand side gain no binding;
   method boundaries still isolate locals.
   Explicit method type parameters declared by `forall T, U` bind throughout
   that method's parameter restrictions, return type, and body. They do not
   leak into sibling methods or nested declarations. DOT calls on such a type
   parameter remain unjudgeable before specialization and do not fall back to
   a same-named global type's methods. This also applies to dependent constant
   paths such as `T::MAX` and DOT calls on `T::MIN`; an absolute `::T` is not a
   method-local parameter.
2. A prelude baseline entry: prelude types (`String`, `Int32`, … — the same
   core list as type completion), prelude top-level methods (`puts`,
   `print`, `p`, `pp`, `gets`, `loop`, `spawn`, `sleep`, `raise`, `exit`,
   …, verified against the distribution's `prelude.cr`), and prelude macros
   (`record`, `property`, `getter`, `setter`, `delegate`, `spawn`, … from
   `macros.cr` and `object/properties.cr`). Always known, even without a
   configured SDK.
3. An indexed declaration is visible through the current file's require
   closure: types via exact file membership, unqualified calls via
   `visibleMethods` + `callableUnqualified` (top-level methods and the
   implicit-self scope of the enclosing type), macros by existence. Type
   **aliases** are indexed declarations too (alias index): a require-visible
   or same-file alias counts as known wherever its name appears — as DOT
   receiver root, namespace root, or type-path segment. Member resolution
   through an alias is unjudgeable until the type session unwraps aliases,
   so calls on an alias receiver stay silent instead of flagging the method
   name.
   FFI aliases use dedicated library-member PSI rather than the ordinary alias
   index. Qualified receivers such as `LibGMP::MpLimb.new!(num)` use the same
   exact, require-visible library lookup as FFI type-path navigation. Aliases
   declared between compile-time conditional tags count as known; the plugin
   does not choose a target platform branch or certify the alias's method set.
   An alias in another library or an unrequired file never heals the receiver.
   Statically named `record` declarations use `CrystalRecordIndex` and the shared
   session's ordered lexical/exact identity lookup. A required sibling file can
   therefore provide `MethodInfo` in a nested module's parameter annotation.
   Unrequired or unrelated records never supply a binding; indexed unavailable
   records remain weak warnings. Dynamic and macro-controlled record declarations
   are not published by this index. See [Record Type References](indexed-navigation.md#record-type-references).
4. The compiler-builtin or magic-name baseline: `sizeof`, `typeof`,
   `pointerof`, `instance_sizeof`, `offsetof`, `alignof`, `uninitialized`,
   `__DIR__`, `__FILE__`, `__LINE__`, `__END_LINE__`, `__METHOD__`, and the
   `require` pseudo-keyword. These have no `def`/`macro` in the index.
5. Same-file or require-visible constant declaration: a bare `CBA` with a
   `CBA = …` assignment in the current file (live PSI), or a constant
   declaration in the constant index whose file belongs to the current
    file's require closure. Private file-level constants count only in their
    own file. Private type constants are accessible by bare name inside their
    owning namespace, including nested namespaces and cross-file reopenings,
    and exact superclass/include ancestry, but not from unrelated owners or
    through explicit `Owner::CONSTANT` access.
    Reopenings do not bypass require/prelude visibility.
    FFI `lib` declarations also count as namespace roots in type paths, such as
    `@mpf : LibGMP::MPF`. Simple roots use the enclosing lexical namespaces and
    then the global identity; absolute roots never fall back to lexical names.
    Only require-visible declarations count. Indexed-but-unavailable libraries
    warn weakly; unknown library roots warn normally. An unrelated same-named
    library never makes the root known. This recognizes the library root, not
    arbitrary missing members inside it.
6. DOT calls whose receiver resolves exactly and whose method set resolves
   (`Methods`, `ImplicitConstructor`, `RecordFallback`, `Accessor`).
   Unqualified type receivers use the shared ordered lexical identity lookup,
   so `Entry.new` inside `Benchmark::IPS::Job` recognizes the visible
   `Benchmark::IPS::Entry` rather than requiring a global `Entry` declaration.
   Locals and parameters use their flow-refined type at the call site before
   their declared base type. In `case message; when LSP::HoverRequest`, a
   `message : LSP::RequestMessage` parameter therefore exposes the subtype's
   `property params`, also in nested blocks. Missing members on that exact
   subtype still warn; refinement does not leak into other branches or past
   reassignment and shadowing.
   Assignment subjects (`case target = node.target`) receive the same `when`
   refinement. A direct `target.is_a?(Type)` test in a pure `&&` chain also
   refines later operands, preventing false `name` warnings on AST variables.
   Multi-type arms remain authoritative unions rather than reverting to the
   assigned base type; exact member consumers still require one exact receiver.
   Request classes generated inside `macro finished` may have no index entry.
   Eligible static hooks supply shared declaration views (see
   [Static Finished Declarations](static-finished-declarations.md)); their typed
   accessors resolve normally and genuinely missing members still warn. For
   opaque hooks, the guarded type spelling remains authoritative, so missing
   member lookup stays unjudgeable instead of reverting to the base class and
   falsely reporting `params`. Opaque hooks have no generated navigation targets
   and do not suppress ordinary missing members outside that branch.
7. Annotation names in `@[Name(...)]` resolve through `CrystalAnnotationIndex`,
   never through ordinary type, method, or constant declarations. Simple names
   search the nearest enclosing namespace, outer namespaces, then the global
   identity; qualified and absolute paths retain their exact identity. Only
   declarations in the effective require/prelude source set are visible.
   Navigation and documentation use this same annotation reference. An unknown
   annotation warns on the final name segment; an exact lexical candidate found
   only outside the source set warns weakly. Ambiguous visible declarations do
   not choose an arbitrary navigation target and do not warn as missing.
   Compiler-created annotations (`AlwaysInline`, `CallConvention`, `Extern`,
   `Flags`, `Link`, `Naked`, `NoInline`, `Packed`, `Primitive`, `Raises`,
   `ReturnsTwice`, `ThreadLocal`, `Deprecated`, `Experimental`, `TargetFeature`)
   are known without an SDK; navigation requires an indexed declaration.
   Indexing mode, unavailable source context, and incomplete annotation syntax
   remain silent. Annotation arguments are metadata and are not checked by this
   unresolved-name inspection; the annotation name itself is checked.
8. Accessor-generated readers are known through the shared accessor-macro
   mapping. `getter! name` and `property! name` generate both `name` and
   `name?`, not `name!`; bare reads in the declaring type and exact DOT
   readers resolve to the macro argument. Setter-only macros and `getter?`
   do not make a missing plain reader known. Local bindings retain precedence,
   and an unrelated type's property cannot heal an unknown name.
   Typed accessor declarations retain keyword names as declarations too:
   `property? struct : Bool` provides the exact receiver's `struct?` reader
   even though the declaration's name token is `STRUCT`, not `IDENTIFIER`.
9. A direct positive `responds_to?` guard with one plain literal method symbol
   proves that member available on the same local binding. For example,
   `if error.responds_to? :inner` makes `error.inner` known even when the
   declared `Crystal::ErrorFormat` module does not itself declare `inner`.
   Scope identity, assignment invalidation, branch polarity and joins follow
   the neutral flow model in [Type Inference](type-inference.md#responds-to-member-guarantees).
   This suppresses a false missing-member verdict without inventing an exact
   implementation, signature, or return type. Known compatible declarations can
   be offered separately as [non-exact navigation targets](guarded-member-navigation.md),
   and hover labels the call as a guarded member rather than an `Any` variable.

## Silence Rules

No diagnostic when the name cannot be judged or the shape is not a plain
unresolved name — mirroring the suppression-first conventions of the
call-argument inspections:

- Program-context consistency (same lens as dependency-aware completion):
  declarations loaded by the selected containing entry are visible throughout
  that program; a helper need not repeat its entry's requires. `require.md`
  specifies entry selection and the conservative standalone-library fallback.
  Names indexed only outside that program remain unavailable; the existing
  known-but-unrequired classification warns weakly. Truly unknown names
  (nothing indexed anywhere) warn strongly. Visiting another executable never
  changes visibility or heals a missing-name warning in the selected program.
- Require-gated stdlib names unknown to the index (no SDK: `JSON` with no
  indexed declaration): silent. The index cannot prove absence.
- Incomplete or suppressed DOT receivers, macro-uncertain enclosing types
  (named-method collection incomplete for an implicit-self bare call),
  macro-interpolated callees or receivers: silent.
- Interpolated type paths such as `UInt{{n}}` are generated names, not missing
  references to their static prefixes. They remain silent without suppressing
  ordinary non-interpolated type paths in the same compile-time loop.
  The same rule applies in expression arguments (`consume(UInt{{n}})`), where
  the parser represents the tight interpolation as a macro argument to the
  static prefix. Whitespace-separated macro arguments do not concatenate with
  their callee and never suppress an otherwise unknown callee.
- Non-code positions: definition names, DOT/DOUBLE_COLON-adjacent segments
  (except the flagged last namespace segment), symbol keys (`sym:`), string
  literals, `require` paths, annotation arguments, `case ... in` patterns (owned by
  `CrystalInvalidInPattern`), macro contexts, macro-call blocks, and arguments
  of macro invocations (`record Config`, `property name` declare API surface
  through expansion), `lib/` and other non-project sources, ECR-lenient
  fragments.
- DOT receivers are never flagged, only DOT method names with exact
  receivers. Receiver-aware resolution stays exact: unknown receivers never
  fall back to name-only matches. The receiver root itself is judged with
  the same rules as constants: the prelude baseline applies (`Bytes.new`
  with no SDK is silent), and an alias receiver silences the whole call
  (unjudgeable members) instead of flagging the root or the method name.
  A visible receiver-owned macro heals a macro-only DOT call (`Apfel.essen`
  with `macro essen` in `Apfel` and no `def`): the call is valid but has no
  method resolution target.

## Hover

`Cannot find '<name>'` replaces the `Any (Variable)` fallback for bare
identifiers and bare constants that the inspection would flag, through the
same shared predicate. Resolved hovers are unchanged. Inspection highlights
additionally surface the message via the platform warning tooltip.

## Out Of Scope

- "Did you mean" quickfixes and require-insertion assists.
- `method_missing`-style dynamic dispatch: Crystal has none; macro-generated
  members are covered by the macro-uncertainty suppression above.
