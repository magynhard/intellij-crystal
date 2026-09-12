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
parser golden. The lib-body member families are complete; the remaining
macro follow-up is macro-interpolated member names (see below).

Macro forms generate lib members: `{% if flag?(:win32) %} ... {% end %}`
header blocks (Android matrix, OpenSSL, LLVM, libyaml, libxml2),
`{% if %} fun ... {% end %}` single-liners (lib_llvm initialization.cr),
`{% if %} @[Primitive(...)] {% end %}` annotation single-liners
(intrinsics.cr, libm.cr), and `{% if flag?(:arm) %} ... {% end %}` inside
enum bodies (lib_unwind's ReasonCode). The compiler's
`parse_lib_body_exp_without_location` accepts `{% %}` and `{{ }}`
(src/compiler/crystal/syntax/parser.cr:5921-5924; parser_spec.cr:1330-1331)
and `parse_enum_body_expressions` takes both plus `@[...]`
(parser.cr:6360-6365), so all four macro rules join `lib_member` and
`enum_member` exactly like class bodies and statements. `macro_control`
only consumes tokens without reconstructing PSI, so `macro_control_token`
additionally admits declaration keywords (`DEF MACRO FUN ALIAS STRUCT UNION
ENUM LIB MODULE CLASS INCLUDE EXTEND ABSTRACT PRIVATE PROTECTED`) — this
widens consumption only and cannot change existing successful parses.
Generation adds only the four macro-list accessors to `CrystalLibBody` and
`CrystalEnumBody`; no stub change, no stub-version bump. Covered by the
LibMacroForms parser golden (header block, annotation single-liner, enum
block, trailing declaration) and negative tests for unterminated `{%` and
empty `{{}}` (both compiler-verified syntax errors). The external
crystal-repository audit drops from 432 errors in 272 files to 292 errors in
236 files — 36 repaired files fully clean — verified by before/after
file-set comparison with zero newly failing files; `target.cr` advances
from 7 to 6 errors, landing on macro-interpolated fun names (next cluster).
The pinned indexed corpus drops from 159 errors in 117 files to 136 errors
in 109 files (8 repaired files fully clean, zero newly failing files).

Macro-spliced member names mix literal fragments with `{{ }}`:
`fun initialize_{{name}}_target = LLVMInitialize{{target.id}}Target`
(lib_llvm target.cr), `fun {{...}}(...)` with a body (raise.cr),
`def {{mapping[0].id}}=(value)` (io_uring.cr),
`def self.init_{{name}} : Nil` (llvm.cr), and
`{{value.id}} = LibC::{{value.id}}` (errno.cr, op_code.cr). The compiler
parses `{% for %}` bodies as opaque macro text (`parse_macro_body`), so
these names are never validated pre-expansion; the plugin mirrors
`method_name`'s compound alternatives with one shared private
`macro_spliced_name` rule admitted by `lib_fun_name`,
`lib_fun_external_symbol`, `top_level_fun_name`, and `method_name` (with
`[ASSIGN]` for setters, mirroring `IDENTIFIER ASSIGN` leniency).
`enum_constant` takes a leading interpolation for generated constants; the
`!ASSIGN` guard on bare enum interpolation members keeps PEG from
committing the prefix and stranding the `=` (errno.cr). The
`LibC::{{value.id}}` right-hand side reuses the existing interpolated
`type_path_piece`. Stub name fallbacks already report generated names
verbatim without claiming resolution, so no stub change and no
stub-version bump; generation only adds `getMacroInterpolation[List]()`
accessors. Covered by the MacroSplicedNames parser golden (all real shapes
plus trailing declaration), negative tests for empty/unterminated
interpolation in names (compiler-verified), and an inspection test proving
untyped parameters under spliced fun names stay flagged. The external
crystal-repository audit drops from 292 errors in 236 files to 280 errors
in 230 files — 6 repaired files fully clean (target, io_uring, raise,
errno, op_code, llvm) — verified by before/after file-set comparison with
zero newly failing files. The pinned indexed corpus drops from 136 errors
in 109 files to 131 errors in 105 files (op_code, errno, llvm, raise fully
clean, zero newly failing files).

Macro forms work in expression positions: `clock = {% if flag?(:darwin) %}
1 {% else %} 2 {% end %}` right-hand sides, `BIGINT_LIMBS = {{ ... }}`
constants, `property n_threads : Int32 = {% ... %}` defaults,
`when {{ i }}, ...` / `in .{{name.id}}?` case conditions, and
`{{ @type <= T }}` statements. The compiler accepts `{% %}` and `{{ }}`
as atomic expressions (src/compiler/crystal/syntax/parser.cr:1015-1024),
so a private `macro_content_expression` rule joins `primary_expression`
and `bare_primary_expression` after `macro_interpolation_call` (PEG
longest-match-first keeps `{{method.id}} path` binding its arguments).
Two lexer states were missing operators the compiler accepts everywhere:
`//` and `//=` (floor division, `{{ BIGINT_BITS // LIMB_BITS }}`) plus
`=`, `<=`, `>=`, `<=>`, `**`, `<<`, `>>`, `^`, `~` in `MACRO_INTERPOLATION`
(`{{ @type <= T }}`, `{{(value = flag?(...))...}}`), mirrored in
`MACRO_CONTROL` with the matching `macro_control_token` additions.
Generation only adds macro-list accessors to `CrystalExpression` and
`CrystalBareArgument`; no stub change, no stub-version bump. Covered by the
MacroExpressions parser golden (RHS control/interpolation, property
default, case/in conditions, trailing declaration) and negative tests for
empty/unterminated `{{ }}` (compiler-verified; a lone `{% if %}` tag is
complete token soup, so it stays unflagged by design). The external
crystal-repository audit drops from 280 errors in 230 files to 211 errors
in 183 files — 47 repaired files fully clean — verified by before/after
file-set comparison with zero newly failing files; one still-failing file
(`thread_pool.cr`, 1 → 2 errors) advances past its macro gap onto the
receiver-qualified ivar gap (`pointerof(fiber.@context)`, same family as
`scheduler.cr`, `pthread.cr`, `empty-hello-world.cr`). The pinned indexed
corpus drops from 131 errors in 105 files to 100 errors in 87 files (18
repaired files fully clean, zero newly failing files).

Structured `{% if %} A {% else %} B {% end %}` envelopes carry expression
branches: call args (`expect_raises({% if %} A {% else %} B {% end %},
"msg")`), binary operands (`== {% if %} A {% else %} B {% end %} &&`),
named-arg values (`system_exit_status: {% if %} ... code << 8 ... {% else %}
... {% end %}`), and rescue types (`rescue IO::Error{% unless %} |
OpenSSL::SSL::Error{% end %}`, valid inside `{% begin %}` macro bodies).
The open tag must start with IF/UNLESS and at least one ELSE/ELSIF branch
is mandatory, so plain `{% if %} ... {% end %}` blocks,
`{% for %}`/`{% begin %}` loops, and assignment branches (`PLATFORM =
"linux"` is a statement, not an expression) keep parsing as separate
members; statement-level tags always match `macro_control` before
`expression_statement`, so no block regroups there. The envelope is a real
`CrystalMacroIfEnvelope` PSI node — otherwise two branch expressions would
flip `CrystalArgument.getExpression()` into a list; stub-name fallbacks
already cover generated names, so no stub change and no stub-version bump.
Two PEG subtleties were load-bearing: `call_argument` tries the envelope
with newlines-only leading trivia because `macro_argument_trivia` would
otherwise eat the open tag (IntelliJ's PEG does not re-split greedily eaten
trivia after a later failure), and expression-position tags exclude stray
`{% else %}`/`{% elsif %}`/`{% end %}` closers (`macro_open_control`) —
otherwise bare single-token branches swallow the middle tag as a bare-call
argument (`A {% else %}` parsed as `A(else)`). The gated tags alias the
`macro_control` element, so single tags keep their `CrystalMacroControl`
node for macro-depth and require-context consumers. Covered by the
MacroIfEnvelope parser golden (all four real shapes plus trailing
declaration; the MacroExpressions RHS regroups onto the envelope node) and
a negative test for envelopes missing the end tag in calls (the existing
empty/unterminated-`{{ }}` tests cover the interpolation side; a missing
`{% end %}` outside parentheses stays unflagged by design since every tag
is complete). The external
crystal-repository audit drops from 211 errors in 183 files to 203 errors
in 175 files — 8 repaired files fully clean (including 3 bonus socket/client
specs and dragonbox) — verified by before/after file-set comparison with
zero newly failing files; `elf.cr`/`mach_o.cr` advance to nested
`lib`-in-`class` bodies and `process_spec.cr` to `with_env("FOO": "bar")`
string-colon args, both separate families. The pinned indexed corpus drops
from 100 errors in 87 files to 97 errors in 84 files (dragonbox,
http/server, process/status fully clean, zero newly failing files).

String keys work for named arguments: `with_env("FOO": "bar")` (paren and
bare calls with blocks), `with_env "LIB": "foo;;bar"`, and
`NamedTuple("a-b": String)` (a call, so no type-args change was needed).
The compiler accepts plain string labels but rejects interpolated ones and
normalizes spaced colons via formatting, so a private `string_label`
(`(STRING_LITERAL | STRING_ESCAPE)+`, mirroring the lib fun external
symbol) joins `named_argument` and `named_bare_argument` in the compact
form only. Labels stay plain leaves: no lexer, PSI, or stub change, only
`CrystalParser.java` regenerates. `CrystalPsiCallArguments.getNamedLabel`
unquotes string labels so `"FOO":` matches parameter `FOO` instead of
flagging every string-keyed call as an unknown argument (identifier labels
unchanged). Covered by the StringNamedArguments parser golden (paren/bare/
NamedTuple/hash shapes plus trailing declaration), negative tests for
incomplete labels and missing colons, and an inspection test proving
string labels match parameters. The external crystal-repository audit drops
from 203 errors in 175 files to 193 errors in 164 files — 11 repaired files
fully clean — verified by before/after file-set comparison with zero newly
failing files; one still-failing file (`time.cr`, 1 → 2 errors) advances
past its `with_env` line onto the receiver-qualified ivar gap
(`pointerof(buf.value.@privileges)`, same family as `fiber.@context`). The
pinned indexed corpus is unchanged at 97 errors in 84 files (the repaired
spec files sit outside the 650-file index).

Nested `lib` definitions work in type bodies: `class Crystal::System::ELF`
with `lib LibELF` (elf.cr), `class Crystal::System::MachO` with
`lib LibMachO` (mach_o.cr), and `module Crystal` with `lib LibFFI`
(lib_ffi.cr). The compiler parses type bodies with full expression parsing
and only forbids `lib` inside method bodies (`check_not_inside_def` at
parser.cr:1193-1198), so the existing `lib_definition` rule joins
`class_member` (shared by the class/struct/module bodies). `LIB` starts no
other class member, keeping the addition PEG-safe. Only `CrystalParser.java`
plus the generated `CrystalClassBody` lib accessor regenerate: no lexer
change, no new PSI element, no stub-format or index-key change, hence no
stub-version bump. Covered by the NestedLibDefinition parser golden (a lib
member plus a following method proving the `end` binding) and negative tests
for unterminated nested libs and `lib` inside method bodies (still rejected,
matching the compiler). The external crystal-repository audit drops from 193
errors in 164 files to 188 errors in 162 files — `elf.cr` and `mach_o.cr`
fully clean, `lib_ffi.cr` advancing 2 → 1 (its remaining error is the
unrelated `fun prep_closure_loc = ffi_prep_closure_loc(` alias shape) —
verified by before/after file-set comparison with zero newly failing files.
The pinned indexed corpus drops from 97 errors in 84 files to 96 in 84 via
the same `lib_ffi.cr` advance (no file fully clean).

Receiver-qualified `pointerof` targets parse: `pointerof(fiber.@context)`
(scheduler, execution-context and thread-pool families),
`pointerof(s.@c)` (empty-hello-world fixture),
`pointerof(buf.value.@privileges)` (time.cr), `pointerof(action.@sa_mask)`
(pthread.cr, signal.cr), `pointerof(event.@timer)` (iocp.cr), and
`pointerof(cookie.@secure)` (cookie_spec.cr). The compiler parses a full
assignment-level expression in `parse_pointerof` (parser.cr:6069-6087) but
only accepts variables, constants, and read-instance-variables semantically
(`pointerof_var`, main_visitor.cr:2687-2724), so a private `pointerof_target`
rule admits exactly dot chains ending in `.@ivar`, reusing the existing
`dot_call_access` PSI (receiver stays a `CrystalVariableReference`, each link
a `CrystalDotCallAccess`). Ordinary calls, index access, literals, `self`,
and mid-chain ivars (`foo.@bar.baz`) stay rejected. Only
`CrystalParser.java` plus an additive `CrystalDotCallAccess` list on
`CrystalPointerofExpression` regenerate: no lexer change, no new element
type, no stub or index-key change, hence no stub-version bump. Covered by
the PointerofQualifiedInstanceVar parser golden (direct, multi-link, and
multiline targets plus the pre-existing simple targets and a trailing
declaration) and negative tests for `self`, literals, ordinary/argument calls,
index access, and mid-chain ivars. The external crystal-repository audit
drops from 188 errors in 162 files to 174 errors in 154 files — 8 repaired
files fully clean — verified by before/after file-set comparison with zero
newly failing files; `scheduler.cr` advances 2 → 1 onto the unrelated
`&->@stack_pool.collect_loop` proc shape (separate family). The pinned
indexed corpus drops from 96 errors in 84 files to 92 in 82
(`scheduler.cr`/`thread_pool.cr` clean). `pointerof(LibFFI.ffi_type_void)`
(lib external variable) stays failing in both corpora as a separate family:
syntactically indistinguishable from an ordinary call, it needs a semantic
approach rather than a wider rule.

Proc pointers accept instance/class-variable receivers:
`&->@stack_pool.collect_loop` (scheduler.cr:257). The compiler requires a dot
plus method name after the variable in `parse_fun_pointer`
(parser.cr:2073-2088), so `proc_literal` gains a dedicated
`ARROW (INSTANCE_VAR | CLASS_VAR) DOT name` alternative with an optional type
list, placed before the plain identifier/constant pointer branch. Raw leaves
keep the existing token-based pointer shape: only `CrystalParser.java`
regenerates — no lexer change (`&->` already lexes as `AMPERSAND` + `ARROW`),
no generated PSI churn, no stub or index change, hence no stub-version bump.
Covered by the ProcPointerVariableReceivers parser golden (ivar, cvar, type
list, and the real block-pass call shape plus a trailing declaration),
negative tests for bare/literal/numeric receivers, and a lexer test locking
the `& -> @x . name` token sequence. The external crystal-repository audit
drops from 174 errors in 154 files to 173 errors in 153 files
(`scheduler.cr` fully clean) — verified by before/after file-set comparison
with zero newly failing files. The pinned indexed corpus is unchanged at 92
errors in 82 files (`crystal/` sits outside the index).

Macro control works in `case` clause sequences: `case` headers followed by
`{% for ... %}` generating `when {{...}}` or `in .{{...}}?` clauses
(enum.cr, tuple.cr, tracing.cr, token.cr, both interpreter sources,
bio.cr). The compiler only ever sees the expanded clauses, but this grammar
keeps complete tags as plain leaves, so a private `case_clause_trivia` rule
admits newlines, semicolons, and `macro_control` before the first and between
`when`/`in` clauses. At least one real clause stays required, and a
`{% end %}` tag can never stand in for the runtime `END`. Only
`CrystalParser.java` plus an additive `CrystalMacroControl` list on
`CrystalCaseStatement` regenerate: no lexer change, no new element type, no
stub or index change, hence no stub-version bump. Covered by the
MacroCaseClauses parser golden (generated `when` and `in` clauses, macro-`if`
between clauses, runtime `else`, semicolon form, trailing declaration) and
negative tests for statements before the first clause, unterminated tags,
missing `end`, and macro-`end` closing the case. The external
crystal-repository audit drops from 173 errors in 153 files to 163 errors in
149 files — 4 repaired files fully clean (`token.cr`, `tracing.cr`,
`enum.cr`, `tuple.cr`) — verified by before/after file-set comparison with
zero newly failing files; both interpreter sources advance 2 → 1 onto
unrelated gaps (`{{operand.var}}, ip = ...` multi-assign fragment and
`private macro call(...)`). The pinned indexed corpus drops from 92 errors in
82 files to 84 in 79 (3 repaired files fully clean).

Macro-generated splat parameters parse: `def initialize({{
properties.map do |field| ... end.splat }})` (macros.cr `record`) and
`def {{name.id}}({{operands.splat(", ")}}*, node : ASTNode?)` (the
interpreter's per-opcode defs). Two coordinated changes: the
`MACRO_INTERPOLATION` (and string `INTERPOLATION`) lexer states now emit
`do`/`end` keywords — both compiler-valid inside interpolations, both
reserved words, so no identifier lexing can change — giving multi-line
blocks real structure; and a new `parameter` branch binds a
`macro_interpolation` as one `CrystalParameter` only when the fragment ends
in `.splat(...)` (new `isMacroSplatFragment` predicate over the raw token
text), with an optional tight `*` named-only marker. Bare `{{ x }}`
fragments stay syntax errors with unchanged signatures. The argument-count
inspection treats fragment parameters as unknown arity (splat-like) instead
of flagging counts. Regenerated `CrystalLexer.java` (table noise plus the
shared do/end actions) and `CrystalParser.java`; no new PSI element, no
stub or index-key change, hence no stub-version bump. Covered by the
MacroSplatParameters parser golden (verbatim `record` body with a real
`block` node, the `{% for %}`-generated def shape, a one-liner, and a
trailing declaration), negative tests for bare fragments, and an inspection
test proving count suppression. The external crystal-repository audit drops
from 163 errors in 149 files to 160 errors in 147 files (`macros.cr` and
`interpreter/compiler.cr` fully clean) — verified by before/after file-set
comparison with zero newly failing files. The pinned indexed corpus drops
from 84 errors in 79 files to 81 in 77.

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
