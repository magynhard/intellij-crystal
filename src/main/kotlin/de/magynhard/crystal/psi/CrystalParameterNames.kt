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
        .filter { it.elementType == CrystalTypes.IDENTIFIER }
        .map { it.text }
    val storageName = instanceVarAccess?.name ?: classVarAccess?.name
    val localName = storageName?.removePrefix("@@")?.removePrefix("@") ?: identifiers.lastOrNull()
    // Keywords are valid external (call-site) labels — `def foo(with entries)` —
    // but never internal bindings. Only a keyword that precedes the internal-name
    // or storage leaf counts; keywords in type position (`x : self`) do not, and
    // neither do prefixes like `&`, `*`, or `out`-marker-adjacent tokens that are
    // not name leaves. The compiler assigns the same external names, including
    // `out` in `def foo(out x)`.
    val internalIndex = if (storageName != null) {
        children.indexOfFirst {
            it.elementType == CrystalTypes.INSTANCE_VAR_ACCESS ||
                it.elementType == CrystalTypes.CLASS_VAR_ACCESS
        }
    } else {
        children.indexOfLast { it.elementType == CrystalTypes.IDENTIFIER }
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

fun CrystalParameter.localBindingNames(): List<String> {
    if (node.findChildByType(CrystalTypes.LPAREN) != null) {
        return node.getChildren(null)
            .filter { it.elementType == CrystalTypes.IDENTIFIER }
            .map { it.text }
    }
    return listOfNotNull(parameterNameInfo().localName)
}
