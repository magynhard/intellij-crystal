# Accessor Rename (getter/setter/property family)

Status: implemented. Source of truth for the bidirectional accessor rename
coupling described in the refactoring specification session of 2026-09-08.

## The implicit chain

Crystal macros like `getter`, `setter`, `property` and their variants bridge
method names, assignment operators and the backing instance/class variables.
The plugin resolves and renames them through ONE declaration unit: the macro
-call argument. For `property foo` the declaration is the argument composite
(`foo`); the macro-generated reader/setter methods have no PSI of their own.
Renaming `foo` must rewrite the entire chain — renaming only the parameter
name would declare an accessor that no variable backs (or vice versa), which
is broken code in both directions, so the coupling is deterministic and silent
(no prompt, no dialog choice — the NAME ITSELF dictates the target).

## The mapping table

```
macro variant            generated methods                coupled variable
getter foo               def foo                          @foo
getter? foo              def foo?                         @foo (Bool)
getter! foo              def foo!                         @foo (nilable)
setter foo               def foo=(val)                    @foo
property foo             def foo, def foo=(val)           @foo
property? foo            def foo?, def foo=(val)          @foo
property! foo            def foo!, def foo=(val)          @foo (nilable)
class_getter foo         def self.foo                     @@foo
class_setter foo         def self.foo=(val)               @@foo
class_property foo       def self.foo, def self.foo=(val) @@foo
class_getter?/…          def self.foo?                    @@foo
class_property! foo      def self.foo!, def self.foo=(val) @@foo (nilable)
```

The generated method names keep their variant suffix: `property? active`
declares `active?` and `active=` — the plain reader `active` is NOT the
property macro's declaration. `allowedAccessorMacros` maps the called method
name to the declaring macro family (plain readers from suffix-free macros
only, `?`/`!` for their suffixed shapes, setters include the `setter!` and
`property!` variants since those still declare `foo=`).

## Rename plumbing

- **Declaration PSI**: arguments of accessor macro calls implement
  `PsiNameIdentifierOwner` via `CrystalAccessorArgumentMixin` (the BNF mixin
  on `argument` and `bare_argument`). Multi-declaration lists
  (`property foo, bar, baz`) rename ONLY the targeted argument — each
  argument is its own element. Typed declarations
  (`property foo : String = value`) expose the leading identifier leaf.
- **Forward direction** (rename the accessor): the
  `CrystalAccessorReferencesSearcher` unions the usage set — the coupled
  `@foo`/`@@foo` variable accesses inside the type (via
  `CrystalInstanceVarFinder`, which also covers the storage-shortcut
  parameters `initialize(@foo : T)`), reader dot-calls whose shared exact
  resolution binds exactly this argument, and setter member assignments
  (`obj.foo = v`, `obj.foo += v`).
- **Reverse direction** (rename `@active`/`@@timeout`):
  `CrystalAccessorRenamePsiElementProcessor.prepareRenaming` pulls the
  coupled accessor argument into the rename set with the same bare name, and
  the searcher also attaches a declaration reference for the accessor
  argument plus its call sites. There is no prompt. The processor ACCEPTS
  the storage-shortcut `CrystalParameter` composite (`canProcessElement`
  with `parameterNameInfo().storageName != null`): the IDE resolves the
  caret on `initialize(@in_loop)` to the PARAMETER, not the instance-var
  access — without the acceptance the default processor handled the rename
  and the coupled `getter? in_loop` declaration was never touched. Coupling
  runs on the parameter's wrapped access composite (`instanceVarAccess`),
  since the parameter text itself carries the type annotation.
- **Setter member assignments** bind inline (`postfix_op` member assignment —
  no call PSI), so a transient `CrystalMemberAssignUsageReference` on the
  name leaf carries the rename; the `=`/compound operator's identity is
  untouched. Only receivers whose exact type resolves to the accessor's
  (instance receiver) or whose constant name resolves to the declared type
  (static receiver — `Session.timeout = 45`) participate: unrelated
  same-name members of other types NEVER follow.
- **Suffix shapes**: reader/writer call sites keep their own shape
  (`obj.on?`, `obj.on = v`) — `CrystalDotCallReference.handleElementRename`
  re-applies the call-site suffix the macro variant dictates, while the
  declaration name itself carries none.
- **The rename dialog prefills the BARE name** for sigil-bearing variables:
  `RenameDialog` hydrates via `UsageViewUtil.getShortName` →
  `ElementDescriptionUtil.getElementDescription(element, UsageViewShortNameLocation)`,
  and `CrystalElementDescriptionProvider` returns the sigil-stripped short name
  for `CrystalInstanceVarAccess` / `CrystalClassVarAccess` / storage-shortcut
  parameters. setNames re-apply the sigil from the token type — the user types
  `cycled` in the dialog and the code gets `@@cycled` everywhere. Only the
  short-name location is overridden; all other ElementDescription locations
  fall through to the platform. getName() on the access composites stays the
  full sigiled token (the Find-Usages word channel depends on it).

- **Inplace rename is disabled** for the coupled symbol family on BOTH gates:
  the processor's `isInplaceRenameSupported() == false` AND
  `CrystalRefactoringSupportProvider.isMemberInplaceRenameAvailable` returning
  false for `CrystalInstanceVarAccess`, `CrystalClassVarAccess`, AND storage
  shortcut parameters (`CrystalParameter` with
  `parameterNameInfo().storageName != null` — the caret resolves the
  `initialize(@in_loop)` leaf to the parameter composite, NOT the
  instance-var access, so the composites-gate alone missed the user's
  trigger). The inplace renamer applies only its own references AND writes
  `getName()` into the buffer the moment the template starts —
  `CrystalParameterMixin.getName()` reports the LOCAL name (`in_loop`), never
  the sigil — so the `@` disappears as soon as rename is triggered and every
  re-typed sigil is normalized away on Enter (ameba flow_expression.cr:35).
  The dialog flow (prepareRenaming + ReferencesSearcher + setName sigil
  re-apply from the original token type) applies the full union with the
  sigil preserved; normal (sigil-less) parameters keep the inplace rename.
- The rename verifier (`CrystalRenameVerifier`) runs the compiler check on
  the file after the rename completes as before.

## Highlight usages symmetry (parameter targets)

Highlight-usages (click a symbol) resolves the caret element through
`CrystalInstanceVarReference.resolve()`, which **promotes the first offset
occurrence to its `CrystalParameter`** when it is a storage shortcut
(`initialize(@in_loop)`). Renames do not alter that promotion, but plain
`else -> return` gates in the ReferencesSearchers dropped every highlight
whenever the search target was a parameter: after renaming `@in_loop`, a
click on the variable no longer marked the coupled `getter? in_loop`
declaration, while clicking the declaration still marked the variable.

Both searchers now accept storage-shortcut parameter targets:

- `CrystalAccessorReferencesSearcher`: a `CrystalParameter` with
  `storageName != null` routes through the wrapped access composite
  (`instanceVarAccess ?: classVarAccess`) and yields the
  `CrystalAccessorDeclarationRenameReference` plus the full union — the
  declaration argument joins the highlight chain exactly as for direct
  variable clicks.
- `CrystalInstanceVarReferencesSearcher`: the wrapped composite supplies the
  enclosing type and the storage name (sigil-prefixed) supplies `varName`,
  so the intra-class var occurrences highlight from the parameter target too.

The resolve-side promotion stays untouched — it is the IDE rename trigger
for storage shortcuts, and the highlight fix lives entirely in the search
targets.

### Untyped default-valued declarations

`getter? in_call_args = false` (ameba `assignment_in_call_argument.cr`):
the bare argument composite is `name = default` and the bearer name is
the assignment LHS — previously `accessorNameIdentifier` resolved
neither a direct IDENTIFIER child nor a bare variable_reference wrapper,
so `findAccessorArgForVar` found nothing and the ivar-driven reverse
rename left the declaration behind (the forward direction worked by a
different plumbing path). `accessorNameIdentifier` now resolves the
assignment LHS of the bare argument: the nested
`CrystalAssignment`'s direct IDENTIFIER child (direct-child or nested
forms both handled). Rename plumbing (declaration rename reference
rewrite, narrowed highlight range, arg matching) shares this resolver,
so the default-valued shape joins every direction.

Note the honest boundary: a SEPARATE method with the same name as the
reader (`private def in_call_args(value = true, &)` next to
`getter? in_call_args`) is a distinct symbol — its declaration and its
bare calls never join the rename.

### Bare implicit-self reader calls

The lexer folds the `?` reader suffix into the IDENTIFIER token
(`in_loop?` is ONE identifier leaf), so bare implicit-self reader calls
(`flow_expression?(exp, in_loop?)`) carry no receiver composite and no
dot-call binding — the ameba `flow_expression.cr` rename left every bare
reader while declarations, ivars and qualified readers followed. The
accessor word-hit walker now covers the implicit-self call:

- The bare hit must textually equal the reader name implied by the macro:
  `getter`/`property` without a suffix declare the plain name reader, the
  `?`-variants (`getter?`, `property?`, …) declare the `?`-suffixed
  reader. `!`-variants and setter-only macros never declare an instance
  reader, and class-var accessors skip the implicit-self surface (the
  class method needs a class-level self — follow-up).
- The hit must live in the declaring type's own body (implicit self).
- CONSISTENCY GATE: a bare name resolves to a LOCAL first, and
  distinguishing a local read from the accessor read needs full
  reaching-definition flow — the conservative gate applies: any same-name
  parameter or any same-name local binding (`x = …`) inside the enclosing
  method shadows the accessor, and every bare occurrence in that method
  keeps its name. Binding LHS shapes are identified like
  `CrystalLocalUsageAnalyzer.localAssignmentIdentifier` (direct
  assignment IDENTIFIER child, non-sigil assignments only).
- `def name` method-name positions and the declaration identifier itself
  are excluded.

Renaming the bare hit goes through a transient
`CrystalBareReaderUsageReference`: the leaf rewrite preserves the `?`
suffix (`in_loop?` → `uses_loop?`) since the suffix lives inside the
token.

### Highlight range = the identifier leaf only

`CrystalAccessorDeclarationRenameReference` initially declared
`TextRange(0, element.textLength)` — the whole argument composite. For a
typed declaration (`getter? in_loop : Bool`) the platform highlight-usages
pipeline then marked ` : Bool` together with the name (visibly "too much"
after a rename on both sides: clicking the variable AND clicking the
declaration identifier). The reference range now resolves
`CrystalAccessorCoupling.accessorNameIdentifier` and covers only the
identifier leaf; the full composite remains the fallback when no
identifier resolves. The other yielded references are already narrow:
member-assignment references sit on the IDENTIFIER leaf and var-access
references cover the sigil-prefixed access like standard variables.

### Declaration rename reference on untyped arguments

`CrystalAccessorDeclarationRenameReference.handleElementRename` previously
located the identifier child directly on the argument node — a no-op for
**untyped** declarations (`getter? in_loop`), whose identifier leaf lives
inside the `variable_reference` wrapper. It now resolves the leaf through
`CrystalAccessorCoupling.accessorNameIdentifier`, which handles both shapes
(identically to `CrystalAccessorArgumentMixin.setName`); typed declarations
are unaffected. Regression: a reference-level rename on an untyped argument
rewrites the declaration.

## Unused-variable analysis on declaration-macro arguments

The accessor name is a macro-call ARGUMENT that binds its default value
(`property autocorrect = false`) through the `bare_argument ::= ... |
assignment` alternative. The unused-variable analysis would collect that
composite as a plain local assignment and report "Variable '…' is never
used" — a false positive for the whole family: an accessor declaration is
API surface (Crystal itself never warns for it), consumers are instances /
subclasses / other files that local analysis cannot see, and tracking that
consumption would require full-program receiver analysis.

`CrystalLocalUsageAnalyzer` skips CrystalAssignments under TWO declaration
families:

- accessor macros (`CrystalAccessorCoupling`'s 16-macro set shared with the
  rename coupling),
- `record` declarations — every field argument is a FIELD DECLARATION
  (`record Result, sources = [] of Source, metadata = Metadata.new`): the
  field is consumed through the generated accessor methods, the deserializer,
  or other files — never as a local-variable read. The declaration detection
  mirrors `CrystalPsiUtils.recordDeclaredName` (`record` first child plus the
  capitalized-type name shape), so unrelated `record`-named runtime calls are
  unaffected.

Real locals in the same class keep their ordinary diagnosis; the gate is
argument-scoped, so unrelated assignments stay tracked.

## Known scope boundaries (follow-up)

- Setter receiver chains (`obj.nested.foo = v`) participate only when the
  receiver is a single-level expression that resolves exactly; deeper
  chain-shape matching (resolving `obj.nested` to the declared type through
  the dot-call chain) is future work.
- Reader call sites resolve through the accessor binding on the
  `CrystalDotCallTargetResolver` — the type must resolve exactly, the honest
  Unknown suppression stays: an unresolved receiver never matches. Stubs for
  other re-opened bodies of the same type (same name in other files) are
  honored by the type identity through the exact declaration search.
- Text occurrences in comments/strings are not rewritten.

## Verification matrix

Automated coverage protects:

- Forward rename: declaration argument, `@foo` ivar, `initialize(@foo)`
  storage shortcut, reader dot-calls, setter member assignments.
- Reverse rename: the variable side pulls the same chain (declaration arg,
  ivars, call sites) — deterministic, no UI prompt.
- Multi-decl: only the targeted argument follows; sibling arguments and
  their call sites stay untouched.
- `?` variants: reader call sites keep the `?` suffix; setters follow.
- class_* family: `@@foo` class variable and static receiver call sites
  (`Server.timeout = 45`) follow; the plain family does NOT bind `class_*`
  declarations and vice versa.
- Honest negative: same-name members of unrelated types never rename.
