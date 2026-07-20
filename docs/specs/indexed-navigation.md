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

Type, method, macro, alias, annotation, and library name processing accepts both the requested scope and `IdFilter`. Because the platform can enumerate keys that have no values in a narrow scope, the service verifies that each candidate key has an element in that scope before forwarding it. Element processing remains streaming and stops as soon as the supplied processor returns `false`.

Runtime project-wide `FileTypeIndex` scans, including iteration over every Crystal file, are prohibited. Runtime lookup must use the in-memory StubIndex gateway; narrower scopes and early processor termination are used wherever the required semantics permit them.

## Go To Contributors

Go to Class exposes indexed classes, modules, structs, enums, aliases, annotations, and libraries. Go to Symbol exposes those definitions plus indexed methods and macros. Both contributors process names and resolve navigation items in the requested search scope. Type navigation items retain their concrete class, module, struct, or enum kind and icon.

## Completion Type Lookup

Type lookup searches project scope before all scope. When multiple indexed classes, modules, structs, or enums have the same name and a current file is supplied, the definition in that file takes precedence.

The all-scope fallback exists for library definitions. Its regression coverage requires a configured SDK/library root and is intentionally separate from the lightweight project-fixture coverage.
