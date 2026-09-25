# Ameba Linter Integration

Behavioral specification for the optional Ameba integration: binary
resolution, diagnostics pipeline, overlap suppression, and build offer.
Rationale and research live in `docs/todos/ameba-integration.md` (local,
unversioned).

## Minimum Version

Ameba 1.7.0 or newer is required (`AmebaVersion.MINIMUM`). 1.7.0 rebuilt the
overlap-relevant rules on liveness analysis, improved issue locations, and
stabilized JSON output. Older binaries resolve to nothing: the integration
stays disabled (no diagnostics, no suppression, `--fix` reports the missing
binary) and the built-in inspections remain the fallback. Pre-releases sort
below their release (`1.7.0-dev` rejected); unparsable versions fail closed
with an "undetermined" warning.

Warnings surface in three places: a one-time project-open balloon (only when
linting is enabled), a shard.yml banner when the `version:` pin explicitly
excludes the minimum (`branch`/`commit`/`tag` pins cannot be judged and stay
silent), and a warning text in the settings next to the version label.

## Binary Resolution

`AmebaBinary.resolve(project)` returns the effective binary or null, using
only `java.io` stats and processes (safe off the read thread). Order:

1. Manual `amebaPath` from settings when set, valid (`ameba --version`
   exits 0 and mentions ameba), and meeting the minimum version. A
   set-but-invalid path resolves to nothing — no silent fallback, so
   misconfiguration surfaces.
2. Project-local `<project>/bin/ameba` when the project-root `shard.yml`
   declares an `ameba` dependency and the binary is executable, valid, and
   meets the minimum version (outdated project binaries fall through to
   system detection, mirroring broken binaries).
3. System `PATH` and known install locations (`AmebaDetector`), likewise
   gated on the minimum version.

There is no fallback executing `lib/ameba/bin/ameba.cr` through Crystal.
Results (including misses) cache per project, keyed by settings path,
candidate existence/mtime, and manifest mtime; settings apply clears the
cache.

## Settings

`CrystalSettings.State` carries `amebaEnabled` (default on with
auto-detect: whoever has a usable ≥1.7.0 binary gets diagnostics, everyone
else silently keeps the built-in fallback),
`amebaPath` (empty = auto-resolve), and `amebaConfigPath` (empty = nearest
`.ameba.yml` walking up from the linted file to the project root).
The "Ameba Linter" group in Settings | Languages & Frameworks | Crystal
edits all three with a Detect action and version label. Applying Ameba
changes clears the binary cache and restarts highlighting without touching
stdlib roots.

## Diagnostics Pipeline

- Live: `AmebaExternalAnnotator` (`language="Crystal"`). `collectInformation`
  snapshots the switch, scope gates, binary, config, and document text;
  `doAnnotate` runs `ameba --format json --stdin-filename <relative-path>
  [--config …] <absolute-path>` with the buffer on STDIN (30 s timeout);
  `apply` maps issues to ranges, dropping stale stamps and out-of-range
  positions. Files outside project sources, non-local, or non-Crystal files
  never run. Runner failures balloon at most once per project and error text
  (linking settings); findings (non-zero exit with issues) are success.
- Batch: `AmebaInspection` (short name `Ameba`, on by default, `WARNING`)
  implements `ExternalAnnotatorBatchInspection`; its `checkFile` runs the
  same pipeline synchronously. Disabling it in the profile stops live
  highlighting via the platform `ExternalToolPass` contract.
- Old binaries that reject `--format json` get one `flycheck` retry
  (range-less issues highlight one character).

## ECR Templates

`.ecr` files lint through the same pipeline with their raw text: Ameba
1.7.0+ translates them itself (`ECR.process_string`) and reports template
coordinates for code-tag findings (verified: `greeting` in
`<% greeting = "hi" %>` reports line 2, columns 4–11 — end columns are
inclusive, like TextRange conversion expects). Findings inside string
chunks may carry generated-code positions (no `#<loc>` markers there);
`--fix` stays disabled for `.ecr`. Injected Crystal fragments step aside
together with their host file (`AmebaSuppression` judges the top-level
`.ecr`), so tag findings are never doubled.
- Severity mapping is fixed: Convention → Weak Warning, Warning → Warning,
  Error → Error (mirroring RuboCop defaults; deliberately no IDE mapping
  table). Per-finding strength is controlled via `.ameba.yml` per-rule
  `Severity`, which flows through the JSON output into this mapping and
  stays consistent across IDE, terminal, and CI. Rule selection as well is
  `.ameba.yml`-only (deliberately no per-group or per-rule IDE toggles:
  single source of truth; muting via inline `ameba:disable` or `Excluded`).
  Annotations read
  `<message> [Ameba: <rule>]` and carry the
  explicit `ameba --fix` file action (saves the buffer, background task,
  refresh; never on typing).

## Fix on Save

Opt-in via "Run ameba --fix on save" in the Crystal settings (default off).
A `FileDocumentManagerListener.afterDocumentSaved` hook fires after the save
landed: gated on both switches, local `.cr` files, clean buffers (dirty
buffers are skipped — the next save retries), and project-source scope,
then reuses the explicit file fix (background task, refresh). `.ecr` is
excluded: `--fix` through generated-code positions proved unreliable on
templates (verified against 1.7.0), while read-only diagnostics still flow
for them. No loops: disk writes refresh without save events, and a second
run finds nothing to correct.

## Overlap Suppression

`AmebaSuppression.isActiveFor(file)` (enabled + resolvable binary + Crystal
project source, ECR judged by host) gates four inspections to empty
visitors: `CrystalUnusedVariableInspection` (owned by `Lint/UselessAssign`
et al.), `CrystalColonSpacingInspection` (owned by `Lint/Formatting`),
`CrystalSingleQuoteStringInspection` and `CrystalEmptyCollectionInspection`
(both owned by `Lint/Syntax`, verified against compiler 1.21 and Ameba
1.7.0: same rule, message, and position). Suppressed diagnostics lose
their ranges (Ameba reports points) and the "Add type annotation"
quickfixes while Ameba is active. Everything else (types, arity, resolution,
requires, shards) has no Ameba counterpart and always runs.

## Build Offer

When `shard.yml` declares Ameba but `bin/ameba` is missing/unusable, an
editor banner over the project-root manifest and a one-time project-open
balloon offer the explicit opt-in build — never silently. `shards build
ameba` with a declared `ameba` target, otherwise
`crystal build -o bin/ameba lib/ameba/bin/ameba.cr`; once per project,
cancellable, tree refresh on success.

## Deliberately Deferred

- `ameba-ls` language server instead of direct CLI: revisit only if the
  debounce/push behavior of the CLI pipeline proves inadequate in real use.
- One-shot manual action ("Run Ameba, Lint only, on file") without
  persistent divergent state, if focused runs are ever requested.
- File size as an extra binary-cache signal, if same-mtime swaps ever bite.
