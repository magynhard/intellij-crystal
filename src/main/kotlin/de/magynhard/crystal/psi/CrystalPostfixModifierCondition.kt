package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement

internal fun CrystalPostfixModifier.conditionElement(): PsiElement =
    postfixConditionAssignment ?: requireNotNull(expression)
