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
postfix_modifier ::= (IF | UNLESS | WHILE | UNTIL | RESCUE) condition_with_assignment

private condition_with_assignment ::= condition_assignment | expression
```

This applies wherever `[postfix_modifier]` is referenced: `expression_statement`,
`assignment`, `constant_assignment`, `return_statement`, `break_statement`,
`next_statement`, `yield_statement`, `macro_interpolation`, and
`interpolation_expression`.

**PSI shape:** Both helper rules are private, so no extra composite element is created.
An assigned postfix condition yields flat children under `POSTFIX_MODIFIER`
(`variable` token(s), `ASSIGN`, `EXPRESSION`) — mirroring how block-level
`condition_assignment` flattens into the tree. Existing trees without assignments are
unchanged byte-for-byte.

### In-clause guards (`in_clause`)

```bnf
in_clause ::= IN expression_list [IF condition_with_assignment] then_clause statement_list
```

Guards were introduced speculatively with pin operators (`in ^x if cond`). As of Crystal
1.21 the compiler rejects *any* guard after `in` (`unexpected token: "if"`), so this rule
is intentionally more tolerant than the language: the plugin parses guards (plain and
assignment forms) so that macro-generated or future-compatible code does not produce false
errors. Do not rely on guards appearing in valid Crystal 1.x sources.

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
