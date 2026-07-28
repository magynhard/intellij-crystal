package de.magynhard.crystal.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalPostfixChain
import de.magynhard.crystal.psi.*

internal sealed interface CompletionReceiver {
    data class TypeObject(
        val simpleName: String,
        val qualifiedName: String,
        val explicitIdentity: Boolean
    ) : CompletionReceiver

    data class ValueTypes(val typeNames: List<String>) : CompletionReceiver
    data object Unknown : CompletionReceiver
}

internal object CrystalCompletionReceiverResolver {
    fun resolve(position: PsiElement): CompletionReceiver {
        val dot = findCompletionDot(position) ?: return CompletionReceiver.Unknown
        if (hasIncompleteGrouping(dot)) return CompletionReceiver.Unknown
        val expression = PsiTreeUtil.getParentOfType(dot, CrystalExpression::class.java, false)
            ?: return CompletionReceiver.Unknown
        return resolveExpression(
            expression,
            CrystalTypeSetResolver.session(position),
            dot.textRange.startOffset
        )
    }

    private fun resolveExpression(
        expression: CrystalExpression,
        session: CrystalTypeResolutionSession,
        completionDotOffset: Int? = null
    ): CompletionReceiver {
        val children = directSignificantChildren(expression).filter { child ->
            when {
                completionDotOffset == null -> true
                child.textRange.endOffset <= completionDotOffset -> true
                !child.textRange.containsOffset(completionDotOffset) -> false
                child is CrystalDotCallAccess -> CrystalPostfixChain.dotOffset(child) < completionDotOffset
                else -> false
            }
        }
        if (children.isEmpty()) return CompletionReceiver.Unknown

        val firstAccess = children.indexOfFirst { it is CrystalDotCallAccess }
        if (firstAccess < 0) {
            resolveExactTypeRoot(children, expression, session)?.let { return it }
            val receiver = if (completionDotOffset == null) expression else children.singleOrNull()
                ?: return CompletionReceiver.Unknown
            return resolveElement(receiver, session)
        }

        var receiver = resolveBase(children.take(firstAccess), session)
        for (element in children.drop(firstAccess)) {
            val access = element as? CrystalDotCallAccess ?: return CompletionReceiver.Unknown
            for (component in CrystalPostfixChain.components(access, completionDotOffset)) {
                if (component == null) return CompletionReceiver.Unknown
                receiver = resolveCompletedCall(receiver, methodName(component), component, session)
                if (receiver == CompletionReceiver.Unknown) return receiver
            }
        }
        return receiver
    }

    private fun resolveBase(
        elements: List<PsiElement>,
        session: CrystalTypeResolutionSession
    ): CompletionReceiver {
        if (elements.isEmpty()) return CompletionReceiver.Unknown
        resolveExactTypeRoot(elements, elements.last(), session)?.let { return it }
        return elements.singleOrNull()?.let { resolveElement(it, session) } ?: CompletionReceiver.Unknown
    }

    private fun resolveElement(
        element: PsiElement,
        session: CrystalTypeResolutionSession
    ): CompletionReceiver {
        val normalized = CrystalReceiverExpression.normalize(element)
        if (isRejectedReceiver(normalized)) return CompletionReceiver.Unknown
        CrystalReceiverExpression.extractExactConstantTypeRoot(normalized)?.let {
            return resolveTypeObject(it, normalized, session)
        }
        return session.resolve(normalized).toCompletionReceiver()
    }

    private fun resolveExactTypeRoot(
        elements: List<PsiElement>,
        context: PsiElement,
        session: CrystalTypeResolutionSession
    ): CompletionReceiver.TypeObject? {
        val callArgs = elements.lastOrNull() as? CrystalCallArgs
        val pathElements = if (callArgs == null) elements else elements.dropLast(1)
        val pathRoot = pathElements.lastOrNull()?.let(CrystalReceiverExpression::extractExactConstantTypeRoot)
            ?: return null
        if (callArgs != null) {
            val arguments = callArgs.argumentList?.argumentList.orEmpty()
            if (arguments.isEmpty() || arguments.any { argument ->
                    val argumentExpression = argument.expression ?: return@any true
                    CrystalReceiverExpression.extractExactConstantTypeRoot(argumentExpression) == null
                }) {
                return null
            }
        }
        return resolveTypeObject(pathRoot, context, session) as? CompletionReceiver.TypeObject
    }

    private fun resolveCompletedCall(
        receiver: CompletionReceiver,
        methodName: String?,
        context: PsiElement,
        session: CrystalTypeResolutionSession
    ): CompletionReceiver {
        methodName ?: return CompletionReceiver.Unknown
        if (receiver is CompletionReceiver.TypeObject && methodName == "new") {
            if (!session.isConstructible(receiver.qualifiedName, context)) return CompletionReceiver.Unknown
            val typeName = if (receiver.explicitIdentity) receiver.qualifiedName else receiver.simpleName
            return CompletionReceiver.ValueTypes(listOf(typeName))
        }

        val result = session.resolveCall(
            when (receiver) {
                is CompletionReceiver.TypeObject -> listOf(receiver.qualifiedName)
                is CompletionReceiver.ValueTypes -> receiver.typeNames
                CompletionReceiver.Unknown -> return CompletionReceiver.Unknown
            },
            methodName,
            receiver is CompletionReceiver.TypeObject,
            context
        )
        return result.toCompletionReceiver()
    }

    private fun resolveTypeObject(
        typeRoot: String,
        context: PsiElement,
        session: CrystalTypeResolutionSession
    ): CompletionReceiver {
        val normalizedRoot = typeRoot.removePrefix("::")
        val explicitIdentity = typeRoot.startsWith("::") || normalizedRoot.contains("::")
        val identity = session.resolveType(typeRoot, context) ?: return CompletionReceiver.Unknown
        return CompletionReceiver.TypeObject(identity.simpleName, identity.qualifiedName, explicitIdentity)
    }

    private fun methodName(call: PsiElement): String? {
        var foundDot = false
        for (child in call.node.getChildren(null)) {
            if (child.elementType == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && child.psi !is PsiWhiteSpace) {
                return child.text
            }
        }
        return null
    }

    private fun findCompletionDot(position: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.prevLeaf(position)
        while (leaf is PsiWhiteSpace || leaf?.node?.elementType == CrystalTypes.NEWLINE) {
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return leaf?.takeIf { it.node.elementType == CrystalTypes.DOT }
    }

    private fun hasIncompleteGrouping(dot: PsiElement): Boolean {
        var current: PsiElement? = dot.parent
        while (current != null) {
            if (current is CrystalGroupedExpression &&
                current.node.findChildByType(CrystalTypes.RPAREN) == null) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun isRejectedReceiver(receiver: PsiElement): Boolean =
        receiver is CrystalAssignment ||
            receiver is CrystalGroupedExpression ||
            receiver is CrystalMacroInterpolation ||
            PsiTreeUtil.findChildOfType(receiver, CrystalMacroInterpolation::class.java) != null

    private fun directSignificantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot {
            it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE
        }

    private fun List<String>.toValueTypes(): CompletionReceiver =
        if (isEmpty()) CompletionReceiver.Unknown else CompletionReceiver.ValueTypes(this)

    private fun CrystalTypeResolution.toCompletionReceiver(): CompletionReceiver = when (this) {
        is CrystalTypeResolution.Known -> types.flatMap { normalizeLookupTypes(it.name) }
            .distinct().toValueTypes()
        CrystalTypeResolution.Unknown -> CompletionReceiver.Unknown
    }
}
