# AGENTS.md

## Scope And Instruction Conflicts

- These instructions apply to the entire repository. More specific instructions in a nested
  `AGENTS.md` take precedence for files below that directory.
- The current user request defines the intended outcome and scope. Repository specifications define
  established behavior. Tests and code provide implementation evidence; TODOs and historical comments
  are not authoritative specifications.
- Do not silently violate a repository invariant to satisfy a conflicting request. Explain the
  conflict and ask for clarification or propose a compliant alternative.
- Treat architecture rules as current invariants, not timeless facts. If current platform source,
  tests, or a reproducible diagnosis contradicts one, present the evidence and update the rule with
  the implementation rather than preserving a known-stale workaround.

## Project Language

English is the project language. Code comments, commit messages, documentation, and repository
artifacts must be in English. Chat with the developer in the developer's language.

## Working Method

1. Confirm the requested behavior and relevant non-goals from the prompt and existing specifications.
2. Inspect `git status --short` and relevant diffs before changing files. Treat pre-existing changes
   as user-owned: never delete, revert, overwrite, reformat, stage, or commit them unintentionally.
3. Find the existing implementation, tests, and task-specific spec or skill before designing a new
   mechanism. Reproduce bugs when practical.
4. Implement the smallest evidence-backed solution that completely satisfies the current requirement
   and preserves behavior outside its scope. Do not add speculative abstractions or unrelated
   refactors. Broader refactoring is appropriate only when correctness or an established invariant
   demonstrably requires it.
5. Validate proportionally to the affected behavior, inspect the final diff, and report the result
   honestly.

Use the least invasive diagnostic source first: existing tests and logs, a focused reproduction,
read-only inspection, debugger or PSI dumps, then temporary logging. Add diagnostic logging only when
the required runtime evidence is otherwise unavailable. Never log secrets, credentials, environment
contents, or unnecessary source text. Remove temporary diagnostics and agent-created artifacts before
handoff. Never remove an artifact whose ownership is uncertain.

## Git And Approval Gates

- `go` authorizes implementation, not a Git commit.
- Commit only when the user's latest unambiguous instruction says `commit` or otherwise explicitly
  requests a commit for the current change set. A combined request such as "implement and commit" is
  sufficient; do not ask again if scope and validation remain as described.
- If scope materially expands, unrelated changes overlap, or required validation fails, report it and
  obtain renewed commit approval.
- Before committing, inspect `git status`, the full diff, the staged diff, and recent commit style.
  Stage only intended files and never commit secrets or user-owned changes.
- Pushes, tags, releases, publishing, destructive Git operations, and changes outside the workspace
  require separate explicit approval. A commit instruction does not authorize them.

## Build And Verification

Use the checked-in Gradle wrapper. Build files and the wrapper are authoritative for Gradle, JDK,
IntelliJ Platform, and plugin versions.

```bash
./gradlew build
./gradlew test
./gradlew test --tests "de.magynhard.crystal.CrystalEnterHandlerTest.testEndInsertedAfterDef"
```

- Documentation-only, comment-only, changelog-only, and non-executable metadata changes do not require
  a Gradle test run. Validate links, commands, and internal consistency instead.
- Bug fixes and behavior changes require a regression test at the lowest suitable level. Pure
  refactors may rely on existing tests when behavior and coverage are unchanged.
- During development, run the narrowest relevant test first. Before handoff or an approved commit,
  run the affected test class or subsystem; run the full suite for production Kotlin changes and for
  parser, lexer, stub, index, resolution, inspection, plugin-descriptor, or shared-infrastructure work.
- Preserve the first failure output. Classify failures as likely regression, pre-existing failure,
  environment failure, or flake; use a focused rerun or baseline comparison when needed. Never weaken
  unrelated tests to make a run green. Report every failed or unrun required check.
- `buildSearchableOptions` has a known IntelliJ Platform failure mode. Exclude it with
  `./gradlew build -x buildSearchableOptions` only when the failure matches the documented platform
  issue; investigate plugin initialization, descriptor, and different task failures normally.
- Crystal should be available on `PATH` or configured in the plugin. Some integration tests and audits
  require an installed Crystal distribution and stdlib; report tests skipped because it is absent.

## Generated Sources

Lexer, parser, and generated PSI sources under `src/main/gen/` are committed. Never edit them directly.
Change the corresponding `.flex` or `.bnf` source and regenerate all affected outputs:

```bash
./gradlew generateLexer generateParser generateEcrLexer generateEcrParser
```

Before generation, note existing generated-file changes. Afterwards, inspect the generated diff and
ensure it is attributable to the source change. Source and generated output belong in the same commit.
Parser grammar changes require a focused parser fixture and golden file with no `PsiErrorElement`.

## Critical Repository Invariants

- `CrystalTypes` is the source of truth for token and element types. The lexer returns
  `CrystalTypes.*`; `CrystalTokenTypes.kt` defines only whitespace, bad-character, and token sets.
- Do not add `recoverWhile` to BNF rules. GrammarKit uses first-match PEG semantics, so order shared
  prefixes from the most specific alternative to the least specific.
- Increment `CrystalParserDefinition.FILE.getStubVersion()` whenever serialized stub format or index
  key semantics change. Keep the `CrystalFileType` reference that initializes
  `CrystalStubElementTypeHolder` before index initialization.
- Runtime features must not scan every Crystal file through `FileTypeIndex.processFiles()` or an
  equivalent project-wide iteration. Use `StubIndex`; corpus builders and explicit audit tasks are the
  only scanning exceptions.
- Register extensions descriptor-first in `src/main/resources/META-INF/plugin.xml`. Product modules
  use `<dependencies><module name="..."/></dependencies>`, not legacy `<depends>`. The DAP provider's
  documented runtime registration is the current deliberate fallback exception. Keep the required
  `contextId` on `liveTemplateContext`; omitting it breaks platform startup.
- Preserve exact, receiver-aware resolution. Unknown, ambiguous, incomplete, or suppressed receivers
  must not fall back to unrelated name-only matches.

## Change-Specific Routing

- Lexer or parser work: load `.agents/skills/crystal-bnf-checklist/SKILL.md`; when golden files change,
  also use `.agents/skills/crystal-regenerate-tests/SKILL.md`. See
  `docs/specs/stdlib-parser-compatibility.md` and the relevant syntax spec.
- Heredocs, string injection, IntelliLang, or ECR: read `docs/specs/heredoc-calls.md` and
  `docs/specs/embedded-crystal.md`.
- Resolution, navigation, completion, parameter info, or type inference: read the applicable files in
  `docs/specs/`, especially `indexed-navigation.md`, `completion.md`, `parameter-info.md`, and
  `type-inference.md`.
- Inspections: read the matching spec and add focused positive and negative regression cases.
- IntelliJ Platform API research: prefer the official SDK docs at
  `https://plugins.jetbrains.com/docs/intellij/`, then the exact target-build source or tag in
  `intellij-community`, then community references. Derive the target build from `build.gradle.kts`.

## External Inspection Audit

For parser, stub/index, resolution, type-inference, or inspection changes with broad real-project
impact, run the external audit when the required local RubyMine and Crystal environment is available:

```bash
scripts/crystal-inspect-audit.sh /path/to/project
```

Use a trusted, isolated, disposable checkout unless the user explicitly permits modifying the target.
Install its locked dependencies before the audit, including development dependencies. Installation
can execute third-party hooks, so never do this for an untrusted project or in an environment exposing
unrelated credentials. Run the parse preflight and require zero `PsiErrorElement`, or establish and
record a known baseline before interpreting inspection changes:

```bash
./gradlew stdlibParseAudit -PcrystalCorpus=external -PcrystalStdlibRoot=/path/to/project
```

Record the target revision and never modify the external project merely to make the plugin audit pass.
The audit requires access to the RubyMine license but isolates only IDE state, not operating-system
access. Mechanics, output semantics, and known limits are in `docs/specs/headless-inspect-audit.md`.

## Documentation Policy

- Update `README.md` when installation, usage, setup, or user-visible capabilities change.
- Add a `CHANGELOG.md` entry for release-relevant user or contributor behavior and notable bug fixes.
  Do not add entries for typo, formatting, changelog-only, generated-only, or behavior-neutral changes.
  When an entry is required, follow `.agents/skills/changelog-entry/SKILL.md`.
- Update `docs/specs/` when an established behavior contract or durable architecture decision changes.
  Pure refactors need no spec edit unless existing text becomes false.
- Add to `TODO.md` only for concrete unresolved work or deliberately deferred approved scope. Remove or
  update entries when that work is completed. Documentation-only tasks do not recursively require
  additional documentation artifacts.

## Handoff

Summarize the outcome and affected areas. List relevant validation as passed, failed, or not run, with
the reason for failures or omissions. State material assumptions, limitations, deferred work, and
whether changes are uncommitted. Do not describe work as fully validated when a required check was
skipped or inconclusive.
