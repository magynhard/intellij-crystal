# Indexed Navigation

The plugin uses Crystal stub indexes for navigation and type lookup without scanning project files at runtime.

## Index Gateway

Production code accesses stub indexes through the stateless `CrystalIndexService`. The service owns the index keys and exposes typed lookup and key-processing methods; callers must always supply a `GlobalSearchScope` for element lookups. Completion, references, documentation, Parameter Info, and SDK-aware paths use all scope where library definitions are required, while inspections retain project scope.

Type, method, macro, alias, annotation, and library name processing accepts both the requested scope and `IdFilter`. Because the platform can enumerate keys that have no values in a narrow scope, the service verifies that each candidate key has an element in that scope before forwarding it. Element processing remains streaming and stops as soon as the supplied processor returns `false`.

## Go To Contributors

Go to Class exposes indexed classes, modules, structs, enums, aliases, annotations, and libraries. Go to Symbol exposes those definitions plus indexed methods and macros. Both contributors process names and resolve navigation items in the requested search scope. Type navigation items retain their concrete class, module, struct, or enum kind and icon.

## Completion Type Lookup

Type lookup searches project scope before all scope. When multiple indexed classes, modules, structs, or enums have the same name and a current file is supplied, the definition in that file takes precedence.

The all-scope fallback exists for library definitions. Its regression coverage requires a configured SDK/library root and is intentionally separate from the lightweight project-fixture coverage.
