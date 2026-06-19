## 1. Inspection Implementation

- [x] 1.1 Create `CrystalColonSpacingInspection.kt` — walk `CrystalMethodDefinition` parameters and return type, check COLON tokens for surrounding whitespace
- [x] 1.2 Add quick-fix to insert missing space before/after `:`
- [x] 1.3 Add exception for `=` default values (skip `:` after `=`)

## 2. Registration

- [x] 2.1 Register inspection in `plugin.xml`

## 3. Tests

- [x] 3.1 Add tests: missing space before/after `:` in parameter type, return type, default value exemption
- [x] 3.2 Add quick-fix tests
- [x] 3.3 Run `./gradlew test` — verify no regressions
