# Type Inference

Specs to define the behaviour of type inference across the Crystal plugin.

## Overview

The plugin has two type resolution systems:

1. **`CrystalTypeInference`** (`completion/CrystalTypeInference.kt`) — Resolves the type of
   a **variable** given its name and context. Used by code completion and Go to Definition.
2. **`CrystalExpressionTypeResolver`** (`inspections/CrystalExpressionTypeResolver.kt`) —
   Resolves the type of **any expression** PSI element. Used by the type-check inspection.
   Delegates variable resolution to `CrystalTypeInference`.

Both systems return a type name string (or `ResolvedType` wrapper). When a type cannot be
determined, `null` is returned — callers treat this as "unknown type" and fall back
(all methods shown for completion, no type-check applied for inspections).

---

## Consumers

| Consumer | System Used | Purpose |
|---|---|---|
| `CrystalCompletionContributor` | `CrystalTypeInference` | Dot-completion on `variable.<caret>` |
| `CrystalDotCallReference` | `CrystalTypeInference` | Go to Definition for DOT-call on variables |
| `CrystalTypeCheckInspection` | `CrystalExpressionTypeResolver` | Validates argument types against parameter annotations |
| Inlay Hints (future) | `CrystalTypeInference` | Show inferred types inline (Issue #2) |

---

## Priority Tier 1 — Scalar Literal Assignments

**Affects:** `CrystalTypeInference.inferTypeFromExpression`
**Complexity:** Trivial

Extend `inferTypeFromExpression` to recognize literal expressions as the RHS of an
assignment. Currently only handles `Class.new`, `Class.method`, and bare `method_call`.

| Crystal Code | Inferred Type | Notes |
|---|---|---|
| `x = 1` | `Int32` | Unsuffixed integer defaults to Int32 |
| `x = 1_i64` | `Int64` | Suffixed — parse suffix |
| `x = 1.0` | `Float64` | Unsuffixed float defaults to Float64 |
| `x = 1_f32` | `Float32` | Suffixed — parse suffix |
| `x = "hello"` | `String` | Always String |
| `x = 'a'` | `Char` | Always Char |
| `x = :foo` | `Symbol` | Always Symbol |
| `x = true` | `Bool` | Always Bool |
| `x = false` | `Bool` | Always Bool |
| `x = nil` | `Nil` | Always Nil |

### Suffix Parsing Rules

Integer suffixes: `i8`, `i16`, `i32`, `i64`, `i128`, `u8`, `u16`, `u32`, `u64`, `u128`.
Float suffixes: `f32`, `f64`. Underscores in literals are ignored for suffix detection
(e.g. `1_000_i64` → `Int64`).

### Priority Tier 2 — Trivial Expression Types

**Affects:** `CrystalExpressionTypeResolver`
**Complexity:** Trivial

These expressions always resolve to a fixed type regardless of context.

| Expression PSI Class | Inferred Type | Crystal Example |
|---|---|---|
| `CrystalRegexExpression` | `Regex` | `/pattern/` |
| `CrystalCommandExpression` | `String` | `` `ls` `` |
| `CrystalHeredocLiteral` | `String` | `<<-HEREDOC ... HEREDOC` |
| `CrystalSymbolStringExpression` | `Symbol` | `:"foo"` |
| `CrystalSizeofExpression` | `UInt64` | `sizeof(Int32)` |
| `CrystalInstanceSizeofExpression` | `UInt64` | `instance_sizeof(Foo)` |
| `CrystalOffsetofExpression` | `UInt64` | `offsetof(Foo, @x)` |

### Percent Literals

| Syntax | Inferred Type | Crystal Example |
|---|---|---|
| `%w(...)` | `Array(String)` | `%w(a b c)` |
| `%i(...)` | `Array(Symbol)` | `%i(a b c)` |
| `%q(...)` / `%Q(...)` | `String` | `%q(hello)` |
| `%r(...)` | `Regex` | `%r(foo)` |
| `%b(...)` | `Bytes` | `%b(0x00)` |

---

## Priority Tier 3 — Collection Literals

**Affects:** `CrystalExpressionTypeResolver` + `CrystalTypeInference`
**Complexity:** Medium

### Array Literals

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `[1, 2, 3]` | `Array(Int32)` | Infer element types, all same → concrete type |
| `[1, "hi"]` | `Array(Int32 \| String)` | Union of differing element types |
| `[] of Int32` | `Array(Int32)` | Explicit `of Type` annotation |
| `[1] of String` | Error (static) | Type mismatch — not inferable, skip |

Resolution strategy:
1. Check for `of Type` annotation → use annotated type.
2. Otherwise, resolve each element's type and unify.
3. If all elements resolve to the same type `T`, return `Array(T)`.
4. If elements differ, return `Array(T1 | T2 | ...)`.
5. If any element cannot be resolved, return `null` (unknown).

### Hash Literals

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `{a: 1}` | `Hash(Symbol, Int32)` | Symbol key shorthand |
| `{"a" => 1}` | `Hash(String, Int32)` | Explicit key/value |
| `{} of String => Int32` | `Hash(String, Int32)` | Explicit `of K => V` |
| `{a: 1, "b" => 2}` | `Hash(Symbol \| String, Int32)` | Union of key types |

Resolution strategy:
1. Check for `of K => V` annotation → use annotated types.
2. Otherwise, resolve key types and value types separately, unify each.
3. If both key and value resolve, return `Hash(K, V)`.
4. If either cannot be resolved, return `null`.

### Tuple Literals

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `{1, "hi"}` | `Tuple(Int32, String)` | Compound of element types |
| `{1}` | `Tuple(Int32)` | Single-element tuple |
| `{1, 2, 3}` | `Tuple(Int32, Int32, Int32)` | All element types |

Resolution strategy: Resolve each element type in order. Return `Tuple(T1, T2, ...)`.
If any element cannot be resolved, return `null`.

### Proc Literals

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `-> { 1 }` | `Proc(Int32)` | No params, infer return from body |
| `->(x : Int32) { x.to_s }` | `Proc(Int32, String)` | Explicit param types, infer return |
| `->(x : Int32, y : String) { }` | `Proc(Int32, String, Nil)` | Explicit params, no body → Nil |

Resolution strategy: Collect parameter types from the parameter list (must have type
annotations). Infer return type from the body expression. If params lack annotations,
return `null`.

---

## Priority Tier 4 — Control-Flow Unions

**Affects:** `CrystalTypeInference` + `CrystalExpressionTypeResolver`
**Complexity:** High

### Ternary Expressions

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `x = cond ? 1 : nil` | `Int32?` | Union of true-branch and false-branch types |
| `x = cond ? 1 : "hi"` | `Int32 \| String` | Union of both branch types |
| `x = cond ? 1 : 2` | `Int32` | Both branches same → concrete type |

### If Expressions (as RHS)

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `x = if cond; 1; else; nil; end` | `Int32?` | Last expression per branch, union |
| `x = if cond; 1; end` | `Int32 \| Nil` | Implicit else returns Nil |

### Unless Expressions

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `x = unless cond; 1; end` | `Int32 \| Nil` | Body type union with Nil (implicit else) |
| `x = unless cond; 1; else; nil; end` | `Int32?` | Union of both branches |

### Case Expressions

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `x = case y; when Int; 1; when String; "a"; end` | `Int32 \| String` | Union of all `when` branch types |
| `x = case y; when Int; 1; else; nil; end` | `Int32?` | Include else branch type |

### Begin/Rescue Expressions

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `x = begin; 1; rescue; nil; end` | `Int32?` | Normal block type union with rescue block type |
| `x = begin; 1; ensure; 2; end` | `Int32` | Ensure doesn't contribute to type |

### Resolution Strategy for Control Flow

1. Identify all branches (if/else, when clauses, rescue blocks, etc.).
2. Resolve the type of the last expression in each branch.
3. Unify all branch types into a union.
4. If only one branch exists and no implicit nil, return that single type.
5. If any branch cannot be resolved, return `null` (unknown) — conservative approach.

### Typeof Expressions

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `typeof(x)` | Type of `x` | Delegate to variable inference |
| `typeof(1 + 2)` | `Int32` | Resolve inner expression type |

---

## Priority Tier 5 — Multi-Assignment & Instance Variables

**Affects:** `CrystalTypeInference`
**Complexity:** Medium

### Instance Variable Assignments

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `@x = "hello"` | `String` | Same as local assignment inference |
| `@x : Int32` | `Int32` | Type annotation (if supported in grammar) |

Instance variable inference works like local variables but stores results scoped to
the enclosing class. The existing `inferFromAssignment` already checks for `@name`
patterns (line 76 of `CrystalTypeInference.kt`).

### Multi-Assignment

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `a, b = [1, "hi"]` | `{Int32, String}` | Index into array/tuple type |
| `a, b = 1, "hi"` | `{Int32, String}` | Tuple of RHS element types |

Resolution strategy: Resolve the RHS type. If it is `Array(T)` or `Tuple(T1, T2, ...)`,
index into it for each LHS variable. If RHS is a multi-value expression, assign
by position.

### Chained Assignments

| Crystal Code | Inferred Type | Strategy |
|---|---|---|
| `a = b = value` | Type of `value` | Follow the rightmost (innermost) assignment |

---

## Priority Tier 6 — Operator Result Types

**Affects:** `CrystalExpressionTypeResolver`
**Complexity:** High

### Arithmetic Operators

| Expression | Result Type | Notes |
|---|---|---|
| `Int + Int` | Same Int type | `1 + 2` → `Int32` |
| `Float + Float` | Same Float type | `1.0 + 2.0` → `Float64` |
| `Int + Float` | Float type | Mixed → Float |
| `String + String` | `String` | Concatenation |
| `Int * Int` | Same Int type | Multiplication |
| `Int ** Int` | Same Int type | Power |
| `Int // Int` | Same Int type | Integer division |
| `Int / Int` | Same Int type | Crystal integer division |

### Comparison Operators

| Expression | Result Type |
|---|---|
| `x == y` | `Bool` |
| `x != y` | `Bool` |
| `x < y` | `Bool` |
| `x > y` | `Bool` |
| `x <= y` | `Bool` |
| `x >= y` | `Bool` |
| `x <=> y` | `Int32` |

### Logical Operators

| Expression | Result Type |
|---|---|
| `!x` | `Bool` |
| `x && y` | Type of `y` (short-circuit) |
| `x \|\| y` | Type of `x \| y` (union) |

### Unary Operators

| Expression | Result Type |
|---|---|
| `-x` | Same as `x` |
| `+x` | Same as `x` |
| `~x` | Same as `x` |

### Strategy

For binary operators: resolve both operand types. If both are the same concrete type,
return that type. For mixed numeric types, follow Crystal's promotion rules. For
comparison/logical operators, return the fixed result type.

Note: Full operator overload resolution (custom `def +(other)`) is out of scope for
the initial implementation. Only built-in type rules are supported.

---

## What This Spec Does NOT Cover

- **Full method body return type inference** — inferring return type from all code paths
  in a method body (only annotation-based return types are supported now).
- **Generic type parameter resolution** — `Array(T)` where `T` is a type parameter.
- **Module/mixin type composition** — resolving types from included modules.
- **Nil-check narrowing** — `if x` narrowing `x` from `Int32?` to `Int32` inside the block.
- **Instance variable type inference across files** — only same-file assignments are analyzed.
- **Union type simplification** — `Int32 | Int32` → `Int32` deduplication.
- **Abstract type resolution** — `Number`, `Comparable(T)` as inferred types.

---

## Implementation Notes

### Existing Code to Extend

- `CrystalTypeInference.inferTypeFromExpression` (line 96) — add literal pattern matching
  before the existing method-call regex patterns.
- `CrystalExpressionTypeResolver.resolveType` (line 29) — add cases for collection,
  control-flow, and trivial expression PSI types in the `when` block.

### Testing Strategy

- Unit tests for `CrystalTypeInference` — test each literal type, collection, and
  control-flow pattern in isolation.
- Platform tests via `CrystalTypeCheckInspectionTest` — test that inferred types flow
  into argument-type validation correctly.
- Parser tests for new BNF rules (if any) — add `.cr` + `.txt` golden files.

---
