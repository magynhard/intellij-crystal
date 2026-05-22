package de.magynhard.crystal.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalClassIndex : StringStubIndexExtension<CrystalClassDefinition>() {
    override fun getKey(): StubIndexKey<String, CrystalClassDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalClassDefinition> =
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
