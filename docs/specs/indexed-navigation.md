# Indexed Navigation

The plugin uses Crystal stub indexes for navigation and type lookup without scanning project files at runtime.

## Go To Contributors

Go to Class exposes indexed classes, modules, structs, enums, aliases, annotations, and libraries. Go to Symbol exposes those definitions plus indexed methods and macros. Both contributors resolve their displayed names to navigation items in the requested search scope.

## Completion Type Lookup

Type lookup searches project scope before all scope. When multiple indexed classes, modules, structs, or enums have the same name and a current file is supplied, the definition in that file takes precedence.

The all-scope fallback exists for library definitions. Its regression coverage requires a configured SDK/library root and is intentionally separate from the lightweight project-fixture coverage.
