package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
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
        if (expr is CrystalBareArgument) {
            val inner = findExpressionInContainer(expr)
            if (inner != null) return resolveType(inner)
            return null
        }

        if (expr is CrystalStatement) {
            val inner = expr.expressionStatement ?: expr.ifStatement
                ?: expr.beginStatement ?: expr.assignment ?: expr.multiAssignment
            if (inner != null) return resolveType(inner)
            val firstChild = expr.children.firstOrNull { it !is PsiWhiteSpace }
            if (firstChild != null) return resolveType(firstChild)
            return null
        }

        if (expr is CrystalExpressionStatement) {
            val inner = expr.expressionList.firstOrNull()
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

        // Trivial expression types — always resolve to a fixed type
        if (expr is CrystalRegexExpression) return ResolvedType("Regex")
        if (expr is CrystalCommandExpression) return ResolvedType("String")
        if (expr is CrystalHeredocLiteral) return ResolvedType("String")
        if (expr is CrystalSymbolStringExpression) return ResolvedType("Symbol")
        if (expr is CrystalSizeofExpression) return ResolvedType("Int32")
        if (expr is CrystalInstanceSizeofExpression) return ResolvedType("Int32")
        if (expr is CrystalOffsetofExpression) return ResolvedType("Int32")

        // Array literal
        if (expr is CrystalArrayLiteral) return resolveArrayLiteral(expr)

        // Hash literal
        if (expr is CrystalHashLiteral) return resolveHashLiteral(expr)

        // Tuple literal
        if (expr is CrystalTupleLiteral) return resolveTupleLiteral(expr)

        // Control-flow expressions
        if (expr is CrystalIfStatement) return resolveIfExpression(expr)
        if (expr is CrystalCaseStatement) return resolveCaseExpression(expr)

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
            val astChildren = expr.node.getChildren(null)

            // Operator detection — binary operators like ==, +, etc.
            val opResult = resolveOperatorType(astChildren)
            if (opResult != null) return opResult

            // Ternary expression: or_expression QUESTION expression COLON expression
            val questionIdx = astChildren.indexOfFirst { it.elementType == CrystalTypes.QUESTION }
            if (questionIdx >= 0) {
                val colonIdx = astChildren.indexOfFirst { it.elementType == CrystalTypes.COLON }
                if (colonIdx > questionIdx) {
                    val trueExpr = astChildren.drop(questionIdx + 1).firstOrNull { it.psi !is PsiWhiteSpace }?.psi
                    val falseExpr = astChildren.drop(colonIdx + 1).firstOrNull { it.psi !is PsiWhiteSpace }?.psi
                    val trueType = if (trueExpr != null) resolveType(trueExpr) else null
                    val falseType = if (falseExpr != null) resolveType(falseExpr) else null
                    if (trueType != null && falseType != null) {
                        if (trueType.typeName == falseType.typeName) return trueType
                        return ResolvedType("${trueType.typeName} | ${falseType.typeName}")
                    }
                    if (trueType != null) return trueType
                    if (falseType != null) return falseType
                    return null
                }
            }
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

    private fun resolveArrayLiteral(expr: CrystalArrayLiteral): ResolvedType? {
        // Check for "of Type" annotation
        val typeRef = expr.typeReference
        if (typeRef != null) {
            val typeName = typeRef.text.trim().split("|").first().trim()
                .replace(Regex("""\(.*\)"""), "").trim()
            return ResolvedType("Array($typeName)")
        }

        // Infer from elements
        val elements = expr.expressionList?.expressionList ?: emptyList()
        if (elements.isEmpty()) return null

        val elementTypes = elements.mapNotNull { resolveType(it) }
        if (elementTypes.size != elements.size) return null

        val firstType = elementTypes.first().typeName
        return if (elementTypes.all { it.typeName == firstType }) {
            ResolvedType("Array($firstType)")
        } else {
            val union = elementTypes.joinToString(" | ") { it.typeName }
            ResolvedType("Array($union)")
        }
    }

    private fun resolveHashLiteral(expr: CrystalHashLiteral): ResolvedType? {
        val typeRefs = expr.typeReferenceList
        if (typeRefs.size >= 2) {
            val keyType = typeRefs[0].text.trim().split("|").first().trim()
                .replace(Regex("""\(.*\)"""), "").trim()
            val valueType = typeRefs[1].text.trim().split("|").first().trim()
                .replace(Regex("""\(.*\)"""), "").trim()
            return ResolvedType("Hash($keyType, $valueType)")
        }

        val entries = expr.hashEntryList?.hashEntryList ?: emptyList()
        if (entries.isEmpty()) return null

        val keyTypes = entries.mapNotNull { entry ->
            val expressions = entry.expressionList
            if (expressions.isEmpty()) return@mapNotNull null
            val keyExpr = expressions[0]
            val keyText = keyExpr.text.trim()
            if (keyText.matches(Regex("^[a-zA-Z_]\\w*[?!]?$"))) {
                ResolvedType("Symbol")
            } else {
                resolveType(keyExpr)
            }
        }
        val valueTypes = entries.mapNotNull { it.expressionList.getOrNull(1)?.let { e -> resolveType(e) } }
        if (keyTypes.size != entries.size || valueTypes.size != entries.size) return null

        val keyType = keyTypes.first().typeName
        val valueType = valueTypes.first().typeName
        return if (keyTypes.all { it.typeName == keyType } && valueTypes.all { it.typeName == valueType }) {
            ResolvedType("Hash($keyType, $valueType)")
        } else {
            val keyUnion = keyTypes.joinToString(" | ") { it.typeName }
            val valueUnion = valueTypes.joinToString(" | ") { it.typeName }
            ResolvedType("Hash($keyUnion, $valueUnion)")
        }
    }

    private fun resolveTupleLiteral(expr: CrystalTupleLiteral): ResolvedType? {
        val elements = expr.expressionList.expressionList
        if (elements.isEmpty()) return null

        val types = elements.mapNotNull { resolveType(it) }
        if (types.size != elements.size) return null

        val typeList = types.joinToString(", ") { it.typeName }
        return ResolvedType("Tuple($typeList)")
    }

    private fun resolveIfExpression(expr: CrystalIfStatement): ResolvedType? {
        val branches = mutableListOf<ResolvedType>()

        val thenStatements = expr.statementList?.statementList
        if (!thenStatements.isNullOrEmpty()) {
            val lastThen = thenStatements.lastOrNull()
            if (lastThen != null) {
                val thenType = resolveType(lastThen)
                if (thenType != null) branches.add(thenType)
            }
        }

        val elseClause = expr.elseClause
        if (elseClause != null) {
            val elseStatements = elseClause.statementList.statementList
            val lastElse = elseStatements.lastOrNull()
            if (lastElse != null) {
                val elseType = resolveType(lastElse)
                if (elseType != null) branches.add(elseType)
            }
        } else {
            branches.add(ResolvedType("Nil"))
        }

        if (branches.isEmpty()) return null
        if (branches.size == 1) return branches.first()
        val typeList = branches.joinToString(" | ") { it.typeName }
        return ResolvedType(typeList)
    }

    private fun resolveCaseExpression(expr: CrystalCaseStatement): ResolvedType? {
        val branches = mutableListOf<ResolvedType>()

        for (whenClause in expr.whenClauseList) {
            val thenStatements = whenClause.statementList.statementList
            val lastThen = thenStatements.lastOrNull()
            if (lastThen != null) {
                val thenType = resolveType(lastThen)
                if (thenType != null) branches.add(thenType)
            }
        }

        val elseClause = expr.elseClause
        if (elseClause != null) {
            val elseStatements = elseClause.statementList.statementList
            val lastElse = elseStatements.lastOrNull()
            if (lastElse != null) {
                val elseType = resolveType(lastElse)
                if (elseType != null) branches.add(elseType)
            }
        }

        if (branches.isEmpty()) return null
        if (branches.size == 1) return branches.first()
        val typeList = branches.joinToString(" | ") { it.typeName }
        return ResolvedType(typeList)
    }

    private fun resolveOperatorType(astChildren: Array<com.intellij.lang.ASTNode>): ResolvedType? {
        val nonWhitespace = astChildren.filter { it.psi !is PsiWhiteSpace }
        if (nonWhitespace.size < 3) return null

        val opType = nonWhitespace[1].elementType

        return when (opType) {
            CrystalTypes.EQ, CrystalTypes.NEQ,
            CrystalTypes.LT, CrystalTypes.LTE,
            CrystalTypes.GT, CrystalTypes.GTE,
            CrystalTypes.SPACESHIP, CrystalTypes.CASE_EQ,
            CrystalTypes.MATCH_OP -> ResolvedType("Bool")

            CrystalTypes.AND_AND, CrystalTypes.OR_OR -> ResolvedType("Bool")

            CrystalTypes.PLUS, CrystalTypes.MINUS,
            CrystalTypes.STAR, CrystalTypes.SLASH,
            CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT,
            CrystalTypes.DOUBLE_STAR -> {
                val leftType = resolveType(nonWhitespace[0].psi)
                val rightType = resolveType(nonWhitespace[2].psi)
                if (leftType != null && leftType.typeName == rightType?.typeName) leftType
                else null
            }

            else -> null
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
        val scope = com.intellij.psi.search.GlobalSearchScope.allScope(project)
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
