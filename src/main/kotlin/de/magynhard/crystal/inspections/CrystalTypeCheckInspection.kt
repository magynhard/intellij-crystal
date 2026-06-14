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
                        val dotCallInfo = detectDotCall(element)
                        if (dotCallInfo != null) {
                            checkDotCall(element, dotCallInfo.first, dotCallInfo.second, holder)
                        }
                    }
                }
            }
        }
    }

    private fun checkMethodCall(callExpr: PsiElement, holder: ProblemsHolder) {
        val methodName = extractMethodName(callExpr)
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
        val scope = GlobalSearchScope.allScope(project)
        var methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        ).toList()

        // Special case: "new" on a class → resolve to "initialize" parameters
        if (methods.isEmpty() && methodName == "new") {
            val className = findClassNameBeforeNew(callExpr)
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

        // Check each argument against all overloads
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
     * Detects if a CrystalCallArgs/CrystalBareArgumentList is part of a DOT-call pattern.
     * Returns Pair(receiverName, methodName) or null if not a DOT-call.
     * Avoids double-processing: returns null if parent is already a CrystalMethodCallExpression.
     */
    private fun detectDotCall(argsElement: PsiElement): Pair<String, String>? {
        // Skip if inside a method_call_expression (already handled)
        val parent = argsElement.parent
        if (parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression) return null

        // Look backwards through siblings: expect IDENTIFIER/CONSTANT, then DOT, then receiver
        var sibling = argsElement.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling

        val methodNameNode = sibling ?: return null
        val methodType = methodNameNode.node?.elementType
        if (methodType != CrystalTypes.IDENTIFIER && methodType != CrystalTypes.CONSTANT) return null
        val methodName = methodNameNode.text

        sibling = methodNameNode.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling?.node?.elementType != CrystalTypes.DOT) return null

        sibling = sibling.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        val receiverName = sibling?.text ?: return null

        return Pair(receiverName, methodName)
    }

    /**
     * Type-checks a DOT-call (e.g. Apfel.kurz "lol") by extracting args from the args element.
     */
    private fun checkDotCall(argsElement: PsiElement, receiverName: String, methodName: String, holder: ProblemsHolder) {
        val arguments = mutableListOf<ArgumentInfo>()
        when (argsElement) {
            is CrystalCallArgs -> {
                val argList = argsElement.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        extractArgumentInfo(arg)?.let { arguments.add(it) }
                    }
                }
            }
            is CrystalBareArgumentList -> {
                for (bareArg in argsElement.bareArgumentList) {
                    arguments.add(extractBareArgumentInfo(bareArg))
                }
            }
        }
        if (arguments.isEmpty()) return


        val project = argsElement.project
        val scope = GlobalSearchScope.allScope(project)
        var methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        ).toList()

        // Filter to methods defined inside a class/module matching the receiver name.
        // This prevents false positives like ENV.fetch(...) matching Hash#fetch.
        // Only apply for CONSTANT receivers (class/module names start with uppercase in Crystal).
        if (receiverName.isNotEmpty() && receiverName[0].isUpperCase()) {
            methods = methods.filter { method ->
                val enclosing = findEnclosingTypeName(method)
                enclosing == null || enclosing == receiverName
            }
        }

        // Special case: "new" on a class → resolve to "initialize" parameters
        if (methods.isEmpty() && methodName == "new") {
            val className = findClassNameBeforeNewFromArgs(argsElement)
            if (className != null) {
                val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, argsElement.containingFile)
                if (initMethod != null) {
                    methods = listOf(initMethod)
                }
            }
        }

        if (methods.isEmpty()) {
            return
        }

        // Reuse the same type-checking logic
        for ((argIndex, argInfo) in arguments.withIndex()) {
            val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: continue
            var anyOverloadAccepts = false
            val expectedTypes = mutableListOf<String>()

            for (method in methods) {
                val params = method.parameterList?.parameterList ?: continue
                val param = findMatchingParameter(params, argIndex, argInfo.name) ?: continue
                val paramTypeRef = param.typeReference ?: continue
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
        val name: String? = null // Named argument label, or null for positional
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

        // Skip splat arguments (* or **) and out arguments
        val firstChildType = children.firstOrNull()?.elementType
        if (firstChildType == CrystalTypes.STAR || firstChildType == CrystalTypes.DOUBLE_STAR
            || firstChildType == CrystalTypes.OUT) {
            val expr = arg.expression ?: return null
            return ArgumentInfo(expr, namedLabel)
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
        return ArgumentInfo(bareArg as PsiElement, namedLabel)
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
                val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
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

    private fun extractMethodName(callExpr: PsiElement): String? {
        // For dot-calls (Foo.bar, obj.method): find IDENTIFIER/CONSTANT after DOT
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

    /**
     * For "ClassName.new(...)" — finds the class name (CONSTANT) before ".new".
     * Works for CrystalMethodCallExpression / CrystalBareMethodCallExpression.
     */
    private fun findClassNameBeforeNew(callExpr: PsiElement): String? {
        var child = callExpr.firstChild
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && type == CrystalTypes.IDENTIFIER && child.text == "new") {
                // Found ".new", now look before the DOT for CONSTANT
                var beforeDot = child.prevSibling
                while (beforeDot is PsiWhiteSpace) beforeDot = beforeDot.prevSibling
                return extractClassNameFromElement(beforeDot)
            }
            child = child.nextSibling
        }
        return null
    }

    /**
     * For DOT-calls via args element (e.g. Foo.new(...)) — finds the class name.
     */
    private fun findClassNameBeforeNewFromArgs(argsElement: PsiElement): String? {
        var sibling = argsElement.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling

        val methodNameNode = sibling ?: return null
        if (methodNameNode.text != "new") return null

        sibling = methodNameNode.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling?.node?.elementType != CrystalTypes.DOT) return null

        sibling = sibling.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        return extractClassNameFromElement(sibling)
    }

    /**
     * Extracts a class name (CONSTANT text) from a PSI element.
     * Handles both raw CONSTANT tokens and CrystalVariableReferenceImpl/TypePathImpl wrappers.
     */
    private fun extractClassNameFromElement(element: PsiElement?): String? {
        if (element == null) return null
        // Direct CONSTANT token
        if (element.node?.elementType == CrystalTypes.CONSTANT) {
            return element.text
        }
        // Wrapper (e.g. CrystalVariableReferenceImpl, CrystalTypePathImpl) containing a CONSTANT child
        val constantChild = element.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) {
            return constantChild.text
        }
        return null
    }

    /**
     * Finds the name of the enclosing class, module, or struct for a method definition.
     * Walks up the PSI tree to find the first class/module/struct definition parent.
     */
    private fun findEnclosingTypeName(method: CrystalMethodDefinition): String? {
        var parent = method.parent
        while (parent != null) {
            val typeName = when (parent) {
                is CrystalClassDefinition -> parent.typeName
                is CrystalModuleDefinition -> parent.typeName
                is CrystalStructDefinition -> parent.typeName
                else -> null
            }
            if (typeName != null) {
                // The first CONSTANT child is the type name
                var child = typeName.firstChild
                while (child != null) {
                    if (child.node?.elementType == CrystalTypes.CONSTANT) {
                        return child.text
                    }
                    child = child.nextSibling
                }
            }
            parent = parent.parent
        }
        return null
    }
}
