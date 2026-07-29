# Expression DOT Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make DOT completion derive static or instance candidates from any PSI expression with a resolvable type, including arbitrarily grouped constants, literals, calls, collections, operators, and unions.

**Architecture:** Use a neutral `de.magynhard.crystal.analysis` type-set resolver as the single owner of scoped PSI inference. Each call creates one memoized, recursion-guarded session with cached StubIndex lookups and lexical variable flow. Completion locates/decomposes the receiver and preserves exact type-object identity, but delegates every runtime value and completed call to that neutral session. Legacy inspection/type-inference entry points are compatibility adapters only.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2026.1, PSI, StubIndex, JUnit 4 platform fixtures, Gradle 9.4.1, JDK 21.

## Global Constraints

- Work directly in the current `master` checkout, as explicitly requested for the ongoing work.
- Preserve the existing uncommitted grouped/generic DOT-call resolver changes; never reset, overwrite, or omit them.
- Use StubIndex APIs for runtime project lookups; never use `FileTypeIndex` or iterate all project Crystal files.
- Do not change lexer/parser inputs, generated sources, stub serialization, index emission semantics, or the file stub version.
- `Foo.`, `(Foo).`, and arbitrarily nested `((Foo)).` must produce identical static methods and constructor presentation.
- Transparent grouping applies to every supported receiver expression, including locals, parameters, instance variables, literals, calls, collections, operators, and control flow.
- Direct numeric completion such as `3.<caret>` is supported; completed floating literals remain ordinary float expressions.
- Union receivers expose all ordered known branch types only when every reachable branch resolves; any reachable unknown branch makes analysis `Unknown`.
- Generic value types normalize to their indexed base type for method lookup without flattening unions inside generic arguments.
- Once a DOT receiver is recognized, unresolved type analysis must not fall through to unrelated free-text method completion.
- Static methods and `new` belong only to type-object completion; runtime value completion returns instance methods.
- All-method completion is best-effort for macro-heavy types: body interpolation is irrelevant to
  method-name certainty, and only macro-controlled or dynamically named methods are filtered.
  Exact named consumers remain strict for an uncertain requested name.
- Concrete signed, unsigned, and floating primitives inherit methods through neutral implicit
  `Int`, `UInt`, and `Float` hierarchy edges when those parents are indexed.
- Range expressions resolve to the `Range` lookup base, constructor chains retain qualified
  identity, and DOT references expose every exact overload through polyvariant resolution.
- Candidate collection must fail the complete union when any branch identity or hierarchy is
  missing, ambiguous, or incomplete; completion never narrows to indexed branches.
- Follow strict RED/GREEN TDD for every behavior change.
- Update `docs/specs/completion.md`, `README.md` only if its user-facing feature list needs clarification, `CHANGELOG.md`, and any actionable deferred cases in `TODO.md`.
- Do not commit this architecture correction; leave the complete wave reviewable in the worktree.

---

### Task 1: Share Transparent Receiver Normalization

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/psi/CrystalReceiverExpression.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolver.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolver.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolverTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolverTest.kt`

**Interfaces:**
- Produces: `CrystalReceiverExpression.normalize(receiver: PsiElement): PsiElement`.
- Produces: `CrystalReceiverExpression.extractExactConstantTypeRoot(receiver: PsiElement): String?`.
- Produces: `CrystalReceiverExpression.unwrapTransparent(receiver: PsiElement): PsiElement` for completion Task 2.
- Preserves: current conservative suppression for assignments, multiple expressions, calls with value arguments, macro interpolation, class variables, unknown values, and ambiguous identities.

- [ ] Add focused regression tests proving the neutral helper returns equivalent normalized PSI/type roots for `Foo`, `(Foo)`, `((Foo))`, `Outer::Foo`, `(Outer::Foo)`, `(::Foo)`, local variables, typed parameters, and instance variables.
- [ ] Add negative tests for grouped assignments, comma-separated expressions, conditionals with conflicting types, macro interpolation, and arbitrary descendant constructor calls.
- [ ] Run the existing receiver/target suites and verify RED because the neutral helper does not exist:

```bash
./gradlew test --tests "de.magynhard.crystal.inspections.CrystalExactReceiverTypeResolverTest" --tests "de.magynhard.crystal.inspections.CrystalDotCallTargetResolverTest"
```

- [ ] Move only transparent normalization and exact constant-root extraction from the inspection resolver into `CrystalReceiverExpression`. Keep exact local/parameter/instance-variable evidence collection in `CrystalExactReceiverTypeResolver`.
- [ ] Implement transparent grouping structurally:

```kotlin
object CrystalReceiverExpression {
    fun normalize(receiver: PsiElement): PsiElement =
        unwrapTransparent(promoteVariableAccess(receiver))

    fun extractExactConstantTypeRoot(receiver: PsiElement): String? {
        val normalized = normalize(receiver)
        // Accept only a complete constant path or a generic type-object call
        // whose arguments are type references. Return the written qualified root.
    }

    fun unwrapTransparent(receiver: PsiElement): PsiElement {
        // Recurse through CrystalExpression/CrystalGroupedExpression only when
        // exactly one significant receiver expression is present.
    }
}
```

- [ ] Replace inspection-local normalization calls with the neutral helper and remove duplicated implementations.
- [ ] Run the focused suites and verify GREEN.
- [ ] Run `git diff --check`, inspect only Task 1 files, and commit:

```bash
git add src/main/kotlin/de/magynhard/crystal/psi/CrystalReceiverExpression.kt \
  src/main/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolver.kt \
  src/main/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolver.kt \
  src/test/kotlin/de/magynhard/crystal/inspections/CrystalExactReceiverTypeResolverTest.kt \
  src/test/kotlin/de/magynhard/crystal/inspections/CrystalDotCallTargetResolverTest.kt
git commit -m "refactor(psi): share DOT receiver normalization"
```

### Task 2: Analyze Completion Receiver Expressions

> **Approved architecture correction:** The original completion-specific delegation described below is superseded. Task 2 creates `CrystalTypeResolution`/`CrystalResolvedType` and `CrystalTypeResolutionSession` under `de.magynhard.crystal.analysis`. The session owns sequential lexical flow, truthiness-aware values, structured reachability, rescue/else/ensure, exact constructor/type identity, session-cached hierarchy/all-method lookup, method-return memoization, recursion guards, and cached StubIndex lookups. Completion retains receiver location and lookup-type adaptation only; postfix decomposition is shared. PSI references and compatibility adapters consume neutral analysis directly. No runtime path may use `collectElementsOfType(containingFile)`, project-wide first-name method fallback, or completion-owned inference.

**Correction files:**
- Create: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalTypeSet.kt`
- Create: `src/main/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolver.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/analysis/CrystalTypeSetResolverTest.kt`
- Modify: completion receiver, legacy inference adapter, expression resolver facade, design/spec/changelog/report.

**Correction verification:** lexical/file/type boundaries, nested assignments, all control-flow branches, unknown propagation, exact qualified/implicit-self/top-level calls, overload ambiguity, multi-return bodies, direct/mutual recursion, and all existing completion/inference/type-check/exact-receiver suites.

**Files:**
- Create: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionReceiverResolver.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContext.kt`
- Create: `src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionReceiverResolverTest.kt`

**Interfaces:**
- Produces:

```kotlin
internal sealed interface CompletionReceiver {
    data class TypeObject(
        val simpleName: String,
        val qualifiedName: String,
        val explicitIdentity: Boolean
    ) : CompletionReceiver

    data class ValueTypes(val typeNames: List<String>) : CompletionReceiver
    data object Unknown : CompletionReceiver
}

internal object CrystalCompletionReceiverResolver {
    fun resolve(position: PsiElement): CompletionReceiver
}
```

- Consumes: `CrystalReceiverExpression`, `CrystalExpressionTypeResolver`, `CrystalTypeInference`, the completion offset PSI, and the DOT immediately before the completion position.
- Produces: structured runtime type names with top-level union expansion and generic base normalization.

- [ ] Add platform-fixture tests that call `CrystalCompletionReceiverResolver.resolve(position)` for type objects, nested grouping, locals, typed parameters, instance variables, direct/grouped integer and string literals, arrays, hashes, tuples, operators, method results, and `if`/`case`/ternary results.
- [ ] Add tests for `Foo | Bar` result ordering and for `Array(Int32 | String)` remaining one `Array` lookup type instead of splitting the inner union.
- [ ] Add receiver-location tests for `3.<caret>`, `(3).<caret>`, `foo().<caret>`, `((foo())).<caret>`, and whitespace/newline before the dot.
- [ ] Add negative tests for unknown variables, malformed/incomplete groups, macro-interpolated receivers, assignments, and completion inside a float token such as `3.1<caret>` where no member-access dot exists.
- [ ] Run the new suite and verify RED:

```bash
./gradlew test --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest"
```

- [ ] Implement receiver location by finding the DOT before the completion position, selecting the outermost recognized receiver PSI whose text range ends at that DOT, and normalizing it through `CrystalReceiverExpression`. Do not reconstruct complete expressions from arbitrary source-text regexes.
- [ ] Classify an exact constant path before value inference. Resolve qualified/absolute identities through `CrystalIndexService.findTypes()` plus `CrystalPsiUtils.buildQualifiedName`; ambiguous identities return `Unknown`.
- [ ] Resolve value receivers in this order:

```kotlin
when (receiver) {
    is CrystalVariableReference,
    is CrystalInstanceVarAccess -> resolveVariableValue(receiver)
    else -> CrystalExpressionTypeResolver.resolveType(receiver)?.toValueTypes()
}
```

- [ ] Implement top-level union splitting with parenthesis-depth tracking. Normalize only the outer generic base:

```kotlin
internal fun normalizeLookupTypes(typeName: String): List<String> {
    // "Foo | Bar" -> ["Foo", "Bar"]
    // "Array(Int32 | String)" -> ["Array"]
}
```

- [ ] Run the new suite and verify GREEN, then run `CrystalTypeInferenceTest` and `CrystalTypeCheckInspectionTest` to protect existing inference consumers.
- [ ] Run `git diff --check`, inspect Task 2 files, and commit:

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionReceiverResolver.kt \
  src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContext.kt \
  src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionReceiverResolverTest.kt
git commit -m "feat(completion): resolve expression receiver types"
```

### Task 3: Merge Multi-Type Instance Candidates

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelperTest.kt`

**Interfaces:**
- Produces: `CrystalCompletionHelper.getMethodsAsLookups(typeNames: List<String>, project: Project): List<LookupElement>`.
- Preserves: `getMethodsAsLookups(typeName: String, project: Project)` as a delegating single-type API for existing callers.
- Consumes the neutral session API instead of reimplementing hierarchy traversal:

```kotlin
internal data class CrystalCollectedMethod(
    val method: CrystalMethodDefinition,
    val signatureKey: String,
    val receiverType: CrystalTypeIdentity,
    val declarationMode: CrystalReceiverMode,
    val depth: Int,
    val precedence: Int
)

internal data class CrystalAllMethodCollection(
    val methods: List<CrystalCollectedMethod>,
    val complete: Boolean
)

CrystalTypeResolutionSession.collectMethods(
    receiverType: CrystalTypeIdentity,
    mode: CrystalReceiverMode
): CrystalAllMethodCollection
```

- Named method resolution delegates to `collectMethods(...)`, filters by method name and optional
  actual-self mode, then preserves the existing ambiguity/completeness rules.
- One resolution session caches type declarations, methods by exact type, hierarchy edges, and
  metadata so repeated named/all-method calls do not repeat the same StubIndex query.

- [ ] Add failing tests with two exact types where each contributes one unique instance method and both contribute one identical signature. Assert unique methods remain, the identical signature appears once, and distinct overloads remain separate.
- [ ] Add qualified same-simple-name types and assert only methods from the written type identities contribute.
- [ ] Add primitive and generic base lookups (`Int32`, `String`, `Array`) using project/library index fixtures.
- [ ] Run the helper suite and verify RED:

```bash
./gradlew test --tests "de.magynhard.crystal.completion.CrystalCompletionHelperTest"
```

- [ ] Implement a methods-first merge by consuming `CrystalAllMethodCollection`. Resolve each type
  identity once per session, request instance-mode methods with stable depth/precedence metadata,
  and deduplicate by the shared `signatureKey` before building lookup elements.
- [ ] Keep deterministic type order and hierarchy priorities. Do not deduplicate solely by `lookupString`, because that would discard overloads.
- [ ] Make the existing single-type overload delegate:

```kotlin
fun getMethodsAsLookups(typeName: String, project: Project): List<LookupElement> =
    getMethodsAsLookups(listOf(typeName), project)
```

- [ ] Run the helper suite and verify GREEN.
- [ ] Run `git diff --check`, inspect Task 3 files, and commit:

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelper.kt \
  src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionHelperTest.kt
git commit -m "feat(completion): merge union receiver methods"
```

### Task 4: Route DOT Completion Through Receiver Analysis

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContext.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt`
- Modify: `src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionExtractionTest.kt`

**Interfaces:**
- Consumes: `CrystalCompletionReceiverResolver.resolve(position)` and the multi-type helper API.
- Preserves: existing static method, module, record, constructor tail, icon, priority, and lookup rendering.
- Removes: direct previous-leaf uppercase/lowercase receiver classification from `CrystalCompletionContributor`.

- [ ] Add parity tests comparing complete relevant lookup sets for `Foo.`, `(Foo).`, `((Foo)).`, qualified/absolute constants, modules, records, locals, typed parameters, and instance variables.
- [ ] Add literal completion tests for direct/grouped integers, floats, strings, chars, symbols, booleans, nil, regexes, commands, and heredocs. Assert representative stdlib methods such as `times` for `Int32` and `upcase` for `String`.
- [ ] Add expression tests for arrays, hashes, tuples, operators, and method results with annotated/inferred return types.
- [ ] Add union tests where each branch has a unique method and assert both methods appear. Assert identical signatures appear once and overloads remain distinct.
- [ ] Add negative tests proving runtime values do not offer `new` or static-only methods, modules do not offer `new`, completion inside `3.1<caret>` is not treated as DOT completion while `3.14.<caret>` is treated as `Float64` member access, and recognized unknown DOT receivers do not fall through to unrelated project methods.
- [ ] Run `CrystalCompletionTest` and verify RED for grouped/literal/expression cases:

```bash
./gradlew test --tests "de.magynhard.crystal.CrystalCompletionTest"
```

- [ ] Move DOT handling before the generic numeric-literal early return. Keep `isAfterNumericLiteral()` only for non-DOT completion contexts.
- [ ] Replace the current previous-leaf branching with result dispatch:

```kotlin
when (val receiver = CrystalCompletionReceiverResolver.resolve(position)) {
    is CompletionReceiver.TypeObject -> addTypeObjectCompletions(receiver, parameters, result)
    is CompletionReceiver.ValueTypes -> {
        CrystalCompletionHelper.getMethodsAsLookups(receiver.typeNames, project)
            .forEach(result::addElement)
    }
    CompletionReceiver.Unknown -> if (isDotCompletion(position)) return else Unit
}
```

- [ ] Extract static/constructor insertion into a focused private function or provider so the contributor remains dispatch-oriented. Preserve exact qualified filtering and record lookup behavior.
- [ ] Update `CrystalCompletionExtractionTest` to assert the new resolver/provider responsibility boundary.
- [ ] Run completion, helper, receiver, require-completion, and ECR completion suites and verify GREEN:

```bash
./gradlew test \
  --tests "de.magynhard.crystal.CrystalCompletionTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionHelperTest" \
  --tests "de.magynhard.crystal.CrystalRequireCompletionTest" \
  --tests "de.magynhard.crystal.ecr.EcrCompletionTest"
```

- [ ] Run `git diff --check`, inspect Task 4 files, and commit:

```bash
git add src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContributor.kt \
  src/main/kotlin/de/magynhard/crystal/completion/CrystalCompletionContext.kt \
  src/test/kotlin/de/magynhard/crystal/CrystalCompletionTest.kt \
  src/test/kotlin/de/magynhard/crystal/completion/CrystalCompletionExtractionTest.kt
git commit -m "feat(completion): complete DOT expressions by type"
```

### Task 5: Document And Verify Expression Completion

**Files:**
- Modify: `docs/specs/completion.md`
- Modify: `README.md` only if the existing completion feature summary needs expression-specific clarification
- Modify: `CHANGELOG.md`
- Modify: `TODO.md` only for concrete deferred behavior discovered during implementation
- Include: `docs/superpowers/plans/2026-07-28-expression-dot-completion.md`

**Interfaces:**
- Consumes: final grouped/type-object/value/union behavior from Tasks 1-4.
- Produces: user-facing release notes, durable behavioral specification, and fresh verification evidence.

- [ ] Document type-object versus runtime-value completion, arbitrary transparent grouping, direct numeric/string literal examples, generic base normalization, all-branch union merging, and unknown-receiver suppression in `docs/specs/completion.md`.
- [ ] Add a current-version changelog bullet describing expression-based DOT completion without claiming compiler-level inference.
- [ ] Audit `TODO.md` for every intentionally unsupported case found during implementation and add only actionable entries.
- [ ] Confirm no `.flex`, `.bnf`, generated source, serialized stub/index semantics, or `CrystalParserDefinition.FILE.getStubVersion()` changed.
- [ ] Run focused suites freshly:

```bash
./gradlew test \
  --tests "de.magynhard.crystal.CrystalCompletionTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionReceiverResolverTest" \
  --tests "de.magynhard.crystal.completion.CrystalCompletionHelperTest" \
  --tests "de.magynhard.crystal.inspections.CrystalExactReceiverTypeResolverTest" \
  --tests "de.magynhard.crystal.inspections.CrystalDotCallTargetResolverTest" \
  --rerun-tasks
```

- [ ] Run the full suite freshly:

```bash
./gradlew test --rerun-tasks
```

- [ ] Remove generated `*.java~` backup artifacts, run `git status --short`, `git diff --check`, inspect the complete range, and review `git log --oneline -10`.
- [ ] Stage only final documentation/plan files and commit:

```bash
git add docs/specs/completion.md CHANGELOG.md \
  docs/superpowers/plans/2026-07-28-expression-dot-completion.md
git commit -m "docs(completion): document expression DOT completion"
```
