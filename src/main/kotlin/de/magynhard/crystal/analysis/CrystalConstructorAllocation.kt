package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.*

/** Return evidence for fixed constructor bodies, independent of a macro's generated ivar writes. */
internal object CrystalConstructorAllocation {
    fun dependencies(
        method: CrystalMethodDefinition,
        methods: List<CrystalMethodDefinition>,
        visiting: Set<CrystalMethodDefinition> = emptySet(),
    ): Set<String>? {
        if (method in visiting) return null
        val declared = method.typeReference?.text
        if (declared == "self") return emptySet()
        if (declared != null) return null
        val body = method.methodBody ?: return null
        if (body.rescueClauseList.isNotEmpty() || body.elseClause != null || body.ensureClause != null) return null
        if (PsiTreeUtil.findChildOfAnyType(body, CrystalMacroControl::class.java, CrystalMacroInterpolation::class.java,
                CrystalReturnStatement::class.java, CrystalBreakStatement::class.java, CrystalNextStatement::class.java,
                CrystalYieldStatement::class.java, CrystalBlock::class.java, CrystalProcLiteral::class.java,
                CrystalMultiAssignment::class.java) != null) return null
        val statements = body.statementList.children.filterIsInstance<CrystalStatement>()
        val first = statements.firstOrNull() ?: return null
        val allocation = first.assignment
        if (allocation != null) {
            if (statements.size < 2 || allocation.postfixModifier != null ||
                allocation.node.findChildByType(CrystalTypes.ASSIGN) == null) return null
            val name = allocation.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: return null
            if (!isAllocation(allocation.expression, method)) return null
            if (compact(statements.last().expressionStatement?.expressionList?.singleOrNull()) != name) return null
            if (statements.drop(1).any { it.expressionStatement == null }) return null
            if (PsiTreeUtil.collectElements(body) { CrystalTokenTypes.ASSIGN_OPS.contains(it.node.elementType) }.size != 1) return null
            return setOf("allocate")
        }
        if (statements.size != 1) return null
        val statement = first.expressionStatement ?: return null
        if (statement.postfixModifier != null) return null
        val expression = statement.expressionList.singleOrNull() ?: return null
        if (isAllocation(expression, method)) return setOf("allocate")
        val call = expression.children.singleOrNull() as? CrystalMethodCallExpression ?: return null
        val name = call.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: return null
        val helper = methods.filter { it.name == name && CrystalPsiUtils.isSelfMethod(it) }.singleOrNull() ?: return null
        return dependencies(helper, methods, visiting + method)?.plus(name)
    }

    private fun compact(element: PsiElement?): String? = element?.text?.filterNot(Char::isWhitespace)

    private fun isAllocation(expression: PsiElement?, method: CrystalMethodDefinition): Boolean {
        val text = compact(expression)
        if (text !in setOf("allocate", "allocate()", "self.allocate", "self.allocate()")) return false
        return text != "allocate" || method.parameterList?.parameterList.orEmpty().none {
            "allocate" in it.localBindingNames()
        }
    }
}
