package de.magynhard.crystal.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.inspections.CrystalExpressionTypeResolver
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

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
        val receiver = findReceiver(dot) ?: return CompletionReceiver.Unknown
        if (hasIncompleteGrouping(dot) || containsRejectedReceiver(receiver)) {
            return CompletionReceiver.Unknown
        }

        val normalized = CrystalReceiverExpression.normalize(receiver)
        val exactTypeRoot = CrystalReceiverExpression.extractExactConstantTypeRoot(normalized)
        if (exactTypeRoot != null) return resolveTypeObject(exactTypeRoot, normalized)

        return when (normalized) {
            is CrystalVariableReference,
            is CrystalInstanceVarAccess -> resolveVariableValue(normalized)
            else -> CrystalExpressionTypeResolver.resolveType(normalized)?.toValueTypes()
                ?: CompletionReceiver.Unknown
        }
    }

    private fun findCompletionDot(position: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.prevLeaf(position)
        while (leaf is PsiWhiteSpace || leaf?.node?.elementType == CrystalTypes.NEWLINE) {
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return leaf?.takeIf { it.node.elementType == CrystalTypes.DOT }
    }

    private fun findReceiver(dot: PsiElement): PsiElement? {
        val access = dot.parent as? CrystalDotCallAccess ?: return null
        var receiver = previousSignificantSibling(access) ?: return null
        var current = receiver
        while (current.parent !is PsiFile && current.parent.textRange.endOffset <= dot.textRange.startOffset) {
            current = current.parent
            if (isRecognizedReceiver(current)) receiver = current
        }
        return receiver.takeIf(::isRecognizedReceiver)
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling is PsiWhiteSpace || sibling?.node?.elementType == CrystalTypes.NEWLINE) {
            sibling = sibling.prevSibling
        }
        return sibling
    }

    private fun isRecognizedReceiver(element: PsiElement): Boolean =
        element is CrystalExpression ||
            element is CrystalGroupedExpression ||
            element is CrystalMethodCallExpression ||
            element is CrystalVariableReference ||
            element is CrystalInstanceVarAccess ||
            element is CrystalNamespaceAccess ||
            element is CrystalArrayLiteral ||
            element is CrystalHashLiteral ||
            element is CrystalTupleLiteral ||
            element is CrystalIfStatement ||
            element is CrystalCaseStatement ||
            element is CrystalStringExpression ||
            element.node.elementType in setOf(
                CrystalTypes.INTEGER_LITERAL,
                CrystalTypes.FLOAT_LITERAL,
                CrystalTypes.STRING_LITERAL,
                CrystalTypes.CHAR_LITERAL,
                CrystalTypes.SYMBOL_LITERAL,
                CrystalTypes.TRUE,
                CrystalTypes.FALSE,
                CrystalTypes.NIL
            )

    private fun hasIncompleteGrouping(dot: PsiElement): Boolean {
        var current: PsiElement? = dot.parent
        while (current != null && current !is PsiFile) {
            if (current is CrystalGroupedExpression &&
                current.node.findChildByType(CrystalTypes.RPAREN) == null) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun containsRejectedReceiver(receiver: PsiElement): Boolean =
        receiver is CrystalAssignment ||
            PsiTreeUtil.findChildOfType(receiver, CrystalAssignment::class.java) != null ||
            receiver is CrystalMacroInterpolation ||
            PsiTreeUtil.findChildOfType(receiver, CrystalMacroInterpolation::class.java) != null

    private fun resolveTypeObject(typeRoot: String, context: PsiElement): CompletionReceiver {
        val normalizedRoot = typeRoot.removePrefix("::")
        val simpleName = normalizedRoot.substringAfterLast("::")
        val explicitIdentity = typeRoot.startsWith("::") || normalizedRoot.contains("::")
        val identities = CrystalIndexService.findTypes(
            simpleName,
            context.project,
            GlobalSearchScope.allScope(context.project)
        ).asSequence()
            .mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { !explicitIdentity || it == normalizedRoot }
            .distinct()
            .toList()

        return if (identities.size == 1) {
            CompletionReceiver.TypeObject(simpleName, identities.single(), explicitIdentity)
        } else {
            CompletionReceiver.Unknown
        }
    }

    private fun resolveVariableValue(receiver: PsiElement): CompletionReceiver {
        val typeName = CrystalTypeInference.inferType(receiver.text, receiver, receiver.project)
            ?: return CompletionReceiver.Unknown
        return typeName.toValueTypes()
    }

    private fun CrystalExpressionTypeResolver.ResolvedType.toValueTypes(): CompletionReceiver =
        typeName.toValueTypes()

    private fun String.toValueTypes(): CompletionReceiver {
        val types = normalizeLookupTypes(this)
        return if (types.isEmpty()) CompletionReceiver.Unknown else CompletionReceiver.ValueTypes(types)
    }
}
