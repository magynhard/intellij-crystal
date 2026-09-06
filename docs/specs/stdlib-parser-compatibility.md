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
policy. It contains every user-facing standard-library source plus the builtin
macro API at `compiler/crystal/macros.cr`: exactly 461 `.cr` files in the pinned
distribution.

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

## Release Gates

The indexed corpus reaches zero errors before work moves to the complete
distribution. Final acceptance requires both corpus gates, the full unit suite,
the parser performance canary, and the external kemal inspection audit. The
stub version is incremented once when the newly parsed declarations become part
of persisted indexing semantics.

The pinned download and both zero-error invocations become mandatory CI jobs
only when the indexed corpus reaches zero. Enabling them earlier would make
every unrelated branch fail against a known nonzero baseline.
