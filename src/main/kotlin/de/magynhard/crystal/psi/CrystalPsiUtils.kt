package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.stubs.CrystalNamedStub

/**
 * Utility functions for Crystal PSI elements.
 */
object CrystalPsiUtils {

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
            when (current) {
                is CrystalClassDefinition -> current.name?.let { parts.add(0, it) }
                is CrystalModuleDefinition -> current.name?.let { parts.add(0, it) }
                is CrystalStructDefinition -> current.name?.let { parts.add(0, it) }
                is CrystalEnumDefinition -> current.name?.let { parts.add(0, it) }
            }
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
}
