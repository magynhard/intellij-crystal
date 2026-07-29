package de.magynhard.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.render
import de.magynhard.crystal.psi.CrystalInstanceVarAccess

/** Compatibility adapters over scoped neutral type-set resolution. */
object CrystalTypeInference {
    fun inferType(variableName: String, context: PsiElement, project: Project): String? {
        val session = CrystalTypeSetResolver.session(context)
        val name = bodyVariableName(variableName, context)
        val variable = session.resolveVariableWithProvenance(name, context)
        if (variable.provenance == de.magynhard.crystal.analysis.CrystalVariableProvenance.ANNOTATION &&
            variable.resolution is CrystalTypeResolution.Known) {
            return variable.resolution.types.firstOrNull()?.name?.substringBefore('(')?.trim()
        }
        return variable.resolution.render()
    }

    private fun bodyVariableName(variableName: String, context: PsiElement): String =
        if (!variableName.startsWith("@") &&
            (context is CrystalInstanceVarAccess || context.parent is CrystalInstanceVarAccess)) {
            "@$variableName"
        } else {
            variableName
        }
}
