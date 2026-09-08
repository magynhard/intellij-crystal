package de.magynhard.crystal.navigation

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.TokenType
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalClassVarAccess
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypes

/**
 * ReferencesSearch for accessor-macro declarations, covering both rename
 * directions over one implicit usage set:
 *
 * 1. **Forward** (rename `foo` of `property foo`): the coupled
 *    `@foo`/`@@foo` variable accesses (via CrystalInstanceVarFinder, which
 *    also covers the initializer storage shortcut `initialize(@foo : T)`),
 *    reader dot-calls (`obj.foo`) binding exactly this argument, and setter
 *    member assignments (`obj.foo = v`, `obj.foo += v`).
 * 2. **Reverse** (renaming `@foo`/`@@foo`): the coupled accessor argument —
 *    the plain intra-class variable references stay on the existing
 *    CrystalInstanceVarReferencesSearcher — plus the same union.
 *
 * The member-assignment shape binds inline (no call PSI); its name leaf
 * carries a transient CrystalMemberAssignUsageReference. Receiver types are
 * resolved through the shared exact-type machinery — no name-only fallback: a
 * receiver that resolves to another type's same-name member is never matched,
 * and ambiguous receivers stay unmatched (honest). Only single-level
 * receivers participate in setter matching (`obj.foo = v`, `Session.timeout =` …)
 * — receiver chains plus receiver-type inference are follow-up work.
 */
class CrystalAccessorReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = queryParameters.elementToSearch ?: return

        val targetArg = accessorArgComposite(target)
        val arg: PsiElement
        val varName: String
        when {
            targetArg != null -> {
                arg = targetArg
                varName = CrystalAccessorCoupling.coupledVarName(arg) ?: return
            }
            target is CrystalInstanceVarAccess -> {
                arg = CrystalAccessorCoupling.findAccessorArgForVar(target) ?: return
                varName = target.text
            }
            target is CrystalClassVarAccess -> {
                arg = CrystalAccessorCoupling.findAccessorArgForVar(target) ?: return
                varName = target.text
            }
            else -> return
        }
        if (target is CrystalInstanceVarAccess || target is CrystalClassVarAccess) {
            // Reverse direction: the coupled accessor argument joins the
            // rename through a declaration rename reference — the arg /
            // variable identifiers are the SAME symbol.
            consumer.process(CrystalAccessorDeclarationRenameReference(arg))
        }

        // The coupled variable's accesses of the accessor declaration: every
        // @foo / @@foo occurrence inside the type renames with the same bare
        // name, preserving the @-prefix reasons through the existing ivar
        // reference implementations.
        for (access in CrystalInstanceVarFinder.findAllUsages(varName, arg)) {
            if (access === arg) continue
            access.reference?.let(consumer::process)
        }

        val propertyName = CrystalAccessorCoupling.accessorArgName(arg) ?: return
        val typeName = owningTypeName(arg) ?: return
        val session = CrystalTypeSetResolver.session(arg)
        val identity = session.resolveType(typeName, arg) ?: return

        val hitProcessor = com.intellij.psi.search.TextOccurenceProcessor { hit, _ ->
            if (hit.containingFile?.fileType is CrystalFileType) {
                processWordHit(hit, arg, identity, session, consumer)
            }
            true
        }

        PsiSearchHelper.getInstance(arg.project).processElementsWithWord(
            hitProcessor,
            queryParameters.effectiveSearchScope,
            propertyName,
            UsageSearchContext.IN_CODE,
            true,
        )
    }

    /**
     * The argument element passed by the rename flow is the argument
     * composite (PsiNameIdentifierOwner). An ordinary call argument never
     * exposes an accessor name and never participates.
     */
    private fun accessorArgComposite(target: PsiElement): PsiElement? {
        if (target !is PsiNameIdentifierOwner) return null
        val ident = target.nameIdentifier ?: return null
        if (!PsiTreeUtil.isAncestor(target, ident, false)) return null
        val call = PsiTreeUtil.getParentOfType(
            target,
            de.magynhard.crystal.psi.CrystalMethodCallExpression::class.java,
            de.magynhard.crystal.psi.CrystalBareMethodCallExpression::class.java,
        ) ?: return null
        if (!CrystalAccessorCoupling.isAccessorMacroCall(call)) return null
        return target
    }

    private fun processWordHit(
        hit: PsiElement,
        accessorArg: PsiElement,
        identity: CrystalTypeIdentity,
        session: CrystalTypeResolutionSession,
        consumer: Processor<in PsiReference>,
    ) {
        if (hit.node.elementType != CrystalTypes.IDENTIFIER) return

        val dotCall = PsiTreeUtil.getParentOfType(hit, CrystalDotCallAccess::class.java, false)
        if (dotCall != null) {
            // Reader call site — the dot-call resolution must bind exactly
            // this accessor argument.
            val resolution = CrystalDotCallTargetResolver.resolve(dotCall)
            if (resolution is DotCallResolution.Accessor &&
                resolution.accessorArgs.any { it === accessorArg }
            ) {
                dotCall.reference?.let(consumer::process)
            }
            return
        }

        // Setter member assignment: `recv.foo = v` / `recv.foo += v` —
        // IDENTIFIER with DOT before and an assignment operator after.
        val dotNode = hit.node.treePrev ?: return
        if (dotNode.elementType != CrystalTypes.DOT) return
        val operator = nextSignificantNode(hit.node.treeNext) ?: return
        if (!CrystalTokenTypes.ASSIGN_OPS.contains(operator.elementType)) return

        val receiver = previousSignificantPsi(dotNode.psi) ?: return
        if (!receiverTypeMatches(receiver, identity, session, hit)) return

        consumer.process(CrystalMemberAssignUsageReference(hit))
    }

    private fun nextSignificantNode(start: ASTNode?): ASTNode? {
        var current = start
        while (current != null && current.elementType == TokenType.WHITE_SPACE) {
            current = current.treeNext
        }
        return current
    }

    private fun previousSignificantPsi(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null &&
            (current.node?.elementType == TokenType.WHITE_SPACE ||
                current.node?.elementType == CrystalTypes.NEWLINE || current.text.isBlank())
        ) {
            current = current.prevSibling
        }
        return current
    }

    private fun receiverTypeMatches(
        receiver: PsiElement,
        identity: CrystalTypeIdentity,
        session: CrystalTypeResolutionSession,
        context: PsiElement,
    ): Boolean {
        val constantName = receiver.text
        if (constantName.firstOrNull()?.isUpperCase() == true &&
            constantName.all { it.isLetterOrDigit() || it == '_' } == true
        ) {
            // Constant receiver: `Session.timeout = 45`.
            return session.resolveType(constantName, context) == identity
        }
        // Variable/ivar receiver: the exact-type resolution of the receiver
        // expression serves the identity check.
        val resolution = session.resolve(receiver) as? CrystalTypeResolution.Known ?: return false
        return resolution.types.any { type ->
            type.name == identity.qualifiedName || type.name == identity.simpleName
        }
    }

    private fun owningTypeName(element: PsiElement): String? {
        val typeDef = PsiTreeUtil.getParentOfType(
            element,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java,
        ) ?: return null
        return de.magynhard.crystal.psi.CrystalPsiUtils.buildQualifiedName(typeDef)
            ?.substringAfterLast("::")
    }
}
