# Headless Inspect Code Audit (external projects)

How the plugin's inspections are run autonomously — without opening a GUI IDE —
against arbitrary external projects (kemal, shards, stdlib trees) to find false
positives and parse gaps in bulk. The command:

```bash
scripts/crystal-inspect-audit.sh /path/to/project
```

## Mechanism

- A preflight requires GNU-compatible `flock`, `git`, `install`, `readlink`,
  `stat`, and `unzip`, plus Python 3, an executable Gradle wrapper, RubyMine
  build 262 or newer, and a readable non-symlinked license key. Override the
  default key path with `RUBYMINE_KEY=/path/to/rubymine.key`.
- `./gradlew buildPlugin` builds the dev plugin zip; the Gradle wrapper
  verifies its pinned distribution checksum. The plugin is installed into an
  isolated RubyMine instance (`AUDIT_HOME`, default `/tmp/opencode/rm-audit`)
  via `-Didea.{config,system,plugins,log}.path` set through
  `RUBYMINE_VM_OPTIONS`. The user's real IDE settings are never modified. The
  license key is copied into the isolated config with mode `0600` and removed
  by an exit trap after success, failure, or interruption.
- The inspection run uses the **classic `inspect` command**
  (`rubymine.sh inspect <project> <profile.xml> <output>`), NOT RubyMine's
  Ruby-oriented `rinspect` variant: against kemal in RM-262, `rinspect` exits
  after reporting that no Gemfile exists, and its `--profile` option rejects
  both file paths and names. The legacy command is language-neutral, accepts a
  profile file, and writes per-inspection XML.
- The inspection run itself happens in two phases. **Phase A** runs the
  offline `inspect` command against a one-file scratch project (inside the
  isolated `AUDIT_HOME`) with the base profile; the run's `.descriptions.xml`
  enumerates every tool class the IDE knows. **Phase B** generates
  `crystal-audit.xml` from that list: every non-Crystal tool is explicitly
  disabled, the ten Crystal inspections (including `CrystalParseError`) are
  explicitly enabled, and the real project is inspected with that generated
  profile. Default-enabled platform tools previously executed their own heavy
  machinery (spell checker, RegExp host, javadoc, database SQL tools), poured
  dozens of noise findings into reports, and crashed the run once (RegExp
  inspection on a bundled `_search.js` of an external repo); the generated
  profile makes Crystal the only executed family and keeps runs deterministic.
- The profile enables all ten Crystal inspections
  (`CrystalTypeMismatch`, `CrystalArgumentCount`, `CrystalUnusedVariable`,
  `CrystalEmptyCollection`, `CrystalLibFunParameterType`,
  `CrystalSingleQuoteString`, `CrystalColonSpacing`, `CrystalInstanceVarType`,
  `CrystalRequireContext`, `CrystalParseError`). `CrystalParseError`
  (`enabledByDefault="false"` in `plugin.xml`) exports raw parser
  diagnostics as inspection problems, so offline reports finally show
  `PsiErrorElement` line/description directly instead of only knock-on
  inspection results; the editor error highlighter is unaffected because the
  inspection is opt-in.
- Results land in `$AUDIT_HOME/out/*.xml`; the script prints every Crystal
  problem as `file:line: description` plus a total. Findings inside non-`.cr`
  files (injected Crystal fragments in markdown fences etc.) are counted
  separately as `NOISE` and excluded from the `.cr` total. Each invocation gets
  a separate IDE log under `$AUDIT_HOME/logs/<run-id>/idea.log` and records the
  target revision, plugin version, and RubyMine build in `audit-metadata.txt`.

## Target preparation and trust boundary

- Use a trusted, disposable checkout. Install its locked runtime and
  development dependencies before starting; dependency installation can run
  third-party hooks and must not happen for an untrusted target or in an
  environment exposing unrelated credentials.
- Git targets must have a `HEAD` revision and a clean worktree. Non-Git
  directories are rejected unless the caller explicitly sets
  `ALLOW_NON_GIT_AUDIT_TARGET=1`. Do not modify the target concurrently.
- Run the external parse preflight before interpreting inspection output:

  ```bash
  ./gradlew stdlibParseAudit -PcrystalCorpus=external -PcrystalStdlibRoot=/path/to/project
  ```

  Require zero `PsiErrorElement`, or record an explicit known baseline when
  zero is not yet achievable.
- The audit isolates RubyMine config, system, plugins, and logs. It is not an
  operating-system sandbox: RubyMine, the development plugin, and target code
  visible to the IDE run with the invoking user's filesystem and network
  access. The RubyMine license is an intentional credential exception; no
  unrelated credentials should be available to the process.
- The script hashes every target file except `.git`, including ignored and
  dependency files, before and after inspection. It refuses to publish a
  report if that snapshot changes. Internal symlinks contribute both their
  link and resolved contents; symlinks that escape the target root or create a
  directory cycle are rejected. For Git worktrees it additionally checks that
  `HEAD` remains the recorded revision and the worktree remains clean.
  This detects persistent changes, not a concurrent transient modification
  restored before the final snapshot; the target must remain quiescent.

## Semantics and limits

- Every run indexes the whole target project plus the Crystal stdlib from a
  fresh `$AUDIT_HOME/system` directory. Reusing that directory produced
  duplicate offline-inspection findings even with a byte-identical plugin, so
  deterministic reports take precedence over a faster warm-cache run.
- An ownership marker, current-UID check, mode `0700`, and exclusive lock
  protect `AUDIT_HOME`; retained report and log trees have group/other access
  removed. The script rejects symlinked or pre-existing unowned directories,
  any overlap with the inspected project or plugin repository, and concurrent
  runs. Inspection output is
  staged under `$AUDIT_HOME/reports`; after the IDE exits successfully and its
  log confirms that the expected plugin version loaded, an atomic symlink swap
  publishes it at `$AUDIT_HOME/out` without hiding the previous report first.
  Earlier successful run directories remain available under `reports/`; they
  can contain source paths and diagnostics and should be retained only as long
  as needed.
- Inspect Code runs inspections, not raw parser errors: since the
  `CrystalParseError` inspection exists, `PsiErrorElement` parse failures ARE
  exported as `CrystalParseError.xml` findings. For pure parse auditing of
  stdlib files the `CrystalStdlibSourceParseTest` canary remains the tool of
  choice.
- Injections are in scope of the platform inspection sweep: Crystal
  inspections can fire inside injected fragments of foreign files (observed:
  `CrystalUnusedVariable` inside `.github/**/*.md` markdown code fences).
  Treat non-`.cr` files as noise when reading results.
- `level` attributes in results come from the profile, not from `plugin.xml`
  registration levels.

## Findings from the first kemal audit (0.2.8-dev build, 2026-08-31)

- `spec/event_stream_spec.cr` (the reported `Response.new(io)` case) is clean
  — the type-shaped macro argument fix works in the real IDE.
- New false-positive classes (tracked in `TODO.md` for follow-up work):
  1. `spec/run_spec.cr:54` — "Too many arguments: expected at most 0, got 1"
     on `(Time.monotonic - start).total_milliseconds`.
  2. `src/kemal/helpers/exception_page.cr:16-23` — `new(...)` with 10
     arguments inside `def self.new` checked against a ≤2-argument pool; the
     real target is the ExceptionPage shard's macro-generated constructor.
  3. Eight × `Missing required argument(s): 'status_code'` on
     `env.status(:not_found).json(...)` chains (response_helpers/helpers
     specs) — the argument likely binds to the chain tail instead of
     `status`, or the wrong overload wins.
  4. `spec/static_file_handler_spec.cr:143` — a regular call resolved against
     lib-fun parameters (`'pointer', 'closure_data'`).

The 2026-09-05 audit after the pointer-shaped macro-argument and indexed-value
flow repairs reports 18 Crystal findings: 17 argument-count findings and one
type mismatch. The former `static_file_handler_spec.cr` lib-fun false positive
no longer reproduces; the other three classes above remain.

## Findings from the kemal re-audit (0.2.8 build, 2026-09-17)

Target `kemalcr/kemal @ be453d6` with locked shard deps installed, parse
preflight green (458 files, 0 errors). Result: 418 Crystal problems in `.cr`
files plus 26 filtered non-`.cr` noise — a count jump explained entirely by
scope, not regressions: this run covers `lib/` dependency specs (notably
ameba's), whose `<<-CRYSTAL` samples intentionally contain invalid code.

- All four previously documented false-positive classes are gone: no
  `run_spec` monotonic finding, no `status_code` chains, no
  `static_file_handler` lib-fun case, no `exception_page` constructor case,
  and no type-mismatch findings at all.
- `CrystalUnusedVariable` (334): all inside `lib/ameba/spec` sample code
  (specs for lint rules intentionally declare unused vars, e.g. `a = 1` in a
  `<<-CRYSTAL` sample) plus 5 in `exception_page.ecr` (see below). Zero in
  kemal's own `src/` and `spec/`.
- `CrystalParseError` in `.cr` files (~86): all inside ameba spec samples —
  intentionally invalid fragments (`if (); end`, `()`), non-ASCII identifiers
  under test (`space_👾`), `# ^^^ error:` annotations, and `%`-fragments.
  Zero in kemal's own `.cr` code.
- `CrystalArgumentCount` (1): the already-tracked
  `excessive_allocations_spec.cr:10` (`"Alice".chars.each(arg)` in a sample
  with undefined `arg`; `each` takes no positional args, so the finding is
  arguably correct). Traced, not actionable — TODO item closed accordingly.
- `CrystalColonSpacing` (2): true positives per `crystal tool format`
  ("space required before colon in type restriction" on `&on_message:
  Callback`), but inside abstract-def samples — validates the inspection,
  no action.
- Genuine follow-up family: `lib/exception_page/.../exception_page.ecr`
  (17 parse + 5 unused-var) and kemal's `spec/asset/hello_with_content_for.ecr`
  (1 parse) — block-form `content_for ... do` and HTML/code interleave exceed
  current ECR support. Real shipped/spec-asset templates, tracked in TODO.md.

Note on running the audit at all: the `lib/<shard>/lib -> ..` symlinks that
`shards install` leaves behind trip the target snapshotter's directory-cycle
guard; they were moved aside for the run (nothing requires through them) and
restored afterwards with a clean worktree.

## Finished-hook declaration audit (2026-10-29)

Target: `elbywan/crystalline @ a5f6f1bb9d55decf507138da7b2c47628ee15328`,
with locked runtime and development dependencies (`lsp 0.1.3`, `sentry 0.5.0`).
RubyMine `RM-262.9437.192`, plugin dev version `0.3.1`. Inspection used an isolated
disposable checkout and IDE state; only the dependency installer's redundant
`lib/lsp/lib -> ..` and `lib/sentry/lib -> ..` links were temporarily moved aside
for the documented snapshot-cycle guard, then restored. No target source changed.

The parse preflight retained the previously established baseline: 151 files,
six files with one parse error each. The offline audit completed with:

| Inspection | `.cr` findings |
|---|---:|
| CrystalArgumentCount | 1 |
| CrystalParseError | 91 |
| CrystalTypeMismatch | 1 |
| CrystalUnresolvedName | 429 |
| CrystalUnusedVariable | 42 |
| Total | 564 |

Two findings in non-Crystal files were filtered. Offline parse findings include
injected Crystal samples in specs and are not comparable to the whole-file parse
preflight count. This is not a clean-project claim or a full triage of those findings.

`src/crystalline/controller.cr` has **zero findings**, including the formerly
reported `params` readers and the inherited `id` accessors. Separate focused tests
verify typed completion, hover, original-source navigation, and chained accessors;
the inspection audit does not exercise those UI actions.

An intermediate audit exposed non-idempotent cached-value results from comparing
newly parsed nonphysical PSI by object identity. Declaration views now compare by
source revision and have a parallel-computation regression. The final audit has no
such error. The bundled IDE still logs unrelated RegExp initialization and missing
non-Crystal inspection-description errors.

## Responds-to guard re-audit (2026-10-29)

Re-audited the same crystalline revision
`a5f6f1bb9d55decf507138da7b2c47628ee15328`, with the same locked runtime and
development dependencies and RubyMine `RM-262.9437.192`, after adding binding-scoped
`responds_to?` member guarantees on top of plugin source revision `0840e980`.
The disposable checkout, isolated IDE, and temporary dependency-symlink handling
followed the procedure above; target source remained unchanged.

The preflight baseline is unchanged: six parse errors in six of 151 files.
The target-content fingerprint also matches the preceding run.
The offline audit reports **563** Crystal findings in `.cr` files and two filtered
non-Crystal findings. Comparing inspection/file/line/description records with the
preceding 564-finding run, independent of output ordering, shows exactly one removal:

```text
CrystalUnresolvedName: src/crystalline/diagnostics.cr:28: Cannot find 'inner'
```

There are no added findings. Both `diagnostics.cr` and `controller.cr` have zero
findings. This validates the guarded exception-unwrapping case, not a clean-project
claim; the other findings and bundled-IDE log issues remain outside this change.

## Guarded-navigation index audit (2026-10-29)

The same target revision, locked dependencies, RubyMine build, and target-content
fingerprint were re-audited with stub version 23 and `CrystalTypeInheritorIndex`.
The parse preflight remains at six errors in six of 151 files. The inspection
record multiset is identical to the responds-to re-audit: 563 `.cr` findings,
two filtered non-Crystal findings, no additions and no removals. Both
`diagnostics.cr` and `controller.cr` remain free of findings.

The audit log contains no stub/PSI mirror mismatch, hierarchy-index failure, or
non-idempotent cache computation. The known RegExp text-attribute fallback-key
collision remains; depending on initialization order it can blame Crystal or the
bundled RegExp initializer. Missing bundled non-Crystal inspection descriptions
are also still logged. These pre-existing issues are not a clean-log claim.

Focused tests separately verify possible-target reference flags, exact-resolution
and rename/usage isolation, source/namespace constraints, and platform stub
serialization. A read-only reproduction using crystalline's actual call site and
the real compiler error-class hierarchy finds `TypeException`'s
`property inner : CodeError?`. The offline inspection audit does not exercise that
interactive navigation action.

The ordinary package build initially encountered an occupied development-sandbox
lock (`Only one instance of IDEA can be run at a time`). Packaging, including
`buildSearchableOptions`, succeeded using a temporary isolated `sandboxContainer`
set by an external Gradle init script; no build task was excluded and the running
development IDE was not stopped.

## Cross-file record index audit (2026-10-29)

Re-audited crystalline revision `a5f6f1bb9d55decf507138da7b2c47628ee15328`
with the same locked dependencies, RubyMine `RM-262.9437.192`, and target-content
fingerprint `3d1852b6314ac1858aa63995b6ec79be85ba5a9c5193346f8eb1b4612048b390`,
using stub version 24 and `CrystalRecordIndex`. The disposable target remained
unchanged. The external parse preflight retains its known baseline: six errors
in six of 151 files, including `lightweight/index.cr`'s recovered record field list.

The final offline audit reports **506** Crystal findings in `.cr` files and two
filtered non-Crystal findings. A multiset comparison by inspection/file/line/description
against the preceding 563-finding guarded-navigation audit finds **57 removals, all
`CrystalUnresolvedName`, and zero additions**. In particular, both reported cases
are gone:

```text
src/crystalline/lightweight/contracts.cr:45: Cannot find 'MethodInfo'
src/crystalline/lightweight/contracts.cr:178: Cannot find 'MethodInfo'
```

A separate read-only fixture reproduction used the actual `contracts.cr`, `index.cr`,
and `type_utils.cr` contents and verified that both references resolve to the real
record declaration at `index.cr:23`. The temporary machine-specific reproduction
was removed after validation; portable regressions cover cross-file annotations,
namespace boundaries, unavailable records, duplicate declarations, constructor
completion, and platform stub serialization.

Recovered record headers remain usable for type navigation, but a declaration file
with parse errors cannot certify its complete constructor field list. The audit
exposed truncated-signature false argument warnings during development; the final
parse-completeness gate and its edit-invalidation regression eliminate those warnings
without dropping the type-name binding. The 506 remaining findings and six preflight
errors are not a clean-project claim.

The final complete suite passes **2,979 tests**, with no failures, errors, or skipped
tests. Parser goldens are unchanged. Packaging, including searchable options, passed
in an isolated temporary development sandbox. The final IDE log has no stub/PSI
mirror, record-index, or cached-value idempotence failure. The previously documented
RegExp initialization/fallback-key and missing bundled inspection-description errors
remain; this is not a clean-log claim.

## Guarded assignment flow audit (2026-10-30)

Re-audited the same crystalline revision and locked dependencies with target
fingerprint `3d1852b6314ac1858aa63995b6ec79be85ba5a9c5193346f8eb1b4612048b390`.
`shards check` confirms dependency availability. The external parse preflight
retains the known six errors in six of 151 files; no parser sources changed.
The disposable checkout remained unchanged.

The offline audit reports **496** Crystal findings in `.cr` files and two
filtered non-Crystal findings. Compared by inspection/file/line/description with
the preceding 506-finding record-index audit, there are **ten removals, all
`CrystalUnresolvedName`, and no additions**. All three reported false
`Cannot find 'name'` warnings at `lightweight/definitions.cr:220`, `:230`, and
`:237` are gone, together with seven analogous warnings in `lightweight/inference.cr`.

Portable regression tests reproduce the three shapes with resolvable AST class
hierarchies. They cover `is_a?` refinement in later `&&` operands, assigned
`case` subjects, multiple type patterns, exact member navigation, genuinely
missing members, non-type patterns, reassignment, and expression/branch lifetime.
The complete suite passes **2,992 tests**, without failures, errors, or skips.

The first offline attempt exceeded the terminal's two-minute timeout; a fresh
run with a longer timeout completed successfully. The known bundled RegExp
initialization/fallback-key errors remain in its log. Neither the remaining
findings nor the parser baseline is a clean-project claim.

The follow-up completion reproduction inserts a new `target.` line directly after
`definitions.cr:219` in a fixture copy of the real project, with copies of the real
compiler syntax sources. It confirms that a duplicate `ASTNode#accept(visitor)`
between `ext/compiler.cr` and stdlib `syntax/visitor.cr` emptied the all-method
collection even though the receiver type was known. After signature-local
exclusion, actual IntelliJ completion offers `location`, `clone`, `at`, and
`at_end` again. Portable completion and hierarchy regressions retain this case,
including ancestor shadowing and strict named-resolution checks; the temporary
machine-specific reproduction was removed. The subsequent full suite passes
**2,994 tests**, without failures or skips. This interactive completion check is
separate from the 496-finding offline inspection result above.

## Grouped local bindings and read-site types (2026-10-30)

Compared commit `96337d33` with the grouped-binding/type-flow fixes against the
same crystalline revision `a5f6f1bb9d55decf507138da7b2c47628ee15328` in a disposable
checkout. Locked dependencies were copied from the installed checkout and
verified with `shards check`. The two shard convenience links `lib/lsp/lib -> ..`
and `lib/sentry/lib -> ..` were omitted from that copy because they form directory
cycles; source files and the original checkout were not changed. Both audit runs
confirm the same target fingerprint
`3d1852b6314ac1858aa63995b6ec79be85ba5a9c5193346f8eb1b4612048b390`.

The raw parse preflight retains **three errors in three of 151 files**. The
offline result improves from **477 to 472** Crystal findings in `.cr` files,
with two filtered non-Crystal findings in each run. Comparing inspection, file,
line, and description as multisets yields **five removals and no additions**:
`base_name` at `lightweight/query.cr:156` and `:498`, `existing` at
`lightweight/completion.cr:243`, and `first_indent`/`ind` at
`broken_source_fixer.cr:480`/`:597`. All are `CrystalUnresolvedName`.
The offline parser inspection also includes injected test snippets; its count
must not be equated with the raw source parse preflight.

A separate fixture copy of the real `query.cr` and relevant real stdlib sources
verifies **String** at both right-hand `base_name` reads, including the complete
documentation-provider hover path. The portable regressions retain the
`split(...).first?` chain, generic include/superclass substitution, grouped
truthiness, read-site hover, unrelated destructuring, reassignment, block-call
conservatism, and conflicting generic paths. The complete suite passes
**3,016 tests**, without failures or skips. The temporary machine-specific test
was removed. This is not a clean-project or clean-IDE-log claim.

The follow-up covers the grouped assignment target itself and the successful
`if` body, not only the next operand. Fixture copies of the same real sources
verify all six hover positions: assignment targets at `query.cr:156` and `:498`
show `String | Nil`; the right-hand operands at those lines and body reads at
`:160` and `:502` show `String`. The positive branch of a pure `&&` condition
inherits its source-ordered operand facts, while false/mixed branches and
shadowing remain conservative. Seven additional portable regressions bring the
complete suite to **3,023 passing tests**, with no failures or skips. Repeating
the external audit with the same fingerprint retains **exactly the same 472
findings** (multiset comparison: zero removals and zero additions), two filtered
non-Crystal findings, and the same three-error raw parse baseline.
