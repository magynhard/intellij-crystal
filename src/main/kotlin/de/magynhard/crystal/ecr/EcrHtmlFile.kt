package de.magynhard.crystal.ecr

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.FileViewProvider
import com.intellij.psi.impl.source.html.HtmlFileImpl

class EcrHtmlFile(viewProvider: FileViewProvider) : HtmlFileImpl(viewProvider, EmbeddedCrystalTemplateDataElementType) {
    override fun getLanguage(): Language = HTMLLanguage.INSTANCE
}
