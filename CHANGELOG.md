# Changelog

All notable changes to the Crystal Language Plugin for JetBrains IDEs will be documented in this file.

## [0.3.2] — 2026-10-30
### Added

- **[Ameba linter integration](docs/changelog/2026-10-30_0.3.2.md)**
- **[Cannot-find warnings for unresolved names](docs/changelog/2026-10-30_0.3.2.md)**
- **[Type-alias resolution and completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[Control-flow narrowing for union and nilable receivers](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bracket-call navigation and arity checks](docs/changelog/2026-10-30_0.3.2.md)**
- **[Slice and StaticArray bracket literals infer their types](docs/changelog/2026-10-30_0.3.2.md)**
- **[Cross-file constant resolution through require closures](docs/changelog/2026-10-30_0.3.2.md)**
- **[Hover documentation with type inference for constants](docs/changelog/2026-10-30_0.3.2.md)**
- **[Shard-aware require-path completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[Shard dependency support](docs/changelog/2026-10-30_0.3.2.md)**
- **[Hover types on destructuring targets](docs/changelog/2026-10-30_0.3.2.md)**
- **[Invalid `case ... in` pattern diagnostic](docs/changelog/2026-10-30_0.3.2.md)**
- **[Correct `case ... in` pattern handling in unused-variable analysis](docs/changelog/2026-10-30_0.3.2.md)**
- **[Per-target types for destructuring assignments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unused-variable analysis for destructuring targets](docs/changelog/2026-10-30_0.3.2.md)**
- **[`# language=` injection for percent literals and symbol strings](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parameter-order inspection](docs/changelog/2026-10-30_0.3.2.md)**
- **[`# language=` comment completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[String-literal external parameter names](docs/changelog/2026-10-30_0.3.2.md)**
- **[`lib fun` resolution and call validation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Joint method+macro overload pool for argument counts](docs/changelog/2026-10-30_0.3.2.md)**
- **[Chained-call receivers with exact typing](docs/changelog/2026-10-30_0.3.2.md)**
- **[Update-and-rebuild after failed Ameba builds](docs/changelog/2026-10-30_0.3.2.md)**
- **[Honest stale-field note after successful installs](docs/changelog/2026-10-30_0.3.2.md)**
- **[Backtick commands inside interpolation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Regex literals inside interpolation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-spliced numeric literals](docs/changelog/2026-10-30_0.3.2.md)**
- **[Generic typed collection literals](docs/changelog/2026-10-30_0.3.2.md)**
- **[Leading-`::` bare arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Comma-separated macro-if branch content](docs/changelog/2026-10-30_0.3.2.md)**
- **[Raw `%q(…)` inside interpolation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Visibility-modified lib definitions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Float literals in macro control tags](docs/changelog/2026-10-30_0.3.2.md)**
- **[Empty regex literals (`//`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Heredoc bodies under postfix modifiers](docs/changelog/2026-10-30_0.3.2.md)**
- **[Assignment arguments in parenthesized calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[Lib external vars as `pointerof` targets](docs/changelog/2026-10-30_0.3.2.md)**
- **[Type declarations as array elements](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword setter names in member assignments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parameterless proc literals with `do` bodies](docs/changelog/2026-10-30_0.3.2.md)**
- **[Class variable assignments and visibility-modified methods in enum bodies](docs/changelog/2026-10-30_0.3.2.md)**
- **[Float literals without underscore suffix](docs/changelog/2026-10-30_0.3.2.md)**
- **[Loose grouped postfix arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated proc parameters](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-conditional method headers](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare macro-generated multi-assignment targets](docs/changelog/2026-10-30_0.3.2.md)**
- **[Tight bare splat arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Chained postfix modifiers](docs/changelog/2026-10-30_0.3.2.md)**
- **[`when` as an identifier in nested positions](docs/changelog/2026-10-30_0.3.2.md)**
- **[`out` as an ordinary local-variable name](docs/changelog/2026-10-30_0.3.2.md)**
- **[Semicolon groups in parenthesized expressions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Assignments as tuple entries](docs/changelog/2026-10-30_0.3.2.md)**
- **[Mixed implicit and plain `when` entries](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare `is_a?`/`responds_to?` callees on implicit self](docs/changelog/2026-10-30_0.3.2.md)**
- **[Embedded lexer-state literal and operator coverage](docs/changelog/2026-10-30_0.3.2.md)**
- **[Explicitly qualified method receivers (`def Time::Location.new`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-controlled hash and named-tuple entries](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated receiver ivar access (`other.@{{ivar.id}}`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword setter method names (`def private=`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Multiline ternary expressions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Double-splat parameter restrictions (`**options : **T`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Semicolon-terminated annotation definitions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Block-only `super` and `previous_def` calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[C-FFI aggregate fields and keyword parameters](docs/changelog/2026-10-30_0.3.2.md)**
- **[Constant-assignment heredocs and spaced index ternaries](docs/changelog/2026-10-30_0.3.2.md)**
- **[Typed tuple collections and expression-position `with … yield`](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro body lexing no longer starts after arbitrary `macro` keywords](docs/changelog/2026-10-30_0.3.2.md)**
- **[Lone `uninitialized` as a bare call argument, with end-to-end rename for keyword variables](docs/changelog/2026-10-30_0.3.2.md)**
- **[Multiple `typeof` arguments (`typeof(t, u)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword identifiers as parameter names (`def declare_class_var(node, var, uninitialized)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword identifiers as local variables (`union = ...`, `if of = node.of`, `store @last, union`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated symbols (`:{{name.id}}`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[`.!` pseudo-method suffix (`value.!`, `&.dst?.!`, `find(&.!)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Prefix `!` at unary precedence (`!!a != !!b`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unary wrapping operators (`&-value`, `&+value`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Indexed and `self` targets in multi-assignments (`self[i], self[j] = ...`, `a.value, a[n] = ...`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro fresh variables (`%value{i}`, `%var{key.id}`, bare `%val`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Member targets in multi-assignments (`a.foo, a.bar = 1, 2`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unnamed `lib fun` parameters (`fun strerror_r(Int, Char*, SizeT)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[External `lib fun` symbol aliases (`fun iconv = libiconv(...)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Uppercase `lib fun` names (`fun GetConsoleMode(...)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword `fun` names (`fun select(...)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[`$var = symbol` external vars (`$free = pcre_free : Void* ->`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Annotations on `lib` members (`@[Flags]`, `@[Packed]`, `@[ReturnsTwice]`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro forms as `lib`/`enum` members (`{% if %} ... {% end %}`, `{{ }}`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-spliced member names (`fun initialize_{{name}}_target`, `def {{x}}=`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro forms in expression positions (`x = {% if %} …`, `when {{ i }}`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Structured `{% if %} A {% else %} B {% end %}` envelopes](docs/changelog/2026-10-30_0.3.2.md)**
- **[String keys for named arguments (`with_env("FOO": "bar")`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Nested `lib` definitions in type bodies (`class ELF; lib LibELF ...; end`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Receiver-qualified `pointerof` targets (`pointerof(fiber.@context)`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Proc pointers with variable receivers (`&->@stack_pool.collect_loop`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro control in `case` clause sequences (`case v; {% for ... %}; when {{...}}`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated splat parameters (`def initialize({{ ... end.splat }})`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Chained indexed assignments (`a[i] = b[j] = v`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Setter symbols (`:color=`) lex as one token](docs/changelog/2026-10-30_0.3.2.md)**
- **[Any keyword works as an external parameter name](docs/changelog/2026-10-30_0.3.2.md)**
- **[Raw `%q` literals no longer swallow their own closer](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare `yield` keeps trailing `if`/`unless` on the outer statement](docs/changelog/2026-10-30_0.3.2.md)**
- **[Headless audit hardens its trust and credential boundaries](docs/changelog/2026-10-30_0.3.2.md)**
- **[Binary operators dispatch like method calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[Headless audit reports parse errors and runs only Crystal inspections](docs/changelog/2026-10-30_0.3.2.md)**
- **[Lib-body constant assignments parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[Pinned Crystal 1.21.0 parser compatibility audit](docs/changelog/2026-10-30_0.3.2.md)**
- **[Headless Inspect Code audit script](docs/changelog/2026-10-30_0.3.2.md)**
- **[User-defined enums join the compiler-imposed base hierarchy](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bidirectional accessor rename (`getter`/`setter`/`property` family)](docs/changelog/2026-10-30_0.3.2.md)**
- **[`hexfloat.cr` ternary disambiguation and macro percent literals](docs/changelog/2026-10-30_0.3.2.md)**
- **[`Number`-family bracket calls infer `Array(Type)`](docs/changelog/2026-10-30_0.3.2.md)**

### Changed

- **[Dependency-aware name completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[Release gate](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unlimited IDE compatibility](docs/changelog/2026-10-30_0.3.2.md)**

### Bug Fixes

- **[Incomplete member access while typing](docs/changelog/2026-10-30_0.3.2.md)**
- **[Nilable receiver completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[Enum predicate completion and hover](docs/changelog/2026-10-30_0.3.2.md)**
- **[Record field readers across reopenings](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword-named predicate accessors](docs/changelog/2026-10-30_0.3.2.md)**
- **[Equals-leading regex literals](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unqualified call overload returns](docs/changelog/2026-10-30_0.3.2.md)**
- **[Condition-assignment local references](docs/changelog/2026-10-30_0.3.2.md)**
- **[Generated type names and method type parameters](docs/changelog/2026-10-30_0.3.2.md)**
- **[FFI expression-path navigation](docs/changelog/2026-10-30_0.3.2.md)**
- **[FFI alias constructor diagnostics](docs/changelog/2026-10-30_0.3.2.md)**
- **[Stable program-context test fixtures](docs/changelog/2026-10-30_0.3.2.md)**
- **[FFI navigation with multiple declaration sources](docs/changelog/2026-10-30_0.3.2.md)**
- **[Go to Declaration for FFI type paths](docs/changelog/2026-10-30_0.3.2.md)**
- **[FFI library namespaces in type annotations](docs/changelog/2026-10-30_0.3.2.md)**
- **[Index Crystal runtime support and preserve argument navigation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Private constants in type reopenings](docs/changelog/2026-10-30_0.3.2.md)**
- **[Portable Windows test fixtures](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bang-property readers resolve correctly](docs/changelog/2026-10-30_0.3.2.md)**
- **[Single-entry program visibility](docs/changelog/2026-10-30_0.3.2.md)**
- **[Annotation-aware name resolution](docs/changelog/2026-10-30_0.3.2.md)**
- **[Namespace-aware bare type references](docs/changelog/2026-10-30_0.3.2.md)**
- **[Subtype guards expose members of narrowed receivers](docs/changelog/2026-10-30_0.3.2.md)**
- **[Grouped assignments in logical conditions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Multiline `macro` argument labels](docs/changelog/2026-10-30_0.3.2.md)**
- **[Completion survives conflicting reopened methods](docs/changelog/2026-10-30_0.3.2.md)**
- **[Guarded AST variable member checks](docs/changelog/2026-10-30_0.3.2.md)**
- **[Cross-file record type resolution](docs/changelog/2026-10-30_0.3.2.md)**
- **[Responds-to guards establish member availability](docs/changelog/2026-10-30_0.3.2.md)**
- **[Guarded members link to compatible declarations](docs/changelog/2026-10-30_0.3.2.md)**
- **[Static finished-hook classes expose typed members](docs/changelog/2026-10-30_0.3.2.md)**
- **[Static finished bodies use normal Crystal highlighting and syntax checks](docs/changelog/2026-10-30_0.3.2.md)**
- **[References inside static finished bodies navigate to their definitions](docs/changelog/2026-10-30_0.3.2.md)**
- **[Projects that lost the stdlib library at startup recover it](docs/changelog/2026-10-30_0.3.2.md)**
- **[No false "Unknown named argument" while the stdlib library is still being
  attached](docs/changelog/2026-10-30_0.3.2.md)**
- **[Go to Definition on a bare call offers the whole overload pool](docs/changelog/2026-10-30_0.3.2.md)**
- **[Overload pools span the whole index scope, not just the project scope](docs/changelog/2026-10-30_0.3.2.md)**
- **[Broken version-manager shims no longer shadow the Crystal SDK](docs/changelog/2026-10-30_0.3.2.md)**
- **[Locked shard dependencies trust shards instead of the version field](docs/changelog/2026-10-30_0.3.2.md)**
- **[Inspect Code no longer stalls on files with deep variable dependencies](docs/changelog/2026-10-30_0.3.2.md)**
- **[Unqualified calls no longer report from partial same-type overload pools](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-hook-generated constructors no longer report false zero-arity errors](docs/changelog/2026-10-30_0.3.2.md)**
- **[Compiler-bound macro `call` parameters no longer report false argument errors](docs/changelog/2026-10-30_0.3.2.md)**
- **[Branch-pinned shard dependencies no longer report phantom version mismatches](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare callees with whitespace-separated regex arguments parse like the compiler](docs/changelog/2026-10-30_0.3.2.md)**
- **[Positional arguments after named arguments are rejected](docs/changelog/2026-10-30_0.3.2.md)**
- **[Overload tie diagnostics are deterministic](docs/changelog/2026-10-30_0.3.2.md)**
- **[Operator overload return types resolve exactly](docs/changelog/2026-10-30_0.3.2.md)**
- **[Indexed postfix receivers continue DOT completion](docs/changelog/2026-10-30_0.3.2.md)**
- **[`::` completion respects the require closure](docs/changelog/2026-10-30_0.3.2.md)**
- **[Named-only parameters reject positional arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Custom and non-table collection index reads resolve through the shared call resolver](docs/changelog/2026-10-30_0.3.2.md)**
- **[Indexed receivers carry element types into dot-call chains](docs/changelog/2026-10-30_0.3.2.md)**
- **[Instance/class-variable record fields are recognized](docs/changelog/2026-10-30_0.3.2.md)**
- **[Keyword-named record fields are recognized](docs/changelog/2026-10-30_0.3.2.md)**
- **[Nilable annotations resolve as unions (`T?` → `T | Nil`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Index reads resolve to the element type instead of the container](docs/changelog/2026-10-30_0.3.2.md)**
- **[False "Missing required argument(s)" from a tight binary operator in a call
  argument](docs/changelog/2026-10-30_0.3.2.md)**
- **[False "Too many arguments" on macro-spliced operator arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[False "Missing required argument(s)" on macro-spliced dot-call names](docs/changelog/2026-10-30_0.3.2.md)**
- **[`{{ }}` interpolates inside strings in macro code](docs/changelog/2026-10-30_0.3.2.md)**
- **[Operator symbols parse as symbols](docs/changelog/2026-10-30_0.3.2.md)**
- **[Brace blocks bind to the nearest call in bare position](docs/changelog/2026-10-30_0.3.2.md)**
- **[Compound assignment in conditions parses](docs/changelog/2026-10-30_0.3.2.md)**
- **[Heredoc bodies interrupting argument lists parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[`& (...)` after an operand reads as binary bitand](docs/changelog/2026-10-30_0.3.2.md)**
- **[Wrapping operators after an operand read as binary](docs/changelog/2026-10-30_0.3.2.md)**
- **[Receiver-less `new` checks against the constructor pool](docs/changelog/2026-10-30_0.3.2.md)**
- **[Endless ranges no longer swallow the next line](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated operators and `%w()` iterables parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[Trailing bare arguments after parenthesized calls are counted](docs/changelog/2026-10-30_0.3.2.md)**
- **[Percent literals inside string interpolation parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[Inspect Code results no longer crash on selection](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-generated type definitions and operator-headed compound methods parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare generic restrictions reach family parameters again](docs/changelog/2026-10-30_0.3.2.md)**
- **[`initialize(@major : Int)` with a typed co-declaration is no longer reported](docs/changelog/2026-10-30_0.3.2.md)**
- **[`Int64[]` is no longer falsely highlighted as an empty array literal](docs/changelog/2026-10-30_0.3.2.md)**
- **[The rename dialog label carries the method header, not the body](docs/changelog/2026-10-30_0.3.2.md)**
- **[The rename dialog target display shows the method header only](docs/changelog/2026-10-30_0.3.2.md)**
- **[The rename dialog item shows the method header name only](docs/changelog/2026-10-30_0.3.2.md)**
- **[Renaming the family member method starts the rename dialog
  directly](docs/changelog/2026-10-30_0.3.2.md)**
- **[A bare implicit-self reader occurrence works as the rename trigger](docs/changelog/2026-10-30_0.3.2.md)**
- **[The same-name method joins the accessor family](docs/changelog/2026-10-30_0.3.2.md)**
- **[Renaming the ivar misses default-valued untyped accessor
  declarations](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare implicit-self reader calls join the accessor rename](docs/changelog/2026-10-30_0.3.2.md)**
- **[The narrowed declaration highlight range mis-scoped composite
  offsets](docs/changelog/2026-10-30_0.3.2.md)**
- **[Highlight usages stay symmetric with the accessor coupling after a
  rename](docs/changelog/2026-10-30_0.3.2.md)**
- **[Accessor declaration highlights marked the type annotation](docs/changelog/2026-10-30_0.3.2.md)**
- **[The accessor declaration rename reference rewrote untyped arguments as
  a no-op](docs/changelog/2026-10-30_0.3.2.md)**
- **[The rename dialog prefills the bare name for sigil-bearing variables](docs/changelog/2026-10-30_0.3.2.md)**
- **[Inplace rename no longer drops the `@`/`@@` sigil on variable renames](docs/changelog/2026-10-30_0.3.2.md)**
- **[Escaped macro statements (`\{% stmt %}` / `\{{ expr }}`) parse](docs/changelog/2026-10-30_0.3.2.md)**
- **[Record declaration defaults are never reported as unused variables](docs/changelog/2026-10-30_0.3.2.md)**
- **[Accessor declarations are never reported as unused variables](docs/changelog/2026-10-30_0.3.2.md)**
- **[Bare calls with parenthesis-free arguments count their argument list](docs/changelog/2026-10-30_0.3.2.md)**
- **[Block bodies of macro invocations are macro data, not runtime calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[A tight bracket after a dot-call method name always binds as the index postfix](docs/changelog/2026-10-30_0.3.2.md)**
- **[Program closure: type resolution sees every file of the containing program](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-call arguments are no longer measured as runtime calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[Proc-literal parameters resolve as local declarations](docs/changelog/2026-10-30_0.3.2.md)**
- **[Loose array argument after a dot-call binds as a call argument, not an index](docs/changelog/2026-10-30_0.3.2.md)**
- **[Array-comma bare calls keep their leading array argument](docs/changelog/2026-10-30_0.3.2.md)**
- **[Range operators never start bare arguments of dot-calls](docs/changelog/2026-10-30_0.3.2.md)**
- **[Variable hovers render the resolved type when overloads agree, and "Unknown" when they do not](docs/changelog/2026-10-30_0.3.2.md)**
- **[Assignment left-hand side hover renders the variable, not a method signature](docs/changelog/2026-10-30_0.3.2.md)**
- **[Macro-controlled method bodies no longer infer Nil returns](docs/changelog/2026-10-30_0.3.2.md)**
- **[Index the compiler source tree and accept bare generics (the ameba-master findings collapse from 101 to 13)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Kemal audit reaches zero findings through generic include-edge instantiation](docs/changelog/2026-10-30_0.3.2.md)**
- **[Reduce kemal inspection findings to one through a dot-call binary-operator fix](docs/changelog/2026-10-30_0.3.2.md)**
- **[Honor Crystal's unary-minus whitespace rule in bare-call arguments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Cut kemal inspection false positives from 24 to 10 through shared-resolution fixes](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse the complete kemal checkout and its shards without errors](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse methods inside `record ... do ... end` declarations](docs/changelog/2026-10-30_0.3.2.md)**
- **[Match Crystal 1.21 abrupt-expression and loop syntax](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse C pointer types in declaration-macro arguments (`property stack_top : Void*`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse postfix modifiers on indexed assignments](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse the `!~` operator and its method definition](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse multi-value `return`, `break`, and `next` statements](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse parameters bound to instance/class variables](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse Crystal out arguments with instance-variable targets](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse word keywords as named argument labels](docs/changelog/2026-10-30_0.3.2.md)**
- **[Eliminate false unused-variable warnings with binding-aware control flow](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse compound assignment on DOT receivers (`config.server ||= value`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse bare regex arguments after DOT calls (`range.match /pattern/`)](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse assignments as control-flow values](docs/changelog/2026-10-30_0.3.2.md)**
- **[Parse type-shaped macro arguments (`property upgrade_handler : (IO ->)?`)](docs/changelog/2026-10-30_0.3.2.md)**

## [0.2.8] — 2026-08-30
### Added
- **[`# language=<id>` comment injection into heredocs and string literals](docs/changelog/2026-08-30_0.2.8.md)**
- **[Embedded language highlighting in heredocs (`<<-SQL`, `<<-JAVASCRIPT`, …)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Go to Declaration on `require` statements opens the required file](docs/changelog/2026-08-30_0.2.8.md)**
### Bug Fixes
- **[Resolve bare calls through the compiler-imposed base hierarchy (`{error: "…"}.to_json`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Restore DOT completion on integer and float literals (`5.<caret>`, `5.5.<caret>`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Fix language injection support registration and stub initialization under module dependencies](docs/changelog/2026-08-30_0.2.8.md)**
- **[Hover on top-level calls shows the real method documentation](docs/changelog/2026-08-30_0.2.8.md)**
- **[Resolve unqualified calls in macro context to macros (`{{ run("…") }}`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Parse heredoc calls inside argument lists — including multiple heredocs (`assert_diff(<<-FIRST, <<-SECOND)`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Parse empty brace blocks after method calls (`Thread.new { }`, `spawn { }`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Restore Hash default-value constructors (`Hash(String, Int32).new(0)`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Accept initializer-backed constructor overloads beside explicit `self.new` (`Deque(Int32).new`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Resolve constructors in stdlib types that define setters (`URI.new("http", "localhost", 9999)`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Resolve constructors on stdlib types that generate methods through macros (`HTTP::Client.new`)](docs/changelog/2026-08-30_0.2.8.md)**
- **[Parse char literals inside string interpolation and macro control blocks](docs/changelog/2026-08-30_0.2.8.md)**
- **[Parse assignments in postfix modifier conditions](docs/changelog/2026-08-30_0.2.8.md)**
- **[Parse special global variables inside string interpolation](docs/changelog/2026-08-30_0.2.8.md)**
## [0.2.7] — 2026-08-25
### Bug Fixes
- **[Restore ECR Structure View and spec test locator through main-descriptor registrations](docs/changelog/2026-08-25_0.2.7.md)**
- **[Fix 14 Marketplace verifier binary-incompatibility problems against RubyMine RM-262](docs/changelog/2026-08-25_0.2.7.md)**
- **[Consolidate the DAP debug adapter registration](docs/changelog/2026-08-25_0.2.7.md)**
### Added
- **[Descriptor and runtime registration regression coverage](docs/changelog/2026-08-25_0.2.7.md)**
## [0.2.5] — 2026-08-25
### Changed
- **[Compile target raised to IntelliJ 2026.2 (build 262)](docs/changelog/2026-08-25_0.2.5.md)**
- **[README reorganized around installation and core user features](docs/changelog/2026-08-25_0.2.5.md)**
### Bug Fixes
- **[Zero scheduled-for-removal and zero deprecated API usages](docs/changelog/2026-08-25_0.2.5.md)**
- **[Optional module dependencies moved into extension fragments](docs/changelog/2026-08-25_0.2.5.md)**
### Added
- **[Structure view stub element types comply with the 2026.2 holder contract](docs/changelog/2026-08-25_0.2.5.md)**
## [0.2.4] — 2026-08-25
### Bug Fixes
- **[Replace internal Structure View API so Marketplace upload passes verification](docs/changelog/2026-08-25_0.2.4.md)**
- **[Make the `intellij.platform.smRunner` module dependency optional](docs/changelog/2026-08-25_0.2.4.md)**
### Changed
- **[ECR Structure View: three sections merged into two tabs](docs/changelog/2026-08-25_0.2.4.md)**
## [0.2.3] — 2026-08-24
### Added
- **[Embedded Crystal (ECR) support](docs/changelog/2026-08-24_0.2.3.md)**
- **[JFlex outer-splitter lexer](docs/changelog/2026-08-24_0.2.3.md)**
- **[BNF parser](docs/changelog/2026-08-24_0.2.3.md)**
- **[Layered editor highlighter](docs/changelog/2026-08-24_0.2.3.md)**
- **[Template language infrastructure](docs/changelog/2026-08-24_0.2.3.md)**
- **[3-section Structure View](docs/changelog/2026-08-24_0.2.3.md)**
- **[HTML always activated](docs/changelog/2026-08-24_0.2.3.md)**
- **[`<%>` file icon](docs/changelog/2026-08-24_0.2.3.md)**
- **[Lexer fix for `%` in tag content](docs/changelog/2026-08-24_0.2.3.md)**
- **[Crystal language injection inside `<% %>` tags](docs/changelog/2026-08-24_0.2.3.md)**
### Fixed
- **[Declare SM Test Runner dependency for binary compatibility with RubyMine and other IDEs](docs/changelog/2026-08-24_0.2.3.md)**
- **[Type-check splat and double-splat expansion against real parameters](docs/changelog/2026-08-24_0.2.3.md)**
- **[Infer crystal NamedTuples for `{k: v}` literals and match them against `{k: T, ...}` parameters](docs/changelog/2026-08-24_0.2.3.md)**
- **[Stop binding chained dot expressions as bare arguments](docs/changelog/2026-08-24_0.2.3.md)**
- **[Restore constructor resolution for classes containing `private module`/`private enum`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Flag multi-line unions without backslash continuation at the dangling pipe](docs/changelog/2026-08-24_0.2.3.md)**
- **[Respect require-graph visibility for unqualified calls and name fallbacks](docs/changelog/2026-08-24_0.2.3.md)**
- **[Stop leaking stdlib overloads into dot-call type checks](docs/changelog/2026-08-24_0.2.3.md)**
- **[Stop flagging bracket access after dot-calls as arguments](docs/changelog/2026-08-24_0.2.3.md)**
- **[Restore full `String` completion and indexing](docs/changelog/2026-08-24_0.2.3.md)**
- **[Show method documentation for DOT-calls on literal receivers](docs/changelog/2026-08-24_0.2.3.md)**
- **[Infer types for percent literals (`%w`, `%i`, `%r`, `%q`, …)](docs/changelog/2026-08-24_0.2.3.md)**
- **[Restore full `Enumerable` method completion on arrays and collections](docs/changelog/2026-08-24_0.2.3.md)**
- **[Preserve trailing stdlib wildcard dependencies after macro-generated call arguments](docs/changelog/2026-08-24_0.2.3.md)**
- **[Resolve remaining adversarial require and SDK edge cases](docs/changelog/2026-08-24_0.2.3.md)**
- **[Close canonical wildcard and Windows SDK release gates](docs/changelog/2026-08-24_0.2.3.md)**
- **[Close final require release gates](docs/changelog/2026-08-24_0.2.3.md)**
- **[Match Crystal 1.20.3 require loading exactly](docs/changelog/2026-08-24_0.2.3.md)**
- **[Make SDK-backed tests deterministic](docs/changelog/2026-08-24_0.2.3.md)**
- **[Harden require-aware analysis for real source edits and path syntax](docs/changelog/2026-08-24_0.2.3.md)**
- **[Keep require graph readers coherent under concurrency and shared dependencies](docs/changelog/2026-08-24_0.2.3.md)**
- **[Preserve lexical DOT receiver identity across unrequired collisions](docs/changelog/2026-08-24_0.2.3.md)**
- **[Restore realistic stdlib DOT completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Protect the prelude from project shadowing](docs/changelog/2026-08-24_0.2.3.md)**
- **[Preserve prelude resolution across escaped relative dependencies](docs/changelog/2026-08-24_0.2.3.md)**
- **[Fix `def self.<keyword>` rendering as method body source](docs/changelog/2026-08-24_0.2.3.md)**
- **[Avoid repeated require-closure traversal on clean completion queries](docs/changelog/2026-08-24_0.2.3.md)**
- **[Avoid require-graph sessions outside DOT completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Preserve pre-existing require-completion fixture paths](docs/changelog/2026-08-24_0.2.3.md)**
- **[Avoid repeated require fingerprint PSI walks on analysis hot paths](docs/changelog/2026-08-24_0.2.3.md)**
- **[Reject compound require paths during dependency collection](docs/changelog/2026-08-24_0.2.3.md)**
- **[Preserve qualified constructor identity in navigation fallback](docs/changelog/2026-08-24_0.2.3.md)**
- **[Make expression DOT completion usable with real Crystal stdlib metadata](docs/changelog/2026-08-24_0.2.3.md)**
- **[Navigate every exact DOT-call overload](docs/changelog/2026-08-24_0.2.3.md)**
- **[Apply shared constructor precedence to DOT completion type objects](docs/changelog/2026-08-24_0.2.3.md)**
- **[Keep expression DOT completion exact across namespace collisions](docs/changelog/2026-08-24_0.2.3.md)**
- **[Make expression receiver analysis follow Crystal execution and scope semantics](docs/changelog/2026-08-24_0.2.3.md)**
- **[Resolve transparent parenthesized and generic DOT-call receivers](docs/changelog/2026-08-24_0.2.3.md)**
- **[Validate exact DOT-calls against method signatures](docs/changelog/2026-08-24_0.2.3.md)**
- **[Type-check constructor shorthand parameters](docs/changelog/2026-08-24_0.2.3.md)**
- **[Exclude file-level self-receiver methods from global completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Parse and diagnose nested `require` expressions correctly](docs/changelog/2026-08-24_0.2.3.md)**
- **[Prevent exponential parser replay in nested Spec DSL files](docs/changelog/2026-08-24_0.2.3.md)**
- **[Restore responsive Go to Symbol searches](docs/changelog/2026-08-24_0.2.3.md)**
- **[Block parameter rename/reference resolution](docs/changelog/2026-08-24_0.2.3.md)**
### Changed
- **[Keep each DOT completion on one source snapshot](docs/changelog/2026-08-24_0.2.3.md)**
- **[Make shared type and hierarchy resolution require-aware](docs/changelog/2026-08-24_0.2.3.md)**
- **[Cache require-aware effective source snapshots](docs/changelog/2026-08-24_0.2.3.md)**
- **[Extract static require dependencies from PSI](docs/changelog/2026-08-24_0.2.3.md)**
- **[Complete DOT expressions through shared receiver analysis](docs/changelog/2026-08-24_0.2.3.md)**
- **[Prepare union receiver method candidates for shared dispatch](docs/changelog/2026-08-24_0.2.3.md)**
- **[Centralize scoped expression receiver analysis](docs/changelog/2026-08-24_0.2.3.md)**
- **[Share conservative DOT receiver normalization through neutral PSI infrastructure](docs/changelog/2026-08-24_0.2.3.md)**
- **[Split completion responsibilities into focused providers](docs/changelog/2026-08-24_0.2.3.md)**
- **[Use scoped StubIndex navigation and centralized lookups](docs/changelog/2026-08-24_0.2.3.md)**
- **[Strengthen indexed navigation regression coverage](docs/changelog/2026-08-24_0.2.3.md)**
- **[Correct constructor fallback ambiguity coverage](docs/changelog/2026-08-24_0.2.3.md)**
- **[Split Parameter Info call analysis into focused helpers](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `DefaultLiveTemplatesProvider`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `supportsPossessiveQuantifiers()`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `FileChooserDescriptorFactory.createSingleFileDescriptor()`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `addBrowseFolderListener(title, desc, project, descriptor)`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `ReadAction.run()`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Replace deprecated `GeneratorPeerImpl.getComponent()`](docs/changelog/2026-08-24_0.2.3.md)**
- **[Remove Object fallback from dot-completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Show parameter signatures for class methods in free-text completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Suggest `initialize` in class method completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Suggest file-level constants in free-text completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Suggest top-level (global) methods in free-text completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Instance/class variable `@` completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Prevent endless first-open stdlib indexing](docs/changelog/2026-08-24_0.2.3.md)**
- **[`require` statement and path completion](docs/changelog/2026-08-24_0.2.3.md)**
- **[Suggest class constants after `::`](docs/changelog/2026-08-24_0.2.3.md)**
## [0.1.17] — 2026-07-05
### Enhancements
- **[Keyword block highlighting](docs/changelog/2026-07-05_0.1.17.md)**
- **[Variable hover type info](docs/changelog/2026-07-05_0.1.17.md)**
- **[Numeric type linking](docs/changelog/2026-07-05_0.1.17.md)**
- **[Hash/tuple type inference](docs/changelog/2026-07-05_0.1.17.md)**
- **[Array type deduplication](docs/changelog/2026-07-05_0.1.17.md)**
- **[Ternary type inference](docs/changelog/2026-07-05_0.1.17.md)**
- **[Method return type inference from body](docs/changelog/2026-07-05_0.1.17.md)**
### Bug Fixes
- **[Fix false argument count with operators in call arguments](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix false "Value assigned never used" with conditional reassignment](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix false positive "Too many arguments" on parameter variables](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix `::` namespace not recognized inside string interpolation](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix range with omitted start in bracket access](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix Parameter Info (Ctrl+P) for DOT-calls after method calls](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix type checking for bare DOT-call arguments](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix Go to Definition (Ctrl+B) freezing on top-level bare method calls](docs/changelog/2026-07-05_0.1.17.md)**
- **[Hover documentation for DOT-call methods](docs/changelog/2026-07-05_0.1.17.md)**
- **[Unified DOT-call resolution via `CrystalDotCallReference`](docs/changelog/2026-07-05_0.1.17.md)**
- **[Parameter hover popups](docs/changelog/2026-07-05_0.1.17.md)**
- **[Definition hover popups](docs/changelog/2026-07-05_0.1.17.md)**
- **[Namespace access Go to Definition and hover](docs/changelog/2026-07-05_0.1.17.md)**
- **[Disambiguation for nested classes with same name](docs/changelog/2026-07-05_0.1.17.md)**
- **[Auto-completion popup for `::`](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix postfix `?` after bracket access on method calls](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix rescue clause parsing for typed rescue](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix implicit object bracket access (`&.[]`)](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix string interpolation in percent literals](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix string interpolation in regex literals](docs/changelog/2026-07-05_0.1.17.md)**
- **[Fix string interpolation in backtick command literals](docs/changelog/2026-07-05_0.1.17.md)**
### Changed
- **[Improved documentation hover format for DOT-call methods and class types](docs/changelog/2026-07-05_0.1.17.md)**
- **[Remove FileTypeIndex fallback from Go to Definition](docs/changelog/2026-07-05_0.1.17.md)**
## [0.1.16] — 2026-06-25
### Added
- **[Colon spacing inspection](docs/changelog/2026-06-25_0.1.16.md)**
- **[`@param` shorthand support](docs/changelog/2026-06-25_0.1.16.md)**
- **[Record macro support](docs/changelog/2026-06-25_0.1.16.md)**
- **[Type checking for record macro parameters](docs/changelog/2026-06-25_0.1.16.md)**
- **[Parameter completion priority boost](docs/changelog/2026-06-25_0.1.16.md)**
- **[Keywords as method/macro names](docs/changelog/2026-06-25_0.1.16.md)**
- **[Annotation definitions inside module/class bodies](docs/changelog/2026-06-25_0.1.16.md)**
- **[Global namespace prefix in expressions](docs/changelog/2026-06-25_0.1.16.md)**
- **[Overloaded methods in completion](docs/changelog/2026-06-25_0.1.16.md)**
### Bug Fixes
- **[Fix rename prefix handling for instance/class variables](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix macro body depth tracking for postfix control flow](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix type check and parameter info for DOT-calls on constants](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix false positive "Unknown named argument" on class constructor DOT-calls](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix `case ... end.tap do` parse error](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix `?`/`!` suffix after macro interpolation](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix multi-level pointer types in lib bindings](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix postfix modifier after compound assignment](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix greedy IDENTIFIER consumption in argument and bare_argument rules](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix splat prefix detection in argument extraction](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix record macro parameter priority](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix colon spacing inspection scope](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix binary `+`/`-` parsed as bare method arguments](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix Go to Definition on `.new`](docs/changelog/2026-06-25_0.1.16.md)**
### Changed
- **[Method lookup elements use PSI object identity](docs/changelog/2026-06-25_0.1.16.md)**
- **[Force Re-index button fixed](docs/changelog/2026-06-25_0.1.16.md)**
- **[Force Re-index shows progress](docs/changelog/2026-06-25_0.1.16.md)**
### Bug Fixes
- **[Fix operator method name resolution](docs/changelog/2026-06-25_0.1.16.md)**
- **[Fix ENV.fetch completion](docs/changelog/2026-06-25_0.1.16.md)**
## [0.1.15] — 2026-06-14
### Changed
- **[Parameter info (Ctrl+P) for `ClassName.new`](docs/changelog/2026-06-14_0.1.15.md)**
- **[Type check for `ClassName.new`](docs/changelog/2026-06-14_0.1.15.md)**
### Bug Fixes
- **[Fix `new` resolution using wrong class](docs/changelog/2026-06-14_0.1.15.md)**
## [0.1.13] — 2026-06-14
### Added
- **[Code folding for multi-line arrays and hashes](docs/changelog/2026-06-14_0.1.13.md)**
- **[Improved code folding for all block constructs](docs/changelog/2026-06-14_0.1.13.md)**
### Changed
- **[Suppress completion after numeric literals](docs/changelog/2026-06-14_0.1.13.md)**
### Bug Fixes
- **[Fix nil-safe index (`?`) breaking parser](docs/changelog/2026-06-14_0.1.13.md)**
## [0.1.12] — 2026-06-12
### Changed
- **[Minimum IDE version](docs/changelog/2026-06-12_0.1.12.md)**
## [0.1.11] — 2026-06-12
### Bug Fixes
- **[Improved debugger compatibility on Windows](docs/changelog/2026-06-12_0.1.11.md)**
## [0.1.9] — 2026-06-12
### Bug Fixes
- **[Windows support for debugger](docs/changelog/2026-06-12_0.1.9.md)**
## [0.1.6] — 2026-06-08
### Parser
- **[GrammarKit BNF parser](docs/changelog/2026-06-08_0.1.6.md)**
- **[Generics](docs/changelog/2026-06-08_0.1.6.md)**
- **[Macros](docs/changelog/2026-06-08_0.1.6.md)**
- **[Union types](docs/changelog/2026-06-08_0.1.6.md)**
- **[Proc/Lambda](docs/changelog/2026-06-08_0.1.6.md)**
- **[Pattern matching](docs/changelog/2026-06-08_0.1.6.md)**
- **[Lib bindings](docs/changelog/2026-06-08_0.1.6.md)**
- **[Annotations](docs/changelog/2026-06-08_0.1.6.md)**
- **[Operators](docs/changelog/2026-06-08_0.1.6.md)**
- **[String handling](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parameters](docs/changelog/2026-06-08_0.1.6.md)**
- **[Other](docs/changelog/2026-06-08_0.1.6.md)**
- **[Error-tolerant](docs/changelog/2026-06-08_0.1.6.md)**
### Syntax Highlighting
- **[60+ keywords](docs/changelog/2026-06-08_0.1.6.md)**
- **[Operators](docs/changelog/2026-06-08_0.1.6.md)**
- **[Strings](docs/changelog/2026-06-08_0.1.6.md)**
- **[Heredocs](docs/changelog/2026-06-08_0.1.6.md)**
- **[Percent literals](docs/changelog/2026-06-08_0.1.6.md)**
- **[Symbols](docs/changelog/2026-06-08_0.1.6.md)**
- **[Regex](docs/changelog/2026-06-08_0.1.6.md)**
- **[Annotations](docs/changelog/2026-06-08_0.1.6.md)**
- **[Macros](docs/changelog/2026-06-08_0.1.6.md)**
### Semantic Highlighting
- **[PSI annotator](docs/changelog/2026-06-08_0.1.6.md)**
- **[Instance variables](docs/changelog/2026-06-08_0.1.6.md)**
- **[Class variables](docs/changelog/2026-06-08_0.1.6.md)**
- **[Constants](docs/changelog/2026-06-08_0.1.6.md)**
### Code Intelligence
- **[Go to Definition](docs/changelog/2026-06-08_0.1.6.md)**
- **[Go to Symbol](docs/changelog/2026-06-08_0.1.6.md)**
- **[Go to Class](docs/changelog/2026-06-08_0.1.6.md)**
- **[Find Usages](docs/changelog/2026-06-08_0.1.6.md)**
- **[Structure View](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parameter Info](docs/changelog/2026-06-08_0.1.6.md)**
- **[Dot-completion](docs/changelog/2026-06-08_0.1.6.md)**
- **[Free-text completion](docs/changelog/2026-06-08_0.1.6.md)**
- **[Type completion](docs/changelog/2026-06-08_0.1.6.md)**
- **[Stdlib types](docs/changelog/2026-06-08_0.1.6.md)**
- **[Basic type deduction](docs/changelog/2026-06-08_0.1.6.md)**
- **[Instance variable type](docs/changelog/2026-06-08_0.1.6.md)**
### Code Quality
- **[Type checking](docs/changelog/2026-06-08_0.1.6.md)**
- **[Argument count validation](docs/changelog/2026-06-08_0.1.6.md)**
- **[Unused variable detection](docs/changelog/2026-06-08_0.1.6.md)**
- **[Lib fun type annotation](docs/changelog/2026-06-08_0.1.6.md)**
### Editor Features
- **[Code Formatting](docs/changelog/2026-06-08_0.1.6.md)**
- **[Rename Refactoring](docs/changelog/2026-06-08_0.1.6.md)**
- **[Smart Enter](docs/changelog/2026-06-08_0.1.6.md)**
- **[Code Folding](docs/changelog/2026-06-08_0.1.6.md)**
- **[Brace Matching](docs/changelog/2026-06-08_0.1.6.md)**
- **[TODO Indexing](docs/changelog/2026-06-08_0.1.6.md)**
- **[Live Templates](docs/changelog/2026-06-08_0.1.6.md)**
### Run & Debug
- **[Crystal Run](docs/changelog/2026-06-08_0.1.6.md)**
- **[Crystal Build](docs/changelog/2026-06-08_0.1.6.md)**
- **[Crystal Spec](docs/changelog/2026-06-08_0.1.6.md)**
- **[SMTRunner integration](docs/changelog/2026-06-08_0.1.6.md)**
- **[Gutter run icons](docs/changelog/2026-06-08_0.1.6.md)**
- **[Single-test execution](docs/changelog/2026-06-08_0.1.6.md)**
- **[Real-time output](docs/changelog/2026-06-08_0.1.6.md)**
- **[Re-run failed tests](docs/changelog/2026-06-08_0.1.6.md)**
- **[Folder-level running](docs/changelog/2026-06-08_0.1.6.md)**
- **[Navigate to source](docs/changelog/2026-06-08_0.1.6.md)**
- **[Failure propagation](docs/changelog/2026-06-08_0.1.6.md)**
- **[Per-test timing](docs/changelog/2026-06-08_0.1.6.md)**
- **[Duplicate test names](docs/changelog/2026-06-08_0.1.6.md)**
- **[LLDB DAP integration](docs/changelog/2026-06-08_0.1.6.md)**
- **[Breakpoints](docs/changelog/2026-06-08_0.1.6.md)**
- **[Stepping](docs/changelog/2026-06-08_0.1.6.md)**
- **[Variable inspection](docs/changelog/2026-06-08_0.1.6.md)**
- **[Expression evaluation](docs/changelog/2026-06-08_0.1.6.md)**
- **[Crystal formatters](docs/changelog/2026-06-08_0.1.6.md)**
- **[Debug both](docs/changelog/2026-06-08_0.1.6.md)**
### Infrastructure
- **[StubIndex](docs/changelog/2026-06-08_0.1.6.md)**
- **[Generated files committed](docs/changelog/2026-06-08_0.1.6.md)**
- **[Error-tolerant parsing](docs/changelog/2026-06-08_0.1.6.md)**
### Requirements
- **[IntelliJ Platform](docs/changelog/2026-06-08_0.1.6.md)**
- **[Crystal](docs/changelog/2026-06-08_0.1.6.md)**
- **[lldb-dap](docs/changelog/2026-06-08_0.1.6.md)**
### Compatibility
- **[JetBrains IDEs](docs/changelog/2026-06-08_0.1.6.md)**
- **[Incompatible with](docs/changelog/2026-06-08_0.1.6.md)**
- **[Accessor declarations are never reported as unused variables](docs/changelog/2026-06-08_0.1.6.md)**
### Bug Fixes
- **[Bare calls with parenthesis-free arguments count their argument list](docs/changelog/2026-06-08_0.1.6.md)**
- **[Block bodies of macro invocations are macro data, not runtime calls](docs/changelog/2026-06-08_0.1.6.md)**
- **[A tight bracket after a dot-call method name always binds as the index postfix](docs/changelog/2026-06-08_0.1.6.md)**
- **[Program closure: type resolution sees every file of the containing program](docs/changelog/2026-06-08_0.1.6.md)**
- **[Macro-call arguments are no longer measured as runtime calls](docs/changelog/2026-06-08_0.1.6.md)**
- **[Proc-literal parameters resolve as local declarations](docs/changelog/2026-06-08_0.1.6.md)**
- **[Loose array argument after a dot-call binds as a call argument, not an index](docs/changelog/2026-06-08_0.1.6.md)**
- **[Array-comma bare calls keep their leading array argument](docs/changelog/2026-06-08_0.1.6.md)**
- **[Range operators never start bare arguments of dot-calls](docs/changelog/2026-06-08_0.1.6.md)**
- **[Variable hovers render the resolved type when overloads agree, and "Unknown" when they do not](docs/changelog/2026-06-08_0.1.6.md)**
- **[Assignment left-hand side hover renders the variable, not a method signature](docs/changelog/2026-06-08_0.1.6.md)**
- **[Macro-controlled method bodies no longer infer Nil returns](docs/changelog/2026-06-08_0.1.6.md)**
- **[Index the compiler source tree and accept bare generics (the ameba-master findings collapse from 101 to 13)](docs/changelog/2026-06-08_0.1.6.md)**
- **[Kemal audit reaches zero findings through generic include-edge instantiation](docs/changelog/2026-06-08_0.1.6.md)**
- **[Reduce kemal inspection findings to one through a dot-call binary-operator fix](docs/changelog/2026-06-08_0.1.6.md)**
- **[Honor Crystal's unary-minus whitespace rule in bare-call arguments](docs/changelog/2026-06-08_0.1.6.md)**
- **[Cut kemal inspection false positives from 24 to 10 through shared-resolution fixes](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse the complete kemal checkout and its shards without errors](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse methods inside `record ... do ... end` declarations](docs/changelog/2026-06-08_0.1.6.md)**
- **[Match Crystal 1.21 abrupt-expression and loop syntax](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse C pointer types in declaration-macro arguments (`property stack_top : Void*`)](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse postfix modifiers on indexed assignments](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse the `!~` operator and its method definition](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse multi-value `return`, `break`, and `next` statements](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse parameters bound to instance/class variables](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse Crystal out arguments with instance-variable targets](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse word keywords as named argument labels](docs/changelog/2026-06-08_0.1.6.md)**
- **[Eliminate false unused-variable warnings with binding-aware control flow](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse compound assignment on DOT receivers (`config.server ||= value`)](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse bare regex arguments after DOT calls (`range.match /pattern/`)](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse assignments as control-flow values](docs/changelog/2026-06-08_0.1.6.md)**
- **[Parse type-shaped macro arguments (`property upgrade_handler : (IO ->)?`)](docs/changelog/2026-06-08_0.1.6.md)**

---

*This changelog follows [Keep a Changelog](https://keepachangelog.com/) format.*
