# Crystal Language Plugin for JetBrains IDEs

Crystal language support for IntelliJ IDEA, WebStorm, RubyMine, and other JetBrains IDEs.

## Features

### Syntax & Editing

- **Syntax Highlighting** — 60+ keywords, operators, strings with interpolation, numbers, symbols, regex, percent literals, heredocs
- **Color Settings Page** — customizable colors for all token types
- **Code Folding** — collapse blocks, methods, classes, multi-line comments
- **Brace Matching** — parentheses, brackets, braces, percent literal delimiters
- **Auto-Insert** — automatic closing quotes, brackets, `end` after block keywords
- **Line Commenter** — toggle `#` comments
- **TODO/FIXME Indexing** — highlights and indexes task comments

### Navigation

- **Go to Definition** (Ctrl+Click / Ctrl+B) — jump to class, module, struct, enum, and method definitions via StubIndex
- **Go to Symbol** (Ctrl+Alt+Shift+N) — find any symbol in the project
- **Go to Class** (Ctrl+N) — find classes, modules, structs, enums
- **Find Usages** (Alt+F7) — word-based search with token type awareness
- **Structure View** — PSI-based tree with nested types, methods, macros, constants
- **Parameter Info** (Ctrl+P) — shows method signature at call site, project-wide via StubIndex
- **Code Completion** (Ctrl+Space) — context-aware: dot-completion on classes (static methods) and variables (instance methods via type inference), free-text completion for classes, methods, and local variables/parameters

### Refactoring

- **Rename** (Shift+F6) — in-place rename with preview dialog and automatic compiler verification (`crystal build --no-codegen`)
- **Names Validator** — validates Crystal identifier rules (including `?` and `!` suffixes)

### Code Formatting

- **Reformat Code** (Ctrl+Alt+L) — delegates to `crystal tool format` via stdin/stdout
- No configuration needed — Crystal's formatter has no options

### Run & Debug

- **Run Configurations** — crystal run, build, and spec with configurable arguments, environment variables, and working directory
- **Context-aware** — right-click a `.cr` file to run it

### Code Generation

- **Live Templates** — 21 snippets for common Crystal patterns (class, module, struct, def, spec, etc.)

### Parser

- **GrammarKit BNF parser** — covers classes, modules, structs, enums, methods, macros, control flow, expressions with operator precedence, type references, blocks, literals
- **StubIndex** — project-wide index for classes and methods (instant navigation even in large projects)
- **Error-tolerant** — pin/recovery rules ensure the parser works with incomplete code while typing

## Requirements

- **IntelliJ Platform** 2025.1 or later
- **Crystal** installed and available in PATH (for formatting and compiler verification)

## Installation

### From JetBrains Marketplace

> Coming soon

### From Source

```bash
git clone https://github.com/magynhard/intellij-crystal.git
cd intellij-crystal
./gradlew build
```

The plugin ZIP will be at `build/distributions/`. Install via *Settings → Plugins → Install Plugin from Disk*.

To run a development IDE instance:

```bash
./gradlew runIde
```

## Architecture

```
Crystal.flex (JFlex)     →  Lexer (tokenization, highlighting)
Crystal.bnf (GrammarKit) →  Parser (PSI tree, structure)
Stubs                    →  StubIndex (project-wide search, Go to Definition)
```

### Design Decisions

- **All features plugin-native**: No external LSP dependency — everything works offline and instantly
- **StubIndex over FileBasedIndex**: Industry standard for IntelliJ plugins, enables instant project-wide navigation
- **External formatter**: Crystal's built-in `crystal tool format` is canonical — no need to reimplement
- **Rename strategy**: Token-based + preview dialog + compiler verification
- **Generated files committed**: Standard convention for GrammarKit-based plugins to ensure reproducible builds

## Development

### Project Structure

```
src/main/kotlin/de/magynhard/crystal/
├── lexer/              # JFlex lexer definition + token types
├── parser/             # GrammarKit BNF grammar
├── psi/                # PSI element types and stub mixins
├── stubs/              # StubIndex infrastructure
├── highlighting/       # Syntax highlighter + color settings
├── structure/          # Structure View (PSI-based)
├── navigation/         # Go to Symbol/Class, Find Usages, Parameter Info, Go to Definition
├── formatting/         # External formatter (crystal tool format)
├── refactoring/        # Rename support + compiler verification
├── run/                # Run configurations
└── *.kt                # Core (language, file type, icons, commenter, etc.)

src/main/gen/           # Generated lexer, parser, and PSI classes (committed)
src/main/resources/     # plugin.xml, icons, live templates
src/test/               # Lexer tests + test data
```

### Build

Requires JDK 21.

```bash
./gradlew build          # Full build with tests
./gradlew generateLexer  # Regenerate lexer from Crystal.flex
./gradlew generateParser # Regenerate parser from Crystal.bnf
./gradlew runIde         # Launch development IDE
```

## Contributing

See [TODO.md](TODO.md) for the roadmap of planned features and parser extensions.

## License

[MIT](LICENSE)
