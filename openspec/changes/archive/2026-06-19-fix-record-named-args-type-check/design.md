## Context

The IntelliJ Crystal plugin supports type checking and argument count validation for Crystal's `record` macro. When a user writes `Config.new(host: "localhost", port: 8080)`, the inspections validate named arguments against the record's field definitions.

Currently, this works correctly for **parenthesized calls** (`Config.new(host: ...)`) but fails for **bare DOT-calls** (`Config.new host: ...`). In the bare case, all named arguments are reported as "Unknown named argument" — even valid ones.

The root cause: both inspections use `detectDotCall()` to identify DOT-call patterns, then call `findRecordParameters()` / `extractRecordParamInfo()` to look up the record definition. These lookup functions search for `CrystalMethodCallExpression` nodes with method name `"record"`. However, the DOT-call detection and argument extraction path may not correctly propagate the class name to the record lookup, or the record lookup may fail to find the definition when called from the DOT-call context.

## Goals / Non-Goals

**Goals:**
- Record constructor argument validation (type check + argument count) SHALL work for bare DOT-calls
- Existing parenthesized call behavior SHALL remain unchanged
- Test coverage for both parenthesized and bare DOT-calls on record types

**Non-Goals:**
- Record definitions in other files (file-local scope only — existing limitation)
- Performance optimization of record parameter lookup
- Supporting record-like macros beyond the built-in `record`

## Decisions

### Fix both inspections in parallel

Both `CrystalTypeCheckInspection` and `CrystalArgumentCountInspection` have independent record support code. The fix must address both, since they have separate `extractRecordParamInfo` / `findRecordParameters` methods and separate `checkDotCall` paths.

**Why not refactor into shared code?** The inspections have different data models (`RecordParamInfo` vs `ParamInfo`) and different validation logic. A shared utility would add complexity without clear benefit for this bug fix. Refactoring can be a separate concern.

### Add bare DOT-call test cases to existing test classes

Rather than creating new test files, add test methods to `CrystalTypeCheckInspectionTest` and `CrystalArgumentCountInspectionTest`. This follows the existing pattern and keeps record-related tests together.

## Risks / Trade-offs

- **[Risk]** The fix may reveal other DOT-call edge cases with records → **Mitigation**: Test with mixed named/positional args, default values, and unknown args
- **[Risk]** Lexer whitespace between IDENTIFIER and COLON in bare arguments could affect named label extraction → **Mitigation**: Verify parser produces IDENTIFIER as direct child of BARE_ARGUMENT (confirmed in parser test data)
