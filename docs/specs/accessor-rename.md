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
  argument plus its call sites. There is no prompt.
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
- **Inplace rename is disabled** for the coupled symbol family
  (`isInplaceRenameSupported() == false`): the inplace renamer applies only
  the references it resolves itself and would leave the chain half-renamed
  (variable occurrences renamed, accessor declaration and call sites untouched
  — the code is then invalid). The dialog flow (prepareRenaming+
  ReferencesSearcher) applies the full union, so renaming stays a dialog for
  this family.
- The rename verifier (`CrystalRenameVerifier`) runs the compiler check on
  the file after the rename completes as before.

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
