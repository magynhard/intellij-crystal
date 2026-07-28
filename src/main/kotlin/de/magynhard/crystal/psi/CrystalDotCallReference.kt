package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver

/** Resolves a DOT-call method name through neutral scoped receiver and hierarchy analysis. */
class CrystalDotCallReference(
    element: PsiElement,
    private val methodName: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    private val receiverInfo: ReceiverInfo? by lazy { findReceiver() }

    override fun resolve(): PsiElement? {
        val info = receiverInfo ?: return null
        val session = CrystalTypeSetResolver.session(element)
        val identity = if (info.isStatic) {
            session.resolveType(info.qualifiedName ?: info.rawName, element)
        } else {
            val known = session.resolve(info.receiver) as? CrystalTypeResolution.Known ?: return null
            if (known.types.size != 1) return null
            session.resolveType(known.types.single().name, element)
        }

        if (methodName == "new" && info.isStatic) {
            return when (val constructor = session.resolveConstructor(
                info.qualifiedName ?: info.rawName,
                element
            )) {
                is CrystalConstructorResolution.Methods -> constructor.methods.singleOrNull()
                is CrystalConstructorResolution.Record -> constructor.recordDefinition
                is CrystalConstructorResolution.Implicit,
                is CrystalConstructorResolution.Abstract,
                is CrystalConstructorResolution.Unavailable,
                is CrystalConstructorResolution.Incomplete -> null
            }
        }

        identity ?: return null
        val methods = session.collectNamedMethods(
            identity,
            if (info.isStatic) CrystalReceiverMode.STATIC else CrystalReceiverMode.INSTANCE,
            methodName
        )
        return methods.methods.singleOrNull().takeIf { methods.complete }
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: return element
        val newLeaf = createLeafFromText(element.project, newElementName, identNode.elementType)
            ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun findReceiver(): ReceiverInfo? {
        var previous = element.prevSibling
        while (previous is PsiWhiteSpace || previous?.node?.elementType == CrystalTypes.NEWLINE) {
            previous = previous.prevSibling
        }
        previous ?: return null
        if (previous is CrystalNamespaceAccess) return buildNamespaceReceiver(previous)
        if (previous.node.elementType == CrystalTypes.CONSTANT) {
            return ReceiverInfo(previous, isStatic = true, rawName = previous.text)
        }
        previous.node.findChildByType(CrystalTypes.CONSTANT)?.let {
            return ReceiverInfo(previous, isStatic = true, rawName = it.text)
        }
        if (previous.node.elementType == CrystalTypes.IDENTIFIER ||
            previous.node.findChildByType(CrystalTypes.IDENTIFIER) != null) {
            return ReceiverInfo(previous, isStatic = false, rawName = previous.text)
        }
        return null
    }

    private fun buildNamespaceReceiver(namespaceAccess: CrystalNamespaceAccess): ReceiverInfo {
        val pathParts = mutableListOf<String>()
        namespaceAccess.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }
        var current = namespaceAccess.prevSibling
        while (current != null) {
            when {
                current is PsiWhiteSpace || current.node.elementType == CrystalTypes.NEWLINE -> current = current.prevSibling
                current is CrystalNamespaceAccess -> {
                    current.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    current.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }
                    break
                }
                else -> break
            }
        }
        val simpleName = pathParts.last()
        return ReceiverInfo(
            namespaceAccess,
            isStatic = true,
            rawName = simpleName,
            qualifiedName = pathParts.joinToString("::")
        )
    }

    private data class ReceiverInfo(
        val receiver: PsiElement,
        val isStatic: Boolean,
        val rawName: String,
        val qualifiedName: String? = null
    )
}
