# Exception Handling

Specs to define the parser behaviour for `begin/rescue/ensure` exception handling.

## Rescue Clause

The `rescue` clause supports multiple forms:

### Bare Rescue
```crystal
begin
  # code
rescue
  # catches all exceptions
end
```

### Variable Binding (catch-all)
```crystal
begin
  # code
rescue e
  # catches all exceptions, binds to `e`
end
```

### Typed Rescue
```crystal
begin
  # code
rescue SomeError
  # catches SomeError
end
```

### Namespaced Type
```crystal
begin
  # code
rescue JSON::ParseException
  # catches JSON::ParseException
end
```

### Type with Variable Binding
```crystal
begin
  # code
rescue e : SomeError
  # catches SomeError, binds to `e`
end
```

### Union Types
```crystal
begin
  # code
rescue SomeError | OtherError
  # catches SomeError or OtherError
end
```

### Binding with Union Types
```crystal
begin
  # code
rescue e : SomeError | OtherError
  # catches SomeError or OtherError, binds to `e`
end
```

### Leading `::` (Global Namespace)
```crystal
begin
  # code
rescue ::SomeType
  # catches global namespace type
end
```

## BNF Rule

```
rescue_clause ::= RESCUE [rescue_spec] then_clause statement_list

private rescue_spec ::= IDENTIFIER COLON type_reference
                       | IDENTIFIER
                       | type_reference
```

The PEG ordering ensures:
1. `rescue e : T` — variable + type (longest IDENTIFIER alternative first)
2. `rescue e` — variable only
3. `rescue T` — type only (CONSTANT token, not IDENTIFIER)
4. bare `rescue` — no spec

`type_reference` already supports union types (`type_union ::= type_single (PIPE NLS type_single)*`)
and namespaced types (`type_path ::= [DOUBLE_COLON] CONSTANT (DOUBLE_COLON CONSTANT)*`).

## Notes

- Multiple variable bindings in one rescue clause (e.g. `rescue e1, e2 : SomeError`) are NOT supported by Crystal — only one variable per rescue clause.
- The `then_clause` (`NEWLINE`, `SEMICOLON`, or `then` keyword) follows the rescue spec.
- `ensure` has no spec — it always catches all (no type or binding).
