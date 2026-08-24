package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalTypeText
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Inspection that validates argument types against parameter type annotations
 * in Crystal method calls. Reports type mismatches as errors.
 *
 * Handles:
 * - Parenthesized calls: foo(arg1, arg2)
 * - Bare calls: foo arg1, arg2
 * - Named arguments: foo(name: value)
 * - Multiple overloads (only reports error if ALL overloads are incompatible)
 * - Numeric autocasting (Crystal's lossless widening rules)
 * - Union types and nilable types
 * - Splat parameters (skipped)
 * - Default parameter values (no error for missing args)
 */
class CrystalTypeCheckInspection : LocalInspectionTool() {


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalMethodCallExpression -> {
                        checkMethodCall(element, holder)
                    }
                    is CrystalBareMethodCallExpression -> {
                        checkMethodCall(element, holder)
                    }
                    is CrystalCallArgs, is CrystalBareArgumentList -> {
                        findOwningDotCall(element)?.let { checkDotCall(it, holder) }
                    }
                }
            }
        }
    }

    private fun checkMethodCall(callExpr: PsiElement, holder: ProblemsHolder) {
        val methodName = CrystalCallExtractor.extractMethodName(callExpr)
        if (methodName == null) {
            return
        }

        // Skip if method name is actually a local variable/parameter (binary operator ambiguity)
        // e.g. "text * times" parsed as bare call "text(*times)" but actually is binary "text * times"
        if (isLocalVariableOrParameter(callExpr, methodName)) {
            return
        }

        val arguments = extractArguments(callExpr)
        if (arguments.isEmpty()) {
            return
        }

        // Find all overloads of this method
        val project = callExpr.project
        val scope = GlobalSearchScope.projectScope(project)
        // Require-graph visibility: methods from files this call cannot see must
        // not participate in the overload set.
        var methods = CrystalRequireVisibility.visibleMethods(
            CrystalIndexService.findMethods(methodName, project, scope).toList(), callExpr
        )

        // Check record definition first — if `record Config, ...` exists in the
        // current file, its parameters take priority over any `class Config`
        // defined elsewhere (which might have a different `initialize`).
        if (methods.isEmpty() && methodName == "new") {
            val className = CrystalCallExtractor.findClassNameBeforeNew(callExpr)
            if (className != null) {
                val recordParams = extractRecordParamInfo(className, callExpr)
                if (recordParams != null) {
                    checkRecordTypeArgs(recordParams, arguments, holder)
                    return
                }
            }
        }

        // No record found — try regular class initialize
        if (methods.isEmpty() && methodName == "new") {
            val className = CrystalCallExtractor.findClassNameBeforeNew(callExpr)
            if (className != null) {
                val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, callExpr.containingFile)
                if (initMethod != null) {
                    methods = listOf(initMethod)
                }
            }
        }

        if (methods.isEmpty()) {
            return
        }

        // Check each argument against all overloads (with splat expansion).
        // The unqualified-call lookup is name-based; the overload set may mix
        // unrelated definitions, so unknown slots simply skip their comparison.
        if (arguments.any { it.isSplat || it.isDoubleSplat }) {
            checkExpandedOverloadTypes(methods, arguments, holder)
            return
        }
        for ((argIndex, argInfo) in arguments.withIndex()) {
            val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression)
            if (resolvedType == null) {
                continue
            }

            // Collect expected types from all overloads for this argument position
            val expectedTypes = mutableListOf<String>()
            var anyOverloadAccepts = false

            for (method in methods) {
                val params = method.parameterList?.parameterList ?: continue
                val param = findMatchingParameter(params, argIndex, argInfo.name)

                if (param == null) {
                    continue
                }

                // Skip splat parameters
                if (isSplatParameter(param)) {
                    anyOverloadAccepts = true
                    break
                }

                val paramTypeRef = param.typeReference
                if (paramTypeRef == null) {
                    // No type annotation → duck typing, always compatible
                    anyOverloadAccepts = true
                    break
                }

                val paramType = paramTypeRef.text
                expectedTypes.add(paramType)

                if (CrystalTypeCompatibility.isCompatible(
                        resolvedType.typeName, paramType, resolvedType.isUnsuffixedNumericLiteral
                    )) {
                    anyOverloadAccepts = true
                    break
                }
            }

            if (!anyOverloadAccepts && expectedTypes.isNotEmpty()) {
                val expectedDesc = if (expectedTypes.size == 1) {
                    "'${expectedTypes.first()}'"
                } else {
                    expectedTypes.distinct().joinToString(" or ") { "'$it'" }
                }
                // Use the innermost meaningful element for highlighting
                val highlightElement = findHighlightTarget(argInfo.expression)
                holder.registerProblem(
                    highlightElement,
                    "Type mismatch: expected $expectedDesc, got '${resolvedType.typeName}'",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

    /**
     * Type-checks a DOT-call (e.g. Apfel.kurz "lol") by extracting args from the args element.
     */
    private fun findOwningDotCall(argumentHolder: PsiElement): CrystalDotCallAccess? {
        return generateSequence(argumentHolder.parent) { it.parent }
            .filterIsInstance<CrystalDotCallAccess>()
            .firstOrNull {
                it.callArgs === argumentHolder || it.bareArgumentList === argumentHolder
            }
    }

    /**
     * Type-checks a DOT-call against the shared authoritative resolver.
     * Only exact receiver resolutions are checked — unknown, suppressed, and
     * ambiguous receivers stay silent (no name-only fallback), so stdlib methods
     * sharing the callee name can never leak into the overload set
     * (e.g. handler.call(*args) no longer collides with HTTP handler #call).
     */
    private fun checkDotCall(access: CrystalDotCallAccess, holder: ProblemsHolder) {
        val resolution = CrystalDotCallTargetResolver.resolve(access)
        when (resolution) {
            is DotCallResolution.Methods -> {
                val arguments = extractDotCallArguments(resolution.call.argumentHolder)
                if (arguments.isEmpty()) return
                checkOverloadTypes(resolution.methods, arguments, holder)
            }
            is DotCallResolution.RecordFallback -> {
                val arguments = extractDotCallArguments(resolution.call.argumentHolder)
                if (arguments.isEmpty()) return
                val recordArgs = resolution.recordDefinition.bareArgumentList ?: return
                if (recordArgs.bareArgumentList.size <= 1) return
                checkRecordTypeArgs(recordParamsFrom(recordArgs), arguments, holder)
            }
            is DotCallResolution.ImplicitConstructor,
            DotCallResolution.Suppressed,
            DotCallResolution.Unresolved -> return
        }
    }

    private fun extractDotCallArguments(argsElement: PsiElement?): List<ArgumentInfo> {
        if (argsElement == null) return emptyList()
        val result = mutableListOf<ArgumentInfo>()
        when (argsElement) {
            is CrystalCallArgs -> {
                argsElement.argumentList?.argumentList?.forEach { arg ->
                    extractArgumentInfo(arg)?.let { result.add(it) }
                }
            }
            is CrystalBareArgumentList -> {
                for (bareArg in argsElement.bareArgumentList) {
                    result.add(extractBareArgumentInfo(bareArg))
                }
            }
        }
        return result
    }

    private fun checkOverloadTypes(
        methods: List<CrystalMethodDefinition>,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder
    ) {
        checkExpandedOverloadTypes(methods, arguments, holder)
    }

    /**
     * Type-checks arguments against overloads with full splat expansion:
     * `*tuple` expands to positional slots from the tuple's element types,
     * `**namedTuple` expands to named slots from its entries. Unresolvable
     * expansions leave the whole call unchecked.
     */
    private fun checkExpandedOverloadTypes(
        methods: List<CrystalMethodDefinition>,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder
    ) {
        val slots = materializeEffectiveSlots(arguments) ?: return

        for ((index, slot) in slots.withIndex()) {
            var anyOverloadAccepts = false
            var compared = false
            val expectedTypes = mutableListOf<String>()

            for (method in methods) {
                val verdict = evaluateExpandedSlot(method, slots, index)
                if (verdict.accepted) {
                    anyOverloadAccepts = true
                    break
                }
                if (verdict.expectedType != null) {
                    expectedTypes.add(verdict.expectedType)
                    compared = true
                }
            }

            if (!anyOverloadAccepts && compared) {
                val expectedDesc = if (expectedTypes.size == 1) {
                    "'${expectedTypes.first()}'"
                } else {
                    expectedTypes.distinct().joinToString(" or ") { "'$it'" }
                }
                holder.registerProblem(
                    slot.highlight,
                    "Type mismatch: expected $expectedDesc, got '${slot.typeName}'",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

    private sealed interface EffectiveSlot {
        val highlight: PsiElement
        /** Null means the type could not be resolved — the slot consumes its position unchecked. */
        val typeName: String?

        data class Positional(
            override val typeName: String?,
            override val highlight: PsiElement,
            val isUnsuffixedNumericLiteral: Boolean = false,
        ) : EffectiveSlot

        data class Named(
            val name: String,
            override val typeName: String?,
            override val highlight: PsiElement,
        ) : EffectiveSlot
    }

    /**
     * Materializes argument infos into effective slots. Returns null when a splat
     * or double splat cannot be resolved to tuple elements / named-tuple entries —
     * in that case the call stays unchecked.
     */
    private fun materializeEffectiveSlots(arguments: List<ArgumentInfo>): List<EffectiveSlot>? {
        val slots = mutableListOf<EffectiveSlot>()
        for (argInfo in arguments) {
            when {
                argInfo.isDoubleSplat -> {
                    val typeName = CrystalExpressionTypeResolver.resolveType(argInfo.expression)?.typeName
                        ?: return null
                    val entries = CrystalTypeText.namedTupleEntries(typeName) ?: return null
                    for ((name, entryType) in entries) {
                        slots.add(EffectiveSlot.Named(name, entryType, argInfo.expression))
                    }
                }
                argInfo.isSplat -> {
                    val typeName = CrystalExpressionTypeResolver.resolveType(argInfo.expression)?.typeName
                        ?: return null
                    val elements = CrystalTypeText.tupleElements(typeName) ?: return null
                    for (element in elements) {
                        slots.add(EffectiveSlot.Positional(element, argInfo.expression))
                    }
                }
                else -> {
                    val resolved = CrystalExpressionTypeResolver.resolveType(argInfo.expression)
                    slots.add(
                        EffectiveSlot.Positional(
                            resolved?.typeName,
                            findHighlightTarget(argInfo.expression),
                            resolved?.isUnsuffixedNumericLiteral ?: false,
                        )
                    )
                }
            }
        }
        return slots
    }

    private data class ExpandedSlotVerdict(
        val accepted: Boolean,
        val compared: Boolean,
        val expectedType: String?,
    ) {
        companion object {
            val SKIPPED = ExpandedSlotVerdict(true, false, null)
        }
    }

    /**
     * Sequential parameter walker over one overload. Simulates all [slots] up to
     * and including [targetIndex]; only the target's type compatibility is judged.
     * A rest parameter (`*y`) absorbs every remaining positional slot; unknown
     * named keys are out of scope here and skipped conservatively.
     */
    private fun evaluateExpandedSlot(
        method: CrystalMethodDefinition,
        slots: List<EffectiveSlot>,
        targetIndex: Int,
    ): ExpandedSlotVerdict {
        val params = method.parameterList?.parameterList
            ?: return ExpandedSlotVerdict.SKIPPED
        val hasDoubleSplatParam = params.any {
            it.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null
        }

        var paramIdx = 0
        var restAbsorbed = false

        fun nextPositionalParam(): CrystalParameter? {
            while (paramIdx < params.size) {
                val param = params[paramIdx]
                if (isSplatParameter(param)) {
                    restAbsorbed = true
                    return param
                }
                paramIdx++
                return param
            }
            return null
        }

        for ((index, slot) in slots.withIndex()) {
            val isTarget = index == targetIndex
            when (slot) {
                is EffectiveSlot.Named -> {
                    val param = params.firstOrNull {
                        CrystalCompletionHelper.extractParameterName(it) == slot.name
                    } ?: return if (isTarget) ExpandedSlotVerdict.SKIPPED else continue
                    // Named parameters do not consume positional positions.
                    if (!isTarget) continue
                    val typeRef = param.typeReference ?: return ExpandedSlotVerdict.SKIPPED
                    return ExpandedSlotVerdict(
                        accepted = CrystalTypeCompatibility.isCompatible(slot.typeName ?: return ExpandedSlotVerdict.SKIPPED, typeRef.text),
                        compared = true,
                        expectedType = typeRef.text,
                    )
                }
                is EffectiveSlot.Positional -> {
                    if (restAbsorbed) continue
                    val param = nextPositionalParam() ?: run {
                        // Arity overflow is the argument-count inspection's domain.
                        return if (isTarget) ExpandedSlotVerdict.SKIPPED else continue
                    }
                    if (!isTarget) continue
                    if (isSplatParameter(param)) return ExpandedSlotVerdict.SKIPPED
                    val typeRef = param.typeReference ?: return ExpandedSlotVerdict.SKIPPED
                    val argTypeName = slot.typeName ?: return ExpandedSlotVerdict.SKIPPED
                    return ExpandedSlotVerdict(
                        accepted = CrystalTypeCompatibility.isCompatible(
                            argTypeName, typeRef.text, slot.isUnsuffixedNumericLiteral
                        ),
                        compared = true,
                        expectedType = typeRef.text,
                    )
                }
            }
        }
        return ExpandedSlotVerdict.SKIPPED
    }

    /**
     * Finds the best PSI element to highlight for an error.
     * Unwraps wrapper elements (CrystalBareArgument, CrystalArgument) to get the actual literal/expression.
     */
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

    // ==================== Argument Extraction ====================

    data class ArgumentInfo(
        val expression: PsiElement,
        val name: String? = null, // Named argument label, or null for positional
        val isSplat: Boolean = false, // *expr — expands to N positional values
        val isDoubleSplat: Boolean = false // **expr — expands to named key/value pairs
    )

    private fun extractArguments(callExpr: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()

        when (callExpr) {
            is CrystalMethodCallExpression -> {
                // Try call_args (parenthesized)
                val callArgs = callExpr.callArgs
                if (callArgs != null) {
                    val argList = callArgs.argumentList
                    if (argList != null) {
                        for (arg in argList.argumentList) {
                            extractArgumentInfo(arg)?.let { result.add(it) }
                        }
                    }
                    return result
                }
                // Try bare_argument_list
                val bareArgList = callExpr.bareArgumentList
                if (bareArgList != null) {
                    for (bareArg in bareArgList.bareArgumentList) {
                        result.add(extractBareArgumentInfo(bareArg))
                    }
                }
            }
            is CrystalBareMethodCallExpression -> {
                val callArgs = callExpr.callArgs
                if (callArgs != null) {
                    val argList = callArgs.argumentList
                    if (argList != null) {
                        for (arg in argList.argumentList) {
                            extractArgumentInfo(arg)?.let { result.add(it) }
                        }
                    }
                }
            }
        }

        return result
    }

    private fun extractArgumentInfo(arg: CrystalArgument): ArgumentInfo? {
        // Check for named argument: IDENTIFIER COLON expression
        val children = arg.node.getChildren(null)
        var namedLabel: String? = null

        for (i in children.indices) {
            if (children[i].elementType == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }

        // Splat arguments (* or **) and out arguments
        val firstChildType = children.firstOrNull()?.elementType
        val isSplat = firstChildType == CrystalTypes.STAR
        val isDoubleSplat = firstChildType == CrystalTypes.DOUBLE_STAR
        if (isSplat || isDoubleSplat || firstChildType == CrystalTypes.OUT) {
            val expr = arg.expression ?: return null
            return ArgumentInfo(expr, namedLabel, isSplat, isDoubleSplat)
        }

        val expr = arg.expression ?: return null
        return ArgumentInfo(expr, namedLabel)
    }

    private fun extractBareArgumentInfo(bareArg: CrystalBareArgument): ArgumentInfo {
        // For bare arguments, the structure is more complex
        // Check for named label (IDENTIFIER COLON)
        val children = bareArg.node.getChildren(null)
        var namedLabel: String? = null

        for (i in children.indices) {
            if (children[i].elementType == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }

        // The expression is the first significant PSI child that is not the named label
        // For bare_argument, the content is spread as direct children
        // Use the bare_argument element itself as the expression to resolve type from
        val firstType = children.firstOrNull()?.elementType
        val isSplat = firstType == CrystalTypes.STAR
        val isDoubleSplat = firstType == CrystalTypes.DOUBLE_STAR
        return ArgumentInfo(bareArg as PsiElement, namedLabel, isSplat, isDoubleSplat)
    }

    // ==================== Parameter Matching ====================

    private fun findMatchingParameter(
        params: List<CrystalParameter>,
        argIndex: Int,
        argName: String?
    ): CrystalParameter? {
        // Named argument: find parameter by name
        if (argName != null) {
            for (param in params) {
                val paramName = CrystalCompletionHelper.extractParameterName(param)
                if (paramName == argName) return param
            }
            return null
        }

        // Positional: find by index, skipping splat parameters for counting
        var positionalIndex = 0
        for (param in params) {
            if (isSplatParameter(param)) {
                // Splat absorbs all remaining positional args
                return param
            }
            if (positionalIndex == argIndex) return param
            positionalIndex++
        }
        return null
    }

    private fun isSplatParameter(param: CrystalParameter): Boolean {
        val children = param.node.getChildren(null)
        val firstType = children.firstOrNull()?.elementType
        return firstType == CrystalTypes.STAR || firstType == CrystalTypes.DOUBLE_STAR
            || firstType == CrystalTypes.AMPERSAND
    }

    // ==================== Method Name Extraction ====================

    /**
     * Checks if the given name is a local variable or method parameter in the enclosing scope.
     * This helps disambiguate "text * times" (binary op) from "text(*times)" (bare call with splat).
     */
    private fun isLocalVariableOrParameter(callExpr: PsiElement, name: String): Boolean {
        // Walk up to find enclosing method definition
        var parent = callExpr.parent
        while (parent != null) {
            if (parent is CrystalMethodDefinition) {
                // Check parameters
                val params = parent.parameterList?.parameterList ?: emptyList()
                for (param in params) {
                    val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                        ?: param.node.findChildByType(CrystalTypes.INSTANCE_VAR)?.text?.removePrefix("@")
                    if (paramName == name) return true
                }
                break
            }
            parent = parent.parent
        }

        // Check local variable assignments before this expression
        var sibling = callExpr.parent?.prevSibling
        while (sibling != null) {
            if (sibling is CrystalStatement) {
                val assignment = sibling.firstChild as? CrystalAssignment
                if (assignment != null) {
                    val varName = assignment.firstChild?.text
                    if (varName == name) return true
                }
            }
            sibling = sibling.prevSibling
        }

        return false
    }

    // ==================== Record Macro Support ====================

    data class RecordParamInfo(val name: String, val typeText: String?, val hasDefault: Boolean)

    /**
     * Extracts parameter info from a `record` macro call for type checking.
     * Returns list of (name, typeText, hasDefault) or null if not a record.
     */
    private fun extractRecordParamInfo(className: String, contextElement: PsiElement): List<RecordParamInfo>? {
        val file = contextElement.containingFile ?: return null
        val recordDef = CrystalCompletionHelper.findRecordDefinition(className, file) ?: return null
        val bareArgList = recordDef.bareArgumentList ?: return null
        if (bareArgList.bareArgumentList.size <= 1) return emptyList()
        return recordParamsFrom(bareArgList)
    }

    /** Extracts name/type/default triples from a record definition's argument list (skipping the type name at index 0). */
    private fun recordParamsFrom(bareArgList: CrystalBareArgumentList): List<RecordParamInfo> {
        val args = bareArgList.bareArgumentList
        val params = mutableListOf<RecordParamInfo>()
        for (i in 1 until args.size) {
            val arg = args[i]
            val children = arg.node.getChildren(null)
            var name: String? = null
            var typeText: String? = null
            var hasDefault = false
            var pastColon = false
            var pastAssign = false
            for (child in children) {
                when (child.elementType) {
                    CrystalTypes.IDENTIFIER -> name = child.text
                    CrystalTypes.COLON -> pastColon = true
                    CrystalTypes.ASSIGN -> pastAssign = true
                    else -> {
                        if (child.elementType == com.intellij.psi.TokenType.WHITE_SPACE) continue
                        if (pastAssign) {
                            hasDefault = true
                        } else if (pastColon) {
                            typeText = (typeText ?: "") + child.text
                        }
                    }
                }
            }
            if (name != null) {
                params.add(RecordParamInfo(name, typeText, hasDefault))
            }
        }
        return params
    }

    /**
     * Type-checks arguments against record parameters.
     */
    private fun checkRecordTypeArgs(
        recordParams: List<RecordParamInfo>,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder
    ) {
        for ((argIndex, argInfo) in arguments.withIndex()) {
            // Splat expansion against record fields is not modeled — stay silent.
            if (argInfo.isSplat || argInfo.isDoubleSplat) continue
            val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: continue

            // Find matching record param by name (named) or position (positional)
            val recordParam = if (argInfo.name != null) {
                recordParams.find { it.name == argInfo.name }
            } else {
                recordParams.getOrNull(argIndex)
            }

            if (recordParam == null) continue
            val paramType = recordParam.typeText ?: continue

            if (!CrystalTypeCompatibility.isCompatible(
                    resolvedType.typeName, paramType, resolvedType.isUnsuffixedNumericLiteral
                )) {
                val highlightElement = findHighlightTarget(argInfo.expression)
                holder.registerProblem(
                    highlightElement,
                    "Type mismatch: expected '$paramType', got '${resolvedType.typeName}'",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }
}
