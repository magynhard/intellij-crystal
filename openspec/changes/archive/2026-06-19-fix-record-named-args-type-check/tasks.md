## 1. Reproduce & Diagnose

- [x] 1.1 Write failing test in `CrystalArgumentCountInspectionTest`: bare DOT-call `Config.new host: "localhost", port: 8080, ssl: true` on record — should produce no "Unknown named argument" errors
- [x] 1.2 Write failing test in `CrystalTypeCheckInspectionTest`: bare DOT-call `Config.new host: 123` on record — should produce type mismatch error
- [x] 1.3 Run tests to confirm they fail, diagnosing root cause in `detectDotCall` / `findRecordParameters` / argument extraction path

## 2. Fix Argument Count Inspection

- [x] 2.1 Fix `CrystalArgumentCountInspection.checkDotCall()` to correctly resolve record parameters for bare DOT-calls with named arguments
- [x] 2.2 Verify existing parenthesized record tests still pass (no regression)

## 3. Fix Type Check Inspection

- [x] 3.1 Fix `CrystalTypeCheckInspection.checkDotCall()` to correctly type-check bare DOT-call arguments against record parameters
- [x] 3.2 Verify existing parenthesized record tests still pass (no regression)

## 4. Additional Test Coverage

- [x] 4.1 Add test: bare DOT-call with unknown named argument (should error on unknown only)
- [x] 4.2 Add test: bare DOT-call with missing required argument
- [x] 4.3 Add test: bare DOT-call with positional arguments on record
- [x] 4.4 Run full test suite: `./gradlew test`
