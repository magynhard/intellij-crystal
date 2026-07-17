package de.magynhard.crystal.navigation

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Factory for Crystal's Find Usages and Rename support for definition names.
 *
 * When the user invokes Find Usages (Alt+F7) or Rename (Shift+F6) on a
 * definition element (class, module, struct, enum, method, macro), this factory
 * creates a handler that finds all references to the element.
 *
 * Also handles `.new` DOT-call identifiers: resolves `.new` to the enclosing
 * class's `initialize` method, so Find Usages on `.new` finds all `.new` call
 * sites of that class.
 *
 * Without this factory, the platform falls back to the default handler which
 * can't resolve references from definition names because the CONSTANT/IDENTIFIER
 * leaf inside the definition has no PsiReference.
 */
class CrystalFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    companion object {
        private val IDENTIFIER_OR_CONSTANT = TokenSet.create(
            CrystalTypes.IDENTIFIER,
            CrystalTypes.CONSTANT
        )

        /**
         * Checks if [element] is a `.new` DOT-call identifier (e.g., `Foo.new`).
         * Must be an IDENTIFIER with text "new" preceded by a DOT token.
         */
        fun isDotCallNewIdentifier(element: PsiElement): Boolean {
            if (element.node?.elementType !in IDENTIFIER_OR_CONSTANT) return false
            if (element.text != "new") return false

            // Check if preceded by DOT
            var prev = element.prevSibling
            while (prev != null && (prev is com.intellij.psi.PsiWhiteSpace || prev.node?.elementType == CrystalTokenTypes.WHITE_SPACE)) {
                prev = prev.prevSibling
            }
            return prev?.node?.elementType == CrystalTypes.DOT
        }

        /**
         * Resolves a `.new` DOT-call identifier to its target: `initialize`,
         * `def self.new`, or `record` — following Crystal's constructor
         * resolution order.
         */
        fun resolveNewToInitialize(element: PsiElement): PsiElement? {
            if (!isDotCallNewIdentifier(element)) return null

            // Walk backwards: new → DOT → receiver CONSTANT
            var prev = element.prevSibling
            while (prev != null && (prev is com.intellij.psi.PsiWhiteSpace || prev.node?.elementType == CrystalTokenTypes.WHITE_SPACE)) {
                prev = prev.prevSibling
            }
            if (prev?.node?.elementType != CrystalTypes.DOT) return null

            // DOT is the first child of CrystalDotCallAccess, so prevSibling is null.
            // Cross the composite boundary to find the receiver.
            var receiver = prev.prevSibling
            while (receiver != null && (receiver is com.intellij.psi.PsiWhiteSpace || receiver.node?.elementType == CrystalTokenTypes.WHITE_SPACE)) {
                receiver = receiver.prevSibling
            }
            if (receiver == null) {
                val dotParent = prev.parent
                if (dotParent is de.magynhard.crystal.psi.CrystalDotCallAccess) {
                    receiver = dotParent.prevSibling
                    while (receiver != null && (receiver is com.intellij.psi.PsiWhiteSpace || receiver.node?.elementType == CrystalTokenTypes.WHITE_SPACE)) {
                        receiver = receiver.prevSibling
                    }
                }
            }
            if (receiver == null) return null

            // Only resolve for CONSTANT receivers (class names, not variables)
            val isConstant = receiver.node?.elementType == CrystalTypes.CONSTANT
            val hasConstantChild = receiver.node?.findChildByType(CrystalTypes.CONSTANT) != null
            if (!isConstant && !hasConstantChild) return null

            // Use CrystalDotCallReference's resolution logic via the element's own reference
            val dotCallAccess = element.parent
            val ref = dotCallAccess?.reference ?: element.reference
            return ref?.resolve()
        }
    }

    override fun canFindUsages(element: PsiElement): Boolean {
        if (element is CrystalNamedElement) return true
        return isDotCallNewIdentifier(element)
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        // For .new DOT-call identifiers, resolve to initialize so Find Usages
        // searches for references to initialize (which includes .new usages via
        // CrystalDotCallReference).
        val resolved = resolveNewToInitialize(element)
        return CrystalFindUsagesHandler(resolved ?: element)
    }
}
