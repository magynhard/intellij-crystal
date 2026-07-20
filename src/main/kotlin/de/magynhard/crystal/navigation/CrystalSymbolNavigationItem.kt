package de.magynhard.crystal.navigation

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*
import javax.swing.Icon

enum class CrystalSymbolKind(val icon: Icon, val label: String) {
    CLASS(AllIcons.Nodes.Class, "class"),
    MODULE(AllIcons.Nodes.Module, "module"),
    STRUCT(AllIcons.Nodes.Record, "struct"),
    ENUM(AllIcons.Nodes.Enum, "enum"),
    METHOD(AllIcons.Nodes.Method, "method"),
    MACRO(AllIcons.Nodes.Template, "macro"),
    LIB(AllIcons.Nodes.PpLib, "lib"),
    ANNOTATION(AllIcons.Nodes.Annotationtype, "annotation"),
    CONSTANT(AllIcons.Nodes.Constant, "constant"),
    ALIAS(AllIcons.Nodes.Type, "alias")
}

data class CrystalSymbol(
    val name: String,
    val kind: CrystalSymbolKind,
    val element: PsiElement
)

internal fun crystalTypeSymbolKind(element: CrystalNamedElement): CrystalSymbolKind = when (element) {
    is CrystalClassDefinition -> CrystalSymbolKind.CLASS
    is CrystalModuleDefinition -> CrystalSymbolKind.MODULE
    is CrystalStructDefinition -> CrystalSymbolKind.STRUCT
    is CrystalEnumDefinition -> CrystalSymbolKind.ENUM
    else -> CrystalSymbolKind.CLASS
}
