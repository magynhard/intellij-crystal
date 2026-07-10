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
        dataStream.writeName(stub.enclosingNamespace)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalClassDefinitionStub {
        val name = dataStream.readNameString()
        val enclosingNamespace = if (dataStream.available() > 0) dataStream.readNameString() else null
        return CrystalClassDefinitionStub(parentStub, this, name, enclosingNamespace)
    }

    override fun createStub(psi: CrystalClassDefinition, parentStub: StubElement<out PsiElement>?): CrystalClassDefinitionStub {
        return CrystalClassDefinitionStub(parentStub, this, psi.name, extractEnclosingNamespace(psi))
    }

    override fun createPsi(stub: CrystalClassDefinitionStub): CrystalClassDefinition {
        return CrystalClassDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalClassDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }

        // Index by enclosing class/module/struct/enum name for hierarchical completion.
        // Two sources: (1) parent stub tree (nested classes), (2) qualified type_name prefix (Foo::Bar).
        val enclosingName = findEnclosingParentName(stub) ?: stub.enclosingNamespace
        enclosingName?.let { sink.occurrence(CrystalClassByEnclosingIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalModuleDefinitionElementType(debugName: String) :
    IStubElementType<CrystalModuleDefinitionStub, CrystalModuleDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.MODULE_DEFINITION"

    override fun serialize(stub: CrystalModuleDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.enclosingNamespace)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalModuleDefinitionStub {
        val name = dataStream.readNameString()
        val enclosingNamespace = if (dataStream.available() > 0) dataStream.readNameString() else null
        return CrystalModuleDefinitionStub(parentStub, this, name, enclosingNamespace)
    }

    override fun createStub(psi: CrystalModuleDefinition, parentStub: StubElement<out PsiElement>?): CrystalModuleDefinitionStub {
        return CrystalModuleDefinitionStub(parentStub, this, psi.name, extractEnclosingNamespace(psi))
    }

    override fun createPsi(stub: CrystalModuleDefinitionStub): CrystalModuleDefinition {
        return CrystalModuleDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalModuleDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }

        val enclosingName = findEnclosingParentName(stub) ?: stub.enclosingNamespace
        enclosingName?.let { sink.occurrence(CrystalClassByEnclosingIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalStructDefinitionElementType(debugName: String) :
    IStubElementType<CrystalStructDefinitionStub, CrystalStructDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.STRUCT_DEFINITION"

    override fun serialize(stub: CrystalStructDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.enclosingNamespace)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalStructDefinitionStub {
        val name = dataStream.readNameString()
        val enclosingNamespace = if (dataStream.available() > 0) dataStream.readNameString() else null
        return CrystalStructDefinitionStub(parentStub, this, name, enclosingNamespace)
    }

    override fun createStub(psi: CrystalStructDefinition, parentStub: StubElement<out PsiElement>?): CrystalStructDefinitionStub {
        return CrystalStructDefinitionStub(parentStub, this, psi.name, extractEnclosingNamespace(psi))
    }

    override fun createPsi(stub: CrystalStructDefinitionStub): CrystalStructDefinition {
        return CrystalStructDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalStructDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }

        val enclosingName = findEnclosingParentName(stub) ?: stub.enclosingNamespace
        enclosingName?.let { sink.occurrence(CrystalClassByEnclosingIndex.KEY, it) }
    }

    override fun shouldCreateStub(node: ASTNode?): Boolean = true
}

class CrystalEnumDefinitionElementType(debugName: String) :
    IStubElementType<CrystalEnumDefinitionStub, CrystalEnumDefinition>(debugName, CrystalLanguage) {

    override fun getExternalId(): String = "crystal.ENUM_DEFINITION"

    override fun serialize(stub: CrystalEnumDefinitionStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.enclosingNamespace)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): CrystalEnumDefinitionStub {
        val name = dataStream.readNameString()
        val enclosingNamespace = if (dataStream.available() > 0) dataStream.readNameString() else null
        return CrystalEnumDefinitionStub(parentStub, this, name, enclosingNamespace)
    }

    override fun createStub(psi: CrystalEnumDefinition, parentStub: StubElement<out PsiElement>?): CrystalEnumDefinitionStub {
        return CrystalEnumDefinitionStub(parentStub, this, psi.name, extractEnclosingNamespace(psi))
    }

    override fun createPsi(stub: CrystalEnumDefinitionStub): CrystalEnumDefinition {
        return CrystalEnumDefinitionImpl(stub, this)
    }

    override fun indexStub(stub: CrystalEnumDefinitionStub, sink: IndexSink) {
        stub.name?.let { sink.occurrence(CrystalClassIndex.KEY, it) }

        val enclosingName = findEnclosingParentName(stub) ?: stub.enclosingNamespace
        enclosingName?.let { sink.occurrence(CrystalClassByEnclosingIndex.KEY, it) }
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

        // Also index by enclosing class/module/struct/enum name for O(1) class→methods lookups
        val className = findEnclosingParentName(stub)
        className?.let { sink.occurrence(CrystalMethodByClassIndex.KEY, it) }
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

/**
 * Walks up the stub tree from [stub] to find the name of the immediate enclosing
 * class/module/struct/enum. Returns `null` if no enclosing type is found.
 *
 * Used for indexing: methods and nested types are indexed by their enclosing
 * class name for hierarchical lookups.
 */
private fun findEnclosingParentName(stub: StubElement<*>): String? {
    var parent = stub.parentStub
    while (parent != null) {
        if (parent is com.intellij.psi.stubs.PsiFileStub<*>) break
        val name = (parent as? CrystalNamedStub)?.name
        if (name != null && parent is CrystalClassDefinitionStub) return name
        if (name != null && parent is CrystalModuleDefinitionStub) return name
        if (name != null && parent is CrystalStructDefinitionStub) return name
        if (name != null && parent is CrystalEnumDefinitionStub) return name
        parent = parent.parentStub
    }
    return null
}

/**
 * Extracts the enclosing namespace prefix from a type definition's qualified name.
 * For `class Foo::Bar`, returns "Foo". For `class Baz`, returns null.
 *
 * Works by scanning the PSI children for CONSTANT tokens (type_name is inlined as
 * direct children of the definition node) and returning the first CONSTANT if there
 * are multiple (separated by DOUBLE_COLON).
 */
private fun extractEnclosingNamespace(element: PsiElement): String? {
    val constants = mutableListOf<String>()
    var child = element.node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.CONSTANT) {
            constants.add(child.text)
        }
        // Stop at class_body — CONSTANTS inside the body are not part of the type_name
        if (child.elementType == CrystalTypes.CLASS_BODY) break
        child = child.treeNext
    }
    // If there are multiple CONSTANTS (e.g. Foo::Bar), the first is the enclosing namespace
    return if (constants.size >= 2) constants.first() else null
}
