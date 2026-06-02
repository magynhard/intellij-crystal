package de.magynhard.crystal.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import de.magynhard.crystal.psi.CrystalMacroDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalNamedElement

class CrystalClassIndex : StringStubIndexExtension<CrystalNamedElement>() {
    override fun getKey(): StubIndexKey<String, CrystalNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalNamedElement> =
            StubIndexKey.createIndexKey("crystal.class.index")
    }
}

class CrystalMethodIndex : StringStubIndexExtension<CrystalMethodDefinition>() {
    override fun getKey(): StubIndexKey<String, CrystalMethodDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalMethodDefinition> =
            StubIndexKey.createIndexKey("crystal.method.index")
    }
}

class CrystalMacroIndex : StringStubIndexExtension<CrystalMacroDefinition>() {
    override fun getKey(): StubIndexKey<String, CrystalMacroDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalMacroDefinition> =
            StubIndexKey.createIndexKey("crystal.macro.index")
    }
}
