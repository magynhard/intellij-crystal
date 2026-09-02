# Unused Local Assignments

Behavioral specification for `CrystalUnusedVariableInspection` and its local
definition-use analysis.

## Diagnostic Contract

The inspection reports a plain local-variable assignment when that concrete
assigned value cannot reach any read on a possible execution path.

- A binding with one assignment reports `Variable '<name>' is never used`.
- A dead definition of a binding assigned more than once reports
  `Value assigned to '<name>' is never used`.
- Assignments whose names start with `_` are intentionally unused and are not
  reported.
- Instance variables, class variables, constants, parameters, and destructuring
  targets are outside this inspection's scope.
- Compound assignments read the previous value before writing the new value;
  the synthetic write itself is not reportable as a plain unused assignment.
- Parenthesized simple assignments such as `(value = compute)` create the same
  binding definition as statement assignments, including inside ternaries.

## Binding Identity

Names alone do not identify local variables. Each method, type body, file, and
block has a lexical frame. A block parameter shadows an outer binding, while
an assignment in a block updates an outer binding only when that binding is
already visible at the block's source position. A new block-local binding does
not escape the block.

Method, macro, class, module, struct, enum, and file boundaries never share
local bindings.

Rescue variables create clause-local bindings and shadow same-named outer
locals. Pattern bindings in `case ... in` remain deferred as documented in
`TODO.md`.

## Definition-Use Analysis

Each reportable assignment creates a distinct definition. The analyzer carries
the set of definitions that may reach each binding through the PSI control-flow
structure. A read marks every reaching definition as used.

- Sequential writes replace the previous reaching definition.
- `if`, `unless`, `case`, ternaries, short-circuit operators, and `select` merge possible branches, including
  the path where no branch executes when there is no exhaustive fallback.
- `while`, `until`, `for`, and call blocks iterate to a fixed point, so a write
  from one iteration can reach a read in the next iteration.
- `return`, `break`, and `next`, including postfix forms and assignment-valued
  forms, terminate the corresponding path after evaluating their value.
- Rescue handlers conservatively receive every definition that could have been
  written before an exception in the protected body.
- `else` on a protected block runs only after normal body completion.
- `ensure` runs for normal, returning, breaking, and continuing paths; abrupt
  control flow originating in the ensure body supersedes the protected path.
- An unreachable definition is not reported because no assignment happened at
  runtime.

## Blocks And Generated Code

Call blocks can execute zero, one, or multiple times and may execute after
surrounding assignments. Reads captured from an outer frame therefore keep the
definitions reaching the capture and later definitions that a deferred callback
could observe live. Definitions overwritten before the capture remain eligible
for a warning. This intentionally prefers a missed warning over a false warning
when callback execution timing is unknown.

A visible project macro or ECR-style macro call may read local bindings through
generated code. Every definition visible at that call is conservatively marked
used. Ordinary method calls do not receive this exemption.

Injected Crystal fragments for which the IntelliJ injection manager requests
lenient inspections are skipped. These fragments may be syntactically partial
or lack the host's bindings; inspecting them independently would produce
non-actionable warnings in Markdown code fences and similar hosts.

## Read Forms

Reads include direct references and all PSI forms that resolve to a local
binding, including:

- call arguments and string interpolation;
- DOT-call receivers such as `server.listen`;
- nil checks and abbreviated-proc receivers such as `value.try(&.to_s)`;
- index receivers and postfix conditions;
- the implicit read of `+=`, `||=`, and other compound assignments.

A method, member, named-argument label, or namespace identifier with the same
text is not a local read unless local-reference resolution binds it to that
local declaration.

## Examples

```crystal
x = 1      # Value assigned to 'x' is never used
x = 2
puts x
```

```crystal
x = "fallback"
x = compute if enabled
puts x                         # Both definitions may reach this read
```

```crystal
current = handlers.first
handlers.each do |handler|
  current.next = handler       # Reads the previous iteration's definition
  current = handler
end
```

```crystal
value = compute
begin
  return
ensure
  puts value                   # The ensure path reads value before returning
end
```
