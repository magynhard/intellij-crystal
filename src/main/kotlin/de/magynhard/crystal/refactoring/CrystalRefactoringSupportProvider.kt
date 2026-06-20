package de.magynhard.crystal.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider

/**
 * Refactoring support provider for Crystal.
 *
 * The default RefactoringSupportProvider is sufficient — rename works via
 * PsiElementRenameHandler (dialog-based) for PsiNameIdentifierOwner elements
 * (methods, classes, instance variables via their mixins).
 *
 * Note: isMemberInplaceRenameAvailable is NOT overridden. The IntelliJ
 * MemberInplaceRenameHandler checks `element instanceof PsiNameIdentifierOwner`
 * BEFORE calling isMemberInplaceRenameAvailable (decompiled from
 * MemberInplaceRenameHandler.isAvailable offset 131). Crystal's leaf tokens
 * (IDENTIFIER, CONSTANT) don't implement PsiNameIdentifierOwner, so overriding
 * isMemberInplaceRenameAvailable has no effect — it's dead code.
 *
 * For local variable/parameter rename, CrystalVariableReference and friends
 * need to implement PsiNameIdentifierOwner first. See
 * openspec/specs/rename-refactoring/spec.md for the full analysis.
 */
class CrystalRefactoringSupportProvider : RefactoringSupportProvider()
