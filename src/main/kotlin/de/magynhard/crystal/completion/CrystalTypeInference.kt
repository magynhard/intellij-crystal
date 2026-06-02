package de.magynhard.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Basic type inference for Crystal variables.
 * Resolves the type of an identifier by analyzing its assignment or declaration context.
 */
object CrystalTypeInference {

    /**
     * Infers the type (class name) of a variable at the given position.
     * Returns null if the type cannot be determined.
     */
    fun inferType(variableName: String, context: PsiElement, project: Project): String? {
        // 1. Check if it's a parameter with type annotation
        val paramType = inferFromParameter(variableName, context)
        if (paramType != null) return paramType

        // 2. Check assignments in scope: x = Klasse.new or x = method_call
        val assignType = inferFromAssignment(variableName, context, project)
        if (assignType != null) return assignType

        return null
    }

    /**
     * Check if the variable is a parameter with a type annotation.
     * e.g. def foo(x : Apfel) → type of x is "Apfel"
     */
    private fun inferFromParameter(name: String, context: PsiElement): String? {
        // Find enclosing method
        val method = PsiTreeUtil.getParentOfType(context, CrystalMethodDefinition::class.java)
            ?: return null

        val paramList = method.parameterList ?: return null
        for (param in paramList.parameterList) {
            val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
            if (paramName == name) {
                // Has type annotation?
                val typeRef = param.typeReference
                if (typeRef != null) {
                    return extractTypeName(typeRef.text)
                }
            }
        }
        return null
    }

    /**
     * Search for assignments to this variable in the current scope.
     * Patterns recognized:
     * - x = Klasse.new → type is "Klasse"
     * - x = Klasse.method_name → check return type of method_name
     * - x = method_name → check return type of top-level/enclosing method
     */
    private fun inferFromAssignment(name: String, context: PsiElement, project: Project): String? {
        // Walk backwards through siblings and parent scope to find assignment
        val containingFile = context.containingFile ?: return null

        // Collect all assignments in the file (simple approach)
        val assignments = PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)

        for (assignment in assignments) {
            // Check if this assignment assigns to our variable
            val identNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER)
            if (identNode?.text != name) continue

            // Only consider assignments that appear before our context
            if (assignment.textOffset >= context.textOffset) continue

            // Analyze the right-hand side expression
            val expr = assignment.expression ?: continue
            val inferredType = inferTypeFromExpression(expr, project)
            if (inferredType != null) return inferredType
        }
        return null
    }

    /**
     * Infers the type from an expression.
     * Recognizes patterns like:
     * - Klasse.new → "Klasse"
     * - Klasse.method → return type of that method
     * - method_call → return type of that method
     */
    private fun inferTypeFromExpression(expr: PsiElement, project: Project): String? {
        val text = expr.text.trim()

        // Pattern: Klasse.new
        val newPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\(.*\))?$""")
        val newMatch = newPattern.find(text)
        if (newMatch != null) {
            return newMatch.groupValues[1]
        }

        // Pattern: Klasse.method_name(...)
        val classMethodPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\(.*\))?$""")
        val classMethodMatch = classMethodPattern.find(text)
        if (classMethodMatch != null) {
            val className = classMethodMatch.groupValues[1]
            val methodName = classMethodMatch.groupValues[2]
            if (methodName == "new") return className
            // Try to find return type of the static method
            return inferReturnTypeOfMethod(methodName, className, project)
        }

        // Pattern: bare method_call (no dot)
        val bareCallPattern = Regex("""^(\w+)(?:\(.*\))?$""")
        val bareCallMatch = bareCallPattern.find(text)
        if (bareCallMatch != null) {
            val methodName = bareCallMatch.groupValues[1]
            // Only if it starts with lowercase (method, not class)
            if (methodName[0].isLowerCase()) {
                return inferReturnTypeOfMethod(methodName, null, project)
            }
        }

        return null
    }

    /**
     * Finds the return type annotation of a method by name.
     * If className is provided, searches within that class; otherwise project-wide.
     */
    private fun inferReturnTypeOfMethod(methodName: String, className: String?, project: Project): String? {
        val scope = GlobalSearchScope.projectScope(project)
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        )

        for (method in methods) {
            if (className != null) {
                val enclosing = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosing != className) continue
            }
            val returnType = method.typeReference?.text
            if (returnType != null) {
                return extractTypeName(returnType)
            }
        }
        return null
    }

    /**
     * Extracts the simple type name from a type reference text.
     * e.g. "Array(String)" → "Array", "Int32 | Nil" → "Int32"
     */
    private fun extractTypeName(typeText: String): String {
        // Take first type before any | (union), strip generics
        val simple = typeText.split("|").first().trim()
        val withoutGenerics = simple.replace(Regex("""\(.*\)"""), "").trim()
        return withoutGenerics
    }
}
