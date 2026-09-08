package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalArgument
import de.magynhard.crystal.psi.CrystalBareArgument
import de.magynhard.crystal.psi.CrystalBareMethodCallExpression
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.CrystalVariableReference

/**
 * Coupling utilities for the accessor-macro family. A rename of the accessor
 * name (`foo` in `property foo`) carries the whole implicit chain: the
 * generated reader/setter methods (`foo`, `foo=`, `foo?`, `foo!`), the coupled
 * instance/class variables (`@foo` / `@@foo` for the `class_*` variants), the
 * initializer storage shortcut (`initialize(@foo : String)`), and the reader
 * and setter call sites (`obj.foo`, `obj.foo = v`). Macro-generated code has no
 * PSI of its own, so the accessor macro-call argument is the declaration and
 * every consumer must follow it.
 */
object CrystalAccessorCoupling {

    val ACCESSOR_MACROS = setOf(
        "getter", "getter?", "getter!",
        "setter", "setter!",
        "property", "property?", "property!",
        "class_getter", "class_getter?", "class_getter!",
        "class_setter", "class_setter!",
        "class_property", "class_property?", "class_property!",
    )

    /** Which variable sigil an accessor macro couples to: `@@name` for class_*, `@name` otherwise. */
    fun isClassVarMacro(macroName: String): Boolean = macroName.startsWith("class_")

    fun varNameForMacro(macroName: String, propertyName: String): String =
        if (isClassVarMacro(macroName)) "@@$propertyName" else "@$propertyName"

    /** The accessor macro name of a call (`property?` in `property? active`), or null. */
    fun accessorMacroName(call: PsiElement): String? {
        val text = call.firstChild?.text ?: return null
        if (text !in ACCESSOR_MACROS) return null
        return text
    }

    fun isAccessorMacroCall(call: PsiElement): Boolean = accessorMacroName(call) != null

    /**
     * The rename name of an accessor argument: its leading IDENTIFIER leaf. For
     * typed declarations (`property foo : String = …`) it is the leading
     * identifier of the argument; plain arguments bind the name as their own
     * identifier child.
     */
    fun accessorNameIdentifier(arg: PsiElement?): PsiElement? {
        if (arg == null) return null
        val direct = arg.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
        if (direct != null) return direct
        val ref = PsiTreeUtil.findChildOfType(arg, CrystalVariableReference::class.java) ?: return null
        return ref.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
    }

    fun accessorArgName(arg: PsiElement?): String? = accessorNameIdentifier(arg)?.text

    /**
     * All accessor name arguments of one macro call sharing [propertyName].
     *
     * Positional bare arguments live in the bare-argument-list composite
     * (`property foo, bar` — the args are NOT direct children of the call),
     * while parenthesized calls bind `CrystalArgument` composites at the
     * top level.
     */
    fun accessorArgsOfName(call: PsiElement, propertyName: String): List<PsiElement> {
        val positional = when (call) {
            is CrystalMethodCallExpression -> call.bareArgumentList
            is CrystalBareMethodCallExpression -> call.bareArgumentList
            else -> null
        }?.bareArgumentList.orEmpty()
        return PsiTreeUtil.getChildrenOfTypeAsList(call, CrystalArgument::class.java)
            .filter { accessorArgName(it) == propertyName } +
            positional.filter { accessorArgName(it) == propertyName }
    }

    /**
     * Which macro names may generate the called method shape. Plain readers
     * (`foo`) come only from suffix-free macros; `getter? foo` declares `foo?`
     * (no plain `foo`), `getter! foo` declares `foo!`; setters (`foo=`) come
     * from setter declarations including the `!` variants (`setter! foo`
     * still declares `foo=`).
     */
    fun allowedAccessorMacros(methodName: String): Set<String>? = when {
        methodName.endsWith("?") ->
            setOf("getter?", "property?", "class_getter?", "class_property?")
        methodName.endsWith("!") ->
            setOf("getter!", "property!", "class_getter!", "class_property!")
        methodName.endsWith("=") ->
            setOf("setter", "setter!", "property", "property!",
                "class_setter", "class_setter!", "class_property", "class_property!")
        else ->
            setOf("getter", "property", "class_getter", "class_property")
    }

    /**
     * The first accessor name argument of [propertyName] inside a type body
     * (class, struct, module), or null.
     */
    fun findAccessorArg(propertyName: String, typeDef: PsiElement): PsiElement? =
        findAccessorCallIn(typeDef, propertyName)?.let { accessorArgsOfName(it, propertyName).firstOrNull() }

    /**
     * The coupled instance/class variable name for the accessor declaration
     * holding [arg], or null when the argument is not an accessor name.
     */
    fun coupledVarName(arg: PsiElement): String? {
        val call = PsiTreeUtil.getParentOfType(
            arg,
            CrystalMethodCallExpression::class.java,
            CrystalBareMethodCallExpression::class.java,
        ) ?: return null
        if (!isAccessorMacroCall(call)) return null
        val macro = accessorMacroName(call) ?: return null
        val propertyName = accessorArgName(arg) ?: return null
        return varNameForMacro(macro, propertyName)
    }

    /**
     * The accessor name argument declaration coupled to an instance/class
     * variable element (`@foo` → `foo` of `property foo` in the same type), or
     * null. Keeps the reverse rename direction: renaming the variable pulls
     * the accessor.
     */
    fun findAccessorArgForVar(varElement: PsiElement): PsiElement? {
        val varName = varElement.text // @foo / @@foo
        if (!varName.startsWith("@")) return null
        val typeDef = PsiTreeUtil.getParentOfType(
            varElement,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java,
        ) ?: return null
        val propertyName = varName.removePrefix("@@").removePrefix("@")
        return findAccessorArg(propertyName, typeDef)
    }

    /**
     * All accessor macro calls inside a type declaration (skipping nested
     * types), regardless of the property name.
     */
    fun findAccessorCallsIn(typeDef: PsiElement): List<PsiElement> {
        val calls = mutableListOf<PsiElement>()
        findAccessorCallsInTree(typeDef, calls)
        return calls
    }

    /**
     * All accessor name arguments across [typeDef] bound by one of the
     * [allowedMacroNames] (null = all), matching the instance/class mode of
     * [classMacrosOnly].
     */
    fun accessorArgsMatching(
        typeDef: PsiElement,
        propertyName: String,
        allowedMacroNames: Set<String>?,
        classMacrosOnly: Boolean,
    ): List<PsiElement> {
        return findAccessorCallsIn(typeDef)
            .flatMap { call ->
                val macro = accessorMacroName(call) ?: return@flatMap emptyList<PsiElement>()
                if (allowedMacroNames != null && macro !in allowedMacroNames) return@flatMap emptyList<PsiElement>()
                if (isClassVarMacro(macro) != classMacrosOnly) return@flatMap emptyList<PsiElement>()
                accessorArgsOfName(call, propertyName)
            }
    }

    private fun findAccessorCallsInTree(element: PsiElement, results: MutableList<PsiElement>) {
        if (element is CrystalMethodCallExpression || element is CrystalBareMethodCallExpression) {
            if (isAccessorMacroCall(element)) {
                results.add(element)
                return
            }
        }
        for (child in element.children) {
            if (CrystalPsiUtils.isTypeDefinition(child)) continue
            findAccessorCallsInTree(child, results)
        }
    }

    private fun findAccessorCallIn(element: PsiElement, propertyName: String): PsiElement? {
        if (element is CrystalMethodCallExpression || element is CrystalBareMethodCallExpression) {
            if (isAccessorMacroCall(element) && accessorArgsOfName(element, propertyName).isNotEmpty()) {
                return element
            }
        }
        for (child in element.children) {
            if (CrystalPsiUtils.isTypeDefinition(child)) continue
            val nested = findAccessorCallIn(child, propertyName)
            if (nested != null) return nested
        }
        return null
    }
}
