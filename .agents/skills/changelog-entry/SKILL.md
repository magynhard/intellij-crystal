# Skill: CHANGELOG Entry

Use this workflow when the documentation policy in `AGENTS.md` requires a changelog entry. Do not
create entries for excluded changes such as typo, formatting, generated-only, or behavior-neutral
work.

## Workflow

### 1. Version detection
- Read `version` from `gradle.properties` (e.g. `0.1.16`)
- Read the topmost `## [x.y.z]` from `CHANGELOG.md` (e.g. `0.1.17`)

### 2. Decision
- If CHANGELOG version > gradle.properties version:
  → Development phase — add entry to existing CHANGELOG version section
- If CHANGELOG version ≤ gradle.properties version:
  → New version needed — create NEW version section:
    - Version: CHANGELOG version + patch bump
      (e.g. CHANGELOG `0.1.16` → `## [0.1.17]`)
    - Date: `<year>-xx-yy` (month/day set at release)
    - Add empty sections: `### Added`, `### Bug Fixes`, `### Changed`

### 3. Entry format (split layout since 0.3.2)
- Released versions live in full under `docs/changelog/<date>_<version>.md`
  (`# <version> — <date>` header, then the `### Added` / `### Bug Fixes` /
  `### Changed` / `### Removed` sections verbatim); `CHANGELOG.md` keeps only
  one line per entry:
  `- **[Short description](docs/changelog/<date>_<version>.md)**`
- The unreleased topmost section stays complete inside `CHANGELOG.md` until
  its release; on release day rename its header to the release version and
  date, then extract it into `docs/changelog/` like any other version.
- Full entry text (unreleased section only): `- **Short description** — detailed explanation`
- Place entry under correct section:
  - `### Added` — new features
  - `### Bug Fixes` — bug fixes
  - `### Changed` — changes to existing behavior
  - `### Removed` — removed features

### 4. Verify
After adding the entry, confirm it exists before committing.
