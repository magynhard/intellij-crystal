## ADDED Requirements

### Requirement: Parameter type annotation colon must have spaces on both sides

A warning SHALL be reported when a parameter type annotation `:` is missing a space before or after it.

#### Scenario: Missing space before colon
- **WHEN** a parameter is defined as `speed: String` (no space before `:`)
- **THEN** a warning SHALL be reported on the `:` token

#### Scenario: Missing space after colon
- **WHEN** a parameter is defined as `speed :String` (no space after `:`)
- **THEN** a warning SHALL be reported on the `:` token

#### Scenario: Missing space on both sides
- **WHEN** a parameter is defined as `speed:String` (no spaces at all)
- **THEN** a warning SHALL be reported on the `:` token

#### Scenario: Correct spacing
- **WHEN** a parameter is defined as `speed : String` (spaces on both sides)
- **THEN** no warning SHALL be reported

### Requirement: Return type annotation colon must have spaces on both sides

A warning SHALL be reported when a method return type annotation `:` is missing a space before or after it.

#### Scenario: Missing space before return type colon
- **WHEN** a method is defined as `def foo :Int32` (no space before `:`)
- **THEN** a warning SHALL be reported

#### Scenario: Missing space after return type colon
- **WHEN** a method is defined as `def foo(): String` with no space after `:`
- **THEN** a warning SHALL be reported

#### Scenario: Correct return type spacing
- **WHEN** a method is defined as `def foo() : String` (spaces on both sides)
- **THEN** no warning SHALL be reported

### Requirement: Default value colon is exempt from spacing rule

No warning SHALL be reported for `:` that appears after `=` in a default parameter value.

#### Scenario: Symbol default value without spaces
- **WHEN** a parameter is defined as `speed : String = :name` (symbol default after `=`)
- **THEN** no warning SHALL be reported for the `:` in `:name`

#### Scenario: Default value with spaces is also valid
- **WHEN** a parameter is defined as `speed : String = : name`
- **THEN** no warning SHALL be reported

### Requirement: Quick-fix inserts missing spaces

When a spacing warning is reported, a quick-fix SHALL be available to insert the missing space(s).

#### Scenario: Quick-fix for missing space before colon
- **WHEN** a parameter has `speed: String`
- **THEN** a quick-fix SHALL insert a space before `:` → `speed : String`

#### Scenario: Quick-fix for missing space after colon
- **WHEN** a parameter has `speed :String`
- **THEN** a quick-fix SHALL insert a space after `:` → `speed : String`
