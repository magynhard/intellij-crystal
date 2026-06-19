## Why

Crystal's style guide requires a space before and after `:` in parameter type annotations and return types. Missing spaces (e.g. `speed: String` instead of `speed : String`) are a common style mistake. An inspection that flags these makes it easy to catch and fix them automatically.

## What Changes

- New inspection `CrystalColonSpacingInspection` that reports warnings when:
  - A parameter type annotation is missing a space before or after `:`
  - A method return type annotation is missing a space before or after `:`
- Exception: no space required after `=` in default parameter values (e.g. `= :name` is valid)
- Quick-fix suggestions to insert the missing spaces

## Capabilities

### New Capabilities
- `colon-spacing`: Inspection for proper spacing around `:` in type annotations

### Modified Capabilities

## Impact

- New file: `CrystalColonSpacingInspection.kt` in inspections package
- `plugin.xml`: register the new inspection
- Tests: `CrystalColonSpacingInspectionTest.kt`
