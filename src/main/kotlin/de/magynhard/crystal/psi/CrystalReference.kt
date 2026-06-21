package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Reference from an identifier usage to its definition (class/module/struct/enum/method/macro).
 *
 * Resolution order:
 * 1. Local scope (fast — walks up PSI tree, no I/O) — for variables and parameters
 * 2. StubIndex lookup (fast — in-memory index) — for methods, classes, etc.
 *
 * IMPORTANT: Does NOT use CrystalDefinitionFinder.findDefinitions() because that
 * includes a FileTypeIndex fallback that scans ALL .cr files in the project and
 * walks their PSI trees, causing 90+ second delays on every right-click/hover.
 * Go to Definition via CrystalGotoDeclarationHandler still uses the full
 * CrystalDefinitionFinder with the FileTypeIndex fallback.
 */
class CrystalReference(
    element: PsiElement,
    private val name: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    override fun resolve(): PsiElement? {
        // 1. Local scope fallback (fast — no I/O, walks up PSI tree)
        val local = resolveLocal()
        if (local != null) {
            // If the result is an IDENTIFIER leaf (not PsiNameIdentifierOwner),
            // promote to its parent composite if it implements PsiNameIdentifierOwner.
            // This ensures IntelliJ's rename framework activates (requires
            // element instanceof PsiNameIdentifierOwner in MemberInplaceRenameHandler).
            // Go to Definition still works because getNavigationElement() returns
            // the IDENTIFIER leaf via getNameIdentifier().
            if (local !is PsiNameIdentifierOwner) {
                val parent = local.parent
                if (parent is PsiNameIdentifierOwner) return parent
            }
            return local
        }

        // 2. StubIndex lookup only (fast — in-memory index, no FileTypeIndex scan)
        val scope = GlobalSearchScope.allScope(element.project)
        val types = StubIndex.getElements(
            CrystalClassIndex.KEY, name, element.project, scope,
            CrystalNamedElement::class.java
        )
        if (types.isNotEmpty()) return types.first()

        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, name, element.project, scope,
            CrystalMethodDefinition::class.java
        )
        if (methods.isNotEmpty()) return methods.first()

        return null
    }

    private fun resolveLocal(): PsiElement? {
        var scope: PsiElement? = element.parent
        while (scope != null) {
            // Walk siblings before the reference looking for assignments like "name = ..."
            var sibling = scope.prevSibling
            while (sibling != null) {
                val assignment = findAssignmentWithName(sibling, name)
                if (assignment != null) return assignment
                sibling = sibling.prevSibling
            }
            // Check parameters if we're inside a method
            if (scope is CrystalMethodDefinition || scope is CrystalMacroDefinition) {
                val paramList = when (scope) {
                    is CrystalMethodDefinition -> scope.parameterList
                    is CrystalMacroDefinition -> scope.parameterList
                    else -> null
                }
                paramList?.parameterList?.forEach { param ->
                    val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
                    if (paramIdent?.text == name) return paramIdent.psi
                }
                break // Don't look beyond method boundaries for locals
            }
            scope = scope.parent
        }
        return null
    }

    /**
     * Recursively searches a PSI subtree for a CrystalAssignment node
     * whose variable name matches [targetName].
     *
     * Stops at method/macro/class/struct boundaries to avoid resolving
     * across scope boundaries — a variable in method A should not resolve
     * to an assignment in sibling method B.
     */
    private fun findAssignmentWithName(element: PsiElement, targetName: String): PsiElement? {
        // Don't cross scope boundaries
        if (element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition) {
            return null
        }
        if (element is CrystalAssignment && element is PsiNameIdentifierOwner &&
            (element as PsiNameIdentifierOwner).name == targetName) {
            return element
        }
        for (child in element.children) {
            val result = findAssignmentWithName(child, targetName)
            if (result != null) return result
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: element.node.findChildByType(CrystalTypes.INSTANCE_VAR)
            ?: element.node.findChildByType(CrystalTypes.CLASS_VAR)
            ?: return element

        val fixedName = when (identNode.elementType) {
            CrystalTypes.INSTANCE_VAR -> if (!newElementName.startsWith("@")) "@$newElementName" else newElementName
            CrystalTypes.CLASS_VAR -> if (!newElementName.startsWith("@@")) "@@$newElementName" else newElementName
            else -> newElementName
        }

        val newLeaf = createLeafFromText(element.project, fixedName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
