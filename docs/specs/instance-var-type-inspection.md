# Instance Variable Type Inspection

## Component

`CrystalInstanceVarTypeInspection` — flags instance variable type annotations
that the Crystal compiler rejects:

```
can't use X as the type of instance variable '@name', use a more specific type
```

Covered shapes:

- Direct ivar annotations and property declaration bodies —
  `@x : Int = …`, `property x : Int …` (BFn `property_declaration`):
  **always flagged** when a base type is in `FORBIDDEN_TYPES`
  (abstract bases `Value/Object/Reference/Number/Int/Float/Struct/Enum`,
  unbound generics `Pointer/Tuple/NamedTuple/StaticArray/Class`,
  uninstantiated generics `Array/Hash/Range/Slice/Proc/Union/Enumerable/
  Indexable`). Real-compiler verified: `@x : Int = …` and `property x : Int`
  do not compile.
- initialize parameters declaring an instance variable —
  `def initialize(@x : Type)`: see the restriction rule below (v0.2.9).

## `initialize(@x : AbstractType)` is a restriction, not a declaration

Real-compiler findings (Crystal 1.21):

- The `@x : T` annotation on an initialize parameter is a parameter
  RESTRICTION; abstract type sets are legal restriction positions. `Int`
  matches any integer sub-width as the argument type.
- The actually stored ivar type comes from a TYPED co-declaration elsewhere
  in the type body — `getter x : Int32`, `getter? x : T`, `property x : T`,
  or the ivar annotation `@x : T = …`. Declaration order is irrelevant.
  Reference implementation: `SemanticVersion` declares `getter major : Int32`
  while `initialize(@major : Int …)` stays legal (semantic_version.cr:71;
  `getter major` without a type alone rescues nothing).
- Without any typed co-declaration the compiler rejects the abstract ivar
  type with the message above — these cases stay flagged.

Therefore `checkParameter` reports a forbidden base type only when
`concreteVarDeclarationRescues` finds no typed co-declaration in the
enclosing type body:

- typed `CrystalPropertyDeclaration` for the same ivar with a type reference,
- or a `CrystalMethodCallExpression` whose text starts with
  `getter`/`getter?`/`property`/`property?`/`setter` (optional parens) and
  binds `<name> :` — the typed argument form (`named_type_bare_argument`).

Calls with instantiaded types (`Array(Int32)`) are never flagged because of
the `hasTypeArgs` guard.

## Inspector interplay

A typed co-declaration that is itself forbidden (`property x : Number`) is
still reported — by the property path. The restriction param stays silent
there because the declaration is the failure source the compiler names first.

## Covered by

- `CrystalInstanceVarTypeInspectionTest` — forbidden/not-allowed matrix plus
  rescue cases (typed getter, `getter?`, property, parens form, ivar
  annotation, declaration-after-initialize; negatives: no declaration,
  untyped getter, unrelated getter).
