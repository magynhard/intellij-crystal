# AGENTS.md

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
- **`liveTemplateContext` requires `contextId` attribute** in plugin.xml (IntelliJ 2025.1+). Without it, platform tests and IDE startup fail.
- **Annotator for semantic highlighting** (PSI-based), not lexer-based. Lexer highlighting is token-level only.

## Key Directories

```
src/main/kotlin/de/magynhard/crystal/
├── lexer/          Crystal.flex + CrystalTokenTypes.kt (TokenSets)
├── parser/         Crystal.bnf
├── highlighting/   Syntax highlighter + Annotator + Color settings
├── lsp/            Crystalline LSP client
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

## Plugin Registration

All extensions are registered in `src/main/resources/META-INF/plugin.xml`. When adding new extension points, register them there — not via code-based registration.

## External Dependencies

- Crystal compiler at `/usr/bin/crystal` (used by formatter, run configs)
- Crystalline LSP: optional, auto-detected if installed
