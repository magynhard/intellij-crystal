package de.magynhard.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalVariableProvenance
import de.magynhard.crystal.analysis.render
import de.magynhard.crystal.psi.CrystalInstanceVarAccess

/** Compatibility adapters over scoped neutral type-set resolution. */
object CrystalTypeInference {

    /**
     * The honest placeholder for a bound variable whose current value's type
     * is not inferable (macro-generated returns, unresolved chains). Rendered
     * gray by the documentation provider — distinct from "Any", which stays
     * reserved for genuinely unconstrained (duck-typed) variables and must
     * not be confused with Crystal's JSON::Any / YAML::Any.
     */
    const val UNKNOWN_TYPE = "Unknown"

    fun inferType(variableName: String, context: PsiElement, project: Project): String? {
        val session = CrystalTypeSetResolver.session(context)
        val name = bodyVariableName(variableName, context)
        val variable = session.resolveVariableWithProvenance(name, context)
        if (variable.provenance == CrystalVariableProvenance.ANNOTATION &&
            variable.resolution is CrystalTypeResolution.Known) {
            return variable.resolution.types.firstOrNull()?.name?.substringBefore('(')?.trim()
        }
        // A bound variable (assignment/annotation evidence) whose value type
        // is not inferable renders as "Unknown" — not "Any", which would
        // claim unconstrained duck typing.
        if (variable.resolution is CrystalTypeResolution.Unknown &&
            variable.provenance != CrystalVariableProvenance.UNKNOWN
        ) {
            return UNKNOWN_TYPE
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
