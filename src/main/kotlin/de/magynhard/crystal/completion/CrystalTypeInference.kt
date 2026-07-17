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
        val types = inferTypeList(variableName, context, project)
        return if (types.isEmpty()) null else types.joinToString(" | ")
    }

    /**
     * All candidate types for [variableName], including union members (e.g. `Int32 | Nil`)
     * and receiver-derived types (e.g. `x = obj.foo` where `obj`'s type is known). Used by
     * Go-to-Definition and completion so a union-typed variable resolves methods across every
     * member type, not just the first.
     */
    fun inferTypeList(variableName: String, context: PsiElement, project: Project, depth: Int = 0): List<String> {
        if (depth > 5) return emptyList()
        val paramType = inferFromParameter(variableName, context)
        if (paramType != null) return paramType
        val assignTypes = inferFromAssignmentList(variableName, context, project, depth)
        if (assignTypes.isNotEmpty()) return assignTypes
        return emptyList()
    }

    /**
     * Check if the variable is a parameter with a type annotation.
     * e.g. def foo(x : Apfel) → type of x is "Apfel"; `x : Int32 | Nil` → ["Int32", "Nil"].
     */
    private fun inferFromParameter(name: String, context: PsiElement): List<String>? {
        // Find enclosing method
        val method = PsiTreeUtil.getParentOfType(context, CrystalMethodDefinition::class.java)
            ?: return null

        val paramList = method.parameterList ?: return null
        for (param in paramList.parameterList) {
            val paramName = CrystalCompletionHelper.extractParameterName(param)
            if (paramName == name) {
                // Has type annotation?
                val typeRef = param.typeReference
                if (typeRef != null) {
                    return splitTypeNames(typeRef.text)
                }
            }
        }
        return null
    }

    /**
     * Search for assignments to this variable in the current scope.
     * Patterns recognized:
     * - x = Klasse.new → type is "Klasse"
     * - x = Klasse.method_name → return type (union) of method_name
     * - x = receiver.method → return type of method on the inferred receiver type
     * - x = method_name → return type (union) of top-level/enclosing method
     * Reassigned variables accumulate all candidate types (unions are preserved).
     */
    private fun inferFromAssignmentList(name: String, context: PsiElement, project: Project, depth: Int): List<String> {
        val containingFile = context.containingFile ?: return emptyList()
        val assignments = PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)
        val results = mutableListOf<String>()
        for (assignment in assignments.reversed()) {
            val identNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER)
                ?: assignment.firstChild?.node?.findChildByType(CrystalTypes.IDENTIFIER)
            val instanceVarAccess = assignment.instanceVarAccess
            val varName = identNode?.text ?: instanceVarAccess?.text
            if (varName != name && varName != "@$name") continue
            if (assignment.textOffset > context.textOffset) continue
            val expr = assignment.expression ?: continue
            results.addAll(inferTypeFromExpressionList(expr, project, depth))
        }
        return results.distinct()
    }

    /**
     * Infers the type(s) from an expression, returning every candidate (union members
     * preserved). Recognizes:
     * - Klasse.new → ["Klasse"]
     * - Klasse.method → return type union of that static method
     * - receiver.method → return type union of the method on the inferred receiver type
     * - method_call → return type union of that method
     */
    private fun inferTypeFromExpressionList(expr: PsiElement, project: Project, depth: Int): List<String> {
        val text = expr.text.trim()

        // Scalar literals
        val literalType = inferFromLiteral(expr)
        if (literalType != null) return listOf(literalType)

        // Ternary / control-flow: delegate to CrystalExpressionTypeResolver (may be a union)
        if (expr is CrystalExpression) {
            val resolved = CrystalExpressionTypeResolver.resolveType(expr)
            if (resolved != null) return splitTypeNames(resolved.typeName)
        }

        // Array literal
        if (text.startsWith("[")) return listOf("Array")

        // Hash / tuple literal: extract from CrystalExpression wrapper
        if (expr is CrystalExpression) {
            val hashLiterals = expr.hashLiteralList
            if (hashLiterals.isNotEmpty()) {
                val resolved = CrystalExpressionTypeResolver.resolveType(hashLiterals[0])
                if (resolved != null) return listOf(resolved.typeName)
                return listOf("Hash")
            }
            val tupleLiterals = expr.tupleLiteralList
            if (tupleLiterals.isNotEmpty()) {
                val resolved = CrystalExpressionTypeResolver.resolveType(tupleLiterals[0])
                if (resolved != null) return listOf(resolved.typeName)
                return listOf("Tuple")
            }
        }

        // Fallback text-based detection for hash/tuple
        if (text.startsWith("{") && (text.contains("=>") || text.contains(Regex("\\w+:")))) return listOf("Hash")
        if (text.startsWith("{")) return listOf("Tuple")

        // Pattern: Klasse.new (handles multi-line args and bare args without parens)
        val newMatch = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (newMatch != null) return listOf(newMatch.groupValues[1])

        // Pattern: Klasse.method_name(...) (handles multi-line args and bare args)
        val classMethodMatch = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (classMethodMatch != null) {
            val className = classMethodMatch.groupValues[1]
            val methodName = classMethodMatch.groupValues[2]
            if (methodName == "new") return listOf(className)
            return inferReturnTypeOfMethodList(methodName, className, project)
        }

        // Pattern: receiver.method where receiver is a variable (e.g. x = obj.foo).
        // Infer the receiver's type, then the method's return type across that type.
        val recvMethodMatch = Regex("""^([a-z]\w*)\.(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (recvMethodMatch != null) {
            val recv = recvMethodMatch.groupValues[1]
            val meth = recvMethodMatch.groupValues[2]
            val recvTypes = inferTypeList(recv, expr, project, depth + 1)
            val results = mutableListOf<String>()
            for (rt in recvTypes) results.addAll(inferReturnTypeOfMethodList(meth, rt, project))
            if (results.isNotEmpty()) return results
            // Receiver type unknown — no guess; fall through (no bare-call match for `a.b`).
            return emptyList()
        }

        // Pattern: bare method_call (no dot) (handles multi-line args and bare args)
        val bareCallMatch = Regex("""^(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (bareCallMatch != null) {
            val methodName = bareCallMatch.groupValues[1]
            // Only if it starts with lowercase (method, not class)
            if (methodName[0].isLowerCase()) {
                return inferReturnTypeOfMethodList(methodName, null, project)
            }
        }

        return emptyList()
    }

    /**
     * Finds the return type(s) of a method by name. If [className] is provided, searches
     * within that class; otherwise project-wide. When no explicit return type is annotated,
     * infers from the method body. Union return annotations are preserved.
     */
    private fun inferReturnTypeOfMethodList(methodName: String, className: String?, project: Project): List<String> {
        val scope = GlobalSearchScope.allScope(project)
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope, CrystalMethodDefinition::class.java
        ).toMutableList()

        val results = mutableListOf<String>()
        for (method in methods) {
            if (className != null) {
                val enclosing = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosing != className) continue
            }
            val returnType = method.typeReference?.text
            if (returnType != null) {
                results.addAll(splitTypeNames(returnType))
            } else {
                // No explicit return type — infer from method body
                val inferred = inferReturnTypeFromBody(method)
                if (inferred != null) results.addAll(splitTypeNames(inferred))
            }
        }
        return results.distinct()
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
                val returnExpr = returnStmt.expressionList.firstOrNull()
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
     * Splits a union type annotation into its member type names, preserving
     * generics (e.g. `Array(String)` stays intact). Does NOT split on `|`
     * nested inside parentheses.
     * e.g. "Int32 | Nil" → ["Int32", "Nil"]; "Slice(UInt8) | Nil" → ["Slice(UInt8)", "Nil"]
     */
    private fun splitTypeNames(typeText: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (ch in typeText) {
            when (ch) {
                '(' -> { depth++; current.append(ch) }
                ')' -> { depth = maxOf(0, depth - 1); current.append(ch) }
                '|' -> {
                    if (depth == 0) {
                        val piece = current.toString().trim()
                        if (piece.isNotEmpty()) parts.add(piece)
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) parts.add(last)
        return parts
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
        if (text.matches(Regex("""-?\d[\d_]*\.\d[\d_]*([eE][+-]?\d+)?"""))) return resolveFloatLiteralType(text)

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
