# Static Finished Declarations

## Supported Source Model

Crystal's [`finished` hook](https://crystal-lang.org/reference/latest/syntax_and_semantics/macros/hooks.html)
runs after parsing; stacked hook definitions execute in definition order. The plugin
recognizes a deliberately restricted, statically readable subset, without running
Crystal or expanding arbitrary macros.

An eligible syntax body belongs to a receiver-less, parameterless `macro finished` owned by a non-generic module
whose enclosing namespaces are also non-generic modules. It must be outside macro
control regions and contain no interpolation (`{{`), macro controls (`{%`), or
macro-runtime `@type`. These spellings also reject a body when inside comments or
strings. Ordinary macros and unsupported hooks remain opaque.

To contribute semantic declarations, its entire body must additionally parse
without errors as ordinary Crystal and
contain only non-generic class declarations. Class members may be explicitly named
methods, instance-variable assignments, and accessor-macro calls. Nested hooks,
unsupported members, or syntax errors prevent the whole hook from contributing
symbols, preserving conservative guard/member suppression instead of extracting a
misleading partial declaration set.

For example, the following declares a usable exact type and typed reader:

```crystal
module LSP
  macro finished
    class FormattingRequest < RequestMessage(Nil)
      @method = "textDocument/formatting"
      property params : FormattingParams
    end
  end
end
```

## Editor Syntax

`CrystalAnnotator` uses the same cached namespace-restored syntax file to project
ordinary Crystal token colors onto the original macro-body ranges. Keywords,
constants, instance variables, strings, accessor names, and parameters follow the
normal highlighter's rules; actual comments retain comment colors. An erase marker
clears the host lexer's opaque-string style before those per-token colors apply.
The surrounding `macro finished` header and closing delimiter are not recolored by
the projection.

Parser errors in an eligible body are shown at mapped original-source ranges,
clamped to that body when recovery reports an error at the synthetic suffix. Fixing
the source invalidates the cached syntax and removes the error. Syntax recovery
does not publish malformed declarations for resolution. No normal unresolved-name,
arity, or runtime-type inspections are run against the generated syntax view.

The physical lexer/parser still represents macro bodies as opaque macro text. No
new tokens, grammar alternatives, injected file indexes, or generated parser files
are involved; the editor syntax projection is not general macro expansion.

## References From the Original Macro Text

For Go to Declaration, the Goto handler maps the actual clicked offset inside a
physical `MACRO_BODY_CONTENT` token to its corresponding syntax-view element. It
then delegates to that element's ordinary PSI reference or existing navigation
handler. It does not search declarations by the spelling of the original opaque
token. An existing reference with no targets is authoritative and never triggers
an unrelated name-only fallback.

The require graph attributes registered source-backed views to the original
physical file, while resolution retains the view's lexical class/module context.
Unbacked nonphysical fragments still have no effective source set. Thus a type
such as `DocumentRangeFormattingParams` can navigate from a property in a hook to
the following struct in the same physical file. Superclass names, individual
generic arguments, qualified path segments, absolute paths, and class references
in emitted method bodies use their established resolution rules. Type reopening
selection prefers the original physical source file rather than the temporary
view file.

Generated targets map back to their original source offsets. Comments, ordinary
strings, unsupported macro bodies, unrequired declarations, and unrelated
namespaces do not gain type links. Navigation in dumb mode remains unavailable.
This bridge supplies Go to Declaration, not a writable host reference or a new
rename/Find Usages implementation for opaque macro tokens.

## Lookup, Scope, and Lifetime

`CrystalStaticDeclarations` queries only `CrystalMacroIndex` candidates named
`finished`, in all scope, and filters their physical source files through the
session's captured effective-source snapshot **before** reading hook bodies.
There is no project-wide file iteration and no cross-program union. Independent
eligible hooks contribute independent declarations; an unrequired hook contributes
nothing.

Each accepted body is parsed into a separate, nonphysical PSI file with its module
namespace restored. These declaration views augment the shared session's type and
method candidates. They are never inserted into the original PSI or stub tree,
serialized as stubs, or published as new index keys. Existing exact-identity,
hierarchy-completeness, receiver-mode, and duplicate-signature rules still apply.
Hierarchy ordering and cross-file ambiguity use the **original source file and
offset**, not the synthetic PSI file's identity.

Parsed bodies and accessor signatures are cached on their source PSI with the
containing file as an invalidation dependency. A new session after a source edit or
require-context change obtains the updated source view. No parsed candidate list
escapes the captured source-set filter.
Concurrent cache computations compare declaration-view equivalence by source
revision rather than by the identities of freshly parsed PSI objects. This keeps
the platform cached-value idempotence contract without serializing reads behind a
global lock.

## Typed Accessors and Consumer Parity

Plain `getter`, `setter`, and `property` declarations and typed `getter?` /
`property?` query declarations, including their `class_`
variants in bare-argument form (`property params : Params`), supply read-only
method-signature views when their declared type is one
constant path, optionally nilable. The same mechanism handles ordinary source
accessors and accessors inside accepted `finished` classes. Complex or inferred
property types do not receive synthesized signatures. Generic
owners can expose signatures whose types are independent of their type parameters;
dependent types such as `T` or `T::Member` never resolve through an unrelated global
type with that name. Accessor bindings themselves follow the complete, exact
receiver hierarchy, including inherited generic-base declarations, even when their
return type cannot be inferred.

These signatures feed the shared method hierarchy, completion, and call-result
inference. Return types are resolved in the declaration's lexical context, so a
reader returning `FormattingParams` from `LSP` yields `LSP::FormattingParams` at a
call in another namespace. DOT reference resolution retains the existing
`DotCallResolution.Accessor` variant and the accessor argument as its declaration.
Documentation presents the typed reader signature instead of an `Any` variable.
Query declarations expose `name?` with the declared type (including nilability),
not a plain `name` reader. `property?` additionally exposes `name=`, while
`getter?` does not. Keyword names such as `property? struct : Bool` follow the
same pipeline: completion offers `struct?` and hover renders `struct? : Bool`.

A zero-argument typed accessor can be an exact predecessor in a DOT chain. Union,
nilable, unavailable, or ambiguous results, setter predecessors, and accessor calls
with arguments cannot supply one exact chained receiver.

### Enum Predicate Methods

Written enum members supply read-only instance query signatures, matching the
compiler's `member.name.underscore + "?"` rule (`HEREDOC` → `heredoc?`,
`StringArray` → `string_array?`). They return global `::Bool` and use their
original enum constant as the source/navigation target. The same shared views
feed named resolution, completion, hover, and chained call inference. They do
not become class methods or borrow members from unrelated or unrequired enums.
Enum bodies with macro-controlled/interpolated members remain conservative;
implicit flag members and other generated enum APIs are not synthesized here.

### Record Field Readers

Require-visible, statically named `record` headers supply read-only field-reader
views using the same method and source-origin pipeline. The record index remains
the lookup source; the current file's PSI replaces its indexed original during
completion. No generated code is inserted into physical PSI or stub trees.
Record headers contribute their Struct hierarchy and any written record body;
exact struct reopenings contribute their own methods. Duplicate or macro-controlled
records and declarations in parser-incomplete files cannot certify member lookup.

Readers have no setter. Simple constant-path annotations, optionally nilable,
resolve at the physical record field, so `Token::DelimiterState#kind` retains
`Token::DelimiterKind` through `token.delimiter_state.kind`. Untyped fields and
unsupported or generic-dependent types stay unknown, while their statically known
reader names remain available. Unmodeled `record` macro methods (`initialize`,
`copy_with`, `clone`) remain suppressed rather than producing missing-name errors.
Completion, hover, and navigation use the same reader view and original field.

Navigation maps source-view classes, methods, and accessor arguments back to their
original file and offset inside the macro body. Views are read-only: rename and
source editing are not implemented against generated PSI copies. Ordinary physical
accessor declarations retain their established rename behavior. The new views are
not standalone Go to Class/Symbol index entries, and arbitrary compile-time
generation remains out of scope.
