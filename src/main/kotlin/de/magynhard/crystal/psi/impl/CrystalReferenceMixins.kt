package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.*

/**
 * Mixin classes for PSI elements that need Go to Definition support.
 * These override getReference() to provide CrystalReference instances
 * that resolve identifiers to their definitions via StubIndex.
 */

private fun createCrystalReference(element: ASTWrapperPsiElement): CrystalReference? {
    // Skip if part of a definition name
    val parent = element.parent
    if (parent is CrystalNamedElement) return null
    val grandParent = parent?.parent
    if (grandParent is CrystalNamedElement) return null

    val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
        ?: element.node.findChildByType(CrystalTypes.CONSTANT)
        ?: return null

    val name = identNode.text
    if (name.isBlank()) return null

    val startOffset = identNode.startOffset - element.node.startOffset
    return CrystalReference(element, name, startOffset, identNode.textLength)
}

/**
 * Mixin for variable_reference PSI elements.
 *
 * Implements PsiNameIdentifierOwner so that CrystalReference.resolve() returns
 * a PsiNameIdentifierOwner composite (not just the IDENTIFIER leaf). This enables
 * IntelliJ's TargetElementUtil to resolve PSI_ELEMENT to a renameable element.
 * Without this, resolve() returns IDENTIFIER leaves which are NOT PsiNameIdentifierOwner,
 * causing MemberInplaceRenameHandler to fail its `element instanceof PsiNameIdentifierOwner`
 * check — and TokenInplaceRenameHandler steps aside because a custom renamePsiElementProcessor
 * is registered — leaving rename completely grayed out.
 *
 * getNameIdentifier() returns the IDENTIFIER or CONSTANT leaf child.
 */
abstract class CrystalVariableReferenceMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            ?: node.findChildByType(CrystalTypes.CONSTANT)?.psi
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val ident = nameIdentifier ?: return this
        val tokenType = ident.node.elementType
        val bareName = name.removePrefix("@").removePrefix("@")
        val fixedName = when (tokenType) {
            CrystalTypes.INSTANCE_VAR -> "@$bareName"
            CrystalTypes.CLASS_VAR -> "@@$bareName"
            else -> bareName
        }
        val newNode = createLeafFromText(project, fixedName, tokenType) ?: return this
        ident.node.treeParent.replaceChild(ident.node, newNode)
        return this
    }
}

abstract class CrystalMethodCallExpressionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

abstract class CrystalBareMethodCallExpressionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

abstract class CrystalTypePathMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<PsiReference> = createCrystalTypeReferences(this)
    override fun getReference(): PsiReference? = references.firstOrNull()
}

/**
 * Reference for `type_path` elements (e.g. `IO::FileDescriptor`, `Bytes`, `MatchOptions::DotFiles`).
 *
 * Produces one reference per `::` segment, each resolved by its cumulative qualified name:
 * `IO`, `IO::Class`, `IO::Class::FileDescriptor` are independently navigable, so clicking any
 * segment lands on that segment's definition. This matches RubyMine, where every segment of a
 * qualified constant is a separate navigable reference (rather than selecting the whole path).
 * The reference is suppressed only when this type_path is a definition's own name
 * (e.g. `class Foo` / `alias Bytes` / `module Foo::Bar`), to avoid self-references.
 */
private fun createCrystalTypeReferences(element: ASTWrapperPsiElement): Array<PsiReference> {
    // Suppress the reference only when this type_path is the definition's own name
    // (e.g. `class Foo` / `alias Bytes` / `module Foo::Bar`), so we never create a
    // self-reference. Return-type annotations (`def foo : String`) and supertype
    // clauses (`class Foo < Bar`) keep their reference and navigate normally.
    val parent = element.parent
    if (parent is CrystalNamedElement) return PsiReference.EMPTY_ARRAY

    val text = element.text
    if (text.isBlank()) return PsiReference.EMPTY_ARRAY

    val segments = text.split("::")
    if (segments.size == 1) {
        return arrayOf(CrystalReference(element, text, 0, text.length))
    }
    // One reference per segment, keyed by the cumulative qualified name up to that segment.
    val refs = ArrayList<PsiReference>(segments.size)
    var offset = 0
    for (i in segments.indices) {
        val seg = segments[i]
        val cumulative = segments.subList(0, i + 1).joinToString("::")
        refs.add(CrystalReference(element, cumulative, offset, seg.length))
        offset += seg.length + "::".length
    }
    return refs.toTypedArray()
}
