# Require-Aware DOT Completion Design

## Goal

DOT completion must expose methods from the Crystal program visible to the current file. Core
methods loaded by `prelude.cr` are always visible. Optional standard-library extensions, shard
extensions, and project reopenings are visible only when the current file directly or transitively
requires their source files.

Examples of the required core behavior include:

```crystal
"text".     # String methods such as upcase and downcase
3.          # Int32 and compiler-implicit Int methods such as times
[1, 2].     # Array and included Indexable/Enumerable methods such as each
```

Completion uses the current file's forward require closure. A file does not inherit requirements
from files that require it. For example, if `main.cr` requires both `json` and `model.cr`, editing
`model.cr` does not expose JSON extensions unless `model.cr` also reaches `json` through its own
require closure.

## Load Context

A project-level `CrystalRequireGraphService` owns the load context. Every Crystal file represented
in the graph is a node containing resolved edges for its direct top-level require statements.

The effective source set for a file is the immutable union of:

1. The configured SDK's transitive `prelude.cr` closure.
2. The current file.
3. The current file's transitive require closure.

Cycles terminate through a visited set, and each reachable file appears once. Top-level require
statements surrounded by macro-control directives are collected as unconditional static edges,
regardless of the active compiler flags. Requires in invalid runtime, type, method, macro
interpolation, or other dynamic contexts are not graph edges.

If `prelude.cr` is unavailable, the effective set contains the current file and every otherwise
resolvable requirement. The implementation must not replace missing prelude metadata with
hardcoded method candidates or an all-index fallback.

## Require Resolution

A focused `CrystalRequirePathResolver` resolves graph edges according to Crystal path semantics:

- Relative paths resolve from the requiring file's directory.
- Bare paths resolve against project and shard roots before the configured Crystal standard-library
  roots in a project or shard traversal. The traversal rooted at configured `prelude.cr` carries
  stdlib-foundation provenance through all descendants and resolves their bare paths only against
  that stdlib root, even when a relative edge leaves the root. Project `lib/` files therefore cannot
  shadow the shared prelude foundation. Relative paths retain normal file-relative resolution, and
  the same escaped support file remains project-first when reached independently from a project
  closure.
- `*` and `**` patterns expand only below the resolved target directory.
- Missing or ambiguous paths contribute no edge.
- Resolution never scans all Crystal project files and never uses `FileTypeIndex`.

Each exact edge records the normalized candidate-path prefix through its selected file, including
missing and higher-precedence project/shard candidates that could replace a standard-library result.
Each wildcard edge separately records every concrete watch directory, its intended target path, and
whether expansion is direct (`*`) or recursive (`**`). The public `watchedDirectories` metadata
continues to contain only wildcard directory watches. These rules apply equally to local paths such
as `src/models/*`, shard paths below `lib/`, and standard-library paths.

## Caching And Invalidation

The require graph is a directed graph with cached transitive closures and reverse dependency edges.
Each node stores a fingerprint derived only from its top-level require statements. Ordinary edits to
method bodies do not invalidate direct edges or transitive closures.

When a direct-require fingerprint changes, the service invalidates that node and only the nodes that
transitively depend on it. Closure recomputation is lazy. Unsaved PSI/document changes participate,
so adding, changing, or removing a require statement affects completion before the document is
saved. The project service listens only to Crystal PSI changes that touch a require subtree or can
replace a file-level structure containing one, then compares the fresh fingerprint before mutating
the graph. Ordinary method-body edits therefore retain node, closure, and effective-snapshot caches.

The prelude root is resolved once per SDK and source-root configuration. Its closure is reused as the
static foundation for every file while its complete dependency-version map remains current. A
changed or deleted prelude node, or a new outgoing edge anywhere in that closure, rebuilds the
foundation before another effective snapshot is published. These events invalidate the complete
path-resolution and graph cache:

- Crystal SDK or Crystal path changes.
- Source-root changes.
- Changes to `shard.yml` or `shard.lock`.
- Relevant `lib/` creation, deletion, rename, or movement caused by operations such as
  `shards install`.

Create, copy, delete, rename, and move events are processed after VFS completion using stable old and
new paths. A structural path matches an exact candidate or wildcard target when it is the path, lies
inside it, or is an ancestor containing it. Direct wildcard watches react only to immediate `.cr`
children, while recursive watches react to `.cr` files and potentially containing directories at any
depth. A nearest-existing-parent watch still matches only structural changes along or below its
intended missing target, not unrelated siblings. Mixed batches use structural-event paths only for
this matching.

The synchronous VFS callback performs only immutable path matching, structural cache invalidation,
and dirty marking for already materialized content-changed nodes; it never opens PSI, starts a read
action, or schedules executor work. The graph has no global pending-content barrier. A query validates
the current require fingerprint of each node only while traversing its prelude or requested closure
inside the existing outer read action. Unrelated dirty nodes neither gate nor rebuild that closure.
An unchanged fingerprint, including an ordinary body edit, retains node, closure, and snapshot
versions; a require edit publishes its replacement before that query returns. IntelliJ cancellation
propagates from the read-consistent traversal and leaves dirty state retryable. Unsaved PSI changes
continue to compare their fresh fingerprint immediately. `childMoved` and PSI property events
participate in the same targeted comparison. The PSI, VFS, and project-root listeners are all
registered with project disposal.

Every query atomically captures the current cached stdlib-root identity together with the graph
generation and uses that one root for prelude and bare-path resolution. Any observed `null` to root,
root to different root, or root to `null` transition invalidates the generation before traversal;
stale retries capture both values again. This applies even when a component other than SDK settings
populates the resolver cache. SDK apply separately invalidates immediately after clearing the old
root, then resolves and publishes replacement roots, and guarantees a final invalidation in `finally`
even when resolution, refresh, or a synchronous publication callback fails.

The service publishes immutable snapshots for concurrent completion reads. Graph mutation and
reverse-edge invalidation are serialized. The complete prelude and root traversal, including node
collection, resolution, and publication, shares one IntelliJ read action. An atomic write therefore
cannot produce one effective snapshot containing nodes from states on opposite sides of that write.
The graph mutation lock protects only already materialized in-memory state. Closure traversal is
iterative so arbitrarily deep chains and cycles do not consume the thread stack.

No Crystal compiler process runs during completion. The graph reads only an already cached,
project-scoped stdlib root and conservatively omits the prelude when no cache exists; SDK setup and
explicit refresh actions remain responsible for discovery. A cached-root identity transition is part
of graph validity, so a root populated later during project startup makes the prelude, bare exact
requires, and bare wildcards visible on the next query without an external invalidation callback.

Effective snapshots normalize physical queries and every resolved edge through the original PSI
file's `VirtualFile`. Nonphysical injected Crystal queries receive only the configured prelude
foundation; invalid queries remain empty, and membership checks reject unrelated nonphysical PSI
copies. This preserves core literal completion in ECR without inferring project or host context. A
stable direct-require fingerprint preserves the existing node, closure, and effective snapshot
identity; a changed fingerprint publishes a new node version and lazily rebuilds only cached closures
rooted at that node or its reverse dependents.

## Completion Integration

The graph filters indexed symbols; it does not replace StubIndex as their source.

1. `CrystalCompletionContributor` requests the effective source set for the original file.
2. The actual completion position creates the shared `CrystalTypeResolutionSession`; a project-root
   PSI directory must not substitute for the file context.
3. Existing StubIndex queries retrieve candidates by exact type or enclosing class name.
4. Type declarations, reopenings, methods, include/extend edges, and superclass declarations are
   accepted only when their containing file belongs to the effective source set.
5. Existing exact identity, hierarchy ordering, macro uncertainty, overload, union, and
   compiler-implicit primitive-parent rules operate on that filtered program view.

The effective source set uses normalized `VirtualFile` identities in an immutable hash set, making
each candidate filter constant-time. Project-level graph and prelude caches are reused across
completion invocations; the existing short-lived type and hierarchy session caches remain scoped to
one analysis request.

Filtering is shared by neutral type and hierarchy resolution rather than implemented as a
stdlib-specific completion exception. Consequently, standard-library, shard, and project
reopenings follow one load-context rule across consumers of `CrystalTypeResolutionSession`.

## Failure Behavior

- An unresolved edge contributes nothing immediately. The service does not retain a stale last-good
  closure.
- Ambiguous resolution follows Crystal root precedence. Ambiguity that remains within the selected
  precedence level suppresses that edge.
- Cycles produce a finite deduplicated source set.
- During IntelliJ dumb mode, StubIndex-dependent completion may remain empty. Graph resolution must
  not force indexing or compensate with a filesystem scan.
- Unknown or incomplete receiver resolution retains the existing no-fallback behavior and never
  exposes unrelated free-text methods.

## Verification

Automated tests must cover:

- A realistic prelude closure exposing `String#upcase`/`downcase`, `Int#times` through `Int32`, and
  `Array#each` through included modules.
- Optional stdlib, shard, and project reopenings absent before a require and present after direct or
  transitive requires.
- Forward file-closure semantics without reverse or sibling require leakage.
- Top-level macro-control-wrapped requires.
- Relative, bare, `*`, and `**` path resolution.
- Wildcard invalidation after create, delete, rename, and move events in local, shard, and stdlib
  target directories.
- Invalidation after `shard.yml`, `shard.lock`, SDK/root, `lib/`, and required-file changes.
- Cache reuse after ordinary source edits and targeted dependent invalidation after require edits.
- Query-local saved-content validation without executor barriers, including write-access queries,
  cancellation, and unrelated dirty nodes.
- Cold bare exact and wildcard graphs after another component publishes the stdlib root, plus
  `null`/root/different-root transitions.
- SDK replacement during discovery, synchronous root-publication queries, and failed publication.
- Unsaved require edits, cycles, unresolved paths, ambiguity, missing prelude, and dumb-mode safety.
- Preservation of exact namespaces, overloads, hierarchy ordering, unions, macro uncertainty, and
  optional-extension suppression.

Tests may use test-visible cache instrumentation to prove reuse and targeted invalidation. Production
code must not retain diagnostic logging.
