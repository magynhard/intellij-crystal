package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.stubs.*

// ==================== Helper ====================

private fun findNameIdentifierInTypeName(element: PsiElement): PsiElement? {
    // type_name ::= CONSTANT (DOUBLE_COLON CONSTANT)*
    // We want the last CONSTANT token (the simple name)
    val typeName = element.node.findChildByType(CrystalTypes.TYPE_NAME) ?: return null
    var lastConstant: PsiElement? = null
    var child = typeName.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.CONSTANT) {
            lastConstant = child.psi
        }
        child = child.treeNext
    }
    return lastConstant
}

private fun findNameIdentifierInMethodName(element: PsiElement): PsiElement? {
    // method_name ::= IDENTIFIER | CONSTANT | operator_method_name | SELF DOT (IDENTIFIER | CONSTANT | ...)
    val methodName = element.node.findChildByType(CrystalTypes.METHOD_NAME) ?: return null
    var child = methodName.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.IDENTIFIER || child.elementType == CrystalTypes.CONSTANT) {
            return child.psi
        }
        child = child.treeNext
    }
    return null
}

private fun getNameFromTypeName(element: PsiElement): String? {
    return findNameIdentifierInTypeName(element)?.text
}

private fun getNameFromMethodName(element: PsiElement): String? {
    return findNameIdentifierInMethodName(element)?.text
}

private fun setNameOnIdentifier(nameIdentifier: PsiElement?, name: String): PsiElement? {
    if (nameIdentifier == null) return null
    val factory = com.intellij.psi.PsiFileFactory.getInstance(nameIdentifier.project)
    val dummyFile = factory.createFileFromText(
        "dummy.cr",
        de.magynhard.crystal.CrystalLanguage,
        name
    )
    val newNode = dummyFile.node.firstChildNode ?: return null
    nameIdentifier.node.treeParent.replaceChild(nameIdentifier.node, newNode)
    return newNode.psi
}

// ==================== Type Definitions ====================

abstract class CrystalStubbedClassDefinitionImpl : StubBasedPsiElementBase<CrystalClassDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalClassDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedModuleDefinitionImpl : StubBasedPsiElementBase<CrystalModuleDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalModuleDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedStructDefinitionImpl : StubBasedPsiElementBase<CrystalStructDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalStructDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedEnumDefinitionImpl : StubBasedPsiElementBase<CrystalEnumDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalEnumDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

// ==================== Method / Macro ====================

abstract class CrystalStubbedMethodDefinitionImpl : StubBasedPsiElementBase<CrystalMethodDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMethodDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)
    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedMacroDefinitionImpl : StubBasedPsiElementBase<CrystalMacroDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMacroDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)
    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}
