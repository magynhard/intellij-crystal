package de.magynhard.crystal.stubs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.LanguageStubDefinition
import com.intellij.psi.StubBuilder

class CrystalLanguageStubDefinition : LanguageStubDefinition {
    override val stubVersion: Int = 2
    override val builder: StubBuilder = CrystalStubBuilder()

    override fun shouldBuildStubFor(file: VirtualFile): Boolean {
        return file.extension == "cr"
    }
}
