# AGENTS.md

## Project Language

**English is the project language.** All code comments, commit messages, documentation, and agent responses MUST be in English. This rule is mandatory and must be followed at all times.

## Design Philosophy

- **Always implement the "correct" solution** — proper architecture, full functionality, future-proof. Only consider a "nice-to-have" or simplified approach when the correct solution is technically nearly impossible.
- Never cut corners for convenience. If a feature requires deeper refactoring (e.g., lexer state stack for nested interpolation), do the refactoring.
- **Clean up temporary artifacts.** Any files created during research or debugging (extracted JARs, test outputs, build artifacts in the project root like `META-INF/`, `com/`, etc.) must be deleted immediately after use. Don't leave garbage behind.
- **When assumptions are unclear, add diagnostic logging first.** Don't guess why something fails at runtime — add `LOG.warn("CRYSTAL DEBUG: ...")` statements to trace the actual behavior, then iterate based on evidence. Remove debug logging before committing.

## ⚠️ COMMIT-GATE — MANDATORY USER APPROVAL

**Rule:** Before **any** git commit, the user MUST explicitly say "go" or "commit".

- **If the user explicitly says "commit" or "go" in the prompt** → commit directly.
- **If the user says nothing about committing** → always ask for explicit approval first.
- **Never commit immediately after implementation** without explicit user approval. This avoids commits with broken code.

## Build & Test

```bash
./gradlew build          # compile + generate lexer/parser + package
./gradlew test           # run all tests (JUnit 4, BasePlatformTestCase)
./gradlew test --tests "de.magynhard.crystal.CrystalEnterHandlerTest.testEndInsertedAfterDef"  # single test
```

- Gradle 9.4.1 (wrapper committed), JDK 21 required
- `buildSearchableOptions` may fail due to IntelliJ Platform bug — not a plugin issue, ignore it

## Code Generation

Generated sources live in `src/main/gen/` and are **committed**. Regenerate with:

```bash
./gradlew generateLexer    # from src/main/kotlin/.../lexer/Crystal.flex
./gradlew generateParser   # from src/main/kotlin/.../parser/Crystal.bnf
```

**After any `.flex` or `.bnf` change, regenerate and commit the `src/main/gen/` output.**

## Architecture — Critical Rules

- **`CrystalTypes` (generated) is the single source of truth** for all token and element types. The lexer returns `CrystalTypes.*` tokens. `CrystalTokenTypes.kt` only defines `WHITE_SPACE`, `BAD_CHARACTER`, and `TokenSets` that reference `CrystalTypes.*`.
- **Never add `recoverWhile` to BNF rules** — it caused consecutive statements to fail parsing. The grammar is simpler but less error-tolerant by design.
- **EnterHandler uses `postProcessEnter`** (not `preprocessEnter`) — avoids colliding with IntelliJ's normal Enter processing. Balance check scans the entire document.
- **`liveTemplateContext` requires `contextId` attribute** in plugin.xml (IntelliJ 2026.1+). Without it, platform tests and IDE startup fail.
- **Annotator for semantic highlighting** (PSI-based), not lexer-based. Lexer highlighting is token-level only.
- **Go to Definition uses two mechanisms**: PSI mixins (on `variable_reference`, `method_call_expression`, `bare_method_call_expression`, `type_path`) for direct references, and `GotoDeclarationHandler` for DOT-call expressions (`obj.method`, `Class.method`). Never use `PsiReferenceContributor` — it doesn't receive leaf tokens.
- **StubIndex stores cleaned names**: `def self.tanzen` is indexed as `"tanzen"` (via `psi.name`), not `"self.tanzen"` (via `psi.methodName?.text`).
- **DOT-calls use `dot_call_access` with `CrystalDotCallReference`** — The public `dot_call_access` rule in the BNF creates a real PSI composite that exposes a `reference` via `CrystalDotCallReferenceMixin`. The reference resolves to the target method via `CrystalMethodByClassIndex` + `CrystalTypeInference` for instance methods. For instance methods, resolution returns `null` when the receiver type is unknown (no name-only fallback — prevents false positives). `.new` constructor resolution is NOT in the reference; it is handled by `CrystalGotoDeclarationHandler` and `CrystalDocumentationProvider` as a special case.
- **Parameter Info for DOT-calls** works via `findMethodNameFromSiblings` which traverses `prevSibling` of the args holder to find `IDENTIFIER` preceded by `DOT`. For bare DOT-calls without args, `scanBackwardsForBareCall` + `findMethodNameInLeaves` handles the DOT pattern. The `extractIdentifierFromCallExpression` function is ONLY called for `CrystalMethodCallExpression` parents (which never contain DOT-calls per BNF). Do not modify it for DOT-call support — it would have no effect.
- **Type Check Inspection uses `ProblemHighlightType.GENERIC_ERROR`** — not `GENERIC_ERROR_OR_WARNING`. The latter may fail to render visible markers depending on theme/inspection level configuration. Always highlight the innermost literal/expression element (unwrap `CrystalBareArgument`/`CrystalArgument` wrappers) for `registerProblem` to ensure visibility.
- **Crystal type aliases in compatibility checks**: `Int` = union of all signed integers, `UInt` = unsigned, `Float` = Float32|Float64. These must be expanded in `normalizeType()` before compatibility checking, or they'll be treated as unknown types (escaping the known-builtins guard).
- **PEG parser: longer alternative first** — GrammarKit uses PEG (first-match wins, no backtracking). When two alternatives start with the same token type, the longer/more specific one MUST come first. Example: `IDENTIFIER IDENTIFIER` (external + internal param name) before `IDENTIFIER` (internal only). If reversed, the parser greedily consumes the single IDENTIFIER and fails on the second.
- **New percent literal types need two changes** — (1) Lexer: add the `%x` pattern with `percentTokenType = CrystalTypes.NEW_TOKEN` and (2) Parser: add the new token type to `percent_literal_content`. Missing either causes parse errors — the lexer emits a `PERCENT_LITERAL_BEGIN` but the parser can't consume the content tokens.
- **JFlex lexer: longest match with rule order tiebreak.** When two patterns match the same input length, the first one listed in `.flex` wins. Critical for disambiguating `{%` (macro control) vs `%{` (percent literal) — both match 2 characters, so `"{%"` must appear before the `%` percent-literal rules.
- **Context-dependent keywords must NOT become global lexer tokens.** Keywords only valid in specific syntactic contexts (e.g., `type` inside `lib` blocks) must be handled via IDENTIFIER-based parser rules (e.g., `lib_type_alias ::= IDENTIFIER CONSTANT ASSIGN type_reference`), not by adding a global `TYPE` keyword to the lexer. Adding a context-dependent keyword globally breaks its use as parameter/variable/method names everywhere else.
- **`macro_control` and `macro_interpolation` are valid at multiple grammar levels.** They appear not only inside `macro_body_element` but also at: `top_level_statement`, `class_member`, `statement`, `primary_expression`, `bare_primary_expression`, `method_name` (for `def self.{{name}}`), and DOT-call target positions. When adding new grammar contexts, always consider whether `{% ... %}` / `{{ ... }}` should be valid there.
- **MACRO_INTERPOLATION and MACRO_CONTROL lexer states must mirror INTERPOLATION.** Whenever a token type or rule is added to the `<INTERPOLATION>` state (e.g., `SYMBOL`, `:"string"`), it must also be added to `<MACRO_INTERPOLATION>` and `<MACRO_CONTROL>`. These states share the same semantic structure but are separate in the lexer.
- **TextAttributesKey fallback colors are cached in the user's theme.** Once a user has loaded the plugin, the key's fallback color is persisted. To change a key's effective color, rename it (e.g., `CRYSTAL_PARAMETER` → `CRYSTAL_PARAMETER_V2`) so IntelliJ resolves the new fallback freshly. The `ColorSettingsPage` entry uses the key reference, so no UI change is needed.
- **IntelliJ's built-in TODO highlighting renders above the Annotator layer.** `textAttributes(...)` on the Annotator won't override it. Use `enforcedTextAttributes(...)` with concrete `TextAttributes` obtained from `EditorColorsManager.getInstance().globalScheme.getAttributes(...)` to override the built-in TODO coloring.
- **FileTypeIndex scans are forbidden at runtime.** Any code that uses `FileTypeIndex.processFiles()` or iterates over all `.cr` files in the project (outside of the StubIndex builder) causes 90+ second hangs on every right-click/hover. Only the initial StubIndex builder (which runs once at project load) may scan all files. For runtime lookups, always use `StubIndex.getElements()` which queries the in-memory index.
- **Documentation Provider links via `getDocumentationElementForLink` encoding `psi_element://class:<name>`** — class/method/parameter-type/superclass names inside the rendered documentation popup are hyperlinked; clicking resolves via `CrystalClassIndex` and replaces the popup content with the target element's documentation. Top-level methods render `Object` as the enclosing class (Crystal's universal base, matching RubyMine's behaviour for Ruby). A class's own name in its own class signature is NOT linked (no self-recursion). Non-resolvable type names (e.g. `Int32` absent from the stdlib index) are silently rendered as plain text. The `.new` constructor hover routes through `CrystalGotoDeclarationHandler` and benefits from the same link rendering. Never use `FileTypeIndex` at runtime — only `StubIndex.getElements()`. Never use explicit inline colors on `<a>` tags (e.g. `style="color:#3850BB"`) — let IntelliJ's default documentation link styling handle it.

## Key Directories

```
src/main/kotlin/de/magynhard/crystal/
├── lexer/          Crystal.flex + CrystalTokenTypes.kt (TokenSets)
├── parser/         Crystal.bnf
├── highlighting/   Syntax highlighter + Annotator + Color settings
├── psi/            CrystalNamedElement, CrystalReference, CrystalReferenceContributor
├── navigation/     GoTo Symbol/Class, Find Usages, Parameter Info
├── run/            Run configurations (run/build/spec)
└── settings/       SDK detector, settings configurable

src/main/gen/       Generated lexer + parser + PSI (committed)
src/main/resources/META-INF/plugin.xml   All extension registrations
src/test/           JUnit 4 tests (BasePlatformTestCase + pure unit tests)
```

## Testing Conventions

- Parser tests: `CrystalParserTest` (ParsingTestCase) with `.cr` input + `.txt` golden files in `src/test/testData/parser/`
- Platform tests: `BasePlatformTestCase` subclasses (editor fixture tests)
- Pure unit tests: no IDE dependency (e.g., `CrystalEnterHandlerBalanceTest`)

**Rule: Every implementation must have unit tests.** Where testable, always create tests to catch regressions and provide fast feedback during refactoring. After each implementation, run `./gradlew test` and ensure all tests pass before committing.

**Parser changes require parser tests.** Every BNF change (new rule, modified rule, added token) must be covered by a parser test in `CrystalParserTest`. Create a `.cr` test file exercising the new/changed syntax, run the test once to generate the `.txt` golden file, verify it contains no `PsiErrorElement`, then re-run to confirm it passes. This prevents regressions when future grammar changes affect existing rules.

## Documentation

- **`TODO.md`** contains follow-up tasks for ongoing work (rename, type inference, etc.). Update after completing tasks.
- **`README.md`** should be updated when user-facing features or setup instructions change.
- **`CHANGELOG.md`** — every change must have an entry. The skill `.agents/skills/changelog-entry/SKILL.md` describes the full workflow including version detection and auto-creation of new version sections.
- **`docs/specs/`** contains behavioral specifications for implemented features — this is the project's single source of truth for specs. After every task, extend the relevant spec in `docs/specs/` or create a new `.md` file there if the topic doesn't exist yet. Specs should document what was discussed and decided (popup formats, resolution rules, edge cases, user decisions). Other spec locations are deprecated. If unsure whether a spec exists or which file to update, ask the user.

## Plugin Registration

All extensions are registered in `src/main/resources/META-INF/plugin.xml`. When adding new extension points, register them there — not via code-based registration.

## External Dependencies

- Crystal compiler at `/usr/bin/crystal` (used by formatter, run configs)
