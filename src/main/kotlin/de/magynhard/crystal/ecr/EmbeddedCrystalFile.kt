package de.magynhard.crystal.ecr

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class EmbeddedCrystalFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, EmbeddedCrystalLanguage) {
    override fun getFileType(): EmbeddedCrystalFileType = EmbeddedCrystalFileType
}
