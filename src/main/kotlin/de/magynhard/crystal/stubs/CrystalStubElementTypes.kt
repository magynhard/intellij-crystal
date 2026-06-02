package de.magynhard.crystal.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.*
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.psi.impl.*

class CrystalClassDefinitionElementType(debugName: String) :
    IStubElementType<CrystalClassDefinitionStub, CrystalClassDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.CLASS_DEFINITION"

    override fun serialize(stub: CrystalClassDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalClassDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalClassDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalClassDefinition, parentStub: StubElement<out PsiElement>?): CrystalClassDefinitionStub {
        return CrystalClassDefinitionStub(parentStub, this, psi.typeName?.text)
    }

    override fun createPsi(stub: CrystalClassDefinitionStub): CrystalClassDefinition {
        return CrystalClassDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalClassDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalModuleDefinitionElementType(debugName: String) :
    IStubElementType<CrystalModuleDefinitionStub, CrystalModuleDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.MODULE_DEFINITION"

    override fun serialize(stub: CrystalModuleDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalModuleDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalModuleDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalModuleDefinition, parentStub: StubElement<out PsiElement>?): CrystalModuleDefinitionStub {
        return CrystalModuleDefinitionStub(parentStub, this, psi.typeName?.text)
    }

    override fun createPsi(stub: CrystalModuleDefinitionStub): CrystalModuleDefinition {
        return CrystalModuleDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalModuleDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalStructDefinitionElementType(debugName: String) :
    IStubElementType<CrystalStructDefinitionStub, CrystalStructDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.STRUCT_DEFINITION"

    override fun serialize(stub: CrystalStructDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalStructDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalStructDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalStructDefinition, parentStub: StubElement<out PsiElement>?): CrystalStructDefinitionStub {
        return CrystalStructDefinitionStub(parentStub, this, psi.typeName?.text)
    }

    override fun createPsi(stub: CrystalStructDefinitionStub): CrystalStructDefinition {
        return CrystalStructDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalStructDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalEnumDefinitionElementType(debugName: String) :
    IStubElementType<CrystalEnumDefinitionStub, CrystalEnumDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.ENUM_DEFINITION"

    override fun serialize(stub: CrystalEnumDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalEnumDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalEnumDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalEnumDefinition, parentStub: StubElement<out PsiElement>?): CrystalEnumDefinitionStub {
        return CrystalEnumDefinitionStub(parentStub, this, psi.typeName?.text)
    }

    override fun createPsi(stub: CrystalEnumDefinitionStub): CrystalEnumDefinition {
        return CrystalEnumDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalEnumDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalMethodDefinitionElementType(debugName: String) :
    IStubElementType<CrystalMethodDefinitionStub, CrystalMethodDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.METHOD_DEFINITION"

    override fun serialize(stub: CrystalMethodDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalMethodDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalMethodDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalMethodDefinition, parentStub: StubElement<out PsiElement>?): CrystalMethodDefinitionStub {
        return CrystalMethodDefinitionStub(parentStub, this, psi.name)
    }

    override fun createPsi(stub: CrystalMethodDefinitionStub): CrystalMethodDefinition {
        return CrystalMethodDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalMethodDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalMethodIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalMacroDefinitionElementType(debugName: String) :
    IStubElementType<CrystalMacroDefinitionStub, CrystalMacroDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.MACRO_DEFINITION"

    override fun serialize(stub: CrystalMacroDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalMacroDefinitionStub {
        val name = dataStream.readNameString()
        return CrystalMacroDefinitionStub(parentStub, this, name)
    }

    override fun createStub(psi: CrystalMacroDefinition, parentStub: StubElement<out PsiElement>?): CrystalMacroDefinitionStub {
        return CrystalMacroDefinitionStub(parentStub, this, psi.name)
    }

    override fun createPsi(stub: CrystalMacroDefinitionStub): CrystalMacroDefinition {
        return CrystalMacroDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalMacroDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalMacroIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}
