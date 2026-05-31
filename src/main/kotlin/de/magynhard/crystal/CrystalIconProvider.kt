package de.magynhard.crystal

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class CrystalIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val name = element.name
            if (name.endsWith("_spec.cr")) {
                return CrystalIcons.SPEC_FILE
            }
        }
        return null
    }
}
