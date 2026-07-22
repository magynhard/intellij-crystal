# `require` Completion

Behavioral specification for Crystal's `require` keyword and require-path completion.

## Language Model

`require` is a compiler keyword that accepts a string path:

```crystal
require "json"
require "./user"
```

The parser represents it as `CrystalRequireStatement`. Like the Crystal compiler parser, the plugin accepts `require` as a primary expression in every expression context. This keeps PSI structured even when the compiler later rejects the context.

Crystal permits `require` only at file scope. Compile-time macro controls may conditionally surround a top-level require statement. Semantic analysis rejects dynamic require expressions inside runtime control flow, postfix conditions, binary expressions, blocks, assignments, arguments, conditions, string interpolation, type declarations, `def`, and `fun`. Executing `require` inside macro interpolation or a macro-control directive is rejected as a macro execution error. Keyword completion also excludes macro-definition bodies, where generated source has separate completion requirements.

`require` may also be used as a method name after a receiver. A real method such as `def self.require(path)` is independent from the compiler keyword and remains available through normal DOT completion.

## Keyword Completion

### Eligibility

The synthesized `require` keyword is offered when all of these conditions hold:

- The lowercase completion prefix matches `require`. An uppercase prefix such as `Req` does not match.
- The prefix starts an independent statement.
- The position is at file scope, including between top-level macro-control directives.
- The prefix is not preceded by `.`, `:`, or an unfinished argument/literal separator, ignoring whitespace, line breaks, and comments.

Valid contexts include:

```crystal
req<caret>

{% if flag?(:win32) %}
  req<caret>
{% end %}

foo; req<caret>
```

The keyword is not offered when the prefix belongs to a larger expression:

```crystal
Loader.req<caret>
loader.req<caret>
Loader::req<caret>
loader : req<caret>
loader = req<caret>
load(req<caret>)
if req<caret>
end

if flag?
  req<caret>
end

class Loader
  req<caret>
end

1.times do
  req<caret>
end

Loader.
  req<caret>

load(
  req<caret>
```

The receiver exclusions apply only to the synthesized keyword. If `Loader` defines a real class method named `require`, `Loader.<caret>` may offer that method with its method signature and normal method insertion behavior.

### Presentation

The synthesized lookup uses:

- Lookup string: `require`
- Tail text: `(name)`
- Type text: `keyword`
- Include-style icon

### Insertion

Selecting the keyword:

1. Replaces the typed prefix with `require`.
2. Inserts ` ""`.
3. Places the caret between the quotes.
4. Opens path completion automatically.

The resulting document is:

```crystal
require "<caret>"
```

## Context Diagnostics

The parser always preserves a `CrystalRequireStatement`; invalid placement is reported by the `CrystalRequireContext` inspection instead of a generic `PsiErrorElement`.

Only the `require` keyword is highlighted. The path remains independently navigable and editable.

| Context | Diagnostic |
|---------|------------|
| Direct file scope, including between top-level macro-control directives | None |
| Inside `def` | `Can't require inside def` |
| Inside `fun` | `Can't require inside fun` |
| Inside a class, module, or struct | `Can't require inside type declarations` |
| Inside macro interpolation or a macro-control directive | `Can't execute Require in a macro` |
| Runtime control flow, blocks, assignments, arguments, conditions, and other nested expressions | `Can't require dynamically` |

## Path Completion

Path completion is active only when the caret is inside the string expression of a `CrystalRequireStatement`. Other string literals retain normal string-completion suppression.

The typed path prefix is the document text between the opening quote and the caret.

### Mode Selection

The first path character selects the lookup mode:

| Prefix | Mode | Search roots |
|--------|------|--------------|
| Starts with `.` or `/` | Relative | Directory containing the current Crystal file |
| Empty or any other character | Shard/stdlib | Project `lib/` and configured Crystal stdlib roots |

### Relative Paths

Relative mode supports `./`, `../`, nested directories, and partially typed path segments.

Candidates are:

- Directories whose names match the current segment.
- `.cr` files whose base names match the current segment.

The current file, dotfiles, hidden directories, and non-Crystal files are excluded. File lookup names omit the `.cr` extension. Directory entries display a trailing `/`.

Examples:

```crystal
require "./<caret>"
require "../models/<caret>"
require "./sr<caret>"
```

### Shard And Stdlib Paths

Shard/stdlib mode merges candidates from:

- `<project>/lib`, when present.
- Filtered source roots returned by `CrystalStdlibRoots` for the configured Crystal SDK.

Matching names are deduplicated across roots. Completion supports nested paths such as `json/parser`; each completed directory segment narrows the next lookup to that directory.

If a project library or stdlib root is unavailable, completion returns candidates from the remaining roots. Missing roots and unreadable directories do not fail the completion request.

### Path Insertion

File selection replaces the currently typed path segment with the candidate's base name and leaves the caret after the completed path.

Directory selection:

1. Replaces the current path segment with the directory name.
2. Appends `/`.
3. Places the caret after `/`.
4. Opens completion again for the directory's children.

The insert handler preserves all path components before the current segment. It must not duplicate a selected segment:

```crystal
require ".<caret>"       # selecting src/ -> require "./src/<caret>"
require "./src/<caret>"  # selecting user -> require "./src/user<caret>"
require "json/pa<caret>" # selecting parser -> require "json/parser<caret>"
```

## Performance And Indexing

- Directory completion uses `VirtualFile` children and direct path resolution.
- Runtime completion never scans project files through `FileTypeIndex`.
- Stdlib resolution uses the project-scoped cached SDK path.
- Completion queries only the selected path roots and current directory level.

## Verification Matrix

Automated coverage protects:

- Keyword discovery, lowercase matching, presentation, and insertion.
- Valid statement contexts and invalid expression contexts.
- Exclusion from runtime control flow, blocks, type declarations, `def`, `fun`, and macro definitions.
- Structured PSI without `PsiErrorElement` for nested require expressions.
- Context-specific compiler diagnostics on the `require` keyword.
- Require tokenization and diagnostics in string and macro interpolation.
- Real methods named `require` after DOT.
- Multiline receiver and unfinished-argument recovery contexts.
- Relative files, directories, parent traversal, and current-file exclusion.
- Project shard and stdlib completion.
- Multi-segment filtering and insertion without duplicated path components.
- Graceful behavior when optional roots are unavailable.
