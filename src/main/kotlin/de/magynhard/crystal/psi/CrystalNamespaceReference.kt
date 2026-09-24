package de.magynhard.crystal.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.analysis.CrystalRequireGraphService
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
                .filter { candidate ->
                    CrystalPsiUtils.buildQualifiedName(candidate) == fullName
                }
            if (candidates.isNotEmpty()) return candidates.first()
        } else {
            // 3. Simple name only (e.g., `::Foo` — no preceding path)
            val simple = CrystalIndexService.findTypes(simpleName, project, scope).firstOrNull()
            if (simple != null) return simple
        }

        // 4. Constant members (`Foo::BAR`, `LibC::F_GETFD`): the by-owner
        //    index is keyed by the qualified owner, so query the owner part
        //    and match the member name. Types keep precedence; enum values
        //    stay unresolved (no member index for them).
        if (fullName != simpleName && simpleName.firstOrNull()?.isUpperCase() == true) {
            val owner = fullName.substringBeforeLast("::")
            val constants = CrystalIndexService.findConstantsByOwner(owner, project, scope)
                .filter { it.name == simpleName && isConstantVisible(it) }
            if (constants.isNotEmpty()) {
                val file = element.containingFile
                return constants.minWithOrNull(
                    compareBy<CrystalConstantAssignment> { it.containingFile != file }
                        .thenBy { it.containingFile?.name ?: "" }
                ) ?: constants.first()
            }
        }
        return null
    }

    /**
     * True when a constant declaration is visible from this reference: its
     * file is in the reference's effective source set, and private constants
     * additionally require the same file. Unjudgeable contexts stay silent
     * by resolving to nothing (callers treat null as unknown).
     */
    private fun isConstantVisible(candidate: CrystalConstantAssignment): Boolean {
        return try {
            val service = CrystalRequireGraphService.getInstance(element.project)
            if (service.isProgramLessInjection(element)) return false
            val sources = service.effectiveSources(element).takeIf { it.files.isNotEmpty() } ?: return false
            if (!sources.contains(candidate)) return false
            val isPrivate = candidate.stub?.isPrivate ?: CrystalPsiUtils.isPrivateConstant(candidate)
            if (isPrivate && candidate.containingFile != element.containingFile) return false
            true
        } catch (_: Throwable) {
            false
        }
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
