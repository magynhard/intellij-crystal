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
failures, while `concurrent.cr` and `http/server/handler.cr` parse cleanly. This
number is diagnostic progress, not an allowlist or an accepted threshold: the
gate remains zero errors.

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

## Release Gates

The indexed corpus reaches zero errors before work moves to the complete
distribution. Final acceptance requires both corpus gates, the full unit suite,
the parser performance canary, and the external kemal inspection audit. The
stub version is incremented once when the newly parsed declarations become part
of persisted indexing semantics.

The pinned download and both zero-error invocations become mandatory CI jobs
only when the indexed corpus reaches zero. Enabling them earlier would make
every unrelated branch fail against a known nonzero baseline.
