package de.magynhard.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.render
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition

/** Compatibility adapters over scoped neutral type-set resolution. */
object CrystalTypeInference {
    fun inferType(variableName: String, context: PsiElement, project: Project): String? {
        val result = CrystalTypeSetResolver.session(context).resolveVariable(bodyVariableName(variableName, context), context)
        return result.render()
    }

    internal fun inferTypePreservingUnion(
        variableName: String,
        context: PsiElement,
        project: Project
    ): String? = CrystalTypeSetResolver.session(context)
        .resolveVariable(bodyVariableName(variableName, context), context).render()

    internal fun inferReturnTypePreservingUnion(method: CrystalMethodDefinition): String? =
        CrystalTypeSetResolver.session(method).resolveMethodReturn(method).render()

    private fun bodyVariableName(variableName: String, context: PsiElement): String =
        if (!variableName.startsWith("@") &&
            (context is CrystalInstanceVarAccess || context.parent is CrystalInstanceVarAccess)) {
            "@$variableName"
        } else {
            variableName
        }
}
