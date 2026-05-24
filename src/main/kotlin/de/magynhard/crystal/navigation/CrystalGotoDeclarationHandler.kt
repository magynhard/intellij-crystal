package de.magynhard.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for identifiers after DOT,
 * e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen".
 *
 * This complements the PSI mixin-based references (which handle variable_reference,
 * method_call_expression, etc.) by covering leaf IDENTIFIER tokens in postfix_op
 * positions that don't have their own composite PSI wrapper.
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val elementType = sourceElement.node.elementType
        if (elementType != CrystalTypes.IDENTIFIER && elementType != CrystalTypes.CONSTANT) {
            return null
        }

        val name = sourceElement.text
        if (name.isBlank()) return null

        // Check if this identifier is after a DOT (dot-call like "obj.method" or "Class.method")
        val prev = skipWhitespaceBefore(sourceElement)
        if (prev != null && prev.node.elementType == CrystalTypes.DOT) {
            return resolveDotCall(sourceElement, name)
        }

        return null
    }

    private fun skipWhitespaceBefore(element: PsiElement): PsiElement? {
        var prev = element.prevSibling
        while (prev != null && prev.node.elementType.toString() == "WHITE_SPACE") {
            prev = prev.prevSibling
        }
        return prev
    }

    private fun resolveDotCall(element: PsiElement, methodName: String): Array<PsiElement>? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val results = mutableListOf<PsiElement>()

        // 1. Search method index (covers both "def tanzen" and "def self.tanzen")
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, methodName, project, scope,
            CrystalMethodDefinition::class.java
        )
        results.addAll(methods)

        // 2. Search class index (for "Module::ClassName" style navigation)
        if (methodName.first().isUpperCase()) {
            val types = StubIndex.getElements(
                CrystalClassIndex.KEY, methodName, project, scope,
                CrystalClassDefinition::class.java
            )
            results.addAll(types)
        }

        // 3. PSI tree walk fallback for same-file definitions
        if (results.isEmpty()) {
            val file = element.containingFile ?: return null
            findMethodsInTree(file, methodName, results)
        }

        return if (results.isNotEmpty()) results.toTypedArray() else null
    }

    private fun findMethodsInTree(root: PsiElement, name: String, results: MutableList<PsiElement>) {
        for (child in root.children) {
            when (child) {
                is CrystalMethodDefinition -> {
                    if (child.name == name) results.add(child)
                }
                is CrystalMacroDefinition -> {
                    if (child.name == name) results.add(child)
                }
                is CrystalClassDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findMethodsInTree(it, name, results) }
                }
                is CrystalModuleDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findMethodsInTree(it, name, results) }
                }
                is CrystalStructDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findMethodsInTree(it, name, results) }
                }
                is CrystalEnumDefinition -> {
                    if (child.name == name) results.add(child)
                }
                else -> findMethodsInTree(child, name, results)
            }
        }
    }
}
