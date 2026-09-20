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
(currently at 19; the latest bump carries explicitly qualified method
receivers as stub owners).

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

Resolution: the dedicated `named_type_bare_argument` alternative
(`IDENTIFIER spaced_colon type_reference [named_bare_type_default]`) precedes
`named_bare_argument` in `bare_argument`. The whitespace-sensitive colon rules
described below make the alternatives disjoint before PEG commits, so the type
branch can safely consume complete proc, nilable, generic, pointer, static-array,
metaclass, and defaulted types without shadowing compact `name: value`
expressions. Defaults accept nested assignments. Both forms pin after their
distinct colon prefix; neither relies on fallthrough after a partially parsed
value.

## Pointer-suffixed type arguments (`property stack_top : Void*`)

The same half-match trap hid C pointer types in declaration-macro calls:

```crystal
property stack_top : Void*                  # fiber/context.cr
getter ip : Void*, count : Int32            # exception/call_stack/stackwalk.cr
property lastmatch : UC* = Pointer(UC).null # float/fast_float/ascii_number.cr
class_getter match_context : T* do ... end  # regex/pcre2.cr
```

`bare_expression` parses the `Void` prefix fine and leaves the `*` dangling,
so the whole call — and every declaration after it in the file — failed. The
fix mirrors the compiler, verified empirically: `name : Type` with a space
before the colon is a *type* position (`probe x : Int32*` yields
`Pointer(Int32)`; `probe x : Int32 * String` is rejected at `String`), while
`name:` without the space is an expression (`probe x: 2 * 3` multiplies,
`probe x: Int32` is a named-argument path).

Resolution preserves the compiler's whitespace distinction before parsing the
argument value. Because ordinary whitespace tokens are skipped by `PsiBuilder`,
`compact_colon` and `spaced_colon` use a non-consuming external predicate that
scans the skipped source range before `:` for horizontal whitespace or an
escaped newline. This recognizes spaces, tabs, form feeds, and a bare line
continuation while keeping a genuinely unspaced colon compact. The alternatives
are therefore disjoint before PEG commits:

- `name: value` selects `named_argument` / `named_bare_argument` and keeps
  expressions such as `CONST *\n 3` intact. Absolute namespace values such as
  `level: ::Socket::Protocol::TCP` use a bounded normal-expression path because
  globally adding `DOUBLE_COLON` to the bare-postfix grammar changes unrelated
  namespace-call PSI and resolution.
- `name : Type` selects `named_type_argument` / `named_type_bare_argument` and
  consumes the complete `Void*`, `UInt8***? | Nil`, `UInt8*[4]`,
  `UInt8[BUFFER_SIZE]*`, `Int32*.class`, or `UInt8* -> Nil` type reference.

The type-shaped alternatives run first but cannot shadow a compact named
argument. They do not consume a trailing newline, so statement separators stay
outside `CrystalArgument` / `CrystalBareArgument`. Parenthesized multiline
forms, defaults, and trailing `do` blocks use the same rules without a
pointer-specific follower list or CONSTANT-vs-identifier heuristic.

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

**Brace-block binding:** `{ ... }` binds to the nearest call while `do ... end` binds
to the outermost command. A dot-call inside bare arguments therefore accepts an
optional brace block (`assert_prints JSON.build { |json| ... }, expected` keeps the
block on `build` so trailing arguments survive), via a `brace_block` twin of the
`block` rule aliased to the same `BLOCK` composite. `do` blocks keep attaching
outward (`write_extra_newlines (a).b, c.d do ... end`), and trailing arguments after
a `do` block would be invalid Crystal anyway. Bare-callee block attachment
(`collect build_report { |r| r }, "done"`, where the inner call is a plain variable
reference) is a known follow-up: it needs its own call node and is out of scope here.

**Wrapping-operator protection:** `&+` / `&-` after an operand on the same line read
as binary (`size &+ s.size`, `new_len &- @length`, tight `x &-y`), never as a bare
call with a unary wrapping argument — `size(&+...)` falsely measured the operand
against a parameterless method in bigint.cr. The `isWrapUnaryAllowed` predicate gates
both unary rules, so genuine prefix positions keep the unary reading (`(&-boundary)`
in pointer.cr). A newline between operand and operator keeps the established behavior,
and bare `&+` alone is not a block argument (the compiler rejects `reduce(&+)`, and
the plugin agrees).

Deliberate leniency: prefix `&-`/`&+` before literals (`&-2`) is rejected by the
compiler, but the plugin keeps parsing it as unary without complaint. Mirroring the
rejection would require a per-literal probe matrix (int/float/string/char, tight and
spaced, both operators), and an incomplete one risks false errors on valid code —
invalid code already fails at compile time, so silence there is the honest trade-off.

**Bitand-versus-block-pass protection:** `&` after an operand reads as block-pass
only with whitespace before `&` plus a tight operand (`foo &block`, `foo &(blk)`);
every other arrangement is binary (`x & (y | z)`, `size & (limit)`, all-tight
`foo&bar`) — compiler-verified across the full spacing matrix. The `isBlockPassAllowed`
predicate gates the `argument`, `bare_argument`, and both unary `AMPERSAND`
alternatives, so the block reading cannot sneak back through the unary backdoor the
wrapping fix closed. Positions without a preceding operand keep the block reading.

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

## Explicitly qualified method receivers (`def Time::Location.new`)

The compiler accepts a constant path as a `def` receiver, owning the method for
that type's static side outside any lexical type body:

```crystal
def Time::Location.new(pull : JSON::PullParser)
  load(pull.read_string)
end
```

`method_name` therefore accepts `CONSTANT (:: CONSTANT)* DOT
qualified_method_target`, where the target mirrors the single-segment and `self`
shapes (identifier, setter, keyword, operator). The alternative sits exactly
where the old single-segment `CONSTANT DOT` form was — after the
CONSTANT-leading macro-spliced forms, so generated names keep precedence (PEG
longest-match-first). It starts with `CONSTANT`, so the compiler-rejected
leading-`::` form (`def ::Time::Location.new`) stays a parse error, and it
admits no generic arguments (`def Box(Int32).new` stays invalid).

**Naming:** the method name is the target after the receiver DOT — `def
Float64.new` is `new`, not `Float64`. `getNameIdentifier()` anchors on the
target token so rename and `getTextOffset()` keep working; operator and keyword
fallbacks compose only the target region, never receiver segments.

**Ownership:** an explicit receiver outranks the lexical enclosure. The method
stub persists it as `ownerQualifiedName` (generalizing the former
record-only field; the binary layout is unchanged), `def Time::Location.new`
indexes under `CrystalMethodIndex["new"]` and
`CrystalMethodByClassIndex["Location"]` but never under `CrystalMethodIndex["Time"]`
or the top-level index — including the `struct Int8; def Float64.new; end; end`
shape, which belongs to `Float64`, not `Int8`. Constant receivers classify as
self (static) methods; macro-interpolated receivers stay owner-unknown and
unclassified. Exact-identity filtering and unqualified call sites
(`callableUnqualified`, `resolveUnqualifiedCall`) consult the same owner, so
`load(...)` inside `def Time::Location.new` resolves against `Time::Location`
and `Other::Location.new` never receives `Time::Location`'s methods.

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
  `NilableParenthesizedType.cr`, `QualifiedReceiverMethodDefinitions.cr`
  (qualified receivers, qualified setter, receiver-beats-enclosure),
  `MethodCalls.cr` (nested dot-call tail), `ExpressionAndRangeReplay.cr` (range
  binding), existing implicit-constructor inspection fixtures.
- Negative parser tests: `CrystalInvalidQualifiedReceiverTest` (leading `::`,
  generic receiver, truncated path, missing target).
- PSI naming: `CrystalQualifiedReceiverMethodTest` (target naming, rename
  anchor, qualified setter/operator/keyword, static classification, macro
  receiver untouched).
- Index: `CrystalIndexServiceTest.testIndexesQualifiedReceiverMethodUnderReceiverType`
  (name/class/top-level keys, stub owner and static flag) and
  `testExplicitReceiverBeatsLexicalEnclosureInIndex`.
- Stdlib canary: `CrystalStdlibSourceParseTest` parses real `/usr/lib/crystal`
  sources when a local Crystal is installed (currently `uri.cr`, `http/client.cr`,
  `http/server/response.cr` — the type-shaped macro argument regression source —,
  `http/server/context.cr`, `http/server/request_processor.cr`, `deque.cr`,
  `int.cr`, `float.cr`, `number.cr`, `comparable.cr`, `json/to_json.cr`,
  `json/from_json.cr` and `yaml/from_yaml.cr` — the qualified-receiver sources —,
  and the
  compiler's `crystal/macros.cr`) and fails on any `PsiErrorElement`. This canary
  exists because stdlib files use far more syntax than hand-written fixtures; a
  grammar gap there degrades indexing silently.
- Inspection: `CrystalArgumentCountInspectionTest.testConstructorAfterSetterDefinition*`
  verifies constructors behind setter definitions accept positional args and still flag
  genuine excess; `testConstructorInMacroGeneratingClassIsStillChecked` covers the same
  behind `{% for %}`-generated siblings.
- Resolution semantics: `CrystalMethodHierarchyTest.testInterpolatedMacroMethodNameSuppressesOnlyUnknownNames`
  (explicit names resolve, unknown names stay suppressed),
  `testQualifiedReceiverMethodResolvesOnExactStaticIdentity` (qualified
  receiver on the exact static identity, no cross-namespace leakage, absent
  from the instance side),
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
