## Why

When calling `Config.new host: "lol", port: "ol", ssl: 123` (bare DOT-call with named arguments on a record type), ALL named arguments are incorrectly reported as "Unknown named argument" — including valid ones like `host`, `port`, `ssl`. Only truly unknown arguments like `cool: 33` should trigger this error. The existing tests only cover parenthesized calls (`Config.new(host: "localhost")`), leaving bare DOT-calls untested and broken.

## What Changes

- Fix `CrystalArgumentCountInspection` to correctly resolve record parameters for bare DOT-calls (`Config.new host: "lol", ...`)
- Fix `CrystalTypeCheckInspection` to correctly type-check arguments for bare DOT-calls on record types
- Add test coverage for bare DOT-calls with named arguments on record types

## Capabilities

### New Capabilities

_None — this is a bug fix._

### Modified Capabilities

- `record-type-checking`: Record constructor argument validation now works for bare DOT-calls (without parentheses), not just parenthesized calls.

## Impact

- `CrystalArgumentCountInspection.kt` — argument count/unknown named argument checks for records
- `CrystalTypeCheckInspection.kt` — type mismatch checks for record constructor calls
- Test files: `CrystalArgumentCountInspectionTest.kt`, `CrystalTypeCheckInspectionTest.kt`
