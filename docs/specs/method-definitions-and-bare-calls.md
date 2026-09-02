# Method Definitions and Bare Calls Spec

## Overview

Documents grammar decisions for `def`/`macro` method-name forms and for bare
(argument-list) call nesting. All rules are PEG (GrammarKit): first match wins and
alternatives must be ordered longest-first.

## Setter method definitions (`def host=`)

Crystal setters are methods whose name ends with `=`:

```crystal
def host=(host : String?)
  @host = host
end

def self.env=(value : String)
end

abstract def level=(value : Int32)
```

**Grammar** (`method_name`, longest-first):

```bnf
private method_name ::= IDENTIFIER ASSIGN
              | SELF DOT IDENTIFIER ASSIGN
              | IDENTIFIER | SELF DOT (...) | keyword_as_method | operator_method_name
              | macro_interpolation
```

Without the first two alternatives the parser consumed only the identifier and
stranded the `=`. This broke real stdlib files (`uri.cr` has `def host=` at line 102
and `def query_params=` at line 289) and — via degraded stub indexing of the broken
regions — silently removed members like `URI#initialize` from the index, which then
surfaced as false "Too many arguments: expected at most 0" inspection errors on valid
constructor calls (see call-argument-inspections.md).

**Naming/indexing:** Setter definitions are named `"host="` (identifier plus the
`ASSIGN` suffix), matching Crystal semantics and keeping them distinct from a getter
`host` in `CrystalMethodIndex`. The name identifier anchor remains the `IDENTIFIER`
leaf; `getNameFromMethodName` appends `=` when the next significant sibling token is
`ASSIGN`.

**Stub version:** Parsing-semantics changes in this area require bumping
`CrystalParserDefinition.FILE.getStubVersion()` so persisted indexes rebuild
(currently at 9; the latest bump restores constructors after nilable compound
type restrictions).

## Nilable compound type restrictions

Crystal permits `?` after parenthesized, Tuple, and NamedTuple type forms:

```crystal
block : (Hash(K, V), K -> V)?
entry : {Entry(K, V), Int32}?
options : {name: String, count: Int32}?
```

Each compound branch of `type_single` consumes its optional `QUESTION` suffix.
Without that suffix on the parenthesized branch, real `hash.cr` stopped parsing at
`Hash#initialize(block : (Hash(K, V), K -> V)? = nil, ...)`; both later
`Hash.self.new` overloads disappeared from the method index, and valid
`Hash(String, Int32).new(0)` calls were checked only against the earlier
parameterless initializer. Tuple and NamedTuple branches follow the same language
rule and prevent an equivalent error on return types such as
`{Entry(K, V), Int32}?`.

## Type-shaped macro arguments (`property upgrade_handler : (IO ->)?`)

Macro calls in property/getter position take `name : value` arguments, and the value
side is not always an expression — Crystal's macro argument parser also accepts type
shapes:

```crystal
property upgrade_handler : (IO ->)?        # http/server/response.cr
getter block : (->) | Nil                  # spec/example.cr
```

`bare_expression` has no proc-type rule, so these previously failed the whole
`bare_argument_list` and every member of the stdlib file after the shape silently
dropped from stub indexing. The visible symptom was exactly the empty-constructor
fallback: `HTTP::Server::Response.new(io)` (kemal spec/event_stream_spec.cr:72)
reported "Too many arguments: expected at most 0, got 1" highlighted on `io`, while
`HTTP::Request.new` on the same page resolved fine.

Resolution: a dedicated `named_type_bare_argument` alternative
(`IDENTIFIER COLON type_reference [named_bare_type_default]`) sits in `bare_argument`
AFTER `named_bare_argument` (PEG order — expression-shaped values keep parsing as
expressions). Two GrammarKit traps are documented in the BNF comments: a second
alternative on the `private named_bare_argument` rule gets common-prefix-factored
away (and flips GrammarKit's global error pinning, breaking deque.cr/to_json.cr),
and `pin=2` commits the rule after `IDENTIFIER COLON` (`result_ || pinned_`) so a
failing tail could never fall through — `named_bare_argument` therefore carries no
pin; the required prefix makes the pin unnecessary for correct parsing.

Known limits (TODO.md): shapes where `bare_expression` half-matches still mis-shape
(`property level : Severity? = nil` — the `?` becomes a stray ternary QUESTION,
`= nil` an assignment tail; `property select_context : SelectContext(Nil)?` — the
generic type parses as a bare method call), and trailing `{ ... }` default-value
blocks (spec/context.cr) are not consumed yet.

## Nested bare calls in argument lists

Crystal command syntax nests: `exec new_request method, path` parses as
`exec(new_request(method, path))`. The inner callee consumes the whole comma-separated
tail.

```bnf
bare_method_call_expression ::= (IDENTIFIER | CONSTANT) call_args
                              | (IDENTIFIER | CONSTANT) !DOT !LBRACKET !nested_call_lookahead bare_argument_list
```

Previously only the parenthesized form existed in argument position, so the tail was
split off into a bogus multi-assignment statement (`exec new_request m, p` → statement
`exec new_request m` + garbage assignment `p = ...`-shaped error).

**Range protection:** An identifier directly followed by `..` / `...` always continues
a Range inside an argument list — `consume first..n, other` keeps `first..n` as one
argument, endless ranges included (`consume first.., other`). The nested-call
alternative is therefore blocked by `nested_call_lookahead`, which extends
`binary_op_lookahead` with an unconditional DOTDOT/DOTDOTDOT check. Statement-level
bare calls keep using plain `binary_op_lookahead`, so leading-range arguments remain
valid (`consume ..last` still parses as a call with one range argument).

## Macro-interpolated callees (`{{method.id}} path, form: body`)

Stdlib code generates methods inside `{% for %}` loops and calls them through macro
interpolation, with both argument styles and blocks:

```crystal
{% for method in %w(get post) %}
  def {{method.id}}(path, ...)
    {{method.id}} path, form: body, headers: headers   # bare + named args
    {{method.id}}(path) do |response| ... end          # parens + block
  end
{% end %}
```

```bnf
private macro_interpolation_call ::= macro_interpolation [call_args | !DOT !LBRACKET !binary_op_lookahead bare_argument_list] [block]
```

This rule replaces the plain `macro_interpolation` entries in `primary_expression` and
`bare_primary_expression`. The optional wrapper covers the no-argument case — do NOT
add a separate bare `macro_interpolation` alternative before it; PEG would take the
first alternative and make the argument forms unreachable (this exact mistake was made
and fixed during implementation). The guards mirror `method_call_expression` so `.`
chains, `[index]`, and binary operators after the interpolation attach through the
normal postfix paths.

Resolution of such calls stays suppressed (macro-generated names), consistent with the
DOT-call architecture; the goal here is error-free parsing so surrounding definitions
index cleanly.

## Macro uncertainty vs. explicitly defined methods

`collectNamedMethods` treats a type whose members include macro-interpolated method
names (`def {{method.id}}`) as *uncertain* — the type might expose names nobody wrote
textually. Historically this uncertainty suppressed **every** named lookup against the
type, which broke real stdlib resolution: `HTTP::Client.new(...)` could not reach the
explicitly written `def self.new(uri, tls)` overloads in http/client.cr because the
same class also generates `get`/`post`/… through `{% for %}` loops. Symptoms were
"Any (Variable)" hovers, dead Go-to-definition, and silently skipped argument-count
checks.

Rule since this fix: **explicit definitions win**.

- A lookup for a name that IS textually defined on the type resolves normally,
  regardless of macro-generated siblings.
- A lookup for a name that is NOT defined anywhere stays incomplete (authoritative
  suppression) when the type has macro-generated method names — the macro could be
  producing exactly that name.
- Names collected inside macro-control regions with a known textual name (`macroDepth
  > 0`, non-interpolated) keep their existing `uncertainMethodNames` suppression.

## DOT Member Assignments And Bare Regex Arguments

DOT member compound assignments must be recognized before ordinary DOT-call
access because GrammarKit uses first-match PEG semantics. The dedicated postfix
alternative accepts compound operators only; plain setter syntax such as
`config.host = "localhost"` retains the established expression-statement PSI,
while `config.server ||= build_server` remains one postfix expression.

A whitespace-separated slash after a DOT method name starts a bare regex
argument when the slash is immediately followed by non-whitespace regex content
and an unescaped closing slash exists later in the source. The terminator may be
on a later line because Crystal regex literals can span lines. A whitespace
character immediately after the opening slash keeps the token in division
context, matching the compiler and preserving `object.value / 2 / 3` as two
division operators. Without a closing slash, `object.value /2` also remains
division.

These rules are covered by `DotCompoundAssignment`, `DotRegexDivision`, and
`KemalRangeBlock` parser goldens plus dedicated lexer tests.

## Test Coverage

- Parser goldens: `SetterMethodDefinition.cr`, `MacroInterpolatedCallee.cr`,
  `NilableParenthesizedType.cr`,
  `MethodCalls.cr` (nested dot-call tail), `ExpressionAndRangeReplay.cr` (range
  binding), existing implicit-constructor inspection fixtures.
- Stdlib canary: `CrystalStdlibSourceParseTest` parses real `/usr/lib/crystal`
  sources when a local Crystal is installed (currently `uri.cr`, `http/client.cr`,
  `http/server/response.cr` — the type-shaped macro argument regression source —,
  `http/server/context.cr`, `http/server/request_processor.cr`, `deque.cr`,
  `int.cr`, `float.cr`, `number.cr`, `comparable.cr`, `json/to_json.cr`, and the
  compiler's `crystal/macros.cr`) and fails on any `PsiErrorElement`. This canary
  exists because stdlib files use far more syntax than hand-written fixtures; a
  grammar gap there degrades indexing silently.
- Inspection: `CrystalArgumentCountInspectionTest.testConstructorAfterSetterDefinition*`
  verifies constructors behind setter definitions accept positional args and still flag
  genuine excess; `testConstructorInMacroGeneratingClassIsStillChecked` covers the same
  behind `{% for %}`-generated siblings.
- Resolution semantics: `CrystalMethodHierarchyTest.testInterpolatedMacroMethodNameSuppressesOnlyUnknownNames`
  (explicit names resolve, unknown names stay suppressed),
  `CrystalGotoDeclarationTest.testNewOnClassWithMacroGeneratedMethodsStillResolves` /
  `testInitializeOnClassWithMacroGeneratedMethodsStillResolves` /
  `testUndefinedNameOnMacroGeneratingClassStaysSuppressed`.
- Real stdlib integration (skipped without local Crystal):
  `CrystalStdlibConstructorResolutionTest` — `HTTP::Client.new` resolves to the
  explicit `def self.new` overloads, `URI.new` to `initialize`, and `Deque(Int32).new`
  includes both explicit `self.new` and initializer-backed overloads without a false
  missing-argument diagnostic. `Hash(String, Int32).new(0)` resolves to the explicit
  default-value overload without a false excess-argument diagnostic. The hover popup
  renders a constructor signature instead of the "Any (Variable)" fallback.
