package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalEnumDefinition
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalPropertyDeclaration
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalStructDefinition

/**
 * Inspection that validates instance variable type annotations against Crystal's
 * restriction that abstract and uninstantiated generic types cannot be used as
 * instance variable types.
 *
 * Crystal compiler error: "can't use X as the type of instance variable '@name',
 * use a more specific type"
 *
 * Forbidden types:
 * - Abstract base types: Value, Object, Reference, Number, Int, Float, Struct, Enum
 * - Unbound generics: Pointer, Tuple, NamedTuple, StaticArray, Class
 * - Uninstantiated generics: Array, Hash, Range, Slice, Proc, Union, Enumerable, Indexable
 */
class CrystalInstanceVarTypeInspection : LocalInspectionTool() {

    // Wires the inspectionDescriptions/<shortName>.html resource into the
    // platform's description loading; without it the Inspect Code results
    // view crashes when a result node is selected.
    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalPropertyDeclaration -> checkPropertyDeclaration(element, holder)
                    is CrystalParameter -> checkParameter(element, holder)
                }
            }
        }
    }

    private fun checkParameter(param: CrystalParameter, holder: ProblemsHolder) {
        // Only check parameters that declare instance variables: def initialize(@x : Type)
        val instanceVar = param.instanceVarAccess ?: return
        val varName = instanceVar.text // e.g. "@x"
        val typeRef = param.typeReference ?: return

        checkTypeReference(typeRef, varName, param, holder)
    }

    private fun checkPropertyDeclaration(decl: CrystalPropertyDeclaration, holder: ProblemsHolder) {
        // Only check instance variable declarations
        val instanceVar = decl.instanceVarAccess ?: return
        val varName = instanceVar.text // e.g. "@schaerfe"
        val typeRef = decl.typeReference

        checkTypeReference(typeRef, varName, decl, holder)
    }

    private fun checkTypeReference(typeRef: de.magynhard.crystal.psi.CrystalTypeReference, varName: String, parentElement: PsiElement, holder: ProblemsHolder) {
        val typeText = typeRef.text

        // Handle union types: split on top-level PIPE tokens
        val components = extractUnionComponentsFromText(typeText)
        if (components.size > 1) {
            for (component in components) {
                checkSingleType(component, varName, parentElement, holder)
            }
        } else {
            checkSingleType(typeText, varName, parentElement, holder)
        }
    }

    private fun checkSingleType(typeText: String, varName: String, parentElement: PsiElement, holder: ProblemsHolder) {
        val trimmed = typeText.trim()
        val baseType = extractBaseTypeName(trimmed)
        // Only flag if: (1) base type is forbidden AND (2) it's NOT instantiated (no parentheses after the type name)
        val hasTypeArgs = trimmed.substring(baseType.length).trimStart().startsWith('(')
        if (baseType in FORBIDDEN_TYPES && !hasTypeArgs && !concreteVarDeclarationRescues(varName, parentElement)) {
            val highlightElement = findTypeHighlightTarget(parentElement)
            holder.registerProblem(
                highlightElement,
                "'$baseType' cannot be used as the type of instance variable '$varName', use a more specific type",
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }

    /**
     * `initialize(@x : Int)` is a RESTRICTION on the parameter, not an instance
     * variable declaration: abstract types are legal restriction positions.
     * The actual ivar type comes from a concrete declaration elsewhere in the
     * type body — `getter x : Int32`, `property x : T`, or the ivar annotation
     * `@x : T = ...` (real compiler verified: SemanticVersion declares
     * `getter major : Int32` while `initialize(@major : Int)` stays legal;
     * an untyped `getter x` alone rescues nothing). Only when no such typed
     * co-declaration exists does the crystal compiler reject the abstract ivar
     * type ("can't use Int as the type of instance variable '…'") and this
     * inspection reports.
     *
     * Property declarations (`@x : Int = ...` / `property x : Int`) do NOT go
     * through here — they are their own annotations and stay flagged by
     * checkPropertyDeclaration.
     */
    private fun concreteVarDeclarationRescues(varName: String, parentElement: PsiElement): Boolean {
        val enclosingType = CrystalPsiUtils.getEnclosingType(parentElement) ?: return false
        val body = when (enclosingType) {
            is CrystalClassDefinition -> enclosingType.classBody
            is CrystalStructDefinition -> enclosingType.classBody
            is CrystalModuleDefinition -> enclosingType.classBody
            is CrystalEnumDefinition -> enclosingType.enumBody
            else -> return false
        } ?: return false

        for (decl in PsiTreeUtil.findChildrenOfType(body, CrystalPropertyDeclaration::class.java)) {
            if (decl.instanceVarAccess?.text == varName && decl.typeReference != null) return true
        }

        // Typed accessor macros: `getter x : Int32`, `getter? x : T`, `property x : T`,
        // `setter x : T` — bare names, never `@x`. Declaration order is irrelevant
        // (Crystal resolves the ivar type through the whole type body). The
        // parenthesis after the macro name is optional (`getter( x : Int32)`).
        val baseName = Regex.escape(varName.removePrefix("@"))
        val typedAccessor = Regex("^(getter\\?|getter|property\\?|property|setter)\\s*\\(?\\s*$baseName\\s*:")
        for (call in PsiTreeUtil.findChildrenOfType(body, CrystalMethodCallExpression::class.java)) {
            if (typedAccessor.containsMatchIn(call.text.replace('\n', ' '))) return true
        }
        return false
    }

    /**
     * Extracts the base type name from a type string.
     * "Array(Int32)" → "Array", "Foo::Bar" → "Foo::Bar", "Int32" → "Int32"
     */
    private fun extractBaseTypeName(typeText: String): String {
        val trimmed = typeText.trim()
        // For generic types like "Array(Int32)", extract just "Array"
        val parenIndex = trimmed.indexOf('(')
        val braceIndex = trimmed.indexOf('{')
        val end = when {
            parenIndex >= 0 -> parenIndex
            braceIndex >= 0 -> braceIndex
            else -> trimmed.length
        }
        return trimmed.substring(0, end).trim()
    }

    /**
     * Extracts union components from a type text string by splitting on top-level PIPE tokens.
     * "Int32 | String" → ["Int32", "String"], "Int32" → ["Int32"]
     * Respects parentheses nesting: "Array(Int32 | String)" → single component
     */
    private fun extractUnionComponentsFromText(typeText: String): List<String> {
        val components = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (ch in typeText) {
            when (ch) {
                '(', '[', '{' -> {
                    depth++
                    current.append(ch)
                }
                ')', ']', '}' -> {
                    depth--
                    current.append(ch)
                }
                '|' -> {
                    if (depth == 0) {
                        val component = current.toString().trim()
                        if (component.isNotEmpty()) components.add(component)
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) components.add(last)

        return components
    }

    private fun findTypeHighlightTarget(element: PsiElement): PsiElement {
        // Walk children to find the type reference
        for (child in element.children) {
            if (child is de.magynhard.crystal.psi.CrystalTypeReference) {
                // Return the first type_path child for highlighting
                for (typeChild in child.children) {
                    if (typeChild is de.magynhard.crystal.psi.CrystalTypePath) {
                        return typeChild
                    }
                }
                return child
            }
        }
        return element
    }

    companion object {
        /**
         * Types that Crystal forbids as instance variable types.
         * These are abstract base types and uninstantiated generic types.
         */
        private val FORBIDDEN_TYPES = setOf(
            // Abstract base types
            "Value", "Object", "Reference",
            "Number", "Int", "Float",
            "Struct", "Enum",
            // Unbound generic types
            "Pointer", "Tuple", "NamedTuple", "StaticArray", "Class",
            // Uninstantiated generic types
            "Array", "Hash", "Range", "Slice", "Proc", "Union",
            "Enumerable", "Indexable"
        )
    }
}
