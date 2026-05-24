package de.magynhard.crystal.psi

import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Marker interface for Crystal PSI elements that have a name (classes, modules, structs, enums, methods, macros).
 * Enables Go to Definition, Find Usages, and Rename for named elements.
 */
interface CrystalNamedElement : PsiNameIdentifierOwner
