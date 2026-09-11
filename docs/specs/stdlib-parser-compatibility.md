# Crystal Standard Library Parser Compatibility

## Goal

The Crystal parser must preserve complete PSI and stub-visible declarations for
valid Crystal 1.21.0 sources. A grammar gap near the start of a type or file can
terminate a PEG repetition and leave every later declaration in an error tail;
downstream resolution and inspections then report unrelated false positives.

The compatibility milestone therefore removes the source grammar gaps instead
of adding broad parser recovery. `recoverWhile`, catch-all grammar alternatives,
error suppression, and parser-error allowlists are forbidden.

## Pinned Distribution

- Version: Crystal 1.21.0
- Official archive: `crystal-1.21.0-1-linux-x86_64-bundled.tar.gz`
- SHA-256: `cc407bd071915cc7b5d9348281e669a911d20a1f4b9fac52a62088660eb22208`
- Download: `https://github.com/crystal-lang/crystal/releases/download/1.21.0/`

The audit validates the corpus `VERSION` file and exact source count. Missing,
different, or incomplete distributions fail before parsing.

## Corpora

### Indexed Standard Library

The first gate uses `CrystalStdlibRoots.enumerate`, the production source-root
policy. It contains every user-facing standard-library source plus the
compiler source tree (shards such as ameba require compiler sources): exactly
650 `.cr` files in the pinned distribution.

### Complete Distribution

The second gate parses every `.cr` file below the distribution source root,
including compiler, runtime, LLVM, GC, and all target-specific C bindings:
exactly 1,625 files. Passing this gate does not add excluded internals to the
IDE's synthetic standard-library roots.

## Audit Contract

`stdlibParseAudit` parses each selected source as a real VFS/PSI file and
collects raw `PsiErrorElement` instances. Highlight filters are not consulted.

```bash
./gradlew stdlibParseAudit \
  -PcrystalStdlibRoot=/path/to/crystal/sources \
  -PcrystalCorpus=indexed

./gradlew stdlibParseAudit \
  -PcrystalStdlibRoot=/path/to/crystal/sources \
  -PcrystalCorpus=distribution
```

The task always reparses the external corpus and writes:

- `build/reports/stdlib-parse-audit/<scope>/report.txt`
- `build/reports/stdlib-parse-audit/<scope>/errors.tsv`

The report includes every error, the first error and remaining tail size per
file, total bytes and elapsed time, and the slowest files. Success requires zero
errors without exceptions.

The initial indexed-corpus baseline contained 178 errors in 127 files. The
keyword-label repair reduced it to 177 errors in 125 files; `fiber.cr` and
`fiber/execution_context.cr` now advance past their former `property next`
failures, while `concurrent.cr` and `http/server/handler.cr` parse cleanly. The
out-argument repair reduced the indexed corpus further to 175 errors in 123
files and the complete distribution from 2,671 errors in 758 files to 2,666
errors in 753 files. `big/big_float.cr` and `big/big_rational.cr` now parse
cleanly, while `big/big_int.cr` advances from its constructor's `out @mpz` to
the next independent macro-interpolated method-signature gap at line 717.
These numbers are diagnostic progress, not an allowlist or an accepted
threshold: the gate remains zero errors.

The external-storage-parameter repair reduced the indexed corpus to 163 errors
in 118 files and the complete distribution to 2,649 errors in 746 files.
Constructs such as `calculation @calculation_time`, `at_end @string`, and
`verify @expected_crc32` no longer terminate their parameter lists. Prefixed
storage shorthand such as `&@handler`, `*@values`, and `**@@options` is accepted
as well; affected files either parse cleanly or advance to a later independent
syntax gap.

The multi-value abrupt-statement repair reduced the indexed corpus to 157 errors
in 110 files and the complete distribution to 2,638 errors in 733 files.
`return`, `break`, and `next` accept ordered comma-separated assignment/expression
values, including newline continuations. Multi-value returns infer a tuple type, every value
participates in local-use analysis, and heredoc bodies remain attached to the
abrupt statement. Heredoc interpolation runs at its header's value position,
including headers nested in an assignment RHS; later values cannot affect it.
Postfix conditions retain only the condition-false state on the fallthrough path,
so assignments in the abrupt values do not leak past the statement. Files such as
`channel/select.cr`, `io/stapled.cr`, and
`mime/media_type.cr` now parse cleanly; `hash.cr` advances to later independent
syntax gaps.

The `!~` operator repair reduced the indexed corpus to 156 errors in 109 files
and the complete distribution to 2,637 errors in 732 files. `!~` is one token in
regular, interpolated, and macro expressions, has comparison precedence, is a
valid operator method name, and can be called explicitly after a DOT. Type
inference remains unknown without exact overload resolution because Crystal
allows `!~` methods to return arbitrary types. This removes the sole parser error
from `object.cr`, preserving every declaration after `Object#!~` for PSI and stub
indexing.

The postfix indexed-assignment repair reduced the indexed corpus to 145 errors
in 104 files and the complete distribution to 2,623 errors in 725 files. Indexed
writes own trailing `if`, `unless`, and `rescue` modifiers while nested RHS
assignments remain modifier-free. Type and local-use flow preserve conditional
and short-circuit skip paths and source-ordered failures from indexed targets,
compound getters, RHS expressions, and setters. The external kemal inspection
audit remains stable at 19 known findings.

The pointer-suffixed type-argument repair reduced the indexed corpus to 133
errors in 97 files (`fiber/context.cr`, `float/fast_float/ascii_number.cr`,
`regex/pcre2.cr`, and macro-metaclass signatures in `io/byte_format.cr`,
`json/pull_parser.cr`, `random.cr`, and `random/secure.cr` parse cleanly;
`stackwalk.cr` and `float_common.cr` advance to later independent gaps). A
non-consuming source-text predicate
separates spaced `name : Type` declaration-macro arguments from compact
`name: value` expressions before PEG alternative selection, so complete pointer
types are consumed without stealing multiline multiplications or their
statement-separating newline. Compact named values retain absolute namespace
expressions such as `level: ::Socket::Protocol::TCP`, including binary and
ternary continuations. The complete distribution reaches 2,625 errors in 714
files. The raw count reflects newly visible downstream gaps in files that now
parse further: complete proc and static-array suffixes remove earlier errors and
then expose independent later gaps in some of those same files. No previously
clean file regressed, verified by a before/after file-set comparison. The
external kemal inspection audit improves from 19 to 18 known findings.

Rejecting unsupported trailing `while`/`until` modifiers while preserving block
loop expressions leaves both Crystal 1.21 corpora unchanged: 133 errors in 97
indexed files and 2,625 errors in 714 distribution files. This confirms that no
valid corpus construct depended on the historical trailing-modifier acceptance.
The external kemal inspection audit remains stable at 18 known findings.

Record declarations with `do ... end` bodies use type-body grammar rather than
ordinary call-block grammar, so declarations such as `def matches?` and
`def self.new` remain structured members. The record-specific alternative is
selected only when the unqualified callee text is exactly `record` and accepts
both the bare (`record Name, field : Type`) and parenthesized
(`record(Name, field : Type)`) argument forms plus qualified names
(`record Registry::Entry, ...`). Ordinary method blocks remain statement
bodies and cannot acquire type declarations. Record field extraction is
unified: bare and parenthesized argument shapes share one accessor, so
constructor signatures, Parameter Info, and argument diagnostics treat both
spellings identically.
The indexed corpus drops to 128 errors in 92 files, with the five affected files
advancing past their record bodies and no previously clean file regressing.
Methods in a record body are indexed under the record's qualified type rather
than as top-level methods, including records nested in classes or modules; a
type nested inside a record body keeps its own ownership. Record constructor
completion retains its exact identity. The changed method-stub serialization
increments the file stub version.

The kemal external-project repair parses the full kemal checkout — `src`,
`spec`, `examples`, and the installed shards under `lib/` (radix,
exception_page, backtracer, ameba), 457 files — with zero `PsiErrorElement`.
The audit gains an unpinned `external` corpus mode: it collects every `.cr`
file below the given root, skips VERSION and file-count validation, and reuses
the same report format. The repair touches roughly twenty syntax families,
each compiler-validated against Crystal 1.21.0: comma-separated proc types
(`alias H = A, B ->`, block parameters typed `T1, T2 ->`); typed collection
literals (`HTTP::Headers{...}`); macro-interpolated type segments and namespace
paths (`Crystal::{{ name }}`, `Int{{ n }}` returns, `def to_i{{ n }}` and
`def {{ type.id }}_{{ method.id }}(params)` compound generated names);
`@{{ method.id }}` instance-variable interpolations; backtick commands and
regex literals inside `{{ }}`/`{% %}` (mirrored lexer states plus `=~` and
range tokens); proc literals with `do ... end` bodies; bare calls with a
leading array literal followed by more arguments (`only ["/a"], "POST"`);
nested indexed compound assignments (`x = @q[i] ||= v`) and constant indexed
assignment targets (`ENV["X"] = v`); the `end`-labeled positional parameter
(`def pos(location, end end_pos = false)`); assignment-shaped bare arguments
(`record R, a = 1`); control-expression named-argument values (`skip: if ...`);
grouped expressions with postfix modifiers (`(ifs if ifs.size > 1)`); case
when-entries that bind assignments (`when path = f(x)`); safe-navigation calls
with bare arguments and macro-interpolated targets (`&.size 1`, `&.{{ m.id }}?`);
tight heredoc-body-opener guards so `fail <<-MSG, file, line` and tuple-carried
heredocs (`.should eq({<<-PRE.lines, <<-POST.lines ... })`) bind bodies at the
owning construct; tight-fragment concatenation for macro-generated names
(`Crystal::{{ type.capitalize }}Def`) while `{{ method.id }} path` stays an
argument; bare regex arguments after nested callees and keywords
(`x.should match /re/`, `when /^get_/`); whitespace-separated loose-paren
calls with trailing arguments (`HANDLERS.insert (position || @h), ch1`);
`&->` block-pass proc pointers (the lexer no longer folds `&->` into the wrap
minus); and dot-setter chains as nested assignment values. The indexed corpus
drops from 128 errors in 92 files to 115 errors in 78 files and the complete
distribution from 2,625 errors in 714 files to 2,583 errors in 665 files;
no previously clean file regresses. `y = match /abc/` — a bare callee followed
by a whitespace-separated regex without a dotted receiver — remains a known
limitation: Crystal resolves it through parser-level backtracking that a PEG
lexer/parser split cannot reproduce, and it is tracked in `TODO.md`.

The indexed corpus now includes the compiler source tree: ameba (bundled
with kemal) requires `compiler/crystal/syntax/*` and reopens
`Crystal::Location`, whose primary definition — with the initialize — lives
in the compiler tree; without it a shard's reopening becomes the only
indexed declaration and every constructor call resolves to an empty pool
("expected at most 0, got N"). The indexed corpus grows from 461 to 650
files; the compiler's own parse errors (65 in the distribution corpus) are
now visible in the indexed numbers as well.

A whitespace-separated array after a callee binds as a call argument, not an
index: the dot-call, method-call, and bare-method-call grammars gained a
looseness-guarded bare-array alternative (`CLI.parse_args [...]` — the array
is the call's only argument), while a tight `[` (`config.foo["key"]`) stays
on the index postfix path.

The argument extraction covers the array-comma bare shape: the grammar's
`array_literal COMMA bare_argument_list` alternative keeps the leading array
outside the bare list (bracket-without-comma still binds as an index
postfix), and the shared argument accessor delivers the leading array as the
first argument so two-overload constructors (`AnnotatedSource.new [] of
String, [...]`) see both arguments.

The dot-call bare-argument guard blocks the binary range operators
(`..`, `...`) consistently with the unqualified bare-call path: the range
operators are binary-only in Crystal and always bind to the left expression
(`0.seconds..1.day` in ameba's admonition case ranges is a Range, never
`0.seconds(..1.day)`); Crystal rejects a leading `..` in bare argument
position. The range-as-bare-argument form (`f 1..2`) keeps parsing.

The shared call resolution merges return types across overloads when every
overload declares the same return annotation (all seven `String#gsub`
overloads declare `: String` — the chain type is determinable), while
diverging or missing annotations stay Unknown. Variable hovers distinguish
bound-but-uninferable from unconstrained: a variable with assignment
evidence whose value type is not inferable renders the gray "Unknown"
placeholder; "Any" stays reserved for genuinely unconstrained duck-typed
variables. The assignment left-hand side hovers as the variable itself —
the enclosing-def veto in the identifier walk-up does not reduce the hover
to the raw name.

The argument type check also handles bare unparameterized generics:
`paragraph : Array` is `Array(_)` and accepts every instantiation of the
same base (`Array(String)` — the ameba explain_formatter case), while a
known builtin on the non-generic side stays a definite mismatch
(`Array(String)` is not `String`), mismatched known generic bases are
rejected (`Hash(String, Int32)` is not `Array`), and non-builtin non-generic
sides stay lenient. Named-tuple comparisons keep their definite structural
verdict.

The argument type check gained generic include-edge instantiation: when a
parameter is a generic like `Enumerable(HTTP::Handler)` and the argument is a
generic with a different base like `Array(TestHeaderHandler)`, the checker
walks the argument base's include statements transitively
(`Array(T)` → `Indexable::Mutable(T)` → `Indexable(T)` → `Enumerable(T)`)
through the require-graph lens, substitutes the includer's own type
parameters with the caller's actual type arguments, and accepts when the
reached include's base matches the parameter's generic base with compatible
substituted arguments. Union parameters split and any member accepts; the
traversal only turns definite mismatches into acceptances.

The dot-call and statement-level bare-argument alternatives honor Crystal's
unary-operator whitespace rule: a spaced binary operator blocks the bare
alternatives so `Time.monotonic - start` stays the binary minus, while the
tight minus stays the unary negation of the first bare argument
(`file.seek -ZIP_TAIL_SIZE, IO::Seek::End` in time/location/loader.cr and
`shift -span.to_i, -span.nanoseconds` in time.cr). The refined lookahead
replaced the plain binary-operator guard on both alternatives; the indexed
corpus reaches 114 errors in 77 files.

Lib-body constant assignments parse structurally: `constant_assignment`
(`CONSTANT ASSIGN NLS expression`, already used for top-level constants) was
added to the `lib_member` alternatives. The compiler's
`parse_lib_body_exp_without_location` handles `CONST` + `OP_EQ`
(src/compiler/crystal/syntax/parser.cr:5891) as a plain `Assign`, so no new
rule or lexer state is needed; the rule is PEG-unique among lib members because
no other alternative starts with CONSTANT. This repairs the dominant external
gap: the crystal-lang/crystal checkout audit drops from 2,718 errors in 783
files to 1,029 errors in 510 files, with zero previously-clean files regressing
(before/after file-set comparison). The pinned indexed corpus drops from 180
errors in 127 files to 173 errors in 126 files. Covered by the LibConstants
parser golden. Remaining lib-body gap: `{% ... %}` / `{{ ... }}`
macro-control/interpolation inside `lib` bodies.

Macro-generated type definitions parse structurally: `type_name` (class,
struct, module, enum, alias, annotation) accepts a bare macro interpolation
(`struct {{num.id}}` — primitives.cr:435/480/560, compiler_rt.cr:58/74/173,
log/format.cr, io/byte_format.cr, ast.cr, init.cr, macros.cr), and
`method_name` composes operator heads with interpolations
(`def &{{op.id}}(other : {{int2.id}}) : self` — primitives.cr bitwise
operators; the alternative precedes the bare operator path so PEG does not
commit `&` alone and strand the interpolation). The interpolated type name
reports its compound text verbatim (`{{num.id}}`) the same way macro
compound method names do — macro-generated declarations never claim real
resolution. The indexed corpus drops from 180 errors in 127 files to 175
errors in 126 files (compiler sources included since the 650-file index);
`src/primitives.cr` reaches zero errors, `compiler/crystal/semantic/ast.cr`
and `io/byte_format.cr` improve, and the external crystal-repository audit
loses the same family (2,730 → 2,718 errors in 787 → 783 files).

Bare `yield` with a trailing `if`/`unless` keeps the modifier on the outer
statement: the leading optional expression of `yield_expression_args` no
longer accepts an `if`/`unless`-headed control-flow statement
(`yield_leading_argument` with `!IF !UNLESS`). Previously `return yield
unless ready` (markd `Utils.timer`) parsed the modifier and everything
through the enclosing method's `end` as yield's argument, orphaning every
later declaration with a single error on the next `def`. The compiler parses
`yield if true` the same way (`If(Yield)`, parser_spec.cr:1228), and only
`if`/`unless` are ambiguous — they are the expression-starting postfix
modifier keywords. Parenthesized statement arguments are unaffected
(`yield(if ready then 1 end)` still binds through `argument_list`), as are
comma-separated tails and non-leading positions. The same shape covers
`break`/`next` abrupt values and assignment right-hand sides
(`items = yield if flag`). Covered by the YieldPostfixModifier parser
golden, which shows the trailing declaration as structured PSI. The
external crystal-repository audit drops from 1,029 errors in 510 files to
1,028 errors in 509 files (`lib/markd/src/markd/utils.cr` parses cleanly);
the pinned indexed corpus is unchanged at 173 errors in 126 files.

Raw `%q` literals have no escape sequences: the compiler creates `%q`
(and `%w`, `%i`) with `allow_escapes: false`, so a backslash is literal
content and the char after it lexes normally. The plugin lexer previously
folded every backslash pair into one escape token, so `%q(\)` (reply
`history_spec.cr:103`) consumed its own closer and the literal ran past the
enclosing `describe History do ... end` block; the unpinned `do`-block
alternative then rolled back and reported the error at the block's `do`.
The lexer now tracks `percentAllowEscapes` per opener and, for raw string
literals only, consumes just the backslash and re-lexes the next char — the
`)` still closes and `(` still nests. `%w`/`%i` keep consuming the pair,
matching the compiler's array escape branch, and `%Q`/`%()`/`%r`/`%x` keep
escape semantics. Covered by lexer token tests plus the
PercentLiteralRawBackslash parser golden (raw delimiter shapes with a
trailing declaration). The external crystal-repository audit drops from
1,028 errors in 509 files to 1,023 errors in 504 files
(`lib/reply/spec/history_spec.cr`, `spec/std/http/formdata_spec.cr`,
`spec/std/http/http_spec.cr`, `spec/std/process/utils_spec.cr`, and
`src/crystal/system/win32/file.cr` parse cleanly); the pinned indexed
corpus is unchanged at 173 errors in 126 files.

Any keyword is a valid external parameter name as long as a valid internal
name follows: the compiler lexes keywords as identifier tokens and consumes
the first as the call-site label (`def foo(with foo)` → `Arg(foo,
external_name: "with")`, parser_spec "external names"; verified against
Crystal 1.21.0 for `with`, `end`, and `out`). The parameter rule's leading
position therefore accepts `keyword_identifier` instead of only `IDENTIFIER`
or the special-cased `END`, so `def self.history(with entries = ...)`
(reply `spec_helper.cr`) and `exec_stdio_to_fd(stdio, for dst_io : ...)`
(`src/process.cr:362`) parse; a lone keyword is still rejected because the
second name is mandatory. `parameterNameInfo` recognizes the same leading
keyword as the explicit external name — including `out` in
`def foo(out x)`, matching the compiler — while keywords in type position
(`x : self`) never count because only a leaf preceding the internal-name or
storage leaf qualifies. No stub format changes, so the stub version is
untouched. Covered by the KeywordExternalParameter parser golden, parameter
name unit tests, and an argument-count inspection test proving `with:` is
accepted while `entries:` is still flagged unknown. The external
crystal-repository audit drops from 1,023 errors in 504 files to 1,019
errors in 502 files (`lib/reply/spec/spec_helper.cr` and `src/process.cr`
parse cleanly, verified by before/after file-set comparison with zero newly
failing files); the pinned indexed corpus drops from 173 errors in 126
files to 171 errors in 125 files (its own `process.cr` copy).

Named symbols carry `?`, `!`, and `=` suffixes: the compiler's
consume_symbol folds one trailing `=` into the symbol unless another `=`
follows (`:color=` is `"color="`, `:foo==` is `:foo` + `==`). The plugin
lexer only allowed `?`/`!`, so `:color=` split into `:color` + `=` and
`delegate :color?, :color=, ..., to: @editor` (reply `reader.cr:59`) broke
the bare-argument list at the `=`. The `SYMBOL` macro now takes an optional
trailing `=` in every lexer state that lexes plain symbols, and a shared
helper pushes the `=` back when another `=` follows immediately. Operator
symbols (`:+`, `:[]`, `:==`) stay out of scope: the compiler only produces
them with `wants_symbol` lookahead, which needs its own analysis. Covered
by lexer token tests (`:color=`, `:Constant=`, `:foo==` stays symbol + `EQ`,
`?`/`!` unchanged) and the SetterSymbolArgument parser golden (both real
`delegate` shapes with a trailing definition). The external
crystal-repository audit drops from 1,019 errors in 502 files to 1,015
errors in 499 files (`spec/std/object_spec.cr`, `src/io/hexdump.cr`, and
`src/log/log.cr` parse cleanly; reply `reader.cr` advances past both
`delegate` lines and then fails at its `@editor.width, @editor.height`
member multi-assignment), verified by before/after file-set comparison
with zero newly failing files; the pinned indexed corpus drops from 171
errors in 125 files to 168 errors in 123 files (its own `io/hexdump.cr`
and `log/log.cr` copies).

Multi-assignment targets accept argument-free member accesses:
`@editor.width, @editor.height = Term::Size.size` (reply `reader.cr:214`),
`a.foo, a.bar = 1, 2`, `*a.foo, a.bar = 1`, `a.b.c, d = 1, 2` (all
compiler-verified in parser_spec). A new private `multi_assign_member_target`
(a variable or constant receiver plus at least one dot access) precedes the
plain variable alternative so PEG never commits the receiver alone; splatted
member targets reuse the existing `STAR` prefix without changing established
PSI shapes. Calls with arguments, parentheses, or blocks stay rejected
(`a.foo()`, `a.b {}`, `a.@x` all fail in the compiler too). Predicate-style
member names need no `?`/`!` guard: the compiler itself accepts `a.foo?` as
a target (only the single-target `b? = 1` fails, through a different path),
and keyword/constant member names (`Foo.bar`, `a.Foo`) parse as targets.
The rule is pinned at the `ASSIGN` (element 5), not at the target list:
before the `=` the prefix may still be a plain expression list, and member
targets made that prefix greedier (`transitions.empty?,` in time/tz.cr's
`when transitions.empty?, unix_seconds < ...`, or `foo, bar` inside
`Set{foo, bar}`) — pinning any earlier strands the expression-list fallback
behind the pin and regresses `src/time/tz.cr`. Indexed member targets
(`a[0], a[1] = 1, 2`) stay a separate family. Covered by the
MultiAssignMemberTargets parser golden (real reader excerpt, splats, the
`when`-list shape, trailing definition) and negative tests for
argument/block/ivar targets. The external crystal-repository audit drops
from 1,015 errors in 499 files to 1,005 errors in 494 files
(`spec/std/struct_spec.cr` via the pin fix, `semantic_visitor.cr`,
`formatter.cr`, `win32/process.cr`, `csv/builder.cr`), with further cascade
reductions in `lib_sdl.cr`, the compiler `lexer.cr`, and `fiber.cr`;
reply `reader.cr` still fails later at its `case`/`in`/`then` series, which
now becomes the next isolated family. Verified by before/after file-set
comparison with zero newly failing files; the pinned indexed corpus drops
from 168 errors in 123 files to 163 errors in 120 files
(`semantic_visitor.cr`, `formatter.cr`, `csv/builder.cr`).

Unnamed `lib fun` parameters parse as type-only items:
`fun strerror_r(Int, Char*, SizeT) : Int` (lib_c platform sources), mixed
`fun mixed(Int32, output : Char*, LibC::Timeval*)`, parenthesized proc types
`fun BIO_meth_set_read(BioMethod*, (Bio*, Char*, Int) -> Int)` (openssl
lib_crypto.cr), and trailing `...`. The compiler parses a non-identifier
start as `parse_union_type` with an empty name
(src/compiler/crystal/syntax/parser.cr:5997-6002) and rejects the same shape
for top-level fun (parser_spec.cr:1327); only `fun_definition` uses the new
`lib_fun_parameter_list`, so `def`, `macro`, and top-level `fun` keep the
named `parameter_list`. The list reuses the PARAMETER_LIST element type
(`elementType=parameter_list`), so `CrystalFunDefinition.parameterList` and
the `CrystalParameter` PSI are unchanged; the type-only item is private, and
the lib-fun inspection still flags named untyped parameters
(`fun exit(status)`) while skipping type-only items. One union type per item
mirrors the compiler: proc commas stay list separators, so `Int, Float`
remains two parameters; the parenthesized-proc alternative carries its output
arrow outside the parens per the compiler's paren branch. Covered by the
LibUntypedFunParameters parser golden (real lib_c/openssl shapes, mixed
named/unnamed, varargs, trailing declaration), negative tests for defaults
on unnamed items (`Int32 = 1`) and unnamed items in `def`, and inspection
tests proving type-only items are clean while `output` is still flagged.
The external crystal-repository audit drops from 1,005 errors in 494 files
to 900 errors in 397 files — all 97 repaired files are the lib_c platform
family — verified by before/after file-set comparison with zero newly
failing files; the pinned indexed corpus is unchanged at 163 errors in 120
files (those platform sources sit outside the 650-file index).

External FFI symbol aliases bind through `fun_definition`:
`fun iconv = libiconv(...)` (crystal/lib_iconv.cr), string symbols
`fun realpath = "realpath$DARWIN_EXTSN"(...)`, constant symbols
`fun tlsv1_method = TLSv1_method : SSLMethod` (openssl), and multiline
aliases whose newline binds to the following `(` or `:` per the formatter
spec. The compiler reads the real name after `=` as identifier, constant,
or non-interpolated string
(src/compiler/crystal/syntax/parser.cr:5949-5960; parser_spec.cr:1312-1315)
and rejects interpolation ("interpolation not allowed in fun name"). The
alias segment is optional and scoped to `fun_definition`, so top-level
`fun foo = bar` stays invalid; the symbol rule admits no general
expression, so `bar(Int32)` still binds as the external symbol plus the lib
parameter list. The string target reuses STRING_EXPRESSION PSI without
interpolation support (GrammarKit adds a `getStringExpression` accessor to
`CrystalFunDefinition`; no new node type, no stub change). Uppercase and
keyword local names stay separate families. Covered by the
LibFunExternalAliases parser golden (identifier/constant/string/multiline
targets, named/unnamed params, return type, trailing declaration), negative
tests for interpolated/numeric/symbol/qualified/incomplete targets and
top-level aliases, and an inspection test proving alias parameters stay
checked. The external crystal-repository audit drops from 900 errors in 397
files to 881 errors in 358 files — 39 repaired files, all alias-led —
verified by before/after file-set comparison with zero newly failing files;
8 still-failing files report more localized errors because the alias repair
advances them to the next gap (`@[...]` annotations, `{% %}` macro control,
`$var = symbol` external vars). The pinned indexed corpus drops from 163
errors in 120 files to 163 errors in 119 files (`regex/lib_pcre2.cr` parses
cleanly; `regex/lib_pcre.cr` advances to its `$free = pcre_free` line).

Uppercase `lib fun` names parse through `fun_definition`:
`fun GetConsoleMode(handle : HANDLE, mode : DWORD*) : BOOL` (Windows-MSVC
lib_c), `fun BIO_new(BioMethod*) : Bio*` (openssl, reusing the unnamed
parameter rule), paren-less `fun BIO_get_new_index : Int`, and the combined
shape `fun RtlGenRandom = SystemFunction036(...) : BOOLEAN` (ntsecapi,
reusing the alias rule). The compiler accepts `IdentOrConst` for lib fun
names (src/compiler/crystal/syntax/parser.cr:5940-5945; parser_spec.cr:1322)
while top-level `fun` requires an identifier (parser_spec.cr:1328), so only
`fun_definition` gains the `CONSTANT` alternative — `top_level_fun`, `def`,
and `macro` are untouched, and no lexer, PSI, or stub change results (the
name stays a plain leaf; `CrystalFunDefinition` has no name accessor).
Keyword spellings (`fun select(...)`) stay a separate family: that token set
includes block delimiters like `END` and needs its own negative boundaries.
Covered by the LibFunUppercaseNames parser golden (named/unnamed/paren-less/
alias shapes plus trailing declaration), negative tests for top-level
`fun Foo`, qualified `fun Foo::Bar`, and absolute `fun ::Foo`, and an
inspection test proving unnamed items stay clean while an untyped named
parameter under an uppercase name is still flagged. The external
crystal-repository audit drops from 881 errors in 358 files to 810 errors
in 323 files — 35 repaired files, all Windows-MSVC plus the ntsecapi alias
combo — verified by before/after file-set comparison with zero newly
failing files; one still-failing file (`processthreadsapi.cr`, 2 → 3
errors) advances past three newly parsed declarations to its `{% if %}`
macro-control gap. The pinned indexed corpus is unchanged at 163 errors in
119 files (the Windows sources sit outside the 650-file index; its OpenSSL
copies stop earlier at macro control).

Keyword spellings work as `fun` names: `fun select(nfds : Int, ...) : Int`
(real shape in every platform `lib_c/.../sys/select.cr`), alias targets
(`fun select = c_select(...)`), delimiter-critical `fun end`, and top-level
`fun select`. The compiler keeps word keywords as IDENT tokens, so its
`IdentOrConst`/`check_ident` name checks accept them at all three fun-name
positions (src/compiler/crystal/syntax/parser.cr:5930-5945, 6548-6551);
the plugin lexes them as separate tokens, so private `lib_fun_name` and
`top_level_fun_name` rules admit `keyword_identifier` alongside IDENTIFIER
(and CONSTANT in lib). Operators stay rejected — `keyword_as_method` is
deliberately not used. The alternative is safe against delimiter swallowing
because newlines never match implicitly in the name position: a bare `fun`
cannot consume the next line's `end` as its name (boundary-tested), and
`SELECT` starts no other `lib_member`. Covered by the FunKeywordNames
parser golden (real select shape, alias, `fun end`, top-level select with
body, trailing declaration), boundary tests for next-line `end`,
qualified/absolute names, and operator names, and an inspection test
proving select parameters stay checked. The external crystal-repository
audit drops from 810 errors in 323 files to 778 errors in 307 files — all
16 repaired files are the platform `select` family, each now fully clean —
verified by before/after file-set comparison with zero newly failing files;
the pinned indexed corpus is unchanged at 163 errors in 119 files (no
select source inside the 650-file index).

External vars take plain symbols after `=`: `$free = pcre_free : Void* ->`
(`src/regex/lib_pcre.cr:113`), `$stackbottom = GC_stackbottom : Void*`
(`src/gc/boehm.cr:128`), and keyword spellings (`$select_alias = select`).
The compiler reads the real name as identifier or constant
(`check IdentOrConst`, src/compiler/crystal/syntax/parser.cr:5904-5909;
parser_spec.cr:1318) and rejects anything else — including a newline after
`=` (`next_token_skip_space`, verified against the 1.21.0 compiler) — so
the private `lib_external_symbol` rule admits exactly `IDENTIFIER`,
`CONSTANT`, and `keyword_identifier` next to the legacy string form, with
no `NLS` after `ASSIGN`. The bare-proc type after `:` stays
`type_reference` (its `ARROW` branch already covers `Void* ->`). No lexer,
PSI, or stub change (alias targets stay plain leaves; `CrystalLibExternalVar`
gains no accessor), and no inspection consumes the node yet. Covered by the
extended LibExternalVar parser golden (real pcre/boehm shapes, keyword
alias, legacy string alias, trailing declaration) and negative tests for
numeric/symbol/qualified/incomplete targets, newline after `=`, and a
missing type. The external crystal-repository audit drops from 778 errors
in 307 files to 776 errors in 306 files (`src/regex/lib_pcre.cr` fully
clean); the pinned indexed corpus drops from 163 errors in 119 files to
161 errors in 118 files (`regex/lib_pcre.cr` fully clean) — both verified
by before/after file-set comparison with zero newly failing files.

Annotations attach to lib members: `@[Flags] enum FlockOp` (every platform
`c/sys/file.cr`), `@[Packed]` struct/union (epoll, io_uring),
`@[ReturnsTwice] fun fork` (every platform `c/unistd.cr`), and `@[Raises]
fun __crystal_main` (`src/empty.cr`, `src/crystal/main.cr`). The compiler's
`parse_lib_body_exp_without_location` accepts `@[...]` via `parse_annotation`
(src/compiler/crystal/syntax/parser.cr:5867-5870; parser_spec.cr:1986), so
the existing `annotation_usage` rule joins `lib_member` exactly like class
bodies — no new parser rule, no lexer change. Generation adds only the
`getAnnotationUsageList()` accessor to `CrystalLibBody`/`CrystalLibBodyImpl`;
no stub change, no stub-version bump. Covered by the LibAnnotations parser
golden (enum/struct/fun targets plus trailing declaration) and an inspection
test proving annotated lib fun parameters stay checked. The external
crystal-repository audit drops from 776 errors in 306 files to 432 errors in
272 files — 34 repaired files fully clean, with recovery cascades collapsing
(e.g. android `unistd.cr` 27 → 13) — verified by before/after file-set
comparison with zero newly failing files; 4 still-failing files
(`lib_event2.cr` 7 → 1, `lib_unwind.cr` 3 → 2, android `unistd.cr`,
`io_uring.cr` 2 → 1) advance to the next gap (`{% %}` macro control,
`{{ }}` interpolation). The pinned indexed corpus drops from 161 errors in
118 files to 159 errors in 117 files (`empty.cr` fully clean, `lib_unwind.cr`
3 → 2).

## Fix Requirements

Each repaired syntax family must have a minimized parser golden that contains
the valid construct and a declaration after it. The golden must contain no
`PsiErrorElement` and must show the trailing declaration as structured PSI.
Where the original failure removed indexed declarations, a downstream stub,
resolution, completion, navigation, or inspection test must prove their return.

Grammar changes must respect PEG ordering, keep normal/bare/DOT/macro families
consistent, and regenerate committed lexer/parser sources. Lexer changes must
mirror equivalent interpolation and macro states. Nearby invalid syntax must
not become accepted through a permissive fallback.

Word keywords are valid named argument labels in parenthesized and bare calls
(`trace(for: time)`, `trace for: time`) and in declaration-macro arguments
(`property next : Fiber?`). Operator method names remain excluded from labels.

Out arguments accept a local variable (including `_`) or an instance variable,
with optional newlines after `out`, in both parenthesized and bare calls. Named
arguments may use an out value (`read(target: out @value)`) while `out: value`
remains an ordinary keyword-named argument. Class variables, globals,
constants, literals, and member accesses are not valid out targets. Out values
are valid only for `lib fun` calls; call resolution and diagnostics for FFI
functions remain tracked in `TODO.md`.

Parameters with assignment shorthand carry three distinct names. In
`public_name @internal_name`, `public_name` is the call-site label,
`internal_name` is the local binding available in the method body, and
`@internal_name` is the storage target assigned on entry. The same model applies
to direct `@name`, class-variable `@@name`, explicit `external @@storage`, and
ordinary `external internal` parameters. Call argument matching uses the
call-site name; local resolution, completion, highlighting, and type inference
use the local name. Structural signatures retain the call-site name but ignore
internal local/storage differences that do not change the callable contract.
Rename treats local and storage uses as one symbol within a type, including
multiple shorthand parameters that assign the same instance/class variable.

## Escaped Macro Statements

Crystal's escaped macro forms — `\{% stmt %}` and `\{{ expr }}` — carry a
backslash directly before the opening brace. The compiler consumes the
sequence as verbatim macro-body data (no expansion; see the escape note in
`compiler/crystal/syntax/to_s.cr`); primitives.cr:101/146 use the form
inside `{% if %}` blocks. The lexer pushes the ordinary inner states
(`<MACRO_CONTROL>` / `<MACRO_INTERPOLATION>`) and emits
`MACRO_CONTROL_ESCAPED_BEGIN` / `MACRO_INTERPOLATION_ESCAPED_BEGIN`; the
grammar binds both escaped framings as macro data with no runtime value
(`macro_control_escaped`, `macro_interpolation_escaped`), at the same
statement positions as `macro_control` (top-level, type members,
statements). Covered by the EscapedMacroStatements parser golden.

## Release Gates

The indexed corpus reaches zero errors before work moves to the complete
distribution. Final acceptance requires both corpus gates, the full unit suite,
the parser performance canary, and the external kemal inspection audit. The
stub version is incremented once when the newly parsed declarations become part
of persisted indexing semantics.

The pinned download and both zero-error invocations become mandatory CI jobs
only when the indexed corpus reaches zero. Enabling them earlier would make
every unrelated branch fail against a known nonzero baseline.
