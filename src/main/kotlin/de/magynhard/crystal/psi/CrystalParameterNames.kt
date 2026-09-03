package de.magynhard.crystal.psi

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

    val identifiers = node.getChildren(null)
        .filter { it.elementType == CrystalTypes.IDENTIFIER }
        .map { it.text }
    val storageName = instanceVarAccess?.name ?: classVarAccess?.name
    val localName = storageName?.removePrefix("@@")?.removePrefix("@") ?: identifiers.lastOrNull()
    val explicitExternalName = when {
        storageName != null -> identifiers.firstOrNull()
        identifiers.size > 1 -> identifiers.first()
        else -> null
    }
    return CrystalParameterNameInfo(
        localName = localName,
        storageName = storageName,
        explicitExternalName = explicitExternalName,
    )
}

fun CrystalParameter.localBindingNames(): List<String> {
    if (node.findChildByType(CrystalTypes.LPAREN) != null) {
        return node.getChildren(null)
            .filter { it.elementType == CrystalTypes.IDENTIFIER }
            .map { it.text }
    }
    return listOfNotNull(parameterNameInfo().localName)
}
