package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
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

        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, methodNameElement, holder)
    }

    private fun checkDotCall(argsElement: PsiElement, info: DotCallInfo, holder: ProblemsHolder) {
        val arguments = extractArgumentsFromArgsElement(argsElement)

        val project = argsElement.project
        val scope = GlobalSearchScope.allScope(project)
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, info.methodName, project, scope, CrystalMethodDefinition::class.java
        ).toList()

        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, info.methodNameElement, holder)
    }

    private fun checkArgumentCount(
        methods: List<CrystalMethodDefinition>,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder
    ) {
        val argCount = arguments.size
        val namedArgNames = arguments.mapNotNull { it.name }.toSet()
        val positionalCount = arguments.count { it.name == null }

        // Check each overload
        var bestMatch: OverloadMatch? = null

        for (method in methods) {
            val params = method.parameterList?.parameterList ?: emptyList()
            val match = evaluateOverload(params, argCount, positionalCount, namedArgNames)

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
                    ProblemHighlightType.WARNING
                )
            }
            match.excessStartIndex >= 0 -> {
                // Highlight each excess argument
                for (i in match.excessStartIndex until arguments.size) {
                    val argExpr = arguments[i].element
                    val target = findHighlightTarget(argExpr)
                    holder.registerProblem(
                        target,
                        "Too many arguments: expected at most ${match.maxArgs}, got $argCount",
                        ProblemHighlightType.WARNING
                    )
                }
            }
            match.unknownNamedArgs.isNotEmpty() -> {
                // Highlight unknown named args — highlight the entire argument (including label)
                for (arg in arguments) {
                    if (arg.name != null && arg.name in match.unknownNamedArgs) {
                        holder.registerProblem(
                            arg.element,
                            "Unknown named argument '${arg.name}'",
                            ProblemHighlightType.WARNING
                        )
                    }
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
            val name = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: continue
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
        val name: String? = null
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
        for (i in children.indices) {
            if (children[i].elementType == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }
        return ArgumentInfo(arg, namedLabel)
    }

    private fun extractBareArgInfo(bareArg: CrystalBareArgument): ArgumentInfo {
        val children = bareArg.node.getChildren(null)
        var namedLabel: String? = null
        for (i in children.indices) {
            if (children[i].elementType == CrystalTypes.COLON && i > 0
                && children[i - 1].elementType == CrystalTypes.IDENTIFIER) {
                namedLabel = children[i - 1].text
                break
            }
        }
        return ArgumentInfo(bareArg, namedLabel)
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
}
