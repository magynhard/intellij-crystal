# Shard Dependency Support

Behavior contract for Crystal shard (`shard.yml`/`shard.lock`) support:
dependency model, missing/stale diagnostics with one-click install,
`lib/` as library roots, and the inspection boundary for dependency
sources.

## Manifest And Lock Model (`CrystalShardManifest`)

The project-root `shard.yml` parses into name/version plus
`dependencies`/`development_dependencies` entries with resolver sources
(`github`/`gitlab`/`bitbucket`/`codeberg`/`git`/`hg`/`fossil`/`path`),
`version` requirements, and `tag`/`branch`/`commit`/`bookmark` refs.
`shard.lock` contributes resolved per-dependency versions (lock format
2.x as written by `shards install`). Parsing is safe-constructed;
missing files yield Missing, unusable content yields Malformed — never
exceptions, never network or processes.

Only the project-root manifest participates. `lib/*/shard.yml` files
are never checked (transitive dependencies are the parent install's
business, not the user's).

## Version Requirements (`CrystalVersionRequirement`)

Requirement operators exactly as shards documents them: bare versions
(exact), `*`, `<`, `<=`, `>`, `>=`, `!=`, `~>` (with the documented
pinning table: `~> 0.3.5` is `>= 0.3.5, < 0.4.0`), and comma-separated
combinations (all must hold). Anything else — unknown operators,
non-numeric bounds — yields no verdict instead of guessing.

## Dependency Status (`CrystalShardStatus`)

Per declared dependency, in manifest order:

- **Missing** — no `lib/<name>/` directory.
- **VersionMismatch(expected, actual)** — installed
  `lib/<name>/shard.yml` `version:` disagrees with the lock's exact
  version, or violates the manifest requirement when no lock pins it.
  Unverifiable cases (no installed version, unevaluable requirement)
  stay silent.
- **Ok** — everything else.

Stateless by design: manifest, lock, and `lib/` entries are re-read per
call, so results cannot go stale behind a cache. Malformed manifests
yield no entries — a broken manifest must not manufacture diagnostics.

## `lib/` Sources And Inspection Boundary

Installed shard sources under the project's `lib/` directory stay
indexed exactly as before (plain project content): completion,
navigation, find-usages, and require resolution into `lib/` are
unchanged — verified end-to-end by a headless audit where a missing
argument on a shard-module call is reported.

All our inspections stay silent inside shards-managed `lib/`
directories (`CrystalInspectionScope`, purely path-based so it behaves
identically regardless of index ownership): a file counts as project
source unless it sits under `<project>/lib/` with a project-root
`shard.yml` present, or the project file index marks it excluded or
library-owned. Non-physical and location-less files keep their existing
behavior. A deliberate audit comparison proved the point: with `lib/`
as plain content, dependency calls resolve; an index-exclusion +
synthetic-roots variant silenced them, so that design was rejected —
index ownership of `lib/` is left untouched.

Syntax highlighting stays on everywhere (platform guarantee; the parser
is clean on real shards, so this is noise-free in practice). Users can
re-scope individual inspections through the standard
inspection-profile scopes. Manifests under `lib/` itself (`lib/*/shard.yml`)
are never checked: transitive dependencies are the parent install's
business.

## User-Visible Diagnostics

- **Balloon on project open** (`CrystalShardStatusActivity`) listing up
  to three problem dependencies with a `Run shards install` action;
  silent when everything is installed.
- **Editor banner** over the project-root `shard.yml`
  (`CrystalShardBannerProvider`) with the same action; silent otherwise.
- **In-file markers** (`CrystalShardDependencyInspection`): missing
  dependencies are errors, version mismatches are warnings, each on the
  dependency key with hover text and a `shards install` quickfix.
  Keys are located with an indent-aware line scan (no YAML PSI
  dependency); markers are only created for names the status service
  reports.
- **`shards install` execution** (`CrystalShardsInstall`): resolves the
  binary as a sibling of the configured `crystal` executable with a
  `PATH` fallback (missing binary is an error message, nothing more)
  and runs it as a cancellable per-project-guarded background task in
  the project root; success refreshes VFS (banners and markers
  re-evaluate), failure reports truncated process output. Installs run
  only on explicit user click — never automatically.
