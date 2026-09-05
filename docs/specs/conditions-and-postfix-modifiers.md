# Conditions and Postfix Modifiers Spec

## Overview

Crystal allows assignments to be used as condition expressions — most commonly nil-check
patterns like `if user = users.first?` or `unless t = hash[key]?`. The grammar must accept
assignments in every condition position where Crystal accepts them.

## Condition Positions

### Block-level conditions (`condition`)

`if`, `unless`, `while`, `until`, and `elsif` heads use:

```bnf
condition ::= condition_assignment | expression
private condition_assignment ::= variable ASSIGN NLS expression
```

`variable` covers identifiers, instance variables, class variables, and global variables.
PEG order matters: `condition_assignment` is tried first; it fails fast when no `ASSIGN`
follows the variable, so plain conditions are unaffected.

### Postfix modifiers (`postfix_modifier`)

Postfix `if` / `unless` / `while` / `until` / `rescue` accept assignments exactly like
block-level conditions:

```crystal
return [] of Result unless target = PAIRS[node.name]?
puts "found" if v = cache[key]?
sleep 1 until done = finished?
value = strict_parse rescue fallback = DEFAULTS[:fallback]
```

```bnf
postfix_modifier ::= (IF | UNLESS | WHILE | UNTIL | RESCUE) postfix_condition_with_assignment

private postfix_condition_with_assignment ::= postfix_condition_assignment | expression
postfix_condition_assignment ::= variable ASSIGN NLS expression
```

This applies wherever `[postfix_modifier]` is referenced, including
`expression_statement`, ordinary and indexed assignments, constant assignments,
`return_statement`, `break_statement`, `next_statement`, `yield_statement`,
`macro_interpolation`, and `interpolation_expression`.

**PSI shape:** `postfix_condition_with_assignment` remains private, but an assigned guard creates a
`CrystalPostfixConditionAssignment` composite implementing `CrystalAssignment`. Analyses can
therefore process its write exactly like another assignment without reconstructing it from
flat siblings. Plain expression guards retain their existing PSI shape.

Indexed assignments own their trailing modifier:

```bnf
indexed_assignment ::= assignment_target (LBRACKET argument_list RBRACKET)+
                       assign_op NLS (nested_assignment | expression)
                       [postfix_modifier]
```

`nested_assignment` permits a nested RHS assignment without letting it consume the outer
modifier. This keeps `values[index] = nested = replacement if enabled` as one conditional
indexed assignment rather than an unconditional indexed write whose nested value is
conditional. Compound indexed writes evaluate receiver/index expressions, the implicit
getter, the RHS, the operator, and the setter in source order. `||=` and `&&=` retain the
path that skips the RHS; `rescue` starts from failures at any evaluated phase.
Simple indexed assignments evaluate to their RHS value. A conditional postfix modifier adds
the skipped `Nil` value, while postfix `rescue` adds the handler value. Compound indexed
assignment values remain unknown until exact operator/getter resolution is available.

### In-clause guards (`in_clause`)

```bnf
in_clause ::= IN expression_list [IF condition] then_clause statement_list
```

Guards were introduced speculatively with pin operators (`in ^x if cond`). As of Crystal
1.21 the compiler rejects *any* guard after `in` (`unexpected token: "if"`), so this rule
is intentionally more tolerant than the language: the plugin parses guards (plain and
assignment forms) so that macro-generated or future-compatible code does not produce false
errors. Do not rely on guards appearing in valid Crystal 1.x sources.

Crystal 1.21 also rejects trailing `while` and `until` on indexed assignments. The plugin's
historical generic modifier rule still accepts them; narrowing that rule without regressing
other assignment forms is tracked separately in `TODO.md`.

## Verified Against the Compiler

```console
$ crystal eval 'def f(h)
  return [] of Int32 unless t = h["k"]?
  t
end
p f({"k" => 3})'
3
```

## Test Coverage

`src/test/testData/parser/PostfixModifierAssignment.cr` (golden-file parser test) covers:

- Assignment in postfix `unless` on a typed return (`return [] of T unless t = …`)
- Assignment in postfix `if` / `while` / `until` / `rescue`
- Block-level `if` / `unless` assignment conditions (regression guard)
- Assignment in an in-clause guard
- Indexed simple/compound writes with `if`, `unless`, and `rescue`, nested RHS assignments,
  repeated indexes, and `||=`/`&&=` short-circuit operators
- Missing conditions and chained modifiers as invalid boundaries that preserve a following
  declaration
