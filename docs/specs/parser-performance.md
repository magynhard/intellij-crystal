# Parser Performance

## Expression Prefixes

The expression grammar must parse an `or_expression` prefix only once. A trailing `QUESTION` optionally introduces either the postfix question form or a full ternary expression. The ternary branches remain recursive `expression` rules so nested ternaries associate to the right.

The same constraint applies to `bare_expression`, which uses `bare_or_expression` for its initial prefix but normal expressions for complete ternary branches.

These forms must remain accepted:

- ordinary expressions
- postfix `?`
- complete and right-associative ternaries

## Range Prefixes

Omitted-start ranges begin with `..` or `...` and remain the first PEG alternative. All other ranges parse their left bitwise-expression once, followed by an optional range operator and optional right endpoint.

The normal and bare-expression rule families must both support inclusive and exclusive ranges with omitted starts, omitted ends, or both endpoints present. Bare ranges must remain valid in parenthesis-free call arguments.

## Regression Model

Nested Crystal Spec DSL calls amplify parser backtracking because a bare method call can own a `do ... end` block containing another call. The synthetic regression uses nested `context`, `it`, `configure`, and `parse` calls with dotted assertions. At depth nine, parsing must complete within a generous ten-second parser-only limit and produce no `PsiErrorElement` instances.

The fixture is synthetic and self-contained; tests do not read application projects or third-party source files at runtime.
