package de.magynhard.crystal.ecr

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import de.magynhard.crystal.ecr.structure.CrystalInstanceVariablesStructureViewModel
import de.magynhard.crystal.ecr.structure.EcrStructureViewModel

class EcrStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return object : StructureViewBuilder {
            override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
                val descriptors = mutableListOf<StructureViewComposite.StructureViewDescriptor>()

                // 1. ECR section
                val ecrBuilder = object : TreeBasedStructureViewBuilder() {
                    override fun createStructureViewModel(editor: com.intellij.openapi.editor.Editor?): StructureViewModel {
                        return EcrStructureViewModel(psiFile, editor)
                    }
                }
                ecrBuilder.createStructureView(fileEditor, project)?.let { view ->
                    descriptors.add(StructureViewComposite.StructureViewDescriptor("ECR", view, AllIcons.Nodes.Tag))
                }

                // 2. HTML section
                val viewProvider = psiFile.viewProvider
                val htmlFile = viewProvider.getPsi(HTMLLanguage.INSTANCE)
                if (htmlFile != null) {
                    val htmlBuilder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(htmlFile)
                    if (htmlBuilder != null) {
                        htmlBuilder.createStructureView(fileEditor, project)?.let { view ->
                            descriptors.add(StructureViewComposite.StructureViewDescriptor("HTML", view, AllIcons.FileTypes.Html))
                        }
                    }
                }

                // 3. Crystal section (instance variables)
                val crystalBuilder = object : TreeBasedStructureViewBuilder() {
                    override fun createStructureViewModel(editor: com.intellij.openapi.editor.Editor?): StructureViewModel {
                        return CrystalInstanceVariablesStructureViewModel(psiFile, editor)
                    }
                }
                crystalBuilder.createStructureView(fileEditor, project)?.let { view ->
                    descriptors.add(StructureViewComposite.StructureViewDescriptor("Crystal", view, AllIcons.Nodes.Variable))
                }

                if (descriptors.isEmpty()) {
                    val ecrBuilder = object : TreeBasedStructureViewBuilder() {
                        override fun createStructureViewModel(editor: com.intellij.openapi.editor.Editor?): StructureViewModel {
                            return EcrStructureViewModel(psiFile, editor)
                        }
                    }
                    return ecrBuilder.createStructureView(fileEditor, project)!!
                }
                return StructureViewComposite(*descriptors.toTypedArray())
            }
        }
    }
}