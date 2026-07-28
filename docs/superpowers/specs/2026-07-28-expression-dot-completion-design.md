# Expression DOT Completion Design

## Goal

DOT completion must derive candidates from the complete receiver expression rather than only the leaf immediately before the dot. Equivalent expressions must produce equivalent completion results regardless of transparent parentheses.

Examples:

```crystal
Foo.
(Foo).
((Foo)).

3.
(3).
"lol".
("lol").
```

`Foo` forms offer static methods and constructor completion. Runtime expressions offer instance methods for their resolved value types.

## Scope

Completion supports every receiver expression whose type can be determined from PSI without invoking the Crystal compiler. This includes:

- Class, struct, module, record, qualified, and absolute constant receivers.
- Arbitrarily nested transparent grouped expressions.
- Local variables, typed parameters, and instance variables.
- Numeric, string, character, symbol, boolean, nil, regex, command, and heredoc expressions.
- Arrays, hashes, tuples, and explicitly typed collection literals.
- Method-call results with declared or inferable return types.
- Operators and control-flow expressions supported by `CrystalExpressionTypeResolver`.
- Generic value types, normalized to their indexed base type for method lookup.
- Union results from conditionals and other expressions.

Unknown branches do not create name-only guesses. Completion combines candidates from every type that is actually resolved.

## Receiver Model

Receiver analysis returns one of three results:

1. `TypeObject` identifies one exact constant type and requests static completion. Classes and structs may offer `new`; modules do not. Existing record constructor behavior remains unchanged.
2. `ValueTypes` contains one or more resolved runtime value types and requests instance-method completion.
3. `Unknown` produces no receiver-specific candidates.

Constant recognition runs before general expression inference so `Foo` remains a type object rather than a runtime value. Qualified and absolute identities retain their full written path and lexical identity rules.

## Expression Location

Completion locates the PSI expression immediately left of the member-access dot. It must work with incomplete completion PSI and must not rely solely on the previous leaf's text.

Transparent grouping recursively unwraps only one complete expression. Parentheses containing assignments, comma-separated expressions, or incomplete/composite content are not treated as transparent shortcuts. The inner expression may itself be any supported expression, including a qualified constant or literal.

Direct numeric completion such as `3.<caret>` is supported. The lexer already requires digits after the decimal point for a floating-point token, so an integer followed by a trailing dot can be treated as member access. Completed floats such as `3.14` remain numeric literals rather than DOT contexts.

## Type Resolution

The completion receiver analyzer reuses PSI-based expression type resolution instead of adding syntax-specific completion heuristics.

Resolved type text is converted into a structured set of lookup types:

- Top-level unions are split without splitting unions nested inside generic arguments.
- Generic types such as `Array(Int32)` normalize to the indexed base type `Array` for method lookup.
- Qualified type identities remain qualified until exact index filtering is complete.
- Duplicate types are removed while preserving deterministic order.

For a union such as `Foo | Bar`, completion returns the union of methods available on either type, as explicitly selected. Identical method signatures are shown once; distinct overloads remain separate.

## Candidate Collection

Static and instance candidate collection remain separate:

- `TypeObject(Foo)` uses existing static method and constructor presentation rules.
- `ValueTypes(Int32)` uses instance methods from `Int32` and its supported hierarchy.
- `ValueTypes(Foo, Bar)` merges instance methods from both hierarchies.

Candidate deduplication uses method name plus callable parameter signature. Existing lookup rendering, priority, icons, constructor tails, and record presentation are preserved.

Once a DOT receiver is recognized, completion must not fall through to unrelated free-text project methods. An unresolved receiver yields no receiver-specific result rather than name-only noise.

## Architecture

Add a focused completion receiver analyzer with a small result model. It owns:

- Finding the receiver PSI for an incomplete DOT completion site.
- Recognizing exact type-object receivers.
- Delegating runtime expressions to PSI type resolution.
- Expanding top-level unions and normalizing generic base types.

Move reusable transparent receiver normalization into a neutral PSI/analysis helper rather than making completion depend on inspection implementation details. Call inspections and completion remain separate consumers but share receiver normalization semantics.

Extend `CrystalCompletionHelper` with multi-type instance candidate collection so union merging and signature deduplication are implemented once.

## Safety

- No `FileTypeIndex` scans or project-wide file iteration.
- Runtime project lookups remain StubIndex-backed.
- Unknown, malformed, or unresolvable expressions do not trigger broad method-name fallback.
- Static methods and constructors never enter runtime-value completion.
- Instance methods never enter type-object completion unless existing Crystal module exposure semantics explicitly provide them.
- Macro-interpolated or otherwise incomplete receivers remain conservative.

## Verification

Platform completion tests must compare grouped and ungrouped lookup sets and cover:

- One, two, and deeper levels of parentheses.
- Class, struct, module, record, qualified, and absolute type objects.
- Constructor presence, absence, and parameter presentation.
- Direct and grouped integer, float, string, character, symbol, boolean, regex, command, and heredoc values.
- Locals, typed parameters, and instance variables.
- Arrays, hashes, tuples, operators, and method-call results.
- `if`, `case`, and ternary union results, including methods unique to each branch.
- Generic base-type normalization.
- Negative malformed, unknown, macro-interpolated, and composite grouping cases.
- No free-text fallback after a recognized but unresolved DOT receiver.

Focused completion and type-resolution suites run before the full project test suite.
