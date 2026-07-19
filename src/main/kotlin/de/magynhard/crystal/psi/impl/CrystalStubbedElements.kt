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
    // type_name is now private (inlined), CONSTANT tokens are direct children of the definition node.
    // We want the last CONSTANT token (the simple name, e.g. the last part of Foo::Bar)
    var lastConstant: PsiElement? = null
    var child = element.node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.CONSTANT) {
            lastConstant = child.psi
        }
        child = child.treeNext
    }
    return lastConstant
}

private fun findNameIdentifierInMethodName(element: PsiElement): PsiElement? {
    // method_name is now private (inlined), IDENTIFIER/CONSTANT tokens are direct children of the definition node.
    // We want the first IDENTIFIER or CONSTANT token (the method name).
    // DEF, SELF, DOT, and whitespace tokens are skipped.
    // Operator methods (def self.+) and keyword methods (def self.require) have
    // no single IDENTIFIER/CONSTANT leaf — they are composed in the fallback
    // path of `getNameFromMethodName`. Returning null here is correct for them
    // (and preserves the existing behaviour of operator method naming).
    var child = element.node.firstChildNode
    while (child != null) {
        val type = child.elementType
        if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
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
    // Primary path: regular method names have an IDENTIFIER/CONSTANT leaf.
    //   def kung, def self.tanzen, def self.Build
    val identifier = findNameIdentifierInMethodName(element)
    if (identifier != null) return identifier.text

    // Fallback: operator methods (def self.+, def self.[]) and keyword methods
    // (def self.require, def self.class) have no single IDENTIFIER/CONSTANT
    // leaf. Compose from the header tokens, but STOP at the parameter list
    // (LPAREN) and method body — previously the loop walked the entire node,
    // producing "def require(path)\nend" for `def self.require` (the body
    // source got included in the name). Skip DEF, SELF, DOT, and whitespace
    // tokens (none of them are part of the method name).
    val sb = StringBuilder()
    var child = element.node.firstChildNode
    while (child != null) {
        val type = child.elementType
        if (type == CrystalTypes.LPAREN || type == CrystalTypes.METHOD_BODY) break
        if (type != CrystalTypes.DEF && type != CrystalTypes.SELF && type != CrystalTypes.DOT &&
            type != com.intellij.psi.TokenType.WHITE_SPACE
        ) {
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
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedModuleDefinitionImpl : StubBasedPsiElementBase<CrystalModuleDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalModuleDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedStructDefinitionImpl : StubBasedPsiElementBase<CrystalStructDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalStructDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedEnumDefinitionImpl : StubBasedPsiElementBase<CrystalEnumDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalEnumDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

// ==================== Method / Macro ====================

abstract class CrystalStubbedMethodDefinitionImpl : StubBasedPsiElementBase<CrystalMethodDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMethodDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)
    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedMacroDefinitionImpl : StubBasedPsiElementBase<CrystalMacroDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMacroDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)
    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

// ==================== Lib / Annotation / Alias / Constant ====================

abstract class CrystalStubbedLibDefinitionImpl : StubBasedPsiElementBase<CrystalLibDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalLibDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedAnnotationDefinitionImpl : StubBasedPsiElementBase<CrystalAnnotationDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalAnnotationDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}

abstract class CrystalStubbedAliasDefinitionImpl : StubBasedPsiElementBase<CrystalAliasDefinitionStub>, CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalAliasDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)
    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset
    override fun setName(name: String): PsiElement { setNameOnIdentifier(nameIdentifier, name); return this }
}
