package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Reference from an identifier usage to its definition (class/module/struct/enum/method/macro).
 *
 * Resolution order:
 * 1. Local scope (fast — walks up PSI tree, no I/O) — for variables and parameters
 * 2. StubIndex lookup (fast — in-memory index) — for methods, classes, etc.
 */
class CrystalReference(
    element: PsiElement,
    private val name: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    companion object {
        /** Builtin macro-method API module (compiler/crystal/macros.cr). */
        const val CRYSTAL_MACROS_MODULE = "Crystal::Macros"
    }

    override fun resolve(): PsiElement? {
        // 1. Local scope fallback (fast — no I/O, walks up PSI tree)
        val local = resolveLocalDeclaration()
        if (local != null) return local

        // 2. StubIndex lookup (fast — in-memory index), restricted to the
        //    require-graph visibility of this file. Without this filter, an
        //    undefined name would "resolve" to an arbitrary same-named method
        //    from anywhere in the index (allScope), leaking unrelated
        //    signatures into hover documentation and Go to Definition.
        // Macro context ({{ … }} interpolations, macro bodies): unqualified
        // names resolve to macros or builtin Crystal::Macros macro-methods —
        // never to ordinary project methods (Catalyst::CLI.run must not capture
        // {{ run("…") }}).
        if (CrystalMacroContext.isInMacroContext(element)) {
            val macros = CrystalIndexService.findMacros(name, element.project, scope())
                .filter { it.project == element.project }
            if (macros.isNotEmpty()) {
                return macros.minByOrNull { it.containingFile?.name ?: "" }
            }
            val builtinMacroMethods = CrystalIndexService.findMethods(name, element.project, scope())
                .filter { isBuiltinMacroMethodDef(it) }
            if (builtinMacroMethods.isNotEmpty()) {
                return builtinMacroMethods.minByOrNull { it.containingFile?.name ?: "" }
            }
            return null
        }

        val sources = CrystalRequireGraphService.getInstance(element.project).effectiveSources(element)
        if (sources.files.isEmpty()) return null

        val types = CrystalIndexService.findTypes(name, element.project, scope())
            .filter { sources.contains(it) }
        if (types.isNotEmpty()) return types.first()

        val methods = CrystalIndexService.findMethods(name, element.project, scope())
            .filter { sources.contains(it) }
        if (methods.isNotEmpty()) return deterministicCandidate(methods)

        // Bare implicit-self reader calls declare no method definition in
        // the index (the reader name comes from the getter?/property macro).
        // Resolve the occurrence to the coupled accessor argument so rename
        // and highlight treat the bare call as a family trigger; real
        // methods, locals and macro shapes keep their precedence above.
        return resolveToAccessorArg()
    }

    /**
     * Coupled accessor argument for a bare reader/same-name hit in the
     * declaring type's own body, honoring the searcher's macro gates
     * (`?`-suffix rule, no `!`-variants, no class-var macros) and the exact
     * full-text name (`getter?` declares `name?`, plain `getter` declares
     * `name`). Local shadowing is already handled by the local-first
     * resolution above.
     */
    private fun resolveToAccessorArg(): PsiElement? {
        val typeDef = PsiTreeUtil.getParentOfType(
            element,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java,
        ) ?: return null
        val arg = de.magynhard.crystal.navigation.CrystalAccessorCoupling
            .findAccessorArg(name.removeSuffix("?"), typeDef) ?: return null
        val call = PsiTreeUtil.getParentOfType(
            arg,
            CrystalMethodCallExpression::class.java,
            CrystalBareMethodCallExpression::class.java,
        ) ?: return null
        val macroName = de.magynhard.crystal.navigation.CrystalAccessorCoupling.accessorMacroName(call) ?: return null
        if (macroName.endsWith("!") || de.magynhard.crystal.navigation.CrystalAccessorCoupling.isClassVarMacro(macroName)) return null
        val readerName = (de.magynhard.crystal.navigation.CrystalAccessorCoupling.accessorArgName(arg) ?: return null) +
            if (macroName.endsWith("?")) "?" else ""
        if (name != readerName && name != readerName.removeSuffix("?")) return null
        return arg
    }

    /**
     * Builtin macro-method defs (run, system, …) live in compiler/crystal/macros.cr
     * inside module Crystal::Macros — the enclosing-name helper reports the last
     * segment ("Macros"), so both spellings plus the file path are checked.
     */
    private fun isBuiltinMacroMethodDef(def: PsiElement): Boolean {
        if (def !is CrystalMethodDefinition) return false
        val owner = CrystalCompletionHelper.getEnclosingClassName(def) ?: return false
        if (owner != CRYSTAL_MACROS_MODULE && owner != "Macros") return false
        val path = def.containingFile?.virtualFile?.path ?: return false
        return path.contains("compiler/crystal/")
    }

    /** Stable pick for diagnostics/hover: prefer top-level defs, then enclosing name, then file. */
    private fun deterministicCandidate(methods: List<PsiElement>): PsiElement =
        methods.minWithOrNull(
            compareBy<PsiElement> { it !is CrystalMethodDefinition }
                .thenBy { (it as? CrystalMethodDefinition)?.let(CrystalCompletionHelper::getEnclosingClassName) ?: "" }
                .thenBy { (it as? CrystalMethodDefinition)?.containingFile?.name ?: "" }
        ) ?: methods.first()

    private fun scope(): GlobalSearchScope = GlobalSearchScope.allScope(element.project)

    /** Resolves only preceding assignments and enclosing parameters, without index access. */
    fun resolveLocalDeclaration(): PsiElement? {
        val local = findLocalDeclaration()
        if (local != null) {
            // If the result is an IDENTIFIER leaf (not PsiNameIdentifierOwner),
            // promote to its parent composite if it implements PsiNameIdentifierOwner.
            // This ensures IntelliJ's rename framework activates (requires
            // element instanceof PsiNameIdentifierOwner in MemberInplaceRenameHandler).
            // Go to Definition still works because getNavigationElement() returns
            // the IDENTIFIER leaf via getNameIdentifier().
            if (local !is PsiNameIdentifierOwner) {
                val parent = local.parent
                if (parent is PsiNameIdentifierOwner &&
                    parent.nameIdentifier === local &&
                    !isDestructuredParameter(parent)) {
                    return parent
                }
            }
            return local
        }
        return null
    }

    private fun findLocalDeclaration(): PsiElement? {
        val containingFile = element.containingFile ?: return null
        var scope: PsiElement? = element.parent
        // Walk up the PSI tree, but NEVER cross the file boundary — climbing into
        // PsiDirectory would traverse the whole project tree and lazily parse
        // every sibling file (including .sh build scripts), freezing the IDE for
        // tens of seconds on Ctrl+B / identifier highlighting.
        while (scope != null && scope !== containingFile) {
            // Walk siblings before the reference looking for assignments like "name = ..."
            var sibling = scope.prevSibling
            while (sibling != null) {
                val assignment = findAssignmentWithName(sibling, name)
                if (assignment != null) return assignment
                val groupedOrMulti = findGroupedOrMultiDeclaration(sibling, name)
                if (groupedOrMulti != null) return groupedOrMulti
                sibling = sibling.prevSibling
            }
            // Bindings declared by an ancestor construct whose body contains
            // the reference: `for x in …` loop variables, `rescue e` bindings.
            // The iterable of a `for` is evaluated in the outer scope, so loop
            // variables bind only inside the statement list.
            val parent = scope.parent
            if (parent is CrystalForStatement) {
                val body = parent.statementList
                if (body != null && PsiTreeUtil.isAncestor(body, element, false)) {
                    val loopVar = parent.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
                    if (loopVar?.text == name) return loopVar
                }
            }
            if (parent is CrystalRescueClause) {
                val binding = parent.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
                if (binding?.text == name &&
                    PsiTreeUtil.isAncestor(parent.statementList, element, false)
                ) {
                    return binding
                }
            }
            // Check parameters if we're inside a method or macro
            if (scope is CrystalMethodDefinition || scope is CrystalMacroDefinition) {
                val paramList = when (scope) {
                    is CrystalMethodDefinition -> scope.parameterList
                    is CrystalMacroDefinition -> scope.parameterList
                    else -> null
                }
                paramList?.parameterList?.forEach { param ->
                    findParameterNameElement(param, name)?.let { return it }
                }
                break // Don't look beyond method boundaries for locals
            }
            // Check block parameters (e.g., |ola| in each do |ola| ... end)
            if (scope is CrystalBlock) {
                val paramList = scope.parameterList
                paramList?.parameterList?.forEach { param ->
                    findParameterNameElement(param, name)?.let { return it }
                }
            }
            // Check proc-literal parameters (e.g., `error` in
            // `->(context, error) { handler.call(context, error) }`). The proc
            // literal nests inside its enclosing def, so this must happen
            // before the method-boundary break below or the lambda's own
            // parameters are unreachable.
            if (scope is CrystalProcLiteral) {
                val paramList = scope.parameterList
                paramList?.parameterList?.forEach { param ->
                    findParameterNameElement(param, name)?.let { return it }
                }
            }
            scope = scope.parent
        }
        return null
    }

    private fun findParameterNameElement(parameter: CrystalParameter, targetName: String): PsiElement? {
        if (isDestructuredParameter(parameter)) {
            return parameter.node.getChildren(null)
                .firstOrNull { it.elementType == CrystalTypes.IDENTIFIER && it.text == targetName }
                ?.psi
        }

        val owner = parameter as? PsiNameIdentifierOwner ?: return null
        val names = parameter.parameterNameInfo()
        if (names.localName != targetName) return null
        return if (names.storageName != null) parameter else owner.nameIdentifier
    }

    private fun isDestructuredParameter(element: PsiElement): Boolean {
        return element is CrystalParameter &&
            element.node.getChildren(null).any { it.elementType == CrystalTypes.LPAREN }
    }

    override fun isReferenceTo(target: PsiElement): Boolean {
        val parameter = resolve() as? CrystalParameter
        if (parameter != null && target is CrystalParameter) {
            val storageName = parameter.parameterNameInfo().storageName
            val sameType = CrystalPsiUtils.getEnclosingType(parameter) === CrystalPsiUtils.getEnclosingType(target)
            if (storageName != null && sameType && storageName == target.parameterNameInfo().storageName) return true
        }
        val storageTarget = target as? CrystalInstanceVarAccess ?: target as? CrystalClassVarAccess
        if (parameter != null && storageTarget != null) {
            val sameType = CrystalPsiUtils.getEnclosingType(element) === CrystalPsiUtils.getEnclosingType(storageTarget)
            if (sameType && parameter.parameterNameInfo().storageName == storageTarget.text) return true
        }
        return super.isReferenceTo(target)
    }

    /**
     * Recursively searches a PSI subtree for a CrystalAssignment node
     * whose variable name matches [targetName].
     *
     * Stops at method/macro/class/struct boundaries to avoid resolving
     * across scope boundaries — a variable in method A should not resolve
     * to an assignment in sibling method B.
     *
     * Also refuses to cross file/directory boundaries — a defensive guard
     * so any future regression in [findLocalDeclaration] cannot cascade into the
     * project tree and lazily parse every sibling file.
     */
    private fun findAssignmentWithName(element: PsiElement, targetName: String): PsiElement? {
        // Don't cross scope boundaries
        if (element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            CrystalPsiUtils.isTypeDefinition(element)) {
            return null
        }
        // Hard boundary: never recurse into files or directories. This is a defensive
        // guard — findLocalDeclaration() also stops at the file boundary, but this ensures
        // that even if the walk escaped, we cannot trigger lazy parsing of every
        // file in the project (which caused 40+ second EDT freezes).
        if (element is PsiFile || element is PsiDirectory) return null
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

    /**
     * Searches a PSI subtree for grouped `(name = …)` and multi
     * `name, other = …` binding targets matching [targetName], mirroring the
     * scope boundaries of [findAssignmentWithName] (and of
     * `CrystalLocalUsageAnalyzer`, whose flow tracks the same bindings).
     */
    private fun findGroupedOrMultiDeclaration(element: PsiElement, targetName: String): PsiElement? {
        if (element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            CrystalPsiUtils.isTypeDefinition(element)
        ) {
            return null
        }
        if (element is PsiFile || element is PsiDirectory) return null
        if (element is CrystalGroupedExpression && element.expressionList.size >= 2) {
            val reference = element.expressionList.first().children.singleOrNull() as? CrystalVariableReference
            val identifier = reference?.node?.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            if (identifier?.text == targetName) return identifier
        }
        if (element is CrystalMultiAssignment) {
            for (target in element.multiAssignTargetList) {
                if (CrystalPsiUtils.multiAssignTargetLocal(target)?.first?.text == targetName) {
                    return CrystalPsiUtils.multiAssignTargetLocal(target)?.first
                }
            }
        }
        for (child in element.children) {
            val result = findGroupedOrMultiDeclaration(child, targetName)
            if (result != null) return result
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: element.node.findChildByType(CrystalTypes.INSTANCE_VAR)
            ?: element.node.findChildByType(CrystalTypes.CLASS_VAR)
            ?: element.node.getChildren(null)
                .firstOrNull { de.magynhard.crystal.lexer.CrystalTokenTypes.KEYWORD_VARIABLES.contains(it.elementType) }
            ?: return element

        // Strip any @/@@ prefix the user may have typed, then re-apply from original token type.
        val bareName = newElementName.removePrefix("@").removePrefix("@")
        val fixedName = when (identNode.elementType) {
            CrystalTypes.INSTANCE_VAR -> "@$bareName"
            CrystalTypes.CLASS_VAR -> "@@$bareName"
            else -> bareName
        }

        // A keyword variable (e.g. `union`) renames to a plain identifier: the
        // new text no longer lexes as the keyword token.
        val targetType = if (de.magynhard.crystal.lexer.CrystalTokenTypes.KEYWORD_VARIABLES.contains(identNode.elementType)) {
            CrystalTypes.IDENTIFIER
        } else {
            identNode.elementType
        }
        val newLeaf = createLeafFromText(element.project, fixedName, targetType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
