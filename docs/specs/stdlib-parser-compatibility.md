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
