package de.magynhard.crystal.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import de.magynhard.crystal.psi.*

// ==================== Stub Interfaces ====================

interface CrystalNamedStub {
    val name: String?
}

// ==================== Class Stub ====================

class CrystalClassDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalClassDefinition>(parent, elementType), CrystalNamedStub

// ==================== Module Stub ====================

class CrystalModuleDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalModuleDefinition>(parent, elementType), CrystalNamedStub

// ==================== Struct Stub ====================

class CrystalStructDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalStructDefinition>(parent, elementType), CrystalNamedStub

// ==================== Enum Stub ====================

class CrystalEnumDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalEnumDefinition>(parent, elementType), CrystalNamedStub

// ==================== Method Stub ====================

class CrystalMethodDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalMethodDefinition>(parent, elementType), CrystalNamedStub

// ==================== Macro Stub ====================

class CrystalMacroDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalMacroDefinition>(parent, elementType), CrystalNamedStub
