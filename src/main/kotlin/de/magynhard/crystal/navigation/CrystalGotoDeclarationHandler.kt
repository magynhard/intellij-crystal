package de.magynhard.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalStaticDeclarations
import de.magynhard.crystal.stubs.CrystalIndexService
import de.magynhard.crystal.psi.*

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for:
 * 1. Identifiers after DOT (e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen")
 * 2. Instance variables (@name) and class variables (@@name) → jumps to property declaration or shows all usages
 * 3. ".new" on a class (e.g. "Senf.new") → jumps to its explicit "def self.new" and
 *    compiler-forwarded "def initialize" overloads, or to the matching record declaration.
 * 4. require statements → jumps to the required file(s): relative requires resolve
 *    against the containing file's directory, library requires search the project's
 *    `lib` directories and the Crystal standard library (compiler semantics).
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val elementType = sourceElement.node.elementType

        if (elementType == CrystalTypes.MACRO_BODY_CONTENT && !CrystalStaticDeclarations.isView(sourceElement)) {
            val body = PsiTreeUtil.getParentOfType(sourceElement, CrystalMacroBody::class.java) ?: return null
            return findStaticMacroTargets(body, offset, editor).toTypedArray()
        }

        // Require statements: navigate to the required file(s). Multiple matches
        // (wildcard requires) are returned together — the platform shows a popup.
        if (elementType == CrystalTypes.STRING_LITERAL || elementType == CrystalTypes.STRING_ESCAPE ||
            elementType == CrystalTypes.REQUIRE
        ) {
            val requireStatement =
                PsiTreeUtil.getParentOfType(sourceElement, CrystalRequireStatement::class.java, false)
                    ?: return null
            val targets = CrystalRequireResolver.resolveRequireTargets(requireStatement)
            return if (targets.isEmpty()) null else targets.toTypedArray()
        }

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

        val dotCall = PsiTreeUtil.getParentOfType(sourceElement, CrystalDotCallAccess::class.java, false)
        val polyvariant = dotCall?.reference as? PsiPolyVariantReference
        // Arguments have their own references; an enclosing DOT call must not
        // hijack navigation from a constant/local argument to the called method.
        if (dotCall != null && polyvariant != null &&
            polyvariant.rangeInElement.shiftRight(dotCall.textOffset).contains(sourceElement.textRange)
        ) {
            val targets = polyvariant.multiResolve(false).mapNotNull { it.element }
            return targets.toTypedArray()
        }

        val name = sourceElement.text
        if (name.isBlank()) return null

        // The dot_call_access BNF rule now provides a CrystalDotCallReference via its
        // mixin, so the platform's TargetElementUtil resolves DOT-calls the same way as
        // variable_reference (top-level calls). This handler reaches the fallback below
        // only for PSI shapes without that exact reference.
        //
        // The ONLY DOT-call case still handled here is the `.new` constructor. It uses
        // the shared exact constructor resolver and its normal precedence.
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

        // Only a direct lowercase callee is a bare call. Constant/FFI paths,
        // declaration names and arguments retain their existing references.
        if (elementType == CrystalTypes.IDENTIFIER &&
            PsiTreeUtil.getParentOfType(sourceElement, CrystalRequireStatement::class.java) == null &&
            sourceElement.parent?.reference is CrystalReference &&
            sourceElement.parent.firstChild === sourceElement
        ) {
            return findBareCallTargets(sourceElement.parent, name).takeIf { it.isNotEmpty() }?.toTypedArray()
        }

        return null
    }

    private fun findStaticMacroTargets(body: CrystalMacroBody, offset: Int, editor: Editor?): List<PsiElement> {
        if (DumbService.isDumb(body.project)) return emptyList()
        val projected = CrystalStaticDeclarations.syntaxElementAt(body, offset) ?: return emptyList()
        val reference = projected.containingFile.findReferenceAt(projected.textOffset)
        val targets = when (reference) {
            is PsiPolyVariantReference -> reference.multiResolve(false).mapNotNull { it.element }
            null -> getGotoDeclarationTargets(projected, projected.textOffset, editor).orEmpty().toList()
            else -> listOfNotNull(reference.resolve())
        }
        return targets.map { CrystalStaticDeclarations.navigation(it) ?: it.navigationElement }
            .distinctBy { it.containingFile?.virtualFile to it.textOffset }
    }

    private fun findBareCallTargets(call: PsiElement, name: String): List<PsiElement> {
        val reference = call.reference as? CrystalReference ?: return emptyList()
        reference.resolveLocalDeclaration()?.let { return listOf(it) }
        if (CrystalMacroContext.isInMacroContext(call)) return emptyList()
        val candidates = CrystalIndexService.findMethods(name, call.project, GlobalSearchScope.allScope(call.project))
        val visible = CrystalRequireVisibility.callableUnqualified(
            CrystalRequireVisibility.visibleMethods(candidates, call), call,
        )
        // Opening a reopening without a containing entry can leave its source set
        // partial. Offer indexed reopenings of the exact owner, never name-only
        // top-level or unrelated-owner matches.
        val owners = visible.mapNotNull(CrystalPsiUtils::methodOwnerQualifiedName).toMutableSet()
        CrystalPsiUtils.callSiteOwnerQualifiedName(call)?.let(owners::add)
        val additional = candidates.filter {
            it !in visible && CrystalPsiUtils.methodOwnerQualifiedName(it)?.let(owners::contains) == true
        }
        fun ordered(targets: List<CrystalMethodDefinition>) = targets.sortedWith(
            compareBy<CrystalMethodDefinition> { it.containingFile != call.containingFile }
                .thenBy { it.containingFile?.virtualFile?.path.orEmpty() }
                .thenBy { it.textOffset }
        )
        return ordered(visible) + ordered(additional)
    }

    /**
     * Extracts the complete proven constant identity before `.new`, including qualified
     * and absolute paths. Incomplete or dynamic receiver shapes return null.
     */
    private fun findClassNameBeforeNewToken(newToken: PsiElement): String? {
        val dot = prevLeafSkipWhitespace(newToken) ?: return null
        if (dot.node.elementType != CrystalTypes.DOT) return null
        return CrystalReceiverExpression.extractExactConstantTypeRootBeforeDot(dot)
    }

    /**
     * Resolves "ClassName.new" to its actual targets: a record declaration, or the combined
     * overload pool of explicit "def self.new" methods and compiler-forwarded initializers.
     */
    private fun findNewTargets(className: String, sourceElement: PsiElement): List<PsiElement> {
        return when (val resolution = CrystalTypeSetResolver.session(sourceElement)
            .resolveConstructor(className, sourceElement)) {
            is CrystalConstructorResolution.Methods -> resolution.methods
            is CrystalConstructorResolution.Record -> listOf(resolution.recordDefinition)
            is CrystalConstructorResolution.Implicit,
            is CrystalConstructorResolution.Abstract,
            is CrystalConstructorResolution.Unavailable,
            is CrystalConstructorResolution.Incomplete -> emptyList()
        }
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
