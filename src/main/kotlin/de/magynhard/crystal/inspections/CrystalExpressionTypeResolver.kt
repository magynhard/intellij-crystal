package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.completion.CrystalTypeInference
import de.magynhard.crystal.psi.*

/**
 * Resolves the type of a Crystal expression.
 * Returns a [ResolvedType] with the type name and whether numeric autocasting applies.
 */
object CrystalExpressionTypeResolver {

    /**
     * Result of type resolution.
     * @param typeName The resolved type name (e.g. "Int32", "String")
     * @param isUnsuffixedNumericLiteral True if this is a numeric literal without explicit suffix,
     *        meaning Crystal will autocast it to any compatible numeric type.
     */
    data class ResolvedType(
        val typeName: String,
        val isUnsuffixedNumericLiteral: Boolean = false
    )

    /**
     * Resolves the type of a given expression PSI element.
     * Returns null if the type cannot be determined.
     */
    fun resolveType(expr: PsiElement): ResolvedType? {
        // Unwrap bare arguments to get the actual expression inside
        if (expr is CrystalBareArgument) {
            val inner = findExpressionInContainer(expr)
            if (inner != null) return resolveType(inner)
            return null
        }

        val type = expr.node?.elementType

        // Literal types
        when (type) {
            CrystalTypes.INTEGER_LITERAL -> return resolveIntegerLiteral(expr.text)
            CrystalTypes.FLOAT_LITERAL -> return resolveFloatLiteral(expr.text)
            CrystalTypes.STRING_LITERAL -> return ResolvedType("String")
            CrystalTypes.CHAR_LITERAL -> return ResolvedType("Char")
            CrystalTypes.SYMBOL_LITERAL -> return ResolvedType("Symbol")
            CrystalTypes.TRUE -> return ResolvedType("Bool")
            CrystalTypes.FALSE -> return ResolvedType("Bool")
            CrystalTypes.NIL -> return ResolvedType("Nil")
        }

        // String expressions (interpolated strings)
        if (expr is CrystalStringExpression) return ResolvedType("String")

        // Variable references → delegate to existing type inference
        if (expr is CrystalVariableReference) {
            val name = expr.text
            val project = expr.project
            val inferred = CrystalTypeInference.inferType(name, expr, project)
            if (inferred != null) return ResolvedType(inferred)
            return null
        }

        // Method call expressions → resolve return type
        if (expr is CrystalMethodCallExpression || expr is CrystalBareMethodCallExpression) {
            return resolveMethodCallReturnType(expr)
        }

        // For composite expressions (e.g. grouped_expression), try the inner expression
        if (expr is CrystalGroupedExpression) {
            val inner = expr.children.firstOrNull { it is CrystalExpression }
            if (inner != null) return resolveType(inner)
        }

        // Expression wrapper — try first meaningful child
        if (expr is CrystalExpression) {
            // Try literal tokens directly within the expression
            val firstChild = expr.firstChild
            if (firstChild != null) return resolveType(firstChild)
        }

        return null
    }

    private fun resolveIntegerLiteral(text: String): ResolvedType {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("i8") -> ResolvedType("Int8")
            lower.endsWith("i16") -> ResolvedType("Int16")
            lower.endsWith("i32") -> ResolvedType("Int32")
            lower.endsWith("i64") -> ResolvedType("Int64")
            lower.endsWith("i128") -> ResolvedType("Int128")
            lower.endsWith("u8") -> ResolvedType("UInt8")
            lower.endsWith("u16") -> ResolvedType("UInt16")
            lower.endsWith("u32") -> ResolvedType("UInt32")
            lower.endsWith("u64") -> ResolvedType("UInt64")
            lower.endsWith("u128") -> ResolvedType("UInt128")
            else -> ResolvedType("Int32", isUnsuffixedNumericLiteral = true)
        }
    }

    private fun resolveFloatLiteral(text: String): ResolvedType {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("f32") -> ResolvedType("Float32")
            lower.endsWith("f64") -> ResolvedType("Float64")
            else -> ResolvedType("Float64", isUnsuffixedNumericLiteral = true)
        }
    }

    private fun resolveMethodCallReturnType(expr: PsiElement): ResolvedType? {
        val text = expr.text.trim()

        // Pattern: Klasse.new(...) → type is "Klasse"
        val newPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\(.*\))?$""", RegexOption.DOT_MATCHES_ALL)
        val newMatch = newPattern.find(text)
        if (newMatch != null) return ResolvedType(newMatch.groupValues[1])

        // Pattern: Klasse.method(...) → look up return type
        val classMethodPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\(.*\))?$""", RegexOption.DOT_MATCHES_ALL)
        val classMethodMatch = classMethodPattern.find(text)
        if (classMethodMatch != null) {
            val className = classMethodMatch.groupValues[1]
            val methodName = classMethodMatch.groupValues[2]
            if (methodName == "new") return ResolvedType(className)
            val returnType = lookupReturnType(methodName, expr.project)
            if (returnType != null) return ResolvedType(returnType)
        }

        // Pattern: method_name(...) → look up return type
        val methodName = extractMethodName(expr)
        if (methodName != null && methodName[0].isLowerCase()) {
            val returnType = lookupReturnType(methodName, expr.project)
            if (returnType != null) return ResolvedType(returnType)
        }

        return null
    }

    private fun extractMethodName(expr: PsiElement): String? {
        val child = expr.firstChild
        if (child?.node?.elementType == CrystalTypes.IDENTIFIER) return child.text
        if (child?.node?.elementType == CrystalTypes.CONSTANT) return child.text
        return null
    }

    private fun lookupReturnType(methodName: String, project: com.intellij.openapi.project.Project): String? {
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        val methods = com.intellij.psi.stubs.StubIndex.getElements(
            de.magynhard.crystal.stubs.CrystalMethodIndex.KEY,
            methodName, project, scope, CrystalMethodDefinition::class.java
        )
        for (method in methods) {
            val returnType = method.typeReference?.text
            if (returnType != null) {
                return returnType.split("|").first().trim()
                    .replace(Regex("""\(.*\)"""), "").trim()
            }
        }
        return null
    }

    private fun findExpressionInContainer(container: PsiElement): PsiElement? {
        var child = container.firstChild
        while (child != null) {
            val elemType = child.node?.elementType
            if (elemType == CrystalTypes.IDENTIFIER || elemType == CrystalTypes.COLON
                || elemType == CrystalTypes.STAR || elemType == CrystalTypes.DOUBLE_STAR
                || child is com.intellij.psi.PsiWhiteSpace) {
                child = child.nextSibling
                continue
            }
            return child
        }
        return null
    }
}
