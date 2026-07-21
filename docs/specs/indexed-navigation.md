# Indexed Navigation

The plugin uses Crystal stub indexes for navigation and type lookup without scanning project files at runtime.

## Active Indexes

The active index set consists of exactly nine indexes:

- `CrystalClassIndex` indexes class, module, struct, and enum names.
- `CrystalMethodIndex` indexes method names.
- `CrystalMethodByClassIndex` maps enclosing type names to methods.
- `CrystalMacroIndex` indexes macro names.
- `CrystalTopLevelMethodIndex` indexes methods declared outside a type.
- `CrystalClassByEnclosingIndex` maps enclosing type names to nested types.
- `CrystalAliasIndex` indexes alias definitions.
- `CrystalAnnotationIndex` indexes annotation definitions.
- `CrystalLibIndex` indexes lib definitions.

Constant, instance-variable, and class-variable declaration indexes are intentionally absent. Constant indexing is deferred until the grammar distinguishes definition contexts from ordinary statement assignment. Variable indexing remains deferred unless a valid stubbed declaration model is designed.

## Index Gateway

Production code accesses stub indexes through the stateless `CrystalIndexService`, the typed production gateway for StubIndex access. The service exposes typed element lookup, streaming element processing, and key-processing methods; callers must always choose an explicit `GlobalSearchScope`. Completion, references, documentation, Parameter Info, and SDK-aware paths use all scope where library definitions are required. Inspections use project scope when diagnostics must be limited to project declarations. Navigation contributors use the scope supplied by IntelliJ.

Type, method, macro, alias, annotation, and library name processing passes the requested scope and `IdFilter` directly to `StubIndex.processAllKeys()`. IntelliJ may still enumerate project-wide keys for a narrow scope; these keys are cheap name candidates and do not materialize PSI elements. Name and element processing remain streaming and stop as soon as the supplied processor returns `false`.

Runtime project-wide `FileTypeIndex` scans, including iteration over every Crystal file, are prohibited. Runtime lookup must use the in-memory StubIndex gateway; narrower scopes and early processor termination are used wherever the required semantics permit them.

## Go To Contributors

Go to Class exposes indexed classes, modules, structs, enums, aliases, annotations, and libraries. Go to Symbol exposes those definitions plus indexed methods and macros. Both contributors cheaply enumerate project-wide name candidates; only navigation items resolved through `processElementsWithName()` are constrained by `FindSymbolParameters.searchScope`. Type navigation items retain their concrete class, module, struct, or enum kind and icon.

## Completion Type Lookup

Type lookup searches project scope before all scope. When multiple indexed classes, modules, structs, or enums have the same name and a current file is supplied, the definition in that file takes precedence.

The all-scope fallback exists for library definitions. Its regression coverage requires a configured SDK/library root and is intentionally separate from the lightweight project-fixture coverage.

## Standard Library Indexing

`CrystalStdlibLibraryProvider` is the sole source of Crystal standard-library roots. It exposes one synthetic library through `AdditionalLibraryRootsProvider`; the library source roots are always produced by `CrystalStdlibRoots.enumerate()`. For the flat Crystal 1.20+ distribution, this includes top-level `.cr` files and user-facing stdlib directories while excluding `compiler`, `crystal`, `lib_c`, `lib_z`, `ll`, `llvm`, `gc`, and `samples`.

The plugin must never persist or create a `Crystal StdLib` module library. Legacy persisted libraries are loaded into Workspace Model early enough to queue their roots before post-startup activities run, so cleanup alone cannot cancel an already queued broad `/usr/lib/crystal` indexing job.

`CrystalLegacyStdlibExcludePolicy` protects the first project open while a legacy module library still exists. It derives paths exclusively from every source root persisted on module-level libraries named exactly `Crystal StdLib`; current SDK settings do not participate because they may point to a different installation. For a broad flat distribution root, the policy excludes its known non-user-facing children `compiler`, `crystal`, `lib_c`, `lib_z`, `ll`, `llvm`, `gc`, and `samples`. If a persisted source root is itself one of those directories, the policy excludes that root directly. A `src` layout and already-filtered user-facing roots add no exclusions. Without an exact legacy library it excludes nothing, so opening the Crystal compiler source as normal project content is unaffected.

`CrystalLegacyStdlibCleanupActivity` removes exact-name `Crystal StdLib` module libraries after startup without creating replacements. Cleanup is idempotent and preserves all unrelated module libraries and order entries. Once cleanup completes, `CrystalStdlibLibraryProvider` remains the sole stdlib root source.

SDK changes and manual reindex requests notify the platform through `AdditionalLibraryRootsListener.fireAdditionalLibraryChanged()` under a write action. Both paths use the same Crystal-project detection and filtered root resolution as the synthetic provider; Force Re-index is a no-op outside Crystal projects. Manual refresh requests reindexing only for `.cr` files reachable from the already filtered synthetic-library roots. It checks the task's `ProgressIndicator` while recursively collecting files and immediately before every reindex request, so cancellation stops further files from being scheduled. Normal platform indexing populates StubIndex data; no pooled smart-mode waiter or eager top-level index warm-up is used.
