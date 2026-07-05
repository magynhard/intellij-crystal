package de.magynhard.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.inspections.CrystalExpressionTypeResolver
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
            val paramName = de.magynhard.crystal.completion.CrystalCompletionHelper.extractParameterName(param)
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
        val containingFile = context.containingFile ?: return null
        val assignments = PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)
        for (assignment in assignments.reversed()) {
            val identNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER)
                ?: assignment.firstChild?.node?.findChildByType(CrystalTypes.IDENTIFIER)
            val instanceVarAccess = assignment.instanceVarAccess
            val varName = identNode?.text ?: instanceVarAccess?.text
            if (varName != name && varName != "@$name") continue
            if (assignment.textOffset > context.textOffset) continue
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

        // Scalar literals
        val literalType = inferFromLiteral(expr)
        if (literalType != null) return literalType

        // Ternary / control-flow: delegate to CrystalExpressionTypeResolver
        if (expr is CrystalExpression) {
            val resolved = CrystalExpressionTypeResolver.resolveType(expr)
            if (resolved != null) return resolved.typeName
        }

        // Array literal
        if (text.startsWith("[")) return "Array"

        // Hash literal: extract from CrystalExpression wrapper
        if (expr is CrystalExpression) {
            val hashLiterals = expr.hashLiteralList
            if (hashLiterals.isNotEmpty()) {
                val resolved = CrystalExpressionTypeResolver.resolveType(hashLiterals[0])
                if (resolved != null) return resolved.typeName
                return "Hash"
            }
            val tupleLiterals = expr.tupleLiteralList
            if (tupleLiterals.isNotEmpty()) {
                val resolved = CrystalExpressionTypeResolver.resolveType(tupleLiterals[0])
                if (resolved != null) return resolved.typeName
                return "Tuple"
            }
        }

        // Fallback text-based detection for hash/tuple
        if (text.startsWith("{") && (text.contains("=>") || text.contains(Regex("\\w+:")))) return "Hash"
        if (text.startsWith("{")) return "Tuple"

        // Pattern: Klasse.new (handles multi-line args and bare args without parens)
        val newPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\([\s\S]*\)|\s+[\s\S]+)?$""")
        val newMatch = newPattern.find(text)
        if (newMatch != null) {
            return newMatch.groupValues[1]
        }

        // Pattern: Klasse.method_name(...) (handles multi-line args and bare args)
        val classMethodPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""")
        val classMethodMatch = classMethodPattern.find(text)
        if (classMethodMatch != null) {
            val className = classMethodMatch.groupValues[1]
            val methodName = classMethodMatch.groupValues[2]
            if (methodName == "new") return className
            // Try to find return type of the static method
            return inferReturnTypeOfMethod(methodName, className, project)
        }

        // Pattern: bare method_call (no dot) (handles multi-line args and bare args)
        val bareCallPattern = Regex("""^(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""")
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
     * When no explicit return type is annotated, infers from the method body:
     * - return statement → type of the return value
     * - implicit return (last expression) → type of last expression
     */
    private fun inferReturnTypeOfMethod(methodName: String, className: String?, project: Project): String? {
        val scope = GlobalSearchScope.allScope(project)
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        ).toMutableList()

        for (method in methods) {
            if (className != null) {
                val enclosing = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosing != className) continue
            }
            val returnType = method.typeReference?.text
            if (returnType != null) {
                return extractTypeName(returnType)
            }
            // No explicit return type — infer from method body
            val inferred = inferReturnTypeFromBody(method)
            if (inferred != null) return inferred
        }
        return null
    }

    /**
     * Infers the return type from a method body when no explicit return type is annotated.
     * Checks for return statements first, then falls back to the last expression (implicit return).
     */
    private fun inferReturnTypeFromBody(method: CrystalMethodDefinition): String? {
        val body = method.methodBody
        if (body == null) return null
        val statements = body.statementList.statementList
        if (statements.isEmpty()) return null

        // Check each statement for return statements
        for (stmt in statements) {
            val returnStmt = stmt.returnStatement
            if (returnStmt != null) {
                val returnExpr = returnStmt.expression
                if (returnExpr != null) {
                    val resolved = CrystalExpressionTypeResolver.resolveType(returnExpr)
                    if (resolved != null) return resolved.typeName
                }
            }
        }

        // Implicit return: last expression in body
        val lastStmt = statements.lastOrNull()
        if (lastStmt != null) {
            val exprStmt = lastStmt.expressionStatement
            if (exprStmt != null) {
                val innerExpr = exprStmt.expressionList.firstOrNull()
                if (innerExpr != null) {
                    val resolved = CrystalExpressionTypeResolver.resolveType(innerExpr)
                    if (resolved != null) return resolved.typeName
                }
            }
            val resolved = CrystalExpressionTypeResolver.resolveType(lastStmt)
            if (resolved != null) return resolved.typeName
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

    private fun inferFromLiteral(expr: PsiElement): String? {
        val text = expr.text.trim()

        // String literal: "..."
        if (text.startsWith("\"")) return "String"

        // Char literal: 'x', '\n', '\t', '\x41', etc.
        if (text.startsWith("'") && text.endsWith("'") && text.length >= 3) return "Char"

        // Symbol literal: :name
        if (text.startsWith(":") && !text.startsWith("::")) return "Symbol"

        // Boolean literals
        if (text == "true" || text == "false") return "Bool"

        // Nil literal
        if (text == "nil") return "Nil"

        // Float literal with suffix: 1_f32, 1.0_f64
        if (text.contains("_f32") || text.contains("_f64")) return resolveFloatLiteralType(text)

        // Float literal with decimal point: 1.0, -1.5e10
        if (text.matches(Regex("""-?\d[\d_]*\.\d[\d_]*((?:[eE][+-]?\d+))?"""))) return resolveFloatLiteralType(text)

        // Integer literal: 1, 1_i64, 1_u128
        if (text.matches(Regex("""-?\d[\d_]*(?:_?[iu](?:8|16|32|64|128))?"""))) return resolveIntegerLiteralType(text)

        return null
    }

    private fun resolveIntegerLiteralType(text: String): String {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("i8") -> "Int8"
            lower.endsWith("i16") -> "Int16"
            lower.endsWith("i32") -> "Int32"
            lower.endsWith("i64") -> "Int64"
            lower.endsWith("i128") -> "Int128"
            lower.endsWith("u8") -> "UInt8"
            lower.endsWith("u16") -> "UInt16"
            lower.endsWith("u32") -> "UInt32"
            lower.endsWith("u64") -> "UInt64"
            lower.endsWith("u128") -> "UInt128"
            else -> "Int32"
        }
    }

    private fun resolveFloatLiteralType(text: String): String {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("f32") -> "Float32"
            lower.endsWith("f64") -> "Float64"
            else -> "Float64"
        }
    }
}
