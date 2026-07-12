package de.magynhard.crystal

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import de.magynhard.crystal.ecr.EmbeddedCrystalIcons
import javax.swing.Icon

class CrystalIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val name = element.name
            return when {
                name.endsWith(".html.ecr") || name.endsWith(".ecr") ->
                    EmbeddedCrystalIcons.FILE
                name.endsWith("_spec.cr") -> CrystalIcons.SPEC_FILE
                else -> null
            }
        }
        return null
    }
}
