## MODIFIED Requirements

### Requirement: Bare argument parsing correctly handles binary expressions

The `bare_argument` grammar rule SHALL parse binary expressions like `x * scalar` as a single positional argument, not consume the IDENTIFIER greedily via the optional `[IDENTIFIER COLON]` prefix.

#### Scenario: Bare DOT-call with binary multiplication in arguments

- **WHEN** a bare DOT-call has binary multiplication arguments like `Vector2D.new(x * scalar, y * scalar)`
- **THEN** the parser SHALL create two separate arguments: `x * scalar` and `y * scalar`

#### Scenario: Bare DOT-call with named arguments

- **WHEN** a bare DOT-call has named arguments like `Config.new host: "localhost", port: 8080`
- **THEN** the parser SHALL correctly parse `host: "localhost"` and `port: 8080` as named arguments

#### Scenario: Bare call with splat argument

- **WHEN** a bare call has a splat argument like `add *args`
- **THEN** the parser SHALL parse `*args` as a splat argument (STAR applied to `args`)

#### Scenario: Bare call with double-splat argument

- **WHEN** a bare call has a double-splat argument like `configure **opts`
- **THEN** the parser SHALL parse `**opts` as a double-splat argument

#### Scenario: Bare DOT-call with positional literals

- **WHEN** a bare DOT-call has positional literal arguments like `Vector2D.new(1.0, 2.0)`
- **THEN** the parser SHALL parse `1.0` and `2.0` as two separate positional arguments
