## Context

Crystal method parameter type annotations use `:` with spaces on both sides: `speed : String`. The parser already handles both spaced and unspaced variants. This inspection operates at the PSI level, walking method definitions and checking the tokens around each `:` in parameter and return type positions.

## Goals / Non-Goals

**Goals:**
- Flag missing spaces before/after `:` in parameter type annotations
- Flag missing spaces before/after `:` in method return type annotations
- Exception: after `=` in default values, spacing is not enforced
- Provide quick-fix to insert missing spaces

**Non-Goals:**
- Enforcing spacing in type declarations (`alias`, `lib` types)
- Enforcing spacing in variable type annotations (`x : Int32 = 1` at local scope)
- Changing existing formatting

## Decisions

### Decision 1: PSI-based inspection using parameterList and typeReference

Walk `CrystalMethodDefinition` → `parameterList.parameterList` → check each `CrystalParameter` for COLON token and surrounding whitespace. Check `typeReference` for return type.

**Why:** Direct PSI traversal is more reliable than regex/text-based approaches. The parser already normalizes whitespace in the AST.

### Decision 2: Exception for `=` (default values)

When a parameter has `= :name` (symbol default), the `:` after `=` is part of the default value expression, not a type annotation. Skip spacing check when `:` is preceded by `=`.

**Why:** `speed : String = :name` is valid Crystal — the `:name` is a symbol literal.

### Decision 3: Quick-fix using LocalQuickFix

Insert a space before/after `:` where missing. Simple string replacement at the token offset.

## Risks / Trade-offs

- **Risk:** False positives on macro-generated code → **Mitigation:** Only inspect explicit `def` parameters, not macro expansions
- **Risk:** Some Crystal code intentionally uses compact style → **Mitigation:** Warning level (not error), easy to suppress
