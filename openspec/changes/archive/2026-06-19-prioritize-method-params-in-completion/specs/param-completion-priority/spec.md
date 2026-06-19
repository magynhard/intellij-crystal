## ADDED Requirements

### Requirement: Parameters from enclosing method get top priority in completion

When the cursor is inside a method body and free-text completion is triggered, parameters from the enclosing `def` SHALL appear at the top of the completion list with higher priority than keywords, stdlib types, classes, and methods.

#### Scenario: Parameter appears above methods in completion
- **WHEN** cursor is on a new line inside `def foo(bar : String, baz : Int32)` and user types `b`
- **THEN** `bar` and `baz` SHALL appear above `break`, `begin`, and any project-wide methods starting with `b`

#### Scenario: Parameters with @param shorthand get top priority
- **WHEN** cursor is inside `def initialize(@cool : String, other : Int32)` and user types `c`
- **THEN** `cool` SHALL appear at the top of the completion list (without `@` prefix)

#### Scenario: No boost outside method body
- **WHEN** cursor is at top level (not inside any `def`) and user types a letter
- **THEN** no priority boost SHALL be applied — normal completion ordering

### Requirement: Local variables get medium priority in completion

Local variables defined before the cursor SHALL get a priority boost above project-wide methods but below parameters.

#### Scenario: Local variable appears above methods but below parameters
- **WHEN** cursor is inside `def foo(bar : String)` with local `my_var = 1` above cursor, and user types `m`
- **THEN** `my_var` SHALL appear above project-wide methods starting with `m`, but below parameters starting with `m` (if any)

#### Scenario: Local variable defined after cursor not boosted
- **WHEN** cursor is inside a method with `my_var = 1` defined BELOW the cursor position
- **THEN** `my_var` SHALL NOT get a priority boost (only variables defined before the cursor are boosted)
