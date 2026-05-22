package de.magynhard.crystal

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class CrystalFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CrystalLanguage) {
    override fun getFileType(): FileType = CrystalFileType
    override fun toString(): String = "Crystal File"
}
