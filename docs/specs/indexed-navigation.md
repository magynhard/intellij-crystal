# Indexed Navigation

The plugin uses Crystal stub indexes for navigation and type lookup without scanning project files at runtime.

## Active Indexes

The active index set consists of exactly eleven indexes:

- `CrystalClassIndex` indexes class, module, struct, and enum names.
- `CrystalMethodIndex` indexes method names.
- `CrystalMethodByClassIndex` maps enclosing type names to methods.
- `CrystalMacroIndex` indexes macro names.
- `CrystalTopLevelMethodIndex` indexes methods declared outside a type.
- `CrystalClassByEnclosingIndex` maps enclosing type names to nested types.
- `CrystalAliasIndex` indexes alias definitions.
- `CrystalAnnotationIndex` indexes annotation definitions.
- `CrystalLibIndex` indexes lib definitions.
- `CrystalConstantIndex` indexes constant names.
- `CrystalConstantByOwnerIndex` maps qualified owner names to member constants.

Instance-variable and class-variable declaration indexes are intentionally absent. Variable indexing remains deferred unless a valid stubbed declaration model is designed.

Constant declarations (`KODORRA = 123` at file top level, `BAR = 1` in type and lib bodies, `private`/`protected` visibility modifiers) carry stubs with the simple name, the qualified owner (null at top level), and a privacy flag. Statement-context assignments (method and macro bodies, blocks, control flow, case subjects, macro regions) never create stubs: the element type's `shouldCreateStub` only admits file, class-body, lib-body, and visibility-modifier parents past the transparent statement wrapper. The by-owner index is keyed by the full qualified owner (`Foo`, `Foo::Bar`, `LibC`) so reopenings merge and nested owners never collide. Private file-level constants remain file-local; private type constants resolve and complete by bare name inside their owning namespace, including nested namespaces, cross-file reopenings, and exact superclass/include ancestry, only when the declaration belongs to the effective source set. Unrelated owners and explicit qualified accesses cannot expose private type constants. Qualified public `Foo::BAR` paths resolve through the owner index with the member-name filter; enum members stay out of scope (no member index for them).

Methods declared in a `record Name, ... do ... end` body carry the enclosing record's qualified name in their method stub. They are emitted to `CrystalMethodByClassIndex` under the record's simple name and are excluded from `CrystalTopLevelMethodIndex`, even though the record declaration itself remains a macro-call PSI element rather than a named type stub. Exact-identity filtering prefers the serialized qualified owner over PSI ancestor walks, so record-body methods survive index persistence; qualified identities (`record Registry::Entry`) and records nested in classes or modules keep their full identity, while a class nested inside a record body owns its methods itself.

## Index Gateway

Production code accesses stub indexes through the stateless `CrystalIndexService`, the typed production gateway for StubIndex access. The service exposes typed element lookup, streaming element processing, and key-processing methods; callers must always choose an explicit `GlobalSearchScope`. Completion, references, documentation, Parameter Info, and SDK-aware paths use all scope where library definitions are required. Inspections use project scope when diagnostics must be limited to project declarations. Navigation contributors use the scope supplied by IntelliJ.

Type, method, macro, alias, annotation, and library name processing passes the requested scope and `IdFilter` directly to `StubIndex.processAllKeys()`. IntelliJ may still enumerate project-wide keys for a narrow scope; these keys are cheap name candidates and do not materialize PSI elements. Name and element processing remain streaming and stop as soon as the supplied processor returns `false`.

Runtime project-wide `FileTypeIndex` scans, including iteration over every Crystal file, are prohibited. Runtime lookup must use the in-memory StubIndex gateway; narrower scopes and early processor termination are used wherever the required semantics permit them.

## Go To Contributors

Go to Class exposes indexed classes, modules, structs, enums, aliases, annotations, and libraries. Go to Symbol exposes those definitions plus indexed methods, macros, and constants. Both contributors cheaply enumerate project-wide name candidates; only navigation items resolved through `processElementsWithName()` are constrained by `FindSymbolParameters.searchScope`. Type navigation items retain their concrete class, module, struct, or enum kind and icon.

## Annotation References

Annotation-use type paths have a dedicated `CrystalAnnotationReference`. They
query `CrystalAnnotationIndex` in all scope and filter candidates through the
effective require/prelude source set. Simple names search lexical namespaces
from nearest to outermost, then global; qualified and absolute names require
their exact identity. Ordinary types never substitute for annotations. The same
reference supplies documentation targets. Compiler-created annotations are known
even without source declarations, but cannot navigate without an indexed target.

## DOT-Call Navigation

Bare type references use the shared type-resolution session's ordered lexical
identities: nearest enclosing namespace, then each outer namespace, then the
global identity. Candidates must belong to the effective require/prelude source
set. A same-simple-name type in an unrelated namespace is never a fallback.
Navigation and documentation use the same resolved declaration; reopenings of
the selected exact identity prefer the current file, then stable file/offset order.

`CrystalDotCallReference` delegates to the shared exact DOT-call target resolver. Transparent grouping therefore preserves static constants, qualified and absolute constant paths, locals, typed parameters, instance variables, and constructors through arbitrary parenthesis depth. `multiResolve()` returns every overload on the exact receiver identity, while `resolve()` returns a target only when that result is unique.

The Goto handler delegates to that DOT reference only when the clicked name lies
inside its reference range. A constant or local inside the argument list retains
its own reference and never navigates to the enclosing call's method.

An existing polyvariant DOT reference is authoritative even when exact resolution returns no targets. The Goto handler returns that empty result without attempting constructor or simple-name fallback, so incomplete, suppressed, unavailable, or ambiguous receivers cannot navigate to an unrelated same-simple-name type. Constructor fallback is limited to PSI shapes without an exact DOT reference and passes a proven complete receiver identity to the shared exact constructor resolver. Simple names retain lexical lookup, while qualified `A::Service` and absolute `::A::Service` paths retain their explicit identity; truncated or otherwise unprovable recovery PSI produces no target. The fallback also returns no target when separate files declare the same exact type identity and duplicate constructor signature, because exact hierarchy collection is incomplete rather than selecting one declaration arbitrarily. Same-simple-name declarations under different namespaces do not constitute this ambiguity; an unavailable top-level identity remains a separate no-target case. Class and struct navigation returns the combined explicit `self.new` and initializer-backed overload pool; records retain their exact-identity fallback and implicit construction applies only when that pool is empty. DOT calls inside `{{ … }}` or macro bodies resolve to nothing: their receivers are macro-runtime objects dispatching to the `Crystal::Macros` compiler API, and resolving them against runtime `def`s would navigate to false targets. Chained calls (`env.status(:not_found).json(...)`) navigate through the preceding call's single annotated return type when the inner call is applicable and unambiguous; anything else resolves to nothing.

## Bracket-Call Navigation

`CrystalBracketCallReference` resolves tight `[]` calls (`Foo[]`, `Foo[1]`, `obj[0]`)
to their `def []` targets through `CrystalBracketCallResolver`: constant roots resolve
in static mode (`def self.[]`), value receivers resolve through type inference in
instance mode (every inferred arm must yield a complete collection). Macro-backed
(`Int64[]`), unknown, and ambiguous receivers resolve to nothing and stay suppressed,
so no name-only fallback can navigate to an unrelated definition.

## Lib-Fun Navigation

`CrystalDotCallReference` resolves FFI calls (`LibC.exit(1)`) to their
`CrystalFunDefinition` declarations through the shared exact DOT-call target
resolver: only exact qualified library identities inside the effective require
closure navigate, and absent or ambiguous targets resolve to nothing. `multiResolve()`
returns every same-shape declaration on the exact library identity.

## FFI Type-Path Navigation

Ordinary `CrystalTypePath` nodes expose one PSI reference per constant segment.
JetBrains' standard Go to Declaration action (Ctrl+B / Ctrl+Click) therefore
resolves `LibGMP` and `MPF` in `@mpf : LibGMP::MPF` independently. Annotation
paths retain their dedicated annotation reference.
Expression paths expose polyvariant references on their leading constant and
each namespace segment as well. In `LibGMP::MpLimb.new!(num)`, Ctrl+B on
`LibGMP` targets the library declarations and Ctrl+B on `MpLimb` targets the
alias declarations, independently of the following DOT method's reference.
Both paths use `CrystalFfiTypeResolver` and the same visibility and multi-target
rules as type annotations.

Library prefixes are resolved through `CrystalLibIndex` in the effective
require/prelude source set. Simple library names use ordered lexical namespace
candidates; qualified and absolute library prefixes keep their exact identity.
Once a prefix identifies a library, only its indexed reopenings contribute
member declarations. The corresponding library bodies provide their direct
FFI structs, unions, enums, aliases, opaque types, and accessible constants;
the dedicated FFI struct/union/alias PSI forms are not ordinary type-index
entries. This is bounded inspection of the exact indexed libraries, not a
project-wide file scan. Bare member types within a library resolve against
that library as well.
The shared `CrystalFfiTypeResolver` also supplies exact FFI alias lookup for
DOT-receiver diagnostics. Aliases between compile-time conditional tags remain
declaration candidates in each branch; no platform branch is selected, and
navigation retains all candidates rather than arbitrarily choosing an alias.

Unknown, unrequired, macro-interpolated, or incomplete identities produce no
target. Type-path references are polyvariant: multiple exact library or member declarations
remain available through `multiResolve()` and JetBrains' normal target chooser,
while `resolve()` returns no arbitrary single declaration. This includes a
Crystal checkout and installed SDK both contributing the same library to the
effective source set. A missing member never navigates to a same-named declaration
from another library.
Merely indexing an SDK or checkout does not make it a candidate: the declaration
must occur in the effective require/prelude source set. Offering navigation
targets does not certify a compilable program; valid library reopenings and
conflicting duplicate FFI type definitions both retain their actual declaration
locations without selecting one arbitrarily.
Ordinary non-FFI paths retain the shared type session's lexical/exact lookup,
and constant-valued type arguments retain constant navigation.

Qualified PSI and stub identities include enclosing library namespaces.
Library-owned enum stubs are indexed under their library in the nested-type
index, and serialized owner identities include that namespace where applicable.
Stub version 22 invalidates persisted keys from the older library-skipping
identity rules. The platform `DefaultStubBuilder` remains authoritative.

## Completion Type Lookup

Type lookup searches project scope before all scope. When multiple indexed classes, modules, structs, or enums have the same name and a current file is supplied, the definition in that file takes precedence.

The all-scope fallback exists for library definitions. Its regression coverage requires a configured SDK/library root and is intentionally separate from the lightweight project-fixture coverage.

## Standard Library Indexing

`CrystalStdlibLibraryProvider` is the sole source of Crystal standard-library roots. It exposes one synthetic library through `AdditionalLibraryRootsProvider`; the library source roots are always produced by `CrystalStdlibRoots.enumerate()`. For the flat Crystal 1.20+ distribution, this includes top-level `.cr` files, user-facing stdlib directories, the compiler tree, and the `crystal/` runtime tree, while excluding `lib_c`, `lib_z`, `ll`, `llvm`, `gc`, and `samples`. The `crystal/` tree is not CLI tooling: it contains prelude dependencies such as `crystal/hasher.cr` (required by `hash.cr`), `crystal/main.cr`, and `crystal/system/thread.cr`.

The plugin must never persist or create a `Crystal StdLib` module library. Legacy persisted libraries are loaded into Workspace Model early enough to queue their roots before post-startup activities run, so cleanup alone cannot cancel an already queued broad `/usr/lib/crystal` indexing job.

`CrystalLegacyStdlibExcludePolicy` protects the first project open while a legacy module library still exists. It derives paths exclusively from every source root persisted on module-level libraries named exactly `Crystal StdLib`; current SDK settings do not participate because they may point to a different installation. For a broad flat distribution root, the policy excludes its known non-user-facing children `lib_c`, `lib_z`, `ll`, `llvm`, `gc`, and `samples`. If a persisted source root is itself one of those directories, the policy excludes that root directly. Compiler and `crystal/` runtime sources remain included. A `src` layout and already-filtered user-facing roots add no exclusions. Without an exact legacy library it excludes nothing, so opening the Crystal compiler source as normal project content is unaffected.

`CrystalLegacyStdlibCleanupActivity` removes exact-name `Crystal StdLib` module libraries after startup without creating replacements. Cleanup is idempotent and preserves all unrelated module libraries and order entries. Once cleanup completes, `CrystalStdlibLibraryProvider` remains the sole stdlib root source.

SDK changes and manual reindex requests notify the platform through `AdditionalLibraryRootsListener.fireAdditionalLibraryChanged()` under a write action. Both paths use the same Crystal-project detection and filtered root resolution as the synthetic provider; Force Re-index is a no-op outside Crystal projects. Manual refresh requests reindexing only for `.cr` files reachable from the already filtered synthetic-library roots. It checks the task's `ProgressIndicator` while recursively collecting files and immediately before every reindex request, so cancellation stops further files from being scheduled. Normal platform indexing populates StubIndex data; no pooled smart-mode waiter or eager top-level index warm-up is used.
