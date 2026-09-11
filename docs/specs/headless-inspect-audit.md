# Headless Inspect Code Audit (external projects)

How the plugin's inspections are run autonomously — without opening a GUI IDE —
against arbitrary external projects (kemal, shards, stdlib trees) to find false
positives and parse gaps in bulk. The command:

```bash
scripts/crystal-inspect-audit.sh /path/to/project
```

## Mechanism

- `./gradlew buildPlugin` builds the dev plugin zip; it is installed into an
  ISOLATED RubyMine instance (`AUDIT_HOME`, default `/tmp/opencode/rm-audit`)
  via `-Didea.{config,system,plugins,log}.path` set through
  `RUBYMINE_VM_OPTIONS`. The user's real IDE settings are never touched; only
  the license key (`rubymine.key`) is copied so headless mode can start a
  licensed RubyMine.
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
  a separate IDE log under `$AUDIT_HOME/logs/<run-id>/idea.log`.

## Semantics and limits

- Every run indexes the whole target project plus the Crystal stdlib from a
  fresh `$AUDIT_HOME/system` directory. Reusing that directory produced
  duplicate offline-inspection findings even with a byte-identical plugin, so
  deterministic reports take precedence over a faster warm-cache run.
- An ownership marker and exclusive lock protect `AUDIT_HOME`: the script
  rejects pre-existing unowned directories, any overlap with the inspected
  project or plugin repository, and concurrent runs. Inspection output is
  staged under `$AUDIT_HOME/reports`; after the IDE exits successfully and its
  log confirms that the expected plugin version loaded, an atomic symlink swap
  publishes it at `$AUDIT_HOME/out` without hiding the previous report first.
  Earlier successful run directories remain available under `reports/`.
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
