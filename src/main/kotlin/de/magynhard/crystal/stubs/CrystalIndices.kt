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

/**
 * Index that maps class/module/struct/enum names to their method definitions.
 * This allows O(1) lookup of "all methods in class X" instead of scanning
 * the entire method index and filtering by enclosing class.
 */
class CrystalMethodByClassIndex : StringStubIndexExtension<CrystalMethodDefinition>() {
    override fun getKey(): StubIndexKey<String, CrystalMethodDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalMethodDefinition> =
            StubIndexKey.createIndexKey("crystal.method.by.class.index")
    }
}

class CrystalMacroIndex : StringStubIndexExtension<CrystalMacroDefinition>() {
    override fun getKey(): StubIndexKey<String, CrystalMacroDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, CrystalMacroDefinition> =
            StubIndexKey.createIndexKey("crystal.macro.index")
    }
}
