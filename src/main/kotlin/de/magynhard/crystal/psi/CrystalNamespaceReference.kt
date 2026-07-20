package de.magynhard.crystal.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Reference from a `namespace_access` element (e.g. `::Unterklasse` in `Oberklasse::Unterklasse`)
 * to the class/module/struct/enum definition.
 *
 * Reconstructs the full namespace path by walking left through prevSibling elements:
 * - `Oberklasse::Unterklasse` → looks up `"Oberklasse::Unterklasse"` then falls back to `"Unterklasse"`
 * - `A::B::C` → looks up `"A::B::C"` then falls back to `"C"`
 * - `::Foo` → looks up `"Foo"` (no preceding namespace part)
 *
 * The fallback to the simple name handles lexically-nested classes (e.g. `class A; class B; end; end`)
 * where `CrystalClassIndex` is keyed by the simple name `"B"`, not the full path.
 */
class CrystalNamespaceReference(
    element: PsiElement,
    private val simpleName: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val fullName = buildFullName()

        // 1. Try full path first (for namespace-defined classes: `class A::B`)
        val byFullName = CrystalIndexService.findTypes(fullName, project, scope)
        if (byFullName.isNotEmpty()) return byFullName.first()

        // 2. Fall back to filtered simple-name lookup (for lexically-nested classes).
        //    Filter by qualified name to disambiguate: Foo::Sub vs Bar::Sub.
        if (fullName != simpleName) {
            val candidates = CrystalIndexService.findTypes(simpleName, project, scope)
            return candidates.filter { candidate ->
                CrystalPsiUtils.buildQualifiedName(candidate) == fullName
            }.firstOrNull()
        }

        // 3. Simple name only (e.g., `::Foo` — no preceding path)
        return CrystalIndexService.findTypes(simpleName, project, scope).firstOrNull()
    }

    /**
     * Walks left through prevSibling elements to reconstruct the full namespace path.
     * Collects CONSTANT names from preceding [CrystalNamespaceAccess] and
     * [CrystalVariableReference] elements, joining them with `::`.
     *
     * Example: for `A::B::C`, when called on the `::C` element, returns `"A::B::C"`.
     */
    private fun buildFullName(): String {
        val parts = mutableListOf(simpleName)
        var current: PsiElement? = element.prevSibling

        while (current != null) {
            when {
                current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE -> {
                    current = current.prevSibling
                }
                current is CrystalNamespaceAccess -> {
                    // Another namespace_access — get its CONSTANT
                    val nsConstant = current.node.findChildByType(CrystalTypes.CONSTANT)
                    if (nsConstant != null) parts.add(0, nsConstant.text)
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    // The leading variable_reference — get its CONSTANT
                    val vrConstant = current.node.findChildByType(CrystalTypes.CONSTANT)
                    if (vrConstant != null) parts.add(0, vrConstant.text)
                    break
                }
                else -> break
            }
        }

        return parts.joinToString("::")
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val constantNode = element.node.findChildByType(CrystalTypes.CONSTANT) ?: return element
        val newLeaf = createLeafFromText(element.project, newElementName, CrystalTypes.CONSTANT) ?: return element
        constantNode.treeParent.replaceChild(constantNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private fun createLeafFromText(project: com.intellij.openapi.project.Project, text: String, elementType: CrystalTypes): ASTNode? {
            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("dummy.cr", de.magynhard.crystal.CrystalLanguage, text)
            return file.firstChild?.node?.firstChildNode
        }
    }
}
