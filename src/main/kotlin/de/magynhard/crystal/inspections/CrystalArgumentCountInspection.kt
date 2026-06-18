package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalMethodIndex

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

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalMethodCallExpression -> checkCall(element, holder)
                    is CrystalBareMethodCallExpression -> checkCall(element, holder)
                    is CrystalCallArgs, is CrystalBareArgumentList -> {
                        val dotCallInfo = detectDotCall(element)
                        if (dotCallInfo != null) {
                            checkDotCall(element, dotCallInfo, holder)
                        }
                    }
                }
            }
        }
    }

    private fun checkCall(callExpr: PsiElement, holder: ProblemsHolder) {
        val methodName = extractMethodName(callExpr) ?: return
        val arguments = extractArguments(callExpr)
        val methodNameElement = findMethodNameElement(callExpr) ?: return

        val project = callExpr.project
        val scope = GlobalSearchScope.allScope(project)
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        ).toList()

        if (methods.isEmpty()) {
            // Special case: "new" on a class → resolve to "initialize" parameters
            if (methodName == "new") {
                val className = findClassNameBeforeNew(callExpr)
                if (className != null) {
                    val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, callExpr.containingFile)
                    if (initMethod != null) {
                        checkArgumentCount(listOf(initMethod), arguments, methodNameElement, holder)
                        return
                    }
                }
            }
            return
        }

        checkArgumentCount(methods, arguments, methodNameElement, holder)
    }

    private fun checkDotCall(argsElement: PsiElement, info: DotCallInfo, holder: ProblemsHolder) {
        // Skip DOT-calls on local variables/parameters — we can't reliably determine
        // the receiver's type, so we only check calls on Constants (class methods).
        val firstChar = info.receiverName.firstOrNull() ?: return
        if (!firstChar.isUpperCase()) return

        val arguments = extractArgumentsFromArgsElement(argsElement)

        val project = argsElement.project
        val scope = GlobalSearchScope.allScope(project)
        var methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, info.methodName, project, scope, CrystalMethodDefinition::class.java
        ).toList()

        // Filter to methods defined inside a class/module matching the receiver name.
        // This prevents false positives like ::Bytes.new(...) matching unrelated new overloads.
        // Only filter when methods actually have an enclosing type; top-level defs stay.
        methods = methods.filter { method ->
            val enclosing = findEnclosingTypeName(method)
            enclosing == null || enclosing == info.receiverName
        }

        if (methods.isEmpty()) {
            // Special case: "new" on a class → resolve to "initialize" parameters
            if (info.methodName == "new") {
                val initMethod = CrystalCompletionHelper.getInitializeMethod(info.receiverName, project, argsElement.containingFile)
                if (initMethod != null) {
                    checkArgumentCount(listOf(initMethod), arguments, info.methodNameElement, holder)
                    return
                }
            }
            return
        }

        checkArgumentCount(methods, arguments, info.methodNameElement, holder)
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
            val params = method.parameterList?.parameterList ?: emptyList()
            val match = evaluateOverload(params, effectiveArgCount, effectivePositionalCount, namedArgNames)

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
            // Prefer match with fewer missing params
            return missingParams.size < other.missingParams.size
        }
    }

    private fun evaluateOverload(
        params: List<CrystalParameter>,
        argCount: Int,
        positionalCount: Int,
        namedArgNames: Set<String>
    ): OverloadMatch {
        val regularParams = mutableListOf<ParamInfo>()
        var hasSplat = false
        var hasDoubleSplat = false

        for (param in params) {
            val prefix = param.node.getChildren(null).firstOrNull()?.elementType
            when (prefix) {
                CrystalTypes.AMPERSAND -> continue // &block — skip
                CrystalTypes.STAR -> { hasSplat = true; continue }
                CrystalTypes.DOUBLE_STAR -> { hasDoubleSplat = true; continue }
            }
            val name = de.magynhard.crystal.completion.CrystalCompletionHelper.extractParameterName(param) ?: continue
            val hasDefault = param.expression != null
            regularParams.add(ParamInfo(name, hasDefault))
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

        // Positional args fill remaining params in order
        val positionallyRequired = requiredNotSatisfiedByName.size
        if (positionalCount < positionallyRequired) {
            val missing = requiredNotSatisfiedByName.drop(positionalCount).map { it.name }
            return OverloadMatch(isValid = false, missingParams = missing)
        }

        // Check too many args (only if no splat)
        if (!hasSplat) {
            val maxPositional = regularParams.size - namedArgNames.intersect(paramNames).size
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

    data class ParamInfo(val name: String, val hasDefault: Boolean)

    // ==================== DOT-call Detection ====================

    data class DotCallInfo(val receiverName: String, val methodName: String, val methodNameElement: PsiElement)

    private fun detectDotCall(argsElement: PsiElement): DotCallInfo? {
        val parent = argsElement.parent
        if (parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression) return null

        var sibling = argsElement.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling

        val methodNameNode = sibling ?: return null
        val methodType = methodNameNode.node?.elementType
        if (methodType != CrystalTypes.IDENTIFIER && methodType != CrystalTypes.CONSTANT) return null

        val methodNameElement = methodNameNode
        val methodName = methodNameNode.text

        sibling = methodNameNode.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling?.node?.elementType != CrystalTypes.DOT) return null

        sibling = sibling.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        val receiverName = sibling?.text ?: return null

        return DotCallInfo(receiverName, methodName, methodNameElement)
    }

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
                    val argList = callArgs.argumentList
                    if (argList != null) {
                        for (arg in argList.argumentList) {
                            result.add(extractArgInfo(arg))
                        }
                    }
                    return result
                }
                val bareArgList = callExpr.bareArgumentList
                if (bareArgList != null) {
                    for (bareArg in bareArgList.bareArgumentList) {
                        result.add(extractBareArgInfo(bareArg))
                    }
                }
            }
            is CrystalBareMethodCallExpression -> {
                val callArgs = callExpr.callArgs
                if (callArgs != null) {
                    val argList = callArgs.argumentList
                    if (argList != null) {
                        for (arg in argList.argumentList) {
                            result.add(extractArgInfo(arg))
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractArgumentsFromArgsElement(argsElement: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()
        when (argsElement) {
            is CrystalCallArgs -> {
                val argList = argsElement.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        result.add(extractArgInfo(arg))
                    }
                }
            }
            is CrystalBareArgumentList -> {
                for (bareArg in argsElement.bareArgumentList) {
                    result.add(extractBareArgInfo(bareArg))
                }
            }
        }
        return result
    }

    private fun extractArgInfo(arg: CrystalArgument): ArgumentInfo {
        val children = arg.node.getChildren(null)
        var namedLabel: String? = null
        var isSplat = false
        var isDoubleSplat = false
        var isBlockPass = false

        for (i in children.indices) {
            val type = children[i].elementType
            if (type == CrystalTypes.STAR) isSplat = true
            if (type == CrystalTypes.DOUBLE_STAR) isDoubleSplat = true
            if (type == CrystalTypes.AMPERSAND) isBlockPass = true
            if (type == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }

        val resolvedSplatCount = if (isSplat) resolveSplatCount(arg) else null
        val resolvedDoubleSplatKeys = if (isDoubleSplat) resolveDoubleSplatKeys(arg) else null

        return ArgumentInfo(arg, namedLabel, isSplat, isDoubleSplat, isBlockPass, resolvedSplatCount, resolvedDoubleSplatKeys)
    }

    private fun extractBareArgInfo(bareArg: CrystalBareArgument): ArgumentInfo {
        val children = bareArg.node.getChildren(null)
        var namedLabel: String? = null
        var isSplat = false
        var isDoubleSplat = false
        var isBlockPass = false

        for (i in children.indices) {
            val type = children[i].elementType
            if (type == CrystalTypes.STAR) isSplat = true
            if (type == CrystalTypes.DOUBLE_STAR) isDoubleSplat = true
            if (type == CrystalTypes.AMPERSAND) isBlockPass = true
            if (type == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }

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

    private fun extractMethodName(callExpr: PsiElement): String? {
        var child = callExpr.firstChild
        var lastNameBeforeDot: String? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child.text
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameBeforeDot = child.text
            }
            child = child.nextSibling
        }
        return lastNameBeforeDot
    }

    private fun findMethodNameElement(callExpr: PsiElement): PsiElement? {
        var child = callExpr.firstChild
        var lastNameElement: PsiElement? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameElement = child
            }
            child = child.nextSibling
        }
        return lastNameElement
    }

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

    /**
     * For "ClassName.new(...)" — finds the class name (CONSTANT) before ".new".
     */
    private fun findClassNameBeforeNew(callExpr: PsiElement): String? {
        var child = callExpr.firstChild
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && type == CrystalTypes.IDENTIFIER && child.text == "new") {
                var beforeDot = child.prevSibling
                while (beforeDot is PsiWhiteSpace) beforeDot = beforeDot.prevSibling
                if (beforeDot?.node?.elementType == CrystalTypes.CONSTANT) {
                    return beforeDot.text
                }
            }
            child = child.nextSibling
        }
        return null
    }

    private fun findEnclosingTypeName(method: CrystalMethodDefinition): String? {
        return CrystalCompletionHelper.getEnclosingClassName(method)
    }
}
