package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import de.magynhard.crystal.analysis.CrystalAnnotationResolver

/** A qualified annotation path resolves only through the annotation index. */
class CrystalAnnotationReference(private val path: CrystalTypePath) :
    PsiReferenceBase<CrystalTypePath>(path, TextRange(0, path.textLength), true) {
    override fun resolve(): PsiElement? = CrystalAnnotationResolver.lookup(path)?.target

    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = path.node.getChildren(null).lastOrNull { it.elementType == CrystalTypes.CONSTANT }
            ?: return path
        val replacement = createLeafFromText(path.project, newElementName, CrystalTypes.CONSTANT) ?: return path
        leaf.treeParent.replaceChild(leaf, replacement)
        return path
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
