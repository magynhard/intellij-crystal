package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.stubs.CrystalNamedStub

/**
 * Utility functions for Crystal PSI elements.
 */
object CrystalPsiUtils {

    data class RecordDefinition(
        val qualifiedName: String,
        val call: CrystalMethodCallExpression
    )

    fun findRecordDefinition(className: String, file: PsiFile): CrystalMethodCallExpression? {
        return findRecordDefinitions(className, file).firstOrNull()?.call
    }

    fun findRecordDefinitions(className: String, file: PsiFile): List<RecordDefinition> {
        return PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java).mapNotNull { call ->
            if (call.firstChild?.text != "record") return@mapNotNull null
            val firstArgument = call.bareArgumentList?.bareArgumentList?.firstOrNull()
                ?: return@mapNotNull null
            val firstName = firstArgument.firstChild
            val name = if (firstName?.node?.elementType == CrystalTypes.CONSTANT) {
                firstName.text
            } else {
                firstName?.node?.findChildByType(CrystalTypes.CONSTANT)?.text
            }
            if (name != className) return@mapNotNull null
            val enclosingName = getEnclosingType(call)?.let(::buildQualifiedName)
            RecordDefinition(
                listOfNotNull(enclosingName, name).joinToString("::"),
                call
            )
        }
    }

    fun buildLexicalQualifiedNameCandidates(simpleName: String, context: PsiElement): Set<String> {
        val candidates = linkedSetOf<String>()
        val enclosingParts = getEnclosingType(context)
            ?.let(::buildQualifiedName)
            ?.split("::")
            .orEmpty()
        for (size in enclosingParts.size downTo 1) {
            candidates.add(enclosingParts.take(size).joinToString("::") + "::$simpleName")
        }
        candidates.add(simpleName)
        return candidates
    }

    fun isInsideMacroControlRegion(element: PsiElement): Boolean {
        var current = element
        while (true) {
            val parent = current.parent ?: return false
            if (parent is PsiFile || parent is CrystalClassBody) {
                var depth = 0
                for (sibling in parent.children) {
                    if (sibling === current) {
                        if (depth > 0) return true
                        break
                    }
                    if (sibling is CrystalMacroControl) {
                        depth = updateMacroDepth(depth, sibling)
                    }
                }
            }
            current = parent
        }
    }

    private fun updateMacroDepth(depth: Int, control: CrystalMacroControl): Int {
        val keyword = control.text.removePrefix("{%")
            .removeSuffix("%}")
            .trim()
            .takeWhile { !it.isWhitespace() }
        return when (keyword) {
            "end" -> (depth - 1).coerceAtLeast(0)
            "if", "unless", "for", "case", "begin" -> depth + 1
            else -> depth
        }
    }

    /**
     * Returns whether a method has a direct `self` receiver in its definition header.
     */
    fun isSelfMethod(method: CrystalMethodDefinition): Boolean {
        return method.node.findChildByType(CrystalTypes.SELF) != null
    }

    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * by walking up the PSI tree and collecting enclosing type names.
     *
     * Examples:
     * - `class Foo; class Sub; end; end` → `"Foo::Sub"`
     * - `class Foo::Sub` (namespace-defined) → `"Foo::Sub"` (from stub.name)
     * - `class C` in `class B` in `class A` → `"A::B::C"`
     * - `module Foo; class Bar; end; end` → `"Foo::Bar"`
     */
    fun buildQualifiedName(element: PsiElement): String? {
        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        while (current != null) {
            val name = when (current) {
                is CrystalClassDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalModuleDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalStructDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalEnumDefinition -> extractQualifiedTypeName(current) ?: current.name
                else -> null
            }
            if (name != null) parts.add(0, name)
            current = current.parent
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * from a stub element, using the stub tree for faster traversal.
     */
    fun buildQualifiedNameFromStub(stub: com.intellij.psi.stubs.StubElement<*>): String? {
        val parts = mutableListOf<String>()
        var current: com.intellij.psi.stubs.StubElement<*>? = stub
        while (current != null) {
            if (current is CrystalNamedStub) {
                val name = current.name
                if (name != null) {
                    parts.add(0, name)
                }
            }
            current = current.parentStub
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Returns the immediate enclosing class/module/struct/enum of an element.
     */
    fun getEnclosingType(element: PsiElement): PsiElement? {
        return PsiTreeUtil.findFirstParent(element) { parent ->
            parent is CrystalClassDefinition ||
            parent is CrystalModuleDefinition ||
            parent is CrystalStructDefinition ||
            parent is CrystalEnumDefinition
        }
    }

    /**
     * Builds the full namespace path from a [CrystalNamespaceAccess] element
     * by walking left through preceding [CrystalNamespaceAccess] and
     * [CrystalVariableReference] elements.
     *
     * Example: for `Foo::Sub.space`, when called on the `::Sub` element,
     * returns `"Foo::Sub"`.
     */
    fun buildNamespacePath(namespaceAccess: CrystalNamespaceAccess): String {
        val parts = mutableListOf<String>()

        // Get the CONSTANT from this namespace_access
        namespaceAccess.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
            ?.text?.let { parts.add(0, it) }

        // Walk left through preceding namespace_access and variable_reference
        var current = namespaceAccess.prevSibling
        while (current != null) {
            when {
                current is PsiWhiteSpace ||
                    current.node?.elementType == de.magynhard.crystal.psi.CrystalTypes.NEWLINE -> {
                    current = current.prevSibling
                }
                current is CrystalNamespaceAccess -> {
                    current.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
                        ?.text?.let { parts.add(0, it) }
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    current.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
                        ?.text?.let { parts.add(0, it) }
                    break
                }
                else -> break
            }
        }

        return parts.joinToString("::")
    }

    /**
     * Extracts the fully-qualified type name from a type definition's PSI children.
     * For `class Foo::Bar`, returns "Foo::Bar". For `class Baz`, returns null.
     *
     * Works by scanning the PSI children for CONSTANT tokens (type_name is inlined as
     * direct children of the definition node) and joining them with "::" if there are
     * multiple CONSTANTS before the class_body.
     */
    private fun extractQualifiedTypeName(element: PsiElement): String? {
        val constants = mutableListOf<String>()
        var child = element.node.firstChildNode
        while (child != null) {
            if (child.elementType == CrystalTypes.CONSTANT) {
                constants.add(child.text)
            }
            // Stop at class_body — CONSTANTS inside the body are not part of the type_name
            if (child.elementType == CrystalTypes.CLASS_BODY) break
            child = child.treeNext
        }
        // If there are multiple CONSTANTS (e.g. Foo::Bar), return the full qualified name
        return if (constants.size >= 2) constants.joinToString("::") else null
    }
}
