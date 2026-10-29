package de.magynhard.crystal.stubs

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*

/** Unresolved candidate names, extracted during normal full-PSI stub building. */
internal object CrystalDeclaredAncestors {
    private val SIMPLE_TYPE = Regex("[\\p{Lu}][\\p{L}\\p{N}_]*")

    fun names(declaration: PsiElement): List<String> {
        if (declaration.children.takeWhile { it !is CrystalClassBody && it !is CrystalEnumBody }
                .any { it is CrystalMacroInterpolation }) return emptyList()
        val references = mutableListOf<CrystalTypeReference>()
        val superclass = when (declaration) {
            is CrystalClassDefinition -> declaration.superclassClause?.typeReference
            is CrystalStructDefinition -> declaration.superclassClause?.typeReference
            // An enum's base integer is not an inheritance edge.
            else -> null
        }
        superclass?.let(references::add)
        val body = when (declaration) {
            is CrystalClassDefinition -> declaration.classBody
            is CrystalModuleDefinition -> declaration.classBody
            is CrystalStructDefinition -> declaration.classBody
            is CrystalEnumDefinition -> declaration.enumBody
            else -> null
        }
        fun visit(element: PsiElement) {
            when (element) {
                is CrystalIncludeStatement -> element.typeReference?.let(references::add)
                is CrystalStatement, is CrystalVisibilityModifier,
                is CrystalExpressionStatement, is CrystalExpression -> element.children.forEach(::visit)
                // Never descend into nested types, methods, macros, or blocks.
            }
        }
        body?.children?.forEach(::visit)
        val parameters = generateSequence(declaration as PsiElement?) { CrystalPsiUtils.getEnclosingType(it) }
            .flatMap { type ->
                type.node.findChildByType(CrystalTypes.TYPE_PARAMETERS)?.getChildren(null).orEmpty()
                    .filter { it.elementType == CrystalTypes.CONSTANT }.map { it.text }
            }.toSet()
        return references.mapNotNull { reference ->
            val root = reference.text.substringBefore('(').filterNot(Char::isWhitespace)
            val parts = root.removePrefix("::").split("::")
            if (parts.any { !SIMPLE_TYPE.matches(it) }) return@mapNotNull null
            if (!root.startsWith("::") && parts.first() in parameters) return@mapNotNull null
            parts.last()
        }.distinct()
    }
}
