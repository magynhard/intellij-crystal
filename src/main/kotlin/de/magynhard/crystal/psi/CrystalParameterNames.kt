package de.magynhard.crystal.psi

import de.magynhard.crystal.lexer.CrystalTokenTypes

/** The distinct source-level names carried by a Crystal method parameter. */
data class CrystalParameterNameInfo(
    val localName: String?,
    val storageName: String?,
    val explicitExternalName: String?,
) {
    val callSiteName: String?
        get() = explicitExternalName ?: localName

    val sourceName: String?
        get() {
            val internal = storageName ?: localName ?: return explicitExternalName
            return explicitExternalName?.let { "$it $internal" } ?: internal
        }
}

/**
 * Separates a parameter's call-site label, local binding, and shorthand
 * instance/class-variable assignment target.
 */
fun CrystalParameter.parameterNameInfo(): CrystalParameterNameInfo {
    if (node.findChildByType(CrystalTypes.LPAREN) != null) {
        return CrystalParameterNameInfo(null, null, null)
    }

    val children = node.getChildren(null)
    val identifiers = children
        .filter {
            it.elementType == CrystalTypes.IDENTIFIER ||
                CrystalTokenTypes.KEYWORD_VARIABLES.contains(it.elementType) ||
                (isLibFunParameter() && CrystalTokenTypes.KEYWORDS.contains(it.elementType))
        }
        .map { it.text }
    val storageName = instanceVarAccess?.name ?: classVarAccess?.name
    val localName = storageName?.removePrefix("@@")?.removePrefix("@") ?: identifiers.lastOrNull()
    // Every keyword is valid as an external call-site label (`def foo(with
    // entries)`); keyword variables are also internal bindings when they are
    // the sole name. Only a keyword preceding the internal/storage leaf counts
    // as external; keywords in type position (`x : self`) and prefixes do not.
    val internalIndex = if (storageName != null) {
        children.indexOfFirst {
            it.elementType == CrystalTypes.INSTANCE_VAR_ACCESS ||
                it.elementType == CrystalTypes.CLASS_VAR_ACCESS
        }
    } else {
        children.indexOfLast {
            it.elementType == CrystalTypes.IDENTIFIER ||
                CrystalTokenTypes.KEYWORD_VARIABLES.contains(it.elementType)
        }
    }
    val explicitExternalName = if (internalIndex > 0) {
        children.take(internalIndex)
            .firstOrNull {
                it.elementType == CrystalTypes.IDENTIFIER ||
                    it.elementType in CrystalTokenTypes.KEYWORDS
            }
            ?.text
    } else {
        null
    }
    return CrystalParameterNameInfo(
        localName = localName,
        storageName = storageName,
        explicitExternalName = explicitExternalName,
    )
}

/** FFI signatures permit every keyword as an explicitly typed parameter name. */
fun CrystalParameter.isLibFunParameter(): Boolean = parent?.parent is CrystalFunDefinition

fun CrystalParameter.localBindingNames(): List<String> {
    if (node.findChildByType(CrystalTypes.LPAREN) != null) {
        return node.getChildren(null)
            .filter { it.elementType == CrystalTypes.IDENTIFIER }
            .map { it.text }
    }
    return listOfNotNull(parameterNameInfo().localName)
}
