package de.magynhard.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalMethodByClassIndex

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for:
 * 1. Identifiers after DOT (e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen")
 * 2. Instance variables (@name) and class variables (@@name) → jumps to property declaration or shows all usages
 * 3. ".new" on a class (e.g. "Senf.new") → jumps to "def self.new", "record Senf", or "def initialize"
 *    following Crystal's constructor resolution order (self.new > record > initialize).
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val elementType = sourceElement.node.elementType

        // Handle instance variables (@name) and class variables (@@name)
        if (elementType == CrystalTypes.INSTANCE_VAR || elementType == CrystalTypes.CLASS_VAR) {
            // The leaf token's parent should be the CrystalInstanceVarAccess/CrystalClassVarAccess composite
            val varAccess = sourceElement.parent
            if (varAccess is CrystalInstanceVarAccess || varAccess is CrystalClassVarAccess) {
                val varName = varAccess.text
                val targets = CrystalInstanceVarFinder.findDefinitionTargets(varName, varAccess)
                if (targets.isNotEmpty()) return targets.toTypedArray()
                val usages = CrystalInstanceVarFinder.findAllUsages(varName, varAccess)
                    .filter { it !== varAccess }
                return if (usages.isNotEmpty()) usages.toTypedArray() else null
            }
            // Fallback for @name in property_declaration or parameter (still leaf tokens)
            val varName = sourceElement.text
            val targets = CrystalInstanceVarFinder.findDefinitionTargets(varName, sourceElement)
            if (targets.isNotEmpty()) return targets.toTypedArray()
            val usages = CrystalInstanceVarFinder.findAllUsages(varName, sourceElement)
                .filter { it !== sourceElement }
            return if (usages.isNotEmpty()) usages.toTypedArray() else null
        }

        if (elementType != CrystalTypes.IDENTIFIER && elementType != CrystalTypes.CONSTANT) {
            return null
        }

        val name = sourceElement.text
        if (name.isBlank()) return null

        // The dot_call_access BNF rule now provides a CrystalDotCallReference via its
        // mixin, so the platform's TargetElementUtil resolves DOT-calls the same way as
        // variable_reference (top-level calls). This handler is only reached as a
        // fallback when the reference returned null (e.g. unknown-instance receiver).
        //
        // The ONLY DOT-call case still handled here is the `.new` constructor, which
        // has no dedicated `def new` in Crystal and therefore cannot resolve via
        // CrystalMethodByClassIndex. The handler implements Crystal's constructor
        // resolution order: self.new > record > initialize.
        if (name == "new") {
            val className = findClassNameBeforeNewToken(sourceElement)
            if (className != null) {
                val targets = findNewTargets(className, sourceElement)
                return if (targets.isNotEmpty()) targets.toTypedArray() else null
            }
            // No class receiver detected — return null rather than flooding the
            // user with every "new" method in the project.
            return null
        }

        // Non-.new DOT-calls are fully handled by the PsiReference — return null here
        // so the platform uses the reference resolution result (or no-result, when the
        // receiver type is unknown — no false-positive name-only guessing).
        // Check whether this identifier is preceded by DOT to confirm it's a DOT-call
        // (otherwise it's a variable_reference handled entirely by the reference).
        val prev = skipWhitespaceBefore(sourceElement)
        if (prev != null && prev.node.elementType == CrystalTypes.DOT) {
            // DOT-call: delegate to the reference (already invoked by TargetElementUtil
            // before falling through to this handler). Return null so we don't second-
            // guess the reference's resolution.
            return null
        }

        return null
    }

    /**
     * For "ClassName.new" — extracts the class name from the receiver before ".new".
     * Returns null for non-class receivers (e.g. "obj.new" where obj is lowercase).
     *
     * Uses PsiTreeUtil.prevLeaf to cross composite boundaries: for "Senf.new" the leaf
     * before DOT is the CONSTANT "Senf"; for "Outer::Inner.new" the last CONSTANT ("Inner")
     * is returned, which is the correct key for CrystalMethodByClassIndex (immediate
     * enclosing class).
     */
    private fun findClassNameBeforeNewToken(newToken: PsiElement): String? {
        val dot = prevLeafSkipWhitespace(newToken) ?: return null
        if (dot.node.elementType != CrystalTypes.DOT) return null
        val receiver = prevLeafSkipWhitespace(dot) ?: return null
        if (receiver.node.elementType == CrystalTypes.CONSTANT) return receiver.text
        return null
    }

    /**
     * Resolves "ClassName.new" to its actual target, following Crystal's constructor
     * resolution order:
     * 1. "def self.new" in the class — explicit override of the default constructor
     * 2. "record ClassName, ..." macro — auto-generates "new" with record fields
     * 3. "def initialize" in the class — called by the built-in "Class#new"
     */
    private fun findNewTargets(className: String, sourceElement: PsiElement): List<PsiElement> {
        val project = sourceElement.project
        val scope = GlobalSearchScope.allScope(project)

        // 1. Explicit "def self.new" in the class — takes priority (overrides default "new")
        val classMethods = StubIndex.getElements(
            CrystalMethodByClassIndex.KEY, className, project, scope,
            CrystalMethodDefinition::class.java
        )
        val selfNew = classMethods.filter { it.name == "new" }
        if (selfNew.isNotEmpty()) return selfNew.toList()

        // 2. "record" macro — auto-generates "new" with the record fields as parameters
        val recordDef = CrystalCompletionHelper.findRecordDefinition(className, sourceElement.containingFile)
        if (recordDef != null) return listOf(recordDef)

        // 3. Default: "def initialize" (called by the built-in "Class#new")
        val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, sourceElement.containingFile)
        if (initMethod != null) return listOf(initMethod)

        return emptyList()
    }

    private fun prevLeafSkipWhitespace(element: PsiElement): PsiElement? {
        var prev: PsiElement? = PsiTreeUtil.prevLeaf(element)
        while (prev != null && prev.node.elementType.toString() == "WHITE_SPACE") {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        return prev
    }

    private fun skipWhitespaceBefore(element: PsiElement): PsiElement? {
        var prev = element.prevSibling
        while (prev != null && prev.node.elementType.toString() == "WHITE_SPACE") {
            prev = prev.prevSibling
        }
        return prev
    }
}
