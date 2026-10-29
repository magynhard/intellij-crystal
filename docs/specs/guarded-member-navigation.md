# Guarded Member Navigation

## Availability Versus Exact Dispatch

A positive `responds_to?` fact proves a member available on the same local binding.
It does not select a concrete class from a module restriction. Ordinary exact
resolution remains authoritative; when it is suppressed, navigation may offer
known compatible declarations under the following conditions:

- The guard fact is still valid for this binding and member at the call site.
- Neutral variable flow supplies one known nominal type, resolved exactly in the
  captured require/prelude source context. Unknown values, unions, expired guards,
  stale parameter annotations, and macro-runtime contexts do not qualify.
- Each candidate type has a complete instance hierarchy proving the receiver's
  full qualified identity as an ancestor. A same-simple-name module in another
  namespace never establishes compatibility. Absolute superclass/include paths
  retain their global identity instead of trying lexical shadows.
- Candidate declarations and every hierarchy lookup use the same effective source
  snapshot. Indexed but unrequired implementations and reopenings remain excluded.

In crystalline, `error : Crystal::ErrorFormat` guarded by `responds_to?(:inner)`
can therefore navigate to `property inner : CodeError?` in
`Crystal::TypeException`, without declaring the variable itself a `TypeException`.

## Candidate Discovery

`CrystalTypeInheritorIndex` maps the simple names of explicitly declared
superclasses and included modules to class/module/struct/enum declarations.
`CrystalDeclaredAncestors` extracts only candidate names during normal full-PSI
stub building. It does not inspect nested methods, types, macros, or blocks, does
not mistake an enum's integer base for a superclass, excludes interpolated type
names, and excludes references rooted in lexical generic parameters. `extend` and compiler-imposed implicit edges are not
reverse-index hints.

The index is a coarse discovery mechanism, not a subtype certificate. Navigation
walks its declared reverse edges, including intermediate modules and superclass
chains, and proves each qualified candidate through the shared hierarchy. Static
finished-hook views can contribute their explicitly declared candidate edges as
well. There is no enumeration of all type keys or project files. The search checks
cancellation and is capped at 256 encountered qualified types; exceeding the budget
discards partial targets rather than choosing an arbitrary first result.

Inherited member targets deduplicate by their actual declaration. One known target
jumps directly; several use the ordinary platform chooser. The candidate list is
not a proof that the indexed universe contains every possible implementation,
especially for dynamic macro output or paths requiring implicit reverse edges.

## Reference and Documentation Contract

`CrystalDotCallReference.multiResolve()` publishes these possible declarations as
results with `isValidResult == false`. The existing Goto handler may navigate to
them, but `resolve()` returns no exact declaration and `isReferenceTo()` does not
certify them as automatic rename/usage bindings. Ordinary exact results retain
their established behavior. These strict APIs do not run the reverse-index
navigation search; background exact-resolution clients do not acquire a subtype
candidate pool merely by asking `resolve()`.

The DOT resolver remains `Suppressed`; candidate signatures never enter argument
count, type checking, or return-type inference. Hover presents a **guarded member**,
explains that `responds_to?` guarantees availability, and lists known compatible
source declarations explicitly as possibilities. It does not show an `Any`
variable fallback or pretend that a candidate's return type is the call's result.

## Stub Persistence

Stub version 23 adds the `ancestorNames` list to all four nominal type-stub kinds
and registers the reverse index descriptor-first. Ancestor names round-trip through
the platform stub serializer. Existing caches must rebuild after the update.
`CrystalTypes` and the platform `DefaultStubBuilder` remain authoritative: there is
no lexer-only stub builder, synthetic stub-tree insertion, or runtime file scan.
