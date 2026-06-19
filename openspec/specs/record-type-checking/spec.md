## MODIFIED Requirements

### Requirement: Record constructor named argument validation

The type checker and argument count inspections SHALL correctly validate named arguments when calling `RecordType.new` via bare DOT-calls (without parentheses), matching the behavior already implemented for parenthesized calls.

#### Scenario: Bare DOT-call with valid named arguments

- **WHEN** a file contains `record Config, host : String, port : Int32 = 80` and `Config.new host: "localhost", port: 8080`
- **THEN** no "Unknown named argument" errors SHALL be reported for `host` or `port`

#### Scenario: Bare DOT-call with type mismatch

- **WHEN** a file contains `record Config, host : String, port : Int32 = 80` and `Config.new host: 123, port: 8080`
- **THEN** a type mismatch error SHALL be reported on `123` (expected `String`, got `Int32`)

#### Scenario: Bare DOT-call with unknown named argument

- **WHEN** a file contains `record Config, host : String, port : Int32 = 80` and `Config.new host: "localhost", port: 8080, unknown: true`
- **THEN** an "Unknown named argument 'unknown'" error SHALL be reported

#### Scenario: Bare DOT-call with missing required argument

- **WHEN** a file contains `record Config, host : String, port : Int32 = 80` and `Config.new port: 8080`
- **THEN** a "Missing required argument(s): 'host'" error SHALL be reported

#### Scenario: Bare DOT-call with positional arguments

- **WHEN** a file contains `record Config, host : String, port : Int32` and `Config.new "localhost", 8080`
- **THEN** no errors SHALL be reported (positional args match by index)

#### Scenario: Parenthesized call behavior unchanged

- **WHEN** a file contains `record Config, host : String, port : Int32 = 80` and `Config.new(host: "localhost", port: 8080)`
- **THEN** behavior SHALL remain identical to current implementation (no regression)
