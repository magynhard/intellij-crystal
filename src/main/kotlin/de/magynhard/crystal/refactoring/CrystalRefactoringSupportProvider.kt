package de.magynhard.crystal.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Refactoring support provider for Crystal.
 *
 * Controls which elements can be renamed via MemberInplaceRenameHandler.
 * IntelliJ's MemberInplaceRenameHandler checks two things in sequence:
 * 1. element instanceof PsiNameIdentifierOwner — must be true
 * 2. provider.isMemberInplaceRenameAvailable(element, nameIdentifier) — must return true
 *
 * Elements that implement PsiNameIdentifierOwner via their BNF mixins:
 * - CrystalNamedElement subtypes (CrystalMethodDefinition, CrystalClassDefinition, etc.)
 * - CrystalInstanceVarAccess / CrystalClassVarAccess (via their mixins)
 * - CrystalVariableReference (via mixin — for local variables)
 * - CrystalParameter (via mixin — for method parameters)
 * - CrystalAssignment (via mixin — for local variable assignments)
 *
 * Elements that DON'T implement PsiNameIdentifierOwner fall back to
 * TokenInplaceRenameHandler (token-based rename, the old behavior).
 */
class CrystalRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        // Composites that implement PsiNameIdentifierOwner via their BNF mixins
        if (element is CrystalNamedElement || element is CrystalInstanceVarAccess) return true

        // Accessor-macro name arguments (`property foo` / `property? enabled` …
        // | class_ family): the arg is the declaration via
        // CrystalAccessorArgumentMixin (PsiNameIdentifierOwner). `foo` expands
        // the whole chain — reader/setter methods, @foo/@foo=, the initializer
        // storage shortcut, and the call sites.
        if (element is PsiNameIdentifierOwner && element.nameIdentifier != null) return true

        // Also accept the raw element types that our mixins attach to
        val tokenType = element.node?.elementType
        return tokenType == CrystalTypes.IDENTIFIER ||
            tokenType == CrystalTypes.CONSTANT ||
            tokenType == CrystalTypes.INSTANCE_VAR ||
            tokenType == CrystalTypes.CLASS_VAR
    }
}
