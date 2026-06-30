# Plan: RubyMine-style documentation hover for DOT-calls and classes

- **Status**: Ready for implementation
- **Date**: 2026-06-30
- **Scope**: `CrystalDocumentationProvider` rendering, link infrastructure, tests
- **Branch**: `feat/rubymine-doc-hover-format`

## Goal

Bring the Crystal plugin's documentation popup (Ctrl+Q / F1 hover) to parity with
RubyMine's style, specifically:

1. **Two-line method layout** — enclosing class on line 1 (hyperlinked to the
   class's own documentation), method signature on line 2.
2. **No `def ` prefix** on the signature line (user decision #1). Just the bare
   method name and parameters, e.g. `hika(name : String)`.
3. **No self-link on the documented class's own signature** (user decision #2).
   When the popup is showing `class Tesa < Object`, neither `Tesa` (the doc
   target) nor the `class`/`<`/`(` punctuation is a link. The superclass
   `Object` IS a link. Analogously for `module`, `struct`, `enum` — the
   definition's own name stays plain.
4. **Silent omitting of non-resolvable type links** (user decision #3). If a
   parameter type / return type / superclass name is not found in
   `CrystalClassIndex` (e.g. `Int32` when the stdlib index lacks it), the name
   is rendered as plain (un-linked, un-coloured) text — no silent no-op click
   targets.
5. **Top-level methods** show `Object` as the enclosing class link, matching
   RubyMine's Ruby convention (Crystal's universal base class is also `Object`).
6. **Click behaviour**: clicking any hyperlink in the popup replaces the popup
   content with the target element's documentation (platform-provided via
   `getDocumentationElementForLink`).
7. **Identical format at definition and call site** — hovering inside
   `def self.hika` and hovering on `Tesa.hika` produce the same popup.

## Visual Examples

### Instance method hover — `tesa.instanzi(age : Int32)` (call site OR definition)

```
Tesa                              ← blue hyperlink → opens class Tesa doc
instanzi(age : Int32)             ← syntax-highlighted; "Int32" plain (not in index)
```

### Static method hover — `Tesa.hika(name : String)`

```
Tesa                              ← blue hyperlink
hika(name : String)               ← "String" is a blue hyperlink → opens String doc
```

### Top-level method hover — `sahne(bonbon : String)`

```
Object                            ← blue hyperlink (Crystal's universal base)
sahne(bonbon : String)            ← "String" is a blue hyperlink
```

### Class hover — `class Tesa < Object`

```
class Tesa < Object               ← "Object" hyperlink; "Tesa" plain (self-link omitted)
# doc comment rendered as markdown
```

### Module hover — `module Foo`

```
module Foo                        ← nothing linked
# doc comment
```

## Implementation Plan

### 1. `CrystalDocumentationProvider` — link infrastructure

**A. Override `getDocumentationElementForLink(link: String, originalElement: PsiElement?)`.**

- Encoding scheme: `psi_element://class:<name>` (e.g. `psi_element://class:Tesa`).
- IntelliJ's documentation popup recognises the `psi_element://` scheme and
  routes clicks to this override.
- Implementation:
  1. Parse `link` (everything after `psi_element://`).
  2. For the `class:` prefix, use `StubIndex.getElements(CrystalClassIndex.KEY, name, project, GlobalSearchScope.allScope(project), CrystalNamedElement::class.java)`.
  3. Return `firstOrNull()`.
- Unresolvable names (e.g. `Int32` missing from stdlib index): return `null`.
  IntelliJ's popup simply shows "No documentation found" for the clicked link
  — this is the expected "silent" behaviour.

**B. Helper: `linkToClass(name: String, project: Project): String?`.**

- Returns an `<a href='psi_element://class:{name}' style='color:#3850BB; text-decoration:underline'>{name}</a>` string.
- Performs a `CrystalClassIndex.KEY` lookup **first**; returns `null` if the
  name does not resolve (decision #3 — silent omitting). Callers are
  responsible for falling back to plain text.
- The JetBrains-blue `#3850BB` is the standard documentation link colour
  used by IntelliJ's JDK docs popup.

**C. Helper: `wrapTypeLinks(plainText: String, project: Project): String`.**

- Regex-finds all `[A-Z][A-Za-z0-9_]*` substrings in `plainText`.
- For each, calls `linkToClass(name, project)`. Non-resolvable names are kept
  as-is (plain text).
- Used by both the parameter list rendering and the superclass rendering.
- Does NOT touch the syntax-highlighted HTML — works on the plain signature
  string BEFORE highlighting. The resulting string (which contains `<a>`
  tags) is then highlighted by `highlightCrystalCode`; the highlighter must
  preserve the `<a>` tags. We'll use a custom rendering path:
  1. Build plain signature string with `<a>` tags.
  2. Call `highlightCrystalCode` on the plain Crystal text **without** the `<a>`
     tags (use the plain text).
  3. Re-inject the `<a>` wrappers by matching the highlighted spans against
     the known type names.
- Alternative simpler approach (preferred initially): build the signature HTML
  piecewise — highlight each non-type fragment separately, and concatenate with
  `<a>` links for the type names. This avoids fragile regex post-processing.

### 2. `buildMethodSignature` — new two-line layout

Replace the current single-line `ClassName#methodName` / `ClassName.methodName`
format with:

- **Line 1 — enclosing class link**:
  - `enclosingClassName?.let { linkToClass(it, project) ?: it }` — hyperlink
    when resolvable, plain text otherwise.
  - For top-level methods (no enclosing class/module): use `"Object"` as the
    enclosing name (decision per user — Crystal's universal base).
- **Line 1 — newline separator**: `\n`.
- **Line 2 — method signature** (NO `def ` prefix per decision #1):
  - Method name only (`hika`, `tanzen`, `sahne`). For `def self.tanzen`, strip
    the `self.` prefix (use `method.name` which already does this via the
    stub). For top-level `def self.foo`, also strip `self.` (this fixes the
    existing noted bug).
  - `(param1, param2, ...)` where each param text is rendered with
    `wrapTypeLinks` applied to the type-annotation portion.
  - ` : ReturnType` if present, with `wrapTypeLinks` applied to the return
    type text.

### 3. `buildClassSignature` — superclass link only

`class Tesa < Object`:
- `class ` plain.
- Class name plain — **NOT** a link (decision #2 — no self-referential link).
- If `superClassList` is non-empty (use PSI, not the naive `contains("<")`
  text check — fixes the generic-parameter mis-detection noted in the audit):
  - ` < ` plain.
  - For each super class entry: wrap its name with `linkToClass(name, project) ?: name`.
    Multiple superclasses (Crystal allows comma-separated unions) get
    `, `-joined rendering.

### 4. `buildModuleSignature` — no links

`module Foo` — module name plain. No superclasses in Crystal module syntax.

### 5. Struct/Enum signature builders (new) — parity

Add `buildStructSignature` and `buildEnumSignature` methods:
- `struct Foo` — struct name plain.
- `enum Foo` — enum name plain.
- Generic params on structs: `<T>` — keep as plain text (no link targets for
  type parameters).

Add `CrystalStructDefinition` and `CrystalEnumDefinition` cases in
`renderSignature`'s `when` block.

### 6. `resolveTarget` — extend

- **New case**: when the leaf element hovered is a `CONSTANT` whose parent
  chain contains a `CrystalDotCallAccess`, treat it as a class lookup. Look
  up the CONSTANT's text via `CrystalClassIndex.KEY` and return the matching
  type definition. This gives the hover-on-receiver behaviour (`Tesa` in
  `Tesa.new`).

- **Add struct/enum to early-return list** in `resolveTarget` so hovering
  directly on a struct/enum definition produces a properly formatted signature
  instead of falling through to `target.text.lines().first()`.

### 7. Tests — `CrystalDocumentationProviderTest`

- **Update existing tests** that assert flat `Foo#bar` format:
  - `testInstanceMethodShowsClassName` → assert `Foo` appears inside an
    `<a href='psi_element://class:Foo'>` tag; no longer asserts `#` separator.
  - `testClassMethodShowsDotNotation` → assert method name on line 2, class
    link on line 1, no `.` separator.
  - `testMethodWithReturnType`, `testMethodWithDocComment`,
    `testMethodWithoutDocComment`, `testMethodWithCodeExample`,
    `testMethodWithMultilineDoc` — update any assertions that look for the
    old `ClassName#methodName` / `ClassName.methodName` pattern.
  - `testClassDocumentation`, `testModuleDocumentation` — update to check the
    new class/module signature format and that the class's own name is NOT a
    link.

- **New tests**:
  - `testEnclosingClassIsHyperlinked` — class name appears inside `<a href>`.
  - `testParameterTypeIsHyperlinked` — `String` inside the param list is linked.
  - `testReturnTypeIsHyperlinked` — `String` after ` : ` is linked.
  - `testSuperclassIsHyperlinkedInClassSignature` — `Object` is linked.
  - `testOwnClassNameIsNotHyperlinked` — `Tesa` in `class Tesa < Object` is
    plain text, not inside an `<a>`.
  - `testTopLevelMethodShowsObjectAsEnclosingLink` — top-level `def sahne`
    shows `Object` on line 1, as a hyperlink.
  - `testNoDefPrefixInMethodSignature` — signature line 2 does not start with
    `def `.
  - `testNonResolvableTypeIsNotLinked` — parameter `Int32` (when stdlib index
    lacks it) renders as plain text, no `<a>` tag.
  - `testStructDefinitionShowsStructSignature` — `struct Foo` rendered,
    name plain.
  - `testEnumDefinitionShowsEnumSignature` — `enum Foo` rendered, name plain.
  - `testHoverOnDotCallReceiverConstantResolvesToClassDoc` — covers the new
    `resolveTarget` case for `Tesa` in `Tesa.new`.
  - `testGetDocumentationElementForLinkResolvesToClassViaIndex` — pure unit
    test for the new link resolver; calls
    `provider.getDocumentationElementForLink("psi_element://class:Tesa", null)`
    and asserts the returned element is the `CrystalClassDefinition`.
  - `testGetDocumentationElementForLinkReturnsNullForUnknownName` —
    `psi_element://class:DoesNotExist` returns `null`.

- **Test helpers**: a regex-based HTML assertion utility that checks both
  raw text presence AND link presence.

### 8. Tests — `CrystalDotCallReferenceTest`

No changes required — reference resolution logic is unchanged.

### 9. Risks and mitigations

- **Stdlib type links**: behaviour depends on what the stdlib index actually
  contains. `String` is confirmed resolvable; `Int32` may or may not be.
  Non-resolvable names silently render as plain text (decision #3). Verified
  by `testNonResolvableTypeIsNotLinked`.
- **HTML post-processing fragility**: The `highlightCrystalCode` → `<a>`
  injection via regex could break across IntelliJ versions. Preferred safer
  alternative: build the signature HTML piecewise (highlight non-type
  fragments, concatenate with `<a>` wrappers for type names). Start with the
  piecewise approach to avoid the fragility entirely.
- **Generic type parameters**: `<T>` on structs/classes should NOT be
  linked — they type-parameter names are not in `CrystalClassIndex`. The
  `wrapTypeLinks` regex naturally skips them (only links names present in the
  index), but we should confirm via tests.
- **Parameter parsing for nested types**: `Hash(String, Array(Int32))` should
  link `Hash`, `String`, `Array` (and `Int32` if indexed). The
  `[A-Z][A-Za-z0-9_]*` regex captures all four; the `linkToClass` lookup filters
  to only the indexed ones. Confirmed by adding
  `testNestedGenericTypeAreLinked` if time permits.

### 10. Out of scope

- Struct/enum **doc comment extraction** — works already via `collectDocComment`.
- Changing how `.new` constructor resolution itself works — stays in
  `CrystalGotoDeclarationHandler`.
- StubIndex reload behaviour — the index is already populated by the existing
  builder; this plan only reads from it.
- `FileTypeIndex` scans — forbidden (per AGENTS.md); no new scans introduced.

### 11. Acceptance criteria

- All existing `CrystalDocumentationProviderTest` tests pass (after updates
  for the new format).
- All new tests pass.
- Manual hover test scenarios, each reproduced identically at definition and
  call site:
  1. Hover `hika` in `Tesa.hika(...)`: line 1 `Tesa` linked, line 2
     `hika(name : String)` with `String` linked.
  2. Hover `instanzi` in `Tesa.new.instanzi(...)`: line 1 `Tesa` linked, line 2
     `instanzi(age : Int32)` with `Int32` plain (un-indexed).
  3. Hover `sahne` in top-level `sahne(x : String)`: line 1 `Object` linked,
     line 2 `sahne(x : String)` with `String` linked.
  4. Hover `Tesa` in `class Tesa < Object`: `Object` linked, `Tesa` plain.
  5. Click `Tesa` link in method popup: popup switches to `Tesa`'s class doc.
  6. Click `String` link in method popup: popup switches to `String`'s class doc.
- `./gradlew test` runs clean (excluding the 3 pre-existing
  `CrystalCompletionTest` failures unrelated to this change).

### 12. Deliverables

- Modified: `src/main/kotlin/de/magynhard/crystal/documentation/CrystalDocumentationProvider.kt`
- Modified: `src/test/kotlin/de/magynhard/crystal/documentation/CrystalDocumentationProviderTest.kt`
- Modified: `AGENTS.md` — new bullet under "Architecture — Critical Rules"
- Modified: `CHANGELOG.md` — new entry under `[0.1.17]` / Changed section
- No BNF changes, no parser changes, no generated source changes.

### 13. AGENTS.md addition (text)

Add a new bullet under "Architecture — Critical Rules":

> **Documentation Provider links via `getDocumentationElementForLink` encoding `psi_element://class:<name>`** — class/method/parameter-type/superclass names inside the rendered documentation popup are hyperlinked; clicking resolves via `CrystalClassIndex` and replaces the popup content with the target element's documentation. Top-level methods render `Object` as the enclosing class (Crystal's universal base, matching RubyMine's behaviour for Ruby). A class's own name in its own class signature is NOT linked (no self-recursion). Non-resolvable type names (e.g. `Int32` absent from the stdlib index) are silently rendered as plain text. The `.new` constructor hover routes through `CrystalGotoDeclarationHandler` and benefits from the same link rendering. Never use `FileTypeIndex` at runtime — only `StubIndex.getElements()`.

### 14. CHANGELOG addition (text)

Add under `[0.1.17]` — Changed section:

> **Improved documentation hover format for DOT-call methods and class types** — hovering on `Tesa.hika` now displays `Tesa` (hyperlinked, blue) on the first line and `hika(params) : ReturnType` on the second line, with parameter and return types themselves hyperlinked to their class documentation (e.g. clicking `String` opens the `String` class doc in the same popup). Hovering on a class itself (`class Tesa < Object`) links the superclass; a class's own name is not self-linked. Top-level methods show `Object` as the enclosing class (matching RubyMine's Ruby convention). Clicking any link in the popup replaces the popup content with the linked element's documentation via `getDocumentationElementForLink`. Non-resolvable type names render as plain text.