# `require` Completion

Behavioral specification for Crystal's `require` statement and require-path completion.

## Language Model

`require` is a compiler keyword that accepts a string path:

```crystal
require "json"
require "./user"
```

The parser represents it as `CrystalRequireStatement`. Like the Crystal compiler parser, the plugin accepts `require` as a primary expression in every expression context. This keeps PSI structured even when the compiler later rejects the context.

Crystal permits `require` only at file scope. Compile-time macro controls may conditionally surround a top-level require statement. Semantic analysis rejects dynamic require expressions inside runtime control flow, postfix conditions, binary expressions, blocks, assignments, arguments, conditions, string interpolation, type declarations, `def`, and `fun`. Executing `require` inside macro interpolation or a macro-control directive is rejected as a macro execution error. Keyword completion also excludes macro-definition bodies, where generated source has separate completion requirements.

`require` may also be used as a method name after a receiver. A real method such as `def self.require(path)` is independent from the compiler keyword and remains available through normal DOT completion.

## Statement Completion

### Eligibility

The synthesized `require` statement lookup is offered when all of these conditions hold:

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

The statement lookup is not offered when the prefix belongs to a larger expression:

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

The receiver exclusions apply only to the synthesized statement lookup. If `Loader` defines a real class method named `require`, `Loader.<caret>` may offer that method with its method signature and normal method insertion behavior.

The synthesized lookup is the canonical bare `require` candidate. Local and indexed methods with the same name do not add duplicate bare candidates, including when a project defines a top-level `def require(path)`. Receiver completion remains independent.

### Presentation

The synthesized lookup uses:

- Lookup string: `require`
- Tail text: `(path)`
- Type text: none
- Method icon

### Insertion

Selecting the statement lookup:

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

## Static Dependency Collection

Require-aware analysis collects direct dependency paths from exactly one complete, interpolation-free double-quoted string literal in valid file-scope `CrystalRequireStatement` nodes. This includes requires conditionally surrounded by top-level macro-control directives. Adjacent or compound quoted expressions do not become dependency edges; escaped content inside the single literal remains valid.

Collection preserves source order. Requires rejected by the context inspection, requires executed inside macro directives, interpolated paths, and incomplete strings do not become dependency edges. An ordered length-prefixed fingerprint changes when a valid path is added, removed, reordered, or edited, but remains stable when unrelated method bodies change.

## Runtime Path Resolution

Require-aware analysis resolves edges without running the Crystal compiler and without scanning the
project. Every path whose filename starts with `.`, including `.hidden` and `.hidden.cr`, is relative
and starts at the requiring file's directory. Bare paths use compiler precedence:
the project's `lib/` root first, then the configured stdlib root. Exact candidates support both
`path.cr` and `path/path.cr`. For each root, lookup is deterministic and stops at the first existing
compiler-ordered form. Every filename is first tried with exactly one `.cr` suffix. A non-relative
nested path then tries shard `src` non-namespaced, shard `src` namespaced, ordinary directory-main,
shard non-namespaced directory-main, and shard namespaced directory-main forms, in that order. Bare
and relative paths instead try ordinary directory-main, followed by bare shard `src` only for a
non-relative path. Multiple existing forms are not ambiguous. Explicit nested `.cr` paths retain
their direct first candidate and still participate in shard expansion after the nested shard stem's
extension is stripped, exactly as Crystal 1.20.3 does.

The project-`lib/` precedence applies only to a physical project or shard forward traversal. The
entire traversal rooted at configured `prelude.cr` resolves bare exact and wildcard requires only
against that stdlib root. This provenance is inherited across every edge, including relative edges
that leave the stdlib directory; relative paths themselves still resolve from each requiring file's
directory. The same external support file may therefore have separate project and prelude graph
nodes with different bare-path results. A project `lib/string.cr`, `lib/int.cr`, or
`lib/indexable.cr` cannot shadow dependencies of `prelude.cr` or enter the globally shared prelude
foundation.

Top-level requires surrounded by macro-control directives are static graph edges regardless of the
active flags. Runtime, type-body, method, macro interpolation, and other dynamic requires are not.
The effective set is forward-only: requiring a file never gives that file the caller's dependencies,
and sibling branches do not leak into one another.

Terminal `/*` expands direct `.cr` children in stable path order. Terminal `/**` additionally walks
descendant directories in Crystal's sorted depth-first order: sorted direct files, then each sorted
directory recursively. Traversal is iterative and cancellation-aware. The project root and every
directory reached during traversal are canonicalized before safety checks or child enumeration;
canonical identities prevent duplicate alias and symbolic-link-cycle visits. A wildcard directory
whose canonical target equals or contains the canonical project root is skipped, including an initial
target symlink and a nested symlink inside an otherwise allowed target. Recursive `./**`, `../**`, and
deeper ancestor forms are therefore conservatively suppressed before any whole-project scan. Explicit descendant directories such as
`src/extensions/**` remain supported. Each resolved wildcard records both its lexical target/watch identity and
its safe canonical target identity. Structural create, copy, delete, rename, and move events match either identity,
so changes made through a symlink alias or directly through an allowed external target invalidate the same owner.
Unsafe canonical identities that equal or contain the project root are never recorded. Unresolved targets retain
only their lexical intended path and nearest-existing-parent watch so later creation remains observable without
weakening the traversal boundary. Exact candidates record missing
and higher-precedence alternatives for the same reason. Require PSI edits, relevant required-file
content changes, stdlib-root changes, source-root changes, shard metadata changes, and relevant
`lib/` structural changes invalidate the affected cache scope. The next query rebuilds lazily; a
new matching wildcard file is therefore visible without restarting the fixture or IDE.

Static double-quoted paths are decoded before fingerprinting and resolution. Named Crystal escapes,
escaped quote/backslash/hash and other pass-through characters, one-to-four-digit octal values below
256, exactly two-digit hex, exactly four-digit non-surrogate `\uFFFF`, and one-to-six-digit valid
`\u{...}` codepoints separated by one literal ASCII space contribute their runtime string value.
Escaped newline continuations consume following ASCII whitespace, including when that whitespace ends
at the collected content boundary immediately before a PSI-validated closing quote. Interpolation and
invalid escape/codepoint sequences are excluded conservatively. Consequently, invalidation watches the decoded candidate
paths rather than the source spelling.

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

Relative mode supports `./`, `../`, dot-prefixed filenames and directories, nested directories, and
partially typed path segments.

Candidates are:

- Directories whose names match the current segment.
- `.cr` files whose base names match the current segment. Once the segment contains an explicit
  extension prefix, completion retains and inserts the full `.cr` name.

The current file and non-Crystal files are excluded. Dotfiles and hidden directories are shown only
after the current segment starts with `.`, keeping ordinary listings uncluttered while allowing Crystal's
dot-prefixed relative paths. File lookup names normally omit the `.cr` extension; an explicitly typed
extension is preserved. Directory entries
display a trailing `/`.

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
- Stdlib discovery captures a project-scoped generation, effective SDK path, and discovery-override
  frame before running outside the lock. Cache clears, SDK/root changes, and discovery-override ownership
  changes advance the generation. A result is published only if all captured identities remain current;
  stale, disposed, null, and failed discoveries cannot replace a newer SDK root.
- `crystal env CRYSTAL_PATH` output is split with the current platform's `File.pathSeparatorChar`.
  Empty and relative entries are ignored when selecting the first absolute Unix or Windows candidate;
  Windows drive-letter paths therefore remain intact when the platform separator is `;`.
- Completion queries only the selected path roots and current directory level.
- The production require graph reuses every materialized node not marked dirty by
  a VFS content-change event without collecting its require fingerprint again.
  Cached closures maintain reverse ownership from each dependency node to exactly
  the closure roots that contain it. A clean effective snapshot is returned in
  constant time without traversing the root or prelude closure. A content event
  marks only owning closures, plus the shared prelude foundation when applicable,
  as requiring validation; unrelated dirty nodes do not affect another root's
  fast path.
  Dirty nodes validate their fingerprint inside the query's existing read action.
  An unchanged fingerprint preserves node, closure, and effective-snapshot
  identity; a changed fingerprint publishes a replacement and invalidates reverse
  dependents before returning. Unsaved require edits remain covered by the PSI
  listener. Listenerless tests can explicitly select full validation instead of
  imposing unconditional PSI walks on production queries.

## Verification Matrix

Automated coverage protects:

- Statement lookup discovery, lowercase matching, canonical deduplication, method presentation, and insertion.
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
- Direct and recursive wildcard invalidation through lexical aliases and canonical external targets.
- Platform-neutral Unix and Windows `CRYSTAL_PATH` parsing without subprocesses in unit tests.
