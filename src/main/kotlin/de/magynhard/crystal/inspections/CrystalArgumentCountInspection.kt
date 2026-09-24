package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Inspection that validates argument count against method parameter definitions.
 * Reports warnings when:
 * - Required (non-default) parameters are missing
 * - Too many arguments are provided (excess args highlighted individually)
 *
 * Handles:
 * - Parenthesized calls: foo(arg1, arg2)
 * - Bare calls: foo arg1, arg2
 * - DOT-calls: Foo.bar(arg1)
 * - Named arguments: foo(name: value)
 * - Splat (*args) and double-splat (**kwargs) parameters
 * - Block (&block) parameters (not counted)
 * - Default parameter values (make parameter optional)
 * - Multiple overloads (only reports if NO overload matches)
 */
class CrystalArgumentCountInspection : LocalInspectionTool() {

    // Wires the inspectionDescriptions/<shortName>.html resource into the
    // platform's description loading; without it the Inspect Code results
    // view crashes when a result node is selected.
    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CrystalInspectionScope.isProjectSource(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Macro context: {{ … }} interpolations, macro bodies, and
                // macro-invocation block bodies hold AST arguments consumed by
                // macros as data — ordinary argument diagnostics do not apply
                // (v13). The macro-block gate covers DSL forms like ameba's
                // `properties do bin_path nil, as: String? end`, where the
                // documented named argument `as` carries the property type.
                if (CrystalMacroContext.isInMacroContext(element) ||
                    CrystalMacroContext.isInsideMacroCallBlock(element)) {
                    return
                }
                when (element) {
                    is CrystalMethodCallExpression -> checkCall(element, holder)
                    is CrystalBareMethodCallExpression -> checkCall(element, holder)
                    is CrystalVariableReference -> checkArgumentlessDirectCall(element, holder)
                    is CrystalDotCallAccess -> if (element.callArgs == null && element.bareArgumentList == null) {
                        checkDotCall(element, holder)
                    }
                    is CrystalCallArgs, is CrystalBareArgumentList -> findOwningDotCall(element)?.let {
                        checkDotCall(it, holder)
                    }
                }
            }
        }
    }

    private fun findOwningDotCall(argumentHolder: PsiElement): CrystalDotCallAccess? {
        return generateSequence(argumentHolder.parent) { it.parent }
            .filterIsInstance<CrystalDotCallAccess>()
            .firstOrNull {
                it.callArgs === argumentHolder || it.bareArgumentList === argumentHolder
            }
    }

    private fun checkArgumentlessDirectCall(reference: CrystalVariableReference, holder: ProblemsHolder) {
        // A hash-entry key (`{error: "..."}`) is a symbol-like key, not a
        // variable read and not a zero-argument call: the entry's leading
        // identifier followed by the key colon never participates in name
        // resolution, so kemal's `error` DSL must not meet JSON payload keys.
        // The key expression sits inside an EXPRESSION wrapper, so match the
        // nearest hash entry and require the reference to live in its first
        // expression child of a colon-keyed entry.
        val entry = PsiTreeUtil.getParentOfType(reference, CrystalHashEntry::class.java)
        if (entry != null && entry.node.findChildByType(CrystalTypes.COLON) != null) {
            val keyExpression = entry.node.getChildren(null)
                .firstOrNull { it.elementType == CrystalTypes.EXPRESSION }
            if (keyExpression != null && PsiTreeUtil.isAncestor(keyExpression.psi, reference, false)) return
        }

        val methodNameElement = reference.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            ?: reference.node.findChildByType(CrystalTypes.CONSTANT)?.psi
            ?: return

        // Arguments of macro invocations are macro syntax, not runtime calls:
        // inside `property app_name, host_binding, … logging …` the identifiers
        // feed the macro expansion and must never be measured against the
        // same-named top-level defs (`def logging(status)` in kemal's helpers).
        val owningCall = generateSequence(reference.parent) { it.parent }
            .takeWhile { it !is PsiFile }
            .firstOrNull { it is CrystalMethodCallExpression || it is CrystalBareMethodCallExpression }
        if (owningCall != null) {
            val callName = CrystalCallExtractor.extractMethodName(owningCall)
            // Stdlib macros (`property`, `getter`, …) live outside the project
            // content root — the macro existence test must look at the whole
            // index, matching the always-visible prelude semantics of Crystal.
            if (callName != null &&
                CrystalIndexService.findMacros(
                    callName, reference.project, GlobalSearchScope.allScope(reference.project),
                ).isNotEmpty()
            ) {
                return
            }
        }

        var next = reference.nextSibling
        while (next is PsiWhiteSpace || next?.node?.elementType == CrystalTypes.NEWLINE) {
            next = next.nextSibling
        }
        if (next is CrystalDotCallAccess || next is CrystalNamespaceAccess) return

        val project = reference.project
        val scope = GlobalSearchScope.projectScope(project)
        val methodName = methodNameElement.text
        // Require-graph visibility: a top-level method in an unrelated file is NOT
        // callable here — crystal would report `undefined local variable or method`.
        var methods = CrystalRequireVisibility.visibleMethods(
            CrystalIndexService.findTopLevelMethods(methodName, project, scope), reference
        )
        if (methods.isEmpty()) return

        val crystalReference = reference.reference as? CrystalReference
        if (crystalReference?.resolveLocalDeclaration() != null) return

        if (CrystalIndexService.findTypes(methodName, project, scope).isNotEmpty()) return
        // Same-name macros (stdlib ones included) make the name a macro
        // invocation rather than a runtime call.
        if (CrystalIndexService.findMacros(methodName, project, GlobalSearchScope.allScope(project)).isNotEmpty()) {
            return
        }

        checkArgumentCount(methods, emptyList(), methodNameElement, holder)
    }

    private fun checkCall(callExpr: PsiElement, holder: ProblemsHolder) {
        // Skip if the method name resolves to a local variable or parameter.
        // This prevents false positives when a bare call like "count + 87" is parsed
        // as method_call_expression(count, +87) due to binary_op_lookahead not catching
        // operators followed by literals. The parameter shadows any same-named method.
        val resolvedRef = callExpr.reference?.resolve()
        if (resolvedRef != null && resolvedRef !is CrystalMethodDefinition) return

        val methodName = CrystalCallExtractor.extractMethodName(callExpr) ?: return
        val arguments = extractArguments(callExpr)

        // A macro-spliced argument (`{{ … }}`) makes the arity unknowable before
        // expansion: it may expand to zero or more arguments, or to an operator
        // between operands (`to_f32 {{ op.id }} other` in crystal/compiler_rt.cr
        // expands to `to_f32 + other`). Skip arity diagnostics; the target still
        // resolves.
        if (arguments.any { CrystalMacroContext.isMacroSplicedArgument(it.element) }) return

        val methodNameElement = CrystalCallExtractor.findMethodNameElement(callExpr) ?: return

        val project = callExpr.project
        val scope = GlobalSearchScope.projectScope(project)
        // Require-graph visibility: methods from files this call cannot see must
        // not participate in the overload set.
        val methods = CrystalRequireVisibility.callableUnqualified(
            CrystalRequireVisibility.visibleMethods(
                CrystalIndexService.findMethods(methodName, project, scope).toList(), callExpr
            ),
            callExpr,
        )


        // An unqualified `new` inside a type resolves through the shared exact
        // constructor pool — explicit `def self.new` overloads plus implicit
        // initializer forwarders, including inherited ones — exactly like the
        // DOT-call path. The plain method index only contains written `new`
        // definitions, so a forwarding call such as `def self.new(context,
        // exception)` calling `new(exception, ... 10 args)` against an inherited
        // initializer was measured against the two-parameter forwarder alone.
        if (methodName == "new") {
            val enclosingType = CrystalPsiUtils.getEnclosingType(callExpr)
            val qualifiedName = enclosingType?.let(CrystalPsiUtils::buildQualifiedName)
            if (qualifiedName != null) {
                val identity = de.magynhard.crystal.analysis.CrystalTypeIdentity(
                    qualifiedName.substringAfterLast("::"),
                    qualifiedName,
                )
                val session = de.magynhard.crystal.analysis.CrystalTypeSetResolver.session(callExpr)
                when (val resolution = session.resolveConstructor(identity)) {
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Methods -> {
                        checkArgumentCount(resolution.methods, arguments, methodNameElement, holder)
                        return
                    }
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Record -> {
                        val fieldArguments = CrystalPsiUtils.recordFieldArguments(resolution.recordDefinition)
                        if (fieldArguments.isNotEmpty()) {
                            checkRecordArguments(extractRecordFields(fieldArguments), arguments, methodNameElement, holder)
                        }
                        return
                    }
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Implicit -> {
                        checkImplicitConstructorArguments(arguments, methodNameElement, holder)
                        return
                    }
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Abstract,
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Incomplete,
                    -> return
                    is de.magynhard.crystal.analysis.CrystalConstructorResolution.Unavailable -> Unit
                }
            }
        }

        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, methodNameElement, holder)
    }

    private fun checkDotCall(access: CrystalDotCallAccess, holder: ProblemsHolder) {
        val resolution = CrystalDotCallTargetResolver.resolve(access)
        val call = when (resolution) {
            is DotCallResolution.Methods -> resolution.call
            is DotCallResolution.ImplicitConstructor -> resolution.call
            is DotCallResolution.RecordFallback -> resolution.call
            // Accessor declarations have no call-shape beyond their
            // macro-generated reader/setter shape — no arg diagnostics.
            is DotCallResolution.Accessor -> return
            DotCallResolution.Suppressed, DotCallResolution.Unresolved -> return
        }
        val arguments = extractArgumentsFromArgsElement(call.argumentHolder)

        // Same macro-splice rule as the bare-call path: a `{{ … }}`-led argument
        // makes the arity unknowable before expansion.
        if (arguments.any { CrystalMacroContext.isMacroSplicedArgument(it.element) }) return

        when (resolution) {
            is DotCallResolution.Methods -> checkArgumentCount(
                resolution.methods,
                arguments,
                call.methodNameElement,
                holder
            )
            is DotCallResolution.ImplicitConstructor -> checkImplicitConstructorArguments(
                arguments,
                call.methodNameElement,
                holder
            )
            is DotCallResolution.RecordFallback -> {
                checkRecordArguments(
                    extractRecordFields(CrystalPsiUtils.recordFieldArguments(resolution.recordDefinition)),
                    arguments,
                    call.methodNameElement,
                    holder
                )
            }
            DotCallResolution.Suppressed, DotCallResolution.Unresolved -> Unit
        }
    }

    private fun checkImplicitConstructorArguments(
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder
    ) {
        if (arguments.any { it.isSplat && it.resolvedSplatCount == null }) return
        if (arguments.any { it.isDoubleSplat && it.resolvedDoubleSplatKeys == null }) return

        val effectivePositionalCount = arguments.sumOf { argument ->
            when {
                argument.isBlockPass -> 0
                argument.isSplat -> argument.resolvedSplatCount ?: 1
                argument.isDoubleSplat || argument.name != null -> 0
                else -> 1
            }
        }
        val namedArgumentNames = buildSet {
            arguments.forEach { argument ->
                argument.name?.let(::add)
                argument.resolvedDoubleSplatKeys?.let(::addAll)
            }
        }
        val effectiveArgCount = effectivePositionalCount + namedArgumentNames.size
        if (effectiveArgCount == 0) return

        val message = "Too many arguments: expected at most 0, got $effectiveArgCount"
        if (arguments.any { it.isSplat || it.isDoubleSplat }) {
            holder.registerProblem(methodNameElement, message, ProblemHighlightType.GENERIC_ERROR)
            return
        }
        arguments.filterNot { it.isBlockPass }.forEach { argument ->
            holder.registerProblem(
                findHighlightTarget(argument.element),
                message,
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }

    private fun checkArgumentCount(
        methods: List<CrystalMethodDefinition>,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder
    ) {
        // If any argument has an unresolvable splat/double-splat, skip the check entirely
        val hasUnresolvedSplat = arguments.any { it.isSplat && it.resolvedSplatCount == null }
        val hasUnresolvedDoubleSplat = arguments.any { it.isDoubleSplat && it.resolvedDoubleSplatKeys == null }
        if (hasUnresolvedSplat || hasUnresolvedDoubleSplat) return

        // Expand resolved splats into effective argument counts
        val effectivePositionalCount = arguments.sumOf { arg ->
            when {
                arg.isBlockPass -> 0 // block-pass (&block) is not a positional argument
                arg.isSplat -> arg.resolvedSplatCount ?: 1
                arg.isDoubleSplat -> 0 // double-splat contributes named args, not positional
                arg.name != null -> 0 // named args aren't positional
                else -> 1
            }
        }

        // Collect named arg names (including resolved double-splat keys)
        val namedArgNames = mutableSetOf<String>()
        for (arg in arguments) {
            if (arg.name != null) namedArgNames.add(arg.name)
            if (arg.isDoubleSplat && arg.resolvedDoubleSplatKeys != null) {
                namedArgNames.addAll(arg.resolvedDoubleSplatKeys)
            }
        }

        val effectiveArgCount = effectivePositionalCount + namedArgNames.size

        // Check each overload
        var bestMatch: OverloadMatch? = null

        for (method in methods) {
            val match = evaluateOverload(method.parameterList, effectiveArgCount, effectivePositionalCount, namedArgNames)

            if (match.isValid) return // At least one overload accepts this call

            // Track best (closest) match for error reporting
            if (bestMatch == null || match.isBetterThan(bestMatch)) {
                bestMatch = match
            }
        }

        // No overload matched — report problem
        val match = bestMatch ?: return

        when {
            match.missingParams.isNotEmpty() -> {
                val missing = match.missingParams.joinToString(", ") { "'$it'" }
                holder.registerProblem(
                    methodNameElement,
                    "Missing required argument(s): $missing",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
            match.excessStartIndex >= 0 -> {
                if (arguments.none { it.isSplat || it.isDoubleSplat }) {
                    for (i in match.excessStartIndex until arguments.size) {
                        val argExpr = arguments[i].element
                        val target = findHighlightTarget(argExpr)
                        holder.registerProblem(
                            target,
                            "Too many arguments: expected at most ${match.maxArgs}, got ${effectiveArgCount}",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                } else {
                    holder.registerProblem(
                        methodNameElement,
                        "Too many arguments: expected at most ${match.maxArgs}, got ${effectiveArgCount}",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
            match.unknownNamedArgs.isNotEmpty() -> {
                val unknownNames = match.unknownNamedArgs.joinToString(", ") { "'$it'" }
                var highlighted = false
                for (arg in arguments) {
                    if (arg.name != null && arg.name in match.unknownNamedArgs) {
                        holder.registerProblem(
                            arg.element,
                            "Unknown named argument '${arg.name}'",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                        highlighted = true
                    }
                }
                if (!highlighted) {
                    // Unknown keys came from resolved double-splat — report on method name
                    holder.registerProblem(
                        methodNameElement,
                        "Unknown named argument(s): $unknownNames",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
    }

    // ==================== Overload Evaluation ====================

    data class OverloadMatch(
        val isValid: Boolean,
        val missingParams: List<String> = emptyList(),
        val excessStartIndex: Int = -1,
        val maxArgs: Int = 0,
        val unknownNamedArgs: Set<String> = emptySet()
    ) {
        fun isBetterThan(other: OverloadMatch): Boolean {
            // Prefer the match with fewer missing params.
            if (missingParams.size != other.missingParams.size) {
                return missingParams.size < other.missingParams.size
            }
            // Equally close overloads that omit different required names must
            // rank deterministically instead of following collection order.
            val thisMissing = missingParams.sorted().joinToString("\u0000")
            val otherMissing = other.missingParams.sorted().joinToString("\u0000")
            if (thisMissing != otherMissing) return thisMissing < otherMissing
            val thisUnknown = unknownNamedArgs.sorted().joinToString("\u0000")
            val otherUnknown = other.unknownNamedArgs.sorted().joinToString("\u0000")
            return thisUnknown < otherUnknown
        }
    }

    private fun evaluateOverload(
        parameterList: CrystalParameterList?,
        argCount: Int,
        positionalCount: Int,
        namedArgNames: Set<String>
    ): OverloadMatch {
        val params = parameterList?.parameterList.orEmpty()
        val namedOnlyNames = namedOnlyParameterNames(parameterList)
        val regularParams = mutableListOf<ParamInfo>()
        var hasSplat = false
        var hasDoubleSplat = false

        for (param in params) {
            when {
                param.node.findChildByType(CrystalTypes.AMPERSAND) != null -> continue
                param.node.findChildByType(CrystalTypes.STAR) != null -> { hasSplat = true; continue }
                param.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null -> { hasDoubleSplat = true; continue }
                // Macro-generated splat fragments (`{{ items.splat }}`) expand to an
                // unknown number of parameters: suppress count diagnostics like a splat.
                param.node.findChildByType(CrystalTypes.MACRO_INTERPOLATION) != null -> { hasSplat = true; continue }
            }
            val name = param.parameterNameInfo().callSiteName ?: continue
            val hasDefault = param.expression != null
            regularParams.add(ParamInfo(name, hasDefault, name in namedOnlyNames))
        }

        val paramNames = regularParams.map { it.name }.toSet()
        val requiredParams = regularParams.filter { !it.hasDefault }

        // Check unknown named args (only if no double-splat)
        if (!hasDoubleSplat) {
            val unknown = namedArgNames - paramNames
            if (unknown.isNotEmpty()) {
                return OverloadMatch(isValid = false, unknownNamedArgs = unknown)
            }
        }

        // Check: which required params are satisfied?
        val satisfiedByName = namedArgNames.intersect(requiredParams.map { it.name }.toSet())
        val requiredNotSatisfiedByName = requiredParams.filter { it.name !in satisfiedByName }

        // Named-only parameters (after a bare `*` or a `*splat`) can only be
        // satisfied by name; positional arguments never fill them. Report
        // missing parameters in declaration order to keep messages stable.
        val missing = mutableListOf<String>()
        var positionalSlot = 0
        for (param in requiredNotSatisfiedByName) {
            if (param.namedOnly) {
                missing.add(param.name)
            } else {
                if (positionalSlot >= positionalCount) missing.add(param.name)
                positionalSlot++
            }
        }
        if (missing.isNotEmpty()) {
            return OverloadMatch(isValid = false, missingParams = missing)
        }

        // Check too many args (only if no splat)
        if (!hasSplat) {
            val positionalParams = regularParams.filterNot { it.namedOnly }
            val namedSatisfied = namedArgNames.intersect(positionalParams.map { it.name }.toSet())
            val maxPositional = positionalParams.size - namedSatisfied.size
            if (positionalCount > maxPositional) {
                return OverloadMatch(
                    isValid = false,
                    excessStartIndex = argCount - (positionalCount - maxPositional),
                    maxArgs = regularParams.size
                )
            }
        }

        return OverloadMatch(isValid = true)
    }

    /**
     * Names of parameters that follow a bare `*` separator or a `*splat`
     * parameter. Crystal requires such parameters to be passed by name, so a
     * positional argument must never satisfy them.
     */
    private fun namedOnlyParameterNames(parameterList: CrystalParameterList?): Set<String> {
        val result = mutableSetOf<String>()
        if (parameterList == null) return result
        var namedOnly = false
        for (child in parameterList.node.getChildren(null)) {
            when (child.elementType) {
                CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR -> namedOnly = true
                else -> {
                    val param = child.psi as? CrystalParameter ?: continue
                    if (namedOnly) {
                        param.parameterNameInfo().callSiteName?.let { result.add(it) }
                    }
                    if (param.node.findChildByType(CrystalTypes.STAR) != null ||
                        param.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null) {
                        namedOnly = true
                    }
                }
            }
        }
        return result
    }

    data class ParamInfo(val name: String, val hasDefault: Boolean, val namedOnly: Boolean = false)

    // ==================== Argument Extraction ====================

    data class ArgumentInfo(
        val element: PsiElement,
        val name: String? = null,
        val isSplat: Boolean = false,
        val isDoubleSplat: Boolean = false,
        val isBlockPass: Boolean = false,
        /** For splat args resolved to tuple literals: the element count */
        val resolvedSplatCount: Int? = null,
        /** For double-splat args resolved to named tuple literals: the key names */
        val resolvedDoubleSplatKeys: Set<String>? = null
    )

    private fun extractArguments(callExpr: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()
        when (callExpr) {
            is CrystalMethodCallExpression -> {
                val callArgs = callExpr.callArgs
                if (callArgs != null) {
                    for (arg in CrystalPsiCallArguments.getArguments(callArgs)) {
                        result.add(extractArgInfo(arg))
                    }
                    // The trailing bare tail of the `call_args COMMA
                    // bare_argument_list` shape (`restrict (X), context`)
                    // lives outside `call_args` and must be counted too.
                    for (bare in CrystalPsiCallArguments.trailingBareArguments(callArgs)) {
                        result.add(extractBareArgInfo(bare))
                    }
                    // (v12: heredoc headers are ordinary marker arguments inside
                    // the list — no extra terminator accounting needed.)
                    return result
                }
                val bareArgList = callExpr.bareArgumentList
                if (bareArgList != null) {
                    for (element in CrystalPsiCallArguments.argumentElements(bareArgList)) {
                        when (element) {
                            is CrystalBareArgument -> result.add(extractBareArgInfo(element))
                            // The leading array literal of the array-comma shape.
                            else -> result.add(ArgumentInfo(element))
                        }
                    }
                }
            }
            is CrystalBareMethodCallExpression -> {
                val callArgs = callExpr.callArgs
                val argList = callArgs?.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        result.add(extractArgInfo(arg))
                    }
                } else {
                    // Bare (parenthesis-free) arguments: heredoc headers, bare
                    // calls and literals live in the bare list — ameba's
                    // `as_node <<-CRYSTAL` (variable_spec.cr:102) used to lose
                    // its only argument here and report "Missing 'source'".
                    val bareArgList = callExpr.bareArgumentList
                    if (bareArgList != null) {
                        for (element in CrystalPsiCallArguments.argumentElements(bareArgList)) {
                            when (element) {
                                is CrystalBareArgument -> result.add(extractBareArgInfo(element))
                                else -> result.add(ArgumentInfo(element))
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractArgumentsFromArgsElement(argsElement: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()
        for (element in CrystalPsiCallArguments.argumentElements(argsElement)) {
            when (element) {
                is CrystalArgument -> result.add(extractArgInfo(element))
                is CrystalBareArgument -> result.add(extractBareArgInfo(element))
                // The leading array literal of the array-comma bare shape
                // (`obj [a], b`): a positional argument.
                else -> result.add(ArgumentInfo(element))
            }
        }
        return result
    }

    private fun extractArgInfo(arg: CrystalArgument): ArgumentInfo {
        val children = arg.node.getChildren(null)
        var isSplat = false
        var isDoubleSplat = false
        var isBlockPass = false

        val firstType = children.firstOrNull()?.elementType
        when (firstType) {
            CrystalTypes.STAR -> isSplat = true
            CrystalTypes.DOUBLE_STAR -> isDoubleSplat = true
            CrystalTypes.AMPERSAND -> isBlockPass = true
        }
        val namedLabel = CrystalPsiCallArguments.getNamedLabel(arg)

        val resolvedSplatCount = if (isSplat) resolveSplatCount(arg) else null
        val resolvedDoubleSplatKeys = if (isDoubleSplat) resolveDoubleSplatKeys(arg) else null

        return ArgumentInfo(arg, namedLabel, isSplat, isDoubleSplat, isBlockPass, resolvedSplatCount, resolvedDoubleSplatKeys)
    }

    private fun extractBareArgInfo(bareArg: CrystalBareArgument): ArgumentInfo {
        val children = bareArg.node.getChildren(null)
        var isSplat = false
        var isDoubleSplat = false
        var isBlockPass = false

        val firstType = children.firstOrNull()?.elementType
        when (firstType) {
            CrystalTypes.STAR -> isSplat = true
            CrystalTypes.DOUBLE_STAR -> isDoubleSplat = true
            CrystalTypes.AMPERSAND -> isBlockPass = true
        }
        val namedLabel = CrystalPsiCallArguments.getNamedLabel(bareArg)

        val resolvedSplatCount = if (isSplat) resolveSplatCount(bareArg) else null
        val resolvedDoubleSplatKeys = if (isDoubleSplat) resolveDoubleSplatKeys(bareArg) else null

        return ArgumentInfo(bareArg, namedLabel, isSplat, isDoubleSplat, isBlockPass, resolvedSplatCount, resolvedDoubleSplatKeys)
    }

    // ==================== Splat Resolution ====================

    /**
     * For a splat argument (*expr), try to resolve the expression to a tuple literal
     * and return its element count. Returns null if not resolvable.
     */
    private fun resolveSplatCount(argElement: PsiElement): Int? {
        val varName = findSplatVariableName(argElement) ?: return null
        val tupleLiteral = resolveVariableToLiteral(argElement, varName) ?: return null
        return countTupleElements(tupleLiteral)
    }

    /**
     * For a double-splat argument (**expr), try to resolve the expression to a named tuple
     * literal and return its key names. Returns null if not resolvable.
     */
    private fun resolveDoubleSplatKeys(argElement: PsiElement): Set<String>? {
        val varName = findSplatVariableName(argElement) ?: return null
        val literal = resolveVariableToLiteral(argElement, varName) ?: return null
        return extractNamedTupleKeys(literal)
    }

    /**
     * Extract the variable name from a splat/double-splat argument.
     * For `*args` or `**options`, returns "args" or "options".
     */
    private fun findSplatVariableName(argElement: PsiElement): String? {
        // The argument node children include STAR/DOUBLE_STAR followed by EXPRESSION(VARIABLE_REFERENCE(IDENTIFIER))
        val children = argElement.node.getChildren(null)
        var foundSplat = false
        for (child in children) {
            val type = child.elementType
            if (type == CrystalTypes.STAR || type == CrystalTypes.DOUBLE_STAR) {
                foundSplat = true
            } else if (foundSplat && type == CrystalTypes.EXPRESSION) {
                // Look for VARIABLE_REFERENCE > IDENTIFIER inside the expression
                val varRef = child.findChildByType(CrystalTypes.VARIABLE_REFERENCE)
                if (varRef != null) {
                    val id = varRef.findChildByType(CrystalTypes.IDENTIFIER)
                    return id?.text
                }
                // Direct IDENTIFIER
                val id = child.findChildByType(CrystalTypes.IDENTIFIER)
                return id?.text
            }
        }
        return null
    }

    /**
     * Resolve a variable name to its assignment literal in the same scope.
     * Searches backwards from the usage site for `varName = <literal>`.
     * Returns the RHS expression element (tuple/hash literal) or null.
     */
    private fun resolveVariableToLiteral(usageSite: PsiElement, varName: String): PsiElement? {
        // Walk up to statement level
        var current: PsiElement? = usageSite
        while (current != null && current.node.elementType != CrystalTypes.STATEMENT
            && current.parent?.node?.elementType != CrystalTypes.STATEMENT_LIST
            && current.parent?.node?.elementType?.toString() != "FILE") {
            current = current.parent
        }
        if (current == null) return null

        // Search preceding siblings
        var sibling = current.prevSibling
        while (sibling != null) {
            val result = findAssignmentRhsForVar(sibling, varName)
            if (result != null) return result
            sibling = sibling.prevSibling
        }

        return null
    }

    /**
     * In an element subtree, find an assignment `varName = expr` and return the RHS expression.
     */
    private fun findAssignmentRhsForVar(element: PsiElement, varName: String): PsiElement? {
        if (element is CrystalAssignment) {
            // CrystalAssignment node children: IDENTIFIER, ASSIGN, expression
            val idNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            if (idNode != null && idNode.text == varName) {
                // Find the expression child (RHS)
                val exprNode = element.node.findChildByType(CrystalTypes.EXPRESSION)
                return exprNode?.psi
            }
        }

        // Recurse into children
        var child = element.firstChild
        while (child != null) {
            val result = findAssignmentRhsForVar(child, varName)
            if (result != null) return result
            child = child.nextSibling
        }
        return null
    }

    /**
     * Count elements in a tuple literal: {a, b, c} -> 3
     */
    private fun countTupleElements(literal: PsiElement): Int? {
        // Direct tuple literal
        if (literal.node.elementType == CrystalTypes.TUPLE_LITERAL) {
            // Count EXPRESSION nodes in the EXPRESSION_LIST child
            val exprList = literal.node.findChildByType(CrystalTypes.EXPRESSION_LIST)
            if (exprList != null) {
                return exprList.getChildren(null).count { it.elementType == CrystalTypes.EXPRESSION }
            }
            // Or count top-level EXPRESSION children directly
            return literal.node.getChildren(null).count { it.elementType == CrystalTypes.EXPRESSION }
        }
        // Hash literal used as named tuple {x: 1, y: 2}
        if (literal is CrystalHashLiteral) {
            val entryList = literal.hashEntryList ?: return null
            return entryList.hashEntryList.size
        }
        // Expression wrapper — unwrap
        if (literal.node.elementType == CrystalTypes.EXPRESSION) {
            val child = literal.firstChild
            if (child != null) return countTupleElements(child)
        }
        return null
    }

    /**
     * Extract keys from a named tuple literal: {host: "x", port: 8080} -> {"host", "port"}
     */
    private fun extractNamedTupleKeys(literal: PsiElement): Set<String>? {
        if (literal is CrystalHashLiteral) {
            val entryList = literal.hashEntryList ?: return null
            val keys = mutableSetOf<String>()
            for (entry in entryList.hashEntryList) {
                // Named tuple entry: EXPRESSION(VARIABLE_REFERENCE(IDENTIFIER)) COLON EXPRESSION
                val firstExpr = entry.expressionList.firstOrNull() ?: continue
                val varRef = firstExpr.firstChild
                if (varRef is CrystalVariableReference) {
                    val id = varRef.node.findChildByType(CrystalTypes.IDENTIFIER)
                    if (id != null) keys.add(id.text)
                }
            }
            return if (keys.isNotEmpty()) keys else null
        }
        // Expression wrapper — unwrap
        if (literal.node.elementType == CrystalTypes.EXPRESSION) {
            val child = literal.firstChild
            if (child != null) return extractNamedTupleKeys(child)
        }
        return null
    }

    // ==================== Helpers ====================

    private fun findHighlightTarget(element: PsiElement): PsiElement {
        if (element is CrystalBareArgument || element is CrystalArgument) {
            var child = element.firstChild
            while (child != null) {
                val type = child.node?.elementType
                if (type != CrystalTypes.IDENTIFIER && type != CrystalTypes.COLON
                    && type != CrystalTypes.STAR && type != CrystalTypes.DOUBLE_STAR
                    && child !is PsiWhiteSpace) {
                    return child
                }
                child = child.nextSibling
            }
        }
        return element
    }

    // ==================== Record Macro Support ====================

    /**
     * Extracts parameter infos from a record's field arguments.
     * Each record field like `host : String` or `port : Int32 = 80` becomes a ParamInfo.
     */
    private fun extractRecordFields(fieldArguments: List<PsiElement>): List<ParamInfo> {
        val params = mutableListOf<ParamInfo>()
        for (arg in fieldArguments) {
            val field = CrystalPsiUtils.recordFieldInfo(arg)
            val name = field.name ?: continue
            params.add(ParamInfo(name, field.hasDefault))
        }
        return params
    }

    /**
     * Validates arguments against record parameters (from `record` macro).
     * Reports missing required args or too many args.
     */
    private fun checkRecordArguments(
        recordParams: List<ParamInfo>,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder
    ) {
        // If any argument has an unresolvable splat/double-splat, skip the check entirely
        val hasUnresolvedSplat = arguments.any { it.isSplat && it.resolvedSplatCount == null }
        val hasUnresolvedDoubleSplat = arguments.any { it.isDoubleSplat && it.resolvedDoubleSplatKeys == null }
        if (hasUnresolvedSplat || hasUnresolvedDoubleSplat) return

        val effectivePositionalCount = arguments.sumOf { arg ->
            when {
                arg.isBlockPass -> 0
                arg.isSplat -> arg.resolvedSplatCount ?: 1
                arg.isDoubleSplat -> 0
                arg.name != null -> 0
                else -> 1
            }
        }

        val namedArgNames = mutableSetOf<String>()
        for (arg in arguments) {
            if (arg.name != null) namedArgNames.add(arg.name)
            if (arg.isDoubleSplat && arg.resolvedDoubleSplatKeys != null) {
                namedArgNames.addAll(arg.resolvedDoubleSplatKeys)
            }
        }

        val effectiveArgCount = effectivePositionalCount + namedArgNames.size
        val requiredParams = recordParams.filter { !it.hasDefault }
        val paramNames = recordParams.map { it.name }.toSet()

        // Check unknown named args
        if (namedArgNames.isNotEmpty()) {
            val unknown = namedArgNames - paramNames
            if (unknown.isNotEmpty()) {
                for (arg in arguments) {
                    if (arg.name != null && arg.name in unknown) {
                        holder.registerProblem(
                            arg.element,
                            "Unknown named argument '${arg.name}'",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                }
                return
            }
        }

        // Check missing required args
        val satisfiedByName = namedArgNames.intersect(requiredParams.map { it.name }.toSet())
        val requiredNotSatisfiedByName = requiredParams.filter { it.name !in satisfiedByName }
        val positionallyRequired = requiredNotSatisfiedByName.size
        if (effectivePositionalCount < positionallyRequired) {
            val missing = requiredNotSatisfiedByName.drop(effectivePositionalCount).map { it.name }
            val missingStr = missing.joinToString(", ") { "'$it'" }
            holder.registerProblem(
                methodNameElement,
                "Missing required argument(s): $missingStr",
                ProblemHighlightType.GENERIC_ERROR
            )
            return
        }

        // Check too many args (record params have no splat)
        val maxPositional = recordParams.size - namedArgNames.intersect(paramNames).size
        if (effectivePositionalCount > maxPositional) {
            for (i in (arguments.size - (effectivePositionalCount - maxPositional)) until arguments.size) {
                val argExpr = arguments[i].element
                val target = findHighlightTarget(argExpr)
                holder.registerProblem(
                    target,
                    "Too many arguments: expected at most ${recordParams.size}, got $effectiveArgCount",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }
}
