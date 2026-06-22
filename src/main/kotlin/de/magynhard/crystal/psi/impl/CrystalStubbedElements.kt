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
    // Try to find IDENTIFIER or CONSTANT first (regular method names)
    val identifier = findNameIdentifierInMethodName(element)
    if (identifier != null) return identifier.text

    // For operator methods (e.g. def self.[]), compose name from operator tokens
    // method_name ::= SELF DOT (operator_method_name) — tokens like LBRACKET, RBRACKET, ASSIGN, QUESTION, etc.
    val methodName = element.node.findChildByType(CrystalTypes.METHOD_NAME) ?: return null
    val sb = StringBuilder()
    var child = methodName.firstChildNode
    while (child != null) {
        val type = child.elementType
        // Skip SELF and DOT prefix tokens
        if (type != CrystalTypes.SELF && type != CrystalTypes.DOT) {
            sb.append(child.psi.text)
        }
        child = child.treeNext
    }
    return sb.toString().takeIf { it.isNotEmpty() }
}

private fun setNameOnIdentifier(nameIdentifier: PsiElement?, name: String): PsiElement? {
    if (nameIdentifier == null) return null
    val tokenType = nameIdentifier.node.elementType
    val bareName = name.removePrefix("@").removePrefix("@")
    val fixedName = when (tokenType) {
        CrystalTypes.INSTANCE_VAR -> "@$bareName"
        CrystalTypes.CLASS_VAR -> "@@$bareName"
        else -> bareName
    }
    val newNode = de.magynhard.crystal.psi.createLeafFromText(nameIdentifier.project, fixedName, tokenType) ?: return null
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
