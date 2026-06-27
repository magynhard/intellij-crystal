# Skill: CHANGELOG Entry

Before EVERY commit, ensure CHANGELOG.md has an entry for the current change.

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

### 3. Entry format
- Follow [Keep a Changelog](https://keepachangelog.com/)
- Format: `- **Short description** — detailed explanation`
- Place entry under correct section:
  - `### Added` — new features
  - `### Bug Fixes` — bug fixes
  - `### Changed` — changes to existing behavior
  - `### Removed` — removed features

### 4. Verify
After adding the entry, confirm it exists before committing.
