# Hover Popovers

Specs to define the behaviour and display of hover popups in the source code.

## General Behaviours

### Definition and Usages
Show the same doc popovers on the definition (e.g. `Foo` in `class Foo`) and when used
somewhere else in the code. Hovering over a definition should produce the same popup as
hovering over a reference to that definition.

### Documentation Links
Type names, class names, and superclass names inside the documentation popup are
hyperlinked via `psi_element://class:<name>` URLs. Clicking them resolves via
`CrystalClassIndex` and replaces the popup content with the target element's
documentation (handled by `getDocumentationElementForLink`).

- Top-level methods show `Object` as the enclosing class (Crystal's universal base).
- A class's own name in its own signature is NOT linked (no self-recursion).
- Non-resolvable type names (e.g. `Int32` absent from the stdlib index) are rendered as
  plain text silently.
- Never use explicit inline colors on `<a>` tags (e.g. `style="color:#3850BB"`) — let
  IntelliJ's default documentation link styling handle it.

### Numeric Type Linking

Integer and Float types are linked to their parent type documentation since
they don't have individual documentation pages:

| Type | Linked To |
|---|---|
| `Int8`, `Int16`, `Int32`, `Int64`, `Int128` | `Int` |
| `UInt8`, `UInt16`, `UInt32`, `UInt64`, `UInt128` | `Int` |
| `Float32`, `Float64` | `Float` |

This applies to:
- Parameter type annotations (`def foo(x : Int32)`)
- Inferred variable types (`x = 42` → `Int32`)
- Any other type display in popups

---

## Specific Behaviours

### Classes, Modules, Structs, Enums

**Popup format:**
```
class <ClassName> [< <Superclass>]
# doc comment (if present)
```

```
module <ModuleName>
# doc comment (if present)
```

```
struct <StructName> [< <Superclass>]
# doc comment (if present)
```

```
enum <EnumName>
# doc comment (if present)
```

- The definition's own name is plain (no self-link).
- The superclass is hyperlinked if resolvable via `CrystalClassIndex`.
- Doc comments above the definition are rendered as Markdown.

**Hover targets:** `class Foo`, `module Bar`, `struct Baz`, `enum Color` — hovering over
the keyword or name shows the popup. Hovering over a reference to the type (e.g. `Foo`
in `: Foo` or `Foo.new`) also shows the class popup.

### Global Functions and Methods

**Popup format (two-line layout):**
```
<EnclosingClass>           ← hyperlinked to class doc (or "Object" for top-level)
<methodName>(<params>) [: <ReturnType>]   ← no "def " prefix
# doc comment (if present)
```

- Enclosing class: hyperlinked via `linkToClass()` if in `CrystalClassIndex`, plain text
  otherwise. Top-level methods show `Object`.
- Method name: no `self.` prefix for class methods (stripped).
- Parameters: type names hyperlinked when resolvable via `CrystalClassIndex`.
- Return type: hyperlinked when resolvable.
- Non-resolvable types render as plain text.
- Doc comments above the definition are rendered as Markdown.

**Hover targets:** Hovering over the method name at a call site (e.g. `butter` in
`butter("karamel")`) or at the definition (`def butter`) shows the popup.

### Parameters

**Popup format (two-line layout):**
```
<Type> (Parameter)         ← type hyperlinked if resolvable, "(Parameter)" in gray/muted
<parameter_name>
```

Example with type:
```
String (Parameter)
bonbon
```

Example with union type:
```
String | Int32 (Parameter)
value
```

Example without type annotation:
```
Any (Parameter)
x
```

Auto-generated doc for untyped parameters:
> The type of this parameter is not specified and will be determined at runtime.

- The type name is hyperlinked if resolvable via `CrystalClassIndex`.
- Union types (`String | Int32`) have each type name hyperlinked independently.
- Untyped parameters show `Any` as a pseudo-type (not hyperlinked) with a note about
  runtime evaluation.
- The `(Parameter)` label is styled with `color:gray` (muted).
- Doc comments are NOT extracted for parameters (Crystal has no parameter docs).

**Hover targets:** Hovering over a parameter name in the definition (e.g. `bonbon` in
`def butter(bonbon : String)`) or in the method body (e.g. `bonbon` in `return bonbon`)
shows the parameter popup.

### Variables (Type Inference)

**Popup format (two-line layout):**
```
<Type> (Variable)          ← inferred type hyperlinked, "(Variable)" in gray/muted
<variable_name>
```

Example with type:
```
String (Variable)
my_variable
```

Example with hash type:
```
Hash(String, Int32) (Variable)
hash_var
```

Example with tuple type:
```
Tuple(Int32, String) (Variable)
tup
```

Example with union type:
```
Int32 | Nil (Variable)
maybe_val
```

Example with unknown type:
```
Any (Variable)
unknown_var
```

- The inferred type is hyperlinked if resolvable via `CrystalClassIndex`.
- Union types have each type name hyperlinked independently.
- Hash/tuple types show detailed type parameters (e.g. `Hash(String, Int32)`).
- Instance variables (`@var`) work the same as local variables.
- The `(Variable)` label is styled with `color:gray` (muted).

**Hover targets:** Hovering over a variable at its definition (e.g. `x` in `x = "hello"`)
or at any usage site (e.g. `x` in `puts x`, `foo(x)`) shows the variable popup.

### Instance Variables

Instance variables (`@var`) show the same popup format as local variables, with
the inferred type and `(Variable)` label.

**Hover targets:** Hovering over `@name` at its definition or usage shows the
variable popup with the inferred type.

### Namespace Access (Intermediate Segments)

Hovering over intermediate namespace segments (e.g. `Inner` in `Outer::Inner.method`)
now works via the `namespace_access` BNF rule and `CrystalNamespaceReference`.

**Resolution:** The reference walks left through prevSibling elements to reconstruct
the full namespace path (`Outer::Inner`), looks it up in `CrystalClassIndex`, then
falls back to the simple name (`Inner`) for lexically-nested classes. When multiple
classes share the same simple name (e.g. `Foo::Sub` and `Bar::Sub`), the reference
filters candidates by comparing their qualified name (built via `buildQualifiedName`)
with the full namespace path.

**Supported patterns:**
- `::Foo` — leading namespace (global)
- `A::B` — nested namespace
- `A::B::C` — multi-level nested namespace
- `A::B.method` — nested namespace with DOT-call

**Disambiguation:** When multiple classes have the same simple name but different
enclosing classes (e.g. `Foo::Sub` and `Bar::Sub`), resolution correctly identifies
the right class by comparing the full qualified name chain.

**Method completion:** When typing `Foo::Sub.<caret>`, only methods from `Foo::Sub`
are suggested (not from `Bar::Sub`). The completion contributor detects the namespace
receiver, builds the full path via `buildNamespacePath`, and filters
`CrystalMethodByClassIndex` results by the enclosing class's qualified name.

**Auto-popup for `::`:** Typing `::` after a CONSTANT (e.g. `Foo::<caret>`) now
triggers the completion popup automatically — no Ctrl+Space needed. The
`CrystalTypedHandler.checkAutoPopup()` detects the second `:` of `::` and calls
`AutoPopupController.scheduleAutoPopup()`.

---

## Resolution Priority

When hovering over a token, `getCustomDocumentationElement` tries these in order:

1. **PsiReference** on the context element (or its parent) — resolves via
   `CrystalReference`, `CrystalDotCallReference`, etc.
2. **GotoDeclarationHandler fallback** — for DOT-call identifiers without a reference.
3. **Definition walk-up** — walk up from the context element (max 4 levels); if a
   definition is found, return it directly.
4. **Variable identifier** — if the element is a variable (not a definition/parameter),
   return it for type info rendering.

If all four return null, `generateDoc` is called with the raw element. `resolveTarget`
then:
1. Returns the element directly if it's a definition, `CrystalParameter`, or variable.
2. Tries resolving via `element.reference`.
3. Walks up the PSI tree (max 4 levels) looking for a definition.

---

## Edge Cases

- **Untyped parameter without type annotation:** Shows `Any` (not hyperlinked) with
  auto-generated doc: "The type of this parameter is not specified and will be determined
  at runtime."
- **Parameter in union type (`def foo(x : String | Int32)`):** Both `String` and `Int32`
  are hyperlinked independently.
- **Parameter name shadows method name:** Both produce different popups (parameter shows
  type, method shows signature).
- **Struct/enum superclass:** `struct Point < Object` — `Object` is hyperlinked.
- **Module method:** `module Foo; def bar; end; end` — shows `Foo` as enclosing class.
- **Nested class:** `class Outer::Inner` — shows `Outer::Inner` as enclosing class.
- **`::` auto-popup timing:** `checkAutoPopup` is called BEFORE the character is inserted.
  The check must look at the character at `offset - 1` (before caret), not `offset - 2`,
  to detect the first `:` when the second is being typed.
- **Variable usage in method arguments:** Hovering over a variable in `foo(x)` unwraps
  the `CrystalArgument`/`CrystalBareArgument` wrapper to find the variable reference.
- **Assignment resolution:** When reference resolution returns an assignment element
  (not a variable identifier), the code falls through to the variable identifier check
  instead of returning the assignment directly.
