package de.magynhard.crystal.ecr

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.tree.IElementType
import com.intellij.lang.html.HTMLLanguage

class EmbeddedCrystalFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled),
    TemplateLanguageFileViewProvider {

    private val templateDataLanguage: Language = defaultDataLanguage(file.name)

    override fun getBaseLanguage(): Language = EmbeddedCrystalLanguage
    override fun getTemplateDataLanguage(): Language = templateDataLanguage

    override fun getContentElementType(language: Language): IElementType? {
        return if (language == templateDataLanguage) {
            EmbeddedCrystalTemplateDataElementType
        } else null
    }

    override fun getLanguages(): Set<Language> =
        setOf(EmbeddedCrystalLanguage, templateDataLanguage)

    override fun createPsiFileImpl(language: Language): PsiFileImpl {
        return when (language) {
            EmbeddedCrystalLanguage -> EmbeddedCrystalFile(this)
            templateDataLanguage -> EcrHtmlFile(this)
            else -> throw IllegalArgumentException("Unsupported language: $language")
        }
    }

    override fun cloneInner(file: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        EmbeddedCrystalFileViewProvider(manager, file, false)

    companion object {
        fun defaultDataLanguage(name: String): Language = HTMLLanguage.INSTANCE
    }
}
