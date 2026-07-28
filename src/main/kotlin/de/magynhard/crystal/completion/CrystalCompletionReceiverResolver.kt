package de.magynhard.crystal.completion

import com.intellij.psi.PsiElement
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
        if (hasIncompleteGrouping(dot)) return CompletionReceiver.Unknown
        val expression = PsiTreeUtil.getParentOfType(dot, CrystalExpression::class.java, false)
            ?: return CompletionReceiver.Unknown
        return resolveExpression(expression, dot.textRange.startOffset)
    }

    private fun resolveExpression(expression: CrystalExpression, completionDotOffset: Int? = null): CompletionReceiver {
        val children = directSignificantChildren(expression).filter { child ->
            when {
                completionDotOffset == null -> true
                child.textRange.endOffset <= completionDotOffset -> true
                !child.textRange.containsOffset(completionDotOffset) -> false
                child is CrystalDotCallAccess -> dotOffset(child) < completionDotOffset
                else -> false
            }
        }
        if (children.isEmpty()) return CompletionReceiver.Unknown

        val firstAccess = children.indexOfFirst { it is CrystalDotCallAccess }
        if (firstAccess < 0) {
            resolveExactTypeRoot(children, expression)?.let { return it }
            val receiver = if (completionDotOffset == null) expression else children.singleOrNull()
                ?: return CompletionReceiver.Unknown
            return resolveElement(receiver)
        }

        var receiver = resolveBase(children.take(firstAccess))
        for (access in children.drop(firstAccess).filterIsInstance<CrystalDotCallAccess>()) {
            receiver = resolveCompletedCall(receiver, methodName(access), access)
            if (receiver == CompletionReceiver.Unknown) return receiver
            for (implicitCall in trailingImplicitCalls(access, completionDotOffset)) {
                receiver = resolveCompletedCall(receiver, methodName(implicitCall), implicitCall)
                if (receiver == CompletionReceiver.Unknown) return receiver
            }
        }
        return receiver
    }

    private fun resolveBase(elements: List<PsiElement>): CompletionReceiver {
        if (elements.isEmpty()) return CompletionReceiver.Unknown
        resolveExactTypeRoot(elements, elements.last())?.let { return it }
        return elements.singleOrNull()?.let(::resolveElement) ?: CompletionReceiver.Unknown
    }

    private fun resolveElement(element: PsiElement): CompletionReceiver {
        val normalized = CrystalReceiverExpression.normalize(element)
        if (isRejectedReceiver(normalized)) return CompletionReceiver.Unknown
        CrystalReceiverExpression.extractExactConstantTypeRoot(normalized)?.let {
            return resolveTypeObject(it, normalized)
        }
        return when (normalized) {
            is CrystalVariableReference,
            is CrystalInstanceVarAccess -> resolveVariableValue(normalized)
            is CrystalMethodCallExpression -> resolveMethodCall(normalized)
            is CrystalExpression -> {
                if (directSignificantChildren(normalized).any { it is CrystalDotCallAccess }) {
                    resolveExpression(normalized)
                } else {
                    resolveExpressionType(normalized)
                }
            }
            else -> resolveExpressionType(normalized)
        }
    }

    private fun resolveExactTypeRoot(elements: List<PsiElement>, context: PsiElement): CompletionReceiver.TypeObject? {
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
        return resolveTypeObject(pathRoot, context) as? CompletionReceiver.TypeObject
    }

    private fun resolveCompletedCall(
        receiver: CompletionReceiver,
        methodName: String?,
        context: PsiElement
    ): CompletionReceiver {
        methodName ?: return CompletionReceiver.Unknown
        if (receiver is CompletionReceiver.TypeObject && methodName == "new") {
            val typeName = if (receiver.explicitIdentity) receiver.qualifiedName else receiver.simpleName
            return CompletionReceiver.ValueTypes(listOf(typeName))
        }

        val isStatic = receiver is CompletionReceiver.TypeObject
        val receiverTypes = when (receiver) {
            is CompletionReceiver.TypeObject -> listOf(receiver.qualifiedName)
            is CompletionReceiver.ValueTypes -> receiver.typeNames
            CompletionReceiver.Unknown -> return CompletionReceiver.Unknown
        }
        val methods = mutableListOf<CrystalMethodDefinition>()
        for (typeName in receiverTypes) {
            val identity = resolveTypeIdentity(typeName, context) ?: return CompletionReceiver.Unknown
            val candidates = CrystalIndexService.findMethodsByClass(
                identity.simpleName,
                context.project,
                GlobalSearchScope.allScope(context.project)
            ).filter { method ->
                method.name == methodName &&
                    CrystalPsiUtils.isSelfMethod(method) == isStatic &&
                    CrystalPsiUtils.getEnclosingType(method)
                        ?.let(CrystalPsiUtils::buildQualifiedName) == identity.qualifiedName
            }
            if (candidates.isEmpty()) return CompletionReceiver.Unknown
            methods.addAll(candidates)
        }
        return resolveMethodResults(methods)
    }

    private fun resolveMethodCall(call: CrystalMethodCallExpression): CompletionReceiver {
        val methodName = call.node.getChildren(null)
            .firstOrNull { it.elementType == CrystalTypes.IDENTIFIER }
            ?.text
            ?: return resolveExpressionType(call)
        val methods = CrystalIndexService.findMethods(
            methodName,
            call.project,
            GlobalSearchScope.allScope(call.project)
        ).filter { CrystalPsiUtils.getEnclosingType(it) == null && !CrystalPsiUtils.isSelfMethod(it) }
        return if (methods.isEmpty()) resolveExpressionType(call) else resolveMethodResults(methods)
    }

    private fun resolveMethodResults(methods: Collection<CrystalMethodDefinition>): CompletionReceiver {
        val typeNames = mutableListOf<String>()
        for (method in methods) {
            val returnType = CrystalTypeInference.inferReturnTypePreservingUnion(method)
                ?: return CompletionReceiver.Unknown
            typeNames.addAll(normalizeLookupTypes(returnType))
        }
        return typeNames.distinct().toValueTypes()
    }

    private fun resolveVariableValue(receiver: PsiElement): CompletionReceiver {
        val typeName = CrystalTypeInference.inferTypePreservingUnion(
            receiver.text,
            receiver,
            receiver.project
        ) ?: return CompletionReceiver.Unknown
        return normalizeLookupTypes(typeName).toValueTypes()
    }

    private fun resolveExpressionType(expression: PsiElement): CompletionReceiver =
        CrystalExpressionTypeResolver.resolveType(expression)?.typeName
            ?.let(::normalizeLookupTypes)
            ?.toValueTypes()
            ?: CompletionReceiver.Unknown

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

    private fun resolveTypeIdentity(typeName: String, context: PsiElement): TypeIdentity? {
        val normalized = typeName.removePrefix("::")
        val simpleName = normalized.substringAfterLast("::")
        val explicit = normalized.contains("::")
        val identities = CrystalIndexService.findTypes(
            simpleName,
            context.project,
            GlobalSearchScope.allScope(context.project)
        ).asSequence()
            .mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { !explicit || it == normalized }
            .distinct()
            .toList()
        return identities.singleOrNull()?.let { TypeIdentity(simpleName, it) }
    }

    private fun trailingImplicitCalls(
        access: CrystalDotCallAccess,
        completionDotOffset: Int?
    ): List<CrystalImplicitObjectCall> {
        val argument = access.bareArgumentList?.bareArgumentList?.singleOrNull() ?: return emptyList()
        val implicitCall = argument.implicitObjectCallList.singleOrNull() ?: return emptyList()
        if (directSignificantChildren(argument) != listOf(implicitCall)) return emptyList()
        return listOf(implicitCall).filter {
            completionDotOffset == null || dotOffset(it) < completionDotOffset
        }
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

    private fun dotOffset(call: PsiElement): Int =
        call.node.findChildByType(CrystalTypes.DOT)?.startOffset ?: Int.MAX_VALUE

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

    private data class TypeIdentity(val simpleName: String, val qualifiedName: String)
}
