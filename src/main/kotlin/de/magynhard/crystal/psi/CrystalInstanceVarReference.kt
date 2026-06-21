package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.navigation.CrystalInstanceVarFinder

/**
 * Reference for instance variable (@name) and class variable (@@name) accesses.
 * Resolves to the first occurrence of the same variable in the enclosing class.
 * All occurrences (including the "definition") resolve to the same target,
 * enabling Find Usages to find all of them.
 */
class CrystalInstanceVarReference(element: PsiElement) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    private val varName: String = element.text  // Full name including @/@@

    override fun resolve(): PsiElement? {
        val allOccurrences = CrystalInstanceVarFinder.findAllUsages(varName, element)
        // Resolve to the first occurrence in the class (by text offset)
        return allOccurrences.minByOrNull { it.textOffset }
    }

    override fun isReferenceTo(target: PsiElement): Boolean {
        // Must be the same type of variable (instance or class)
        if (target !is CrystalInstanceVarAccess && target !is CrystalClassVarAccess) {
            return false
        }
        // Must have the same full text (@name == @name)
        if (target.text != varName) return false
        // Must be in the same enclosing class
        val myClass = findEnclosingClass(element)
        val targetClass = findEnclosingClass(target)
        return myClass != null && myClass == targetClass
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.INSTANCE_VAR)
            ?: element.node.findChildByType(CrystalTypes.CLASS_VAR)
            ?: return element

        val fixedName = when (identNode.elementType) {
            CrystalTypes.INSTANCE_VAR -> if (!newElementName.startsWith("@")) "@$newElementName" else newElementName
            CrystalTypes.CLASS_VAR -> if (!newElementName.startsWith("@@")) "@@$newElementName" else newElementName
            else -> newElementName
        }

        val newLeaf = createLeafFromText(element.project, fixedName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun findEnclosingClass(el: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            el,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java
        )
    }
}
