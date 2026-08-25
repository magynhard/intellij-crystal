package de.magynhard.crystal.ecr

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import de.magynhard.crystal.ecr.structure.EcrStructureViewModel

class EcrStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return TemplateLanguageStructureViewBuilder.create(psiFile) { file, editor ->
            EcrStructureViewModel(file, editor)
        }
    }
}
