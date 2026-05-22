package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import de.magynhard.crystal.stubs.*

abstract class CrystalStubbedClassDefinitionImpl : StubBasedPsiElementBase<CrystalClassDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalClassDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class CrystalStubbedModuleDefinitionImpl : StubBasedPsiElementBase<CrystalModuleDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalModuleDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class CrystalStubbedStructDefinitionImpl : StubBasedPsiElementBase<CrystalStructDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalStructDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class CrystalStubbedEnumDefinitionImpl : StubBasedPsiElementBase<CrystalEnumDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalEnumDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class CrystalStubbedMethodDefinitionImpl : StubBasedPsiElementBase<CrystalMethodDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMethodDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class CrystalStubbedMacroDefinitionImpl : StubBasedPsiElementBase<CrystalMacroDefinitionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMacroDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}
