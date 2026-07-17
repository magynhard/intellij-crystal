package de.magynhard.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.CrystalFile
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalConstantIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex
import java.util.ArrayDeque

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
/**
 * A stable location of a stdlib definition: a path relative to the stdlib root plus a character
 * offset. Unlike a PsiElement, a [SymbolLoc] can never go stale when a stdlib file is reparsed,
 * so the PsiElement is materialized fresh on every resolve from the current VFS. File-level
 * (internal) so both [CrystalReference] and [CrystalDotCallReference] can reference it.
 */
internal data class SymbolLoc(val relPath: String, val offset: Int)

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

        // 2. StubIndex lookup only (fast — in-memory index, no FileTypeIndex scan).
        // Include the stdlib root explicitly: stdlib symbols come from an
        // AdditionalLibraryRootsProvider (SyntheticLibrary) whose stubs are stored
        // under a scope not covered by allScope, so a plain allScope query misses them.
        val project = element.project
        val baseScope = GlobalSearchScope.allScope(project)
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        val scope = if (stdlibRoot != null) {
            baseScope.union(GlobalSearchScope.fileScope(project, stdlibRoot))
        } else {
            baseScope
        }
        val types = StubIndex.getElements(
            CrystalClassIndex.KEY, name, element.project, scope,
            CrystalNamedElement::class.java
        )
        if (types.isNotEmpty()) {
            val t = types.first()
            if (isUsable(t, element.project)) return t
        }

        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, name, element.project, scope,
            CrystalMethodDefinition::class.java
        )
        if (methods.isNotEmpty()) {
            val m = methods.first()
            if (isUsable(m, element.project)) return m
        }

        // 2b. Constant lookup (project constants only — stdlib constants are handled
        //     by the bounded stdlib cache below, since their stubs are skipped).
        val constants = StubIndex.getElements(
            CrystalConstantIndex.KEY, name, element.project, scope,
            CrystalConstantAssignment::class.java
        )
        if (constants.isNotEmpty()) {
            val c = constants.first()
            if (isUsable(c, element.project)) return c
        }

        // 2c. For a bare method call whose project index missed, prefer a stdlib method
        //     defined on the call site's enclosing class — e.g. `exists?` inside `class File`
        //     should resolve to `File.exists?`, not the first `exists?` in the scanned stdlib
        //     (which would land on `Dir.exists?`). Only applies when the call is a real method
        //     invocation; project methods are already handled by the StubIndex above.
        val enclosingClass = enclosingClassOfCall(element)
        if (enclosingClass != null) {
            val stdlibMethod = resolveStdlibMethod(project, enclosingClass, name)
            if (stdlibMethod != null) return stdlibMethod
        }

        // 3. Bounded stdlib fallback. Stubs indexed from an AdditionalLibraryRootsProvider
        // (SyntheticLibrary) are stored under an internal scope that StubIndex.getElements
        // cannot query with any GlobalSearchScope (verified: allScope / everythingScope /
        // null all return nothing for stdlib symbols, while getAllKeys still lists them).
        // So when the index misses, fall back to a one-time scan of ONLY the known stdlib
        //    root (bounded, cached per project) — never the whole project, to avoid the
        // 90s FileTypeIndex-style freeze.
        return resolveStdlibSymbol(project, name)
    }

    /**
     * If [element] is the variable-reference of a bare method call (inside a
     * method_call_expression / bare_method_call_expression / bare_command_expression),
     * returns the qualified name of the class/module/struct/enum that lexically encloses
     * the call site. Used to prefer a stdlib method defined on that class (e.g. `exists?`
     * inside `class File` → `File.exists?`) over a same-named method elsewhere.
     * Returns null for non-call references or calls not inside a type definition.
     */
    private fun enclosingClassOfCall(element: PsiElement): String? {
        val isMethodCall = element is CrystalMethodCallExpression ||
            element is CrystalBareMethodCallExpression ||
            element is CrystalBareCommandExpression
        if (!isMethodCall) return null
        val enclosing = CrystalPsiUtils.getEnclosingType(element) ?: return null
        return CrystalPsiUtils.buildQualifiedName(enclosing)
    }

    private fun resolveLocal(): PsiElement? {
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
                sibling = sibling.prevSibling
            }
            // Check parameters if we're inside a method or macro
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
            // Check block parameters (e.g. |ola| in each do |ola| ... end)
            if (scope is CrystalBlock) {
                val paramList = scope.parameterList
                paramList?.parameterList?.forEach { param ->
                    val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
                    if (paramIdent?.text == name) return paramIdent.psi
                }
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
     *
     * Also refuses to cross file/directory boundaries — a defensive guard
     * so any future regression in [resolveLocal] cannot cascade into the
     * project tree and lazily parse every sibling file.
     */
    private fun findAssignmentWithName(element: PsiElement, targetName: String): PsiElement? {
        // Don't cross scope boundaries
        if (element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition) {
            return null
        }
        // Hard boundary: never recurse into files or directories. This is a defensive
        // guard — resolveLocal() also stops at the file boundary, but this ensures
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

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: element.node.findChildByType(CrystalTypes.INSTANCE_VAR)
            ?: element.node.findChildByType(CrystalTypes.CLASS_VAR)
            ?: return element

        // Strip any @/@@ prefix the user may have typed, then re-apply from original token type.
        val bareName = newElementName.removePrefix("@").removePrefix("@")
        val fixedName = when (identNode.elementType) {
            CrystalTypes.INSTANCE_VAR -> "@$bareName"
            CrystalTypes.CLASS_VAR -> "@@$bareName"
            else -> bareName
        }

        val newLeaf = createLeafFromText(element.project, fixedName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        /**
         * A stable location of a stdlib definition: a path relative to the stdlib root plus a
         * character offset. Unlike a PsiElement, a [SymbolLoc] can never go stale when a stdlib
         * file is reparsed, so the PsiElement is materialized fresh on every resolve from the
         * current VFS. This eliminates the multi-second "Resolving reference" freeze that the
         * previous PsiElement-caching design caused whenever a reparse invalidated the cache.
         */

        /**
         * Per-project cache of lazily-resolved stdlib symbols, keyed by a qualified name
         * ("Class", "Class::member", or "Class#method"). Stores [SymbolLoc] (stable), never a
         * PsiElement, so the cache is immune to reparse-driven staleness.
         */
        private data class StdlibCache(
            val root: com.intellij.openapi.vfs.VirtualFile,
            val resolved: MutableMap<String, SymbolLoc?>
        )

        private val stdlibCaches =
            java.util.concurrent.ConcurrentHashMap<Project, StdlibCache>()

        private fun cache(project: Project, root: com.intellij.openapi.vfs.VirtualFile): StdlibCache {
            stdlibCaches[project]?.let { if (it.root == root) return it }
            val c = StdlibCache(root, java.util.Collections.synchronizedMap(HashMap<String, SymbolLoc?>()))
            stdlibCaches[project] = c
            return c
        }

        /** Resolve a class/module/struct/enum/constant/method name from the Crystal stdlib to a
         * stable [SymbolLoc] WITHOUT materializing PSI. No-PSI variant of [resolveStdlibSymbol],
         * used by the reference-graph harness. */
        internal fun resolveStdlibSymbolLoc(project: Project, name: String): SymbolLoc? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibSymbolLoc(project, root, name)
        }

        /** Resolve a class/module/struct/enum/constant/method name from the Crystal stdlib
         * (materializes the target PsiElement). See [resolveStdlibSymbolLoc] for the no-PSI variant. */
        fun resolveStdlibSymbol(project: Project, name: String): PsiElement? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibSymbolLoc(project, root, name)?.let { materialize(project, root, it) }
        }

        private fun computeStdlibSymbolLoc(project: Project, root: com.intellij.openapi.vfs.VirtualFile, name: String): SymbolLoc? {
            val c = cache(project, root)
            if (c.resolved.containsKey(name)) return c.resolved[name]
            // Precise, per-file resolution (class/constant or "Class::member").
            val precise = resolveByName(project, root, name)
            if (precise != null) {
                c.resolved[name] = precise
                return precise
            }
            // Fallback: a one-time bounded text scan of the stdlib root. Needed for
            // bare lowercase method names (e.g. `puts`) that have no home-file
            // convention, and for constants the precise per-file lookup cannot
            // locate — either because the name has no canonical file
            // (DEFAULT_CREATE_PERMISSIONS lives in file.cr) or because the
            // definition is nested in a subdirectory (Math::PI lives in
            // math/math.cr). Built once (text scan, no PSI parse — sub-second),
            // then cached per project.
            val loc = globalStdlibData(project, root).symbols[name]
            if (loc != null) {
                c.resolved[name] = loc
                return loc
            }
            c.resolved[name] = null
            return null
        }

        /**
         * Returns true only if [el] is a live PsiElement belonging to [project].
         * A detached element from a reparsed stdlib file reports a stale
         * cross-provider FileViewProvider and throws [PsiInvalidElementAccessException]
         * on `containingFile` access; we treat that as unusable. (Used only for the
         * project StubIndex results above; the stdlib cache no longer stores
         * PsiElements, so it needs no staleness handling.)
         */
        private fun isUsable(el: PsiElement?, project: Project): Boolean {
            if (el == null) return false
            return try {
                el.containingFile?.virtualFile != null && el.manager.project == project
            } catch (_: com.intellij.psi.PsiInvalidElementAccessException) {
                false
            }
        }

        /** No-op: resolution is lazy and per-file, so there is nothing to warm up. */
        fun warmStdlibCache() {}

        /** Expose the bounded stdlib text-symbol table (name → stable [SymbolLoc]) for the
         * reference-graph harness, so it can classify references without materializing PSI. */
        internal fun getStdlibSymbolTable(project: Project): Map<String, SymbolLoc> {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return emptyMap()
            return globalStdlibData(project, root).symbols
        }

        /** Build a stable [SymbolLoc] for an already-resolved PsiElement (e.g. a project StubIndex
         * hit) relative to the stdlib [root]. Returns null when the element is outside the root. */
        internal fun locOf(element: PsiElement, root: com.intellij.openapi.vfs.VirtualFile): SymbolLoc? {
            val cf = element.containingFile as? CrystalFile ?: return null
            val vf = cf.virtualFile ?: return null
            val relPath = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(vf, root) ?: return null
            val owner = element as? PsiNameIdentifierOwner
            val ident = owner?.nameIdentifier
            val offset = ident?.textOffset ?: element.textOffset
            return SymbolLoc(relPath, offset)
        }

        /** Resolve a `ClassName#method` from the stdlib to a stable [SymbolLoc] WITHOUT
         * materializing PSI. Never returns a method belonging to a different class, so project
         * classes (handled by the StubIndex) and coincidentally-named stdlib symbols are never
         * hijacked. Used by the reference-graph harness for a fast (no-PSI) whole-stdlib scan.
         *
         * [className] may be a fully-qualified name (e.g. `Crystal::System::Dir`) — the precise
         * per-file lookup then uses the namespace's home file, and on a miss the bounded stdlib
         * text scan (keyed by `Class#method`) is consulted so nested-class methods resolve. */
        internal fun resolveStdlibMethodLoc(project: Project, className: String, methodName: String): SymbolLoc? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibMethodLoc(project, root, className, methodName)
        }

        /** Resolve a `ClassName#method` from the stdlib, precisely and fast (materializes the
         * target PsiElement). See [resolveStdlibMethodLoc] for the no-PSI variant. */
        fun resolveStdlibMethod(project: Project, className: String, methodName: String): PsiElement? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibMethodLoc(project, root, className, methodName)?.let { materialize(project, root, it) }
        }

        private fun computeStdlibMethodLoc(project: Project, root: com.intellij.openapi.vfs.VirtualFile, className: String, methodName: String): SymbolLoc? {
            val key = "$className#$methodName"
            val c = cache(project, root)
            if (c.resolved.containsKey(key)) return c.resolved[key]
            var result = resolveMemberInFile(project, root, className, methodName)
            if (result == null) {
                // Bounded fallback: the text scan keys methods by qualified `Class#method`
                // (see buildStdlibData), covering classes whose definition lives in a subfile of
                // the namespace home — e.g. `Crystal::System::Dir#info` lives in
                // crystal/system/unix/dir.cr, not a `Dir.cr`.
                result = globalStdlibData(project, root).symbols[key]
            }
            c.resolved[key] = result
            return result
        }

        /** Resolve "Class" or "Class::member" via the class's home file, returning a stable [SymbolLoc]. */
        private fun resolveByName(project: Project, root: com.intellij.openapi.vfs.VirtualFile, name: String): SymbolLoc? {
            if (name.contains("::")) {
                val idx = name.lastIndexOf("::")
                val className = name.substring(0, idx)
                val member = name.substring(idx + 2)
                return resolveMemberInFile(project, root, className, member)
            }
            return resolveMemberInFile(project, root, name, null)
        }

        /**
         * Parses ONLY the stdlib file that conventionally defines [className] and finds
         * [memberName] (a method or constant) inside it, returning a stable [SymbolLoc].
         * If [memberName] is null, finds the class/constant definition itself. Returns null
         * (never a wrong-class match) when the home file or the member is not found.
         */
        private fun resolveMemberInFile(
            project: Project,
            root: com.intellij.openapi.vfs.VirtualFile,
            className: String,
            memberName: String?
        ): SymbolLoc? {
            val relPath = className.replace("::", "/").lowercase() + ".cr"
            val file = com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile(relPath, root) ?: return null
            @Suppress("DEPRECATION")
            return com.intellij.openapi.application.ReadAction.compute<SymbolLoc?, Throwable> {
                val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(file) ?: return@compute null
                val off = findOffsetInPsi(psi, className, memberName) ?: return@compute null
                SymbolLoc(relPath, off)
            }
        }

        private fun findOffsetInPsi(psi: PsiElement, className: String, memberName: String?): Int? {
            var fallback: Int? = null
            psi.accept(object : com.intellij.psi.PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (memberName == null) {
                        // Looking for the class/constant definition itself.
                        val named = element as? CrystalNamedElement
                        if (named != null && named !is CrystalConstantAssignment) {
                            if (CrystalPsiUtils.buildQualifiedName(named) == className) {
                                fallback = nameOffset(named); stopWalking(); return
                            }
                        }
                        val const = element as? CrystalConstantAssignment
                        if (const != null && const.name == className) {
                            fallback = nameOffset(const); stopWalking(); return
                        }
                    } else {
                        val enclosing = CrystalPsiUtils.getEnclosingType(element)
                            ?.let { CrystalPsiUtils.buildQualifiedName(it) }
                        val method = element as? CrystalMethodDefinition
                        if (method != null && method.name == memberName) {
                            if (enclosing == className) { fallback = nameOffset(method); stopWalking(); return }
                            if (fallback == null) fallback = nameOffset(method)
                        }
                        val const = element as? CrystalConstantAssignment
                        if (const != null && const.name == memberName && enclosing == className) {
                            fallback = nameOffset(const); stopWalking(); return
                        }
                    }
                    super.visitElement(element)
                }
            })
            return fallback
        }

        private fun nameOffset(el: PsiElement): Int {
            val owner = el as? PsiNameIdentifierOwner
            val ident = owner?.nameIdentifier
            return ident?.textOffset ?: el.textOffset
        }

        /** Materialize a fresh PsiElement for [loc] from the current VFS. Always returns a live
         * element (never stale). Promotes a bare identifier leaf to its named owner so the
         * rename framework activates. */
        private fun materialize(project: Project, root: com.intellij.openapi.vfs.VirtualFile, loc: SymbolLoc): PsiElement? {
            val file = com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile(loc.relPath, root) ?: return null
            @Suppress("DEPRECATION")
            return com.intellij.openapi.application.ReadAction.compute<PsiElement?, Throwable> {
                val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(file) ?: return@compute null
                val leaf = psi.findElementAt(loc.offset) ?: return@compute null
                if (leaf !is PsiNameIdentifierOwner) {
                    val p = leaf.parent
                    if (p is PsiNameIdentifierOwner) return@compute p
                }
                leaf
            }
        }

        /** Bounded (stdlib-only) text scan, used as a fallback for bare lowercase method names
         * and constants that have no home-file convention. Built lazily once per project.
         * Stores stable (relPath, offset) locations — never PsiElements — so it can never go
         * stale and never triggers a multi-second reparse of the whole stdlib. A text scan of
         * ~2154 files runs in well under a second, versus the 70s the old PSI-walk required. */
        private data class StdlibData(
            val symbols: Map<String, SymbolLoc>
        )

        private val globalStdlibCaches =
            java.util.concurrent.ConcurrentHashMap<Project, Pair<com.intellij.openapi.vfs.VirtualFile, StdlibData>>()

        private fun globalStdlibData(project: Project, root: com.intellij.openapi.vfs.VirtualFile): StdlibData {
            globalStdlibCaches[project]?.let { (cachedRoot, cached) ->
                if (cachedRoot == root) return cached
            }
            val data = buildStdlibData(root)
            globalStdlibCaches[project] = root to data
            return data
        }

        private fun buildStdlibData(root: com.intellij.openapi.vfs.VirtualFile): StdlibData {
            val symbols = HashMap<String, SymbolLoc>()
            // Names whose canonical-file definition (file base name == symbol name) is
            // already stored. Lets us upgrade a first-seen arbitrary definition to the
            // canonical one when we later encounter it during the VFS walk.
            val hasCanonical = HashSet<String>()
            com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively(root, object : com.intellij.openapi.vfs.VirtualFileVisitor<Any>() {
                override fun visitFile(file: com.intellij.openapi.vfs.VirtualFile): Boolean {
                    if (file.isDirectory) return true
                    if (file.extension != "cr") return true
                    val relPath = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(file, root) ?: return true
                    scanFileText(file, relPath, symbols, hasCanonical)
                    return true
                }
            })
            return StdlibData(symbols)
        }

        /**
         * Text-based symbol discovery for a single stdlib file. Finds top-level
         * `alias`, `class`/`struct`/`module`/`enum`/`lib`/`annotation`, SCREAMING_SNAKE constant,
         * and `def`/`macro` definitions by regex over the raw text, tracking the enclosing
         * namespace via a stack so namespaced keys (`Foo::Bar`, `Foo#baz`) are produced. This
         * covers symbols the grammar failed to parse into nodes because of an unrelated parse
         * error elsewhere in the same file — without ever parsing PSI.
         */
        private fun scanFileText(
            file: com.intellij.openapi.vfs.VirtualFile,
            relPath: String,
            symbols: MutableMap<String, SymbolLoc>,
            hasCanonical: MutableSet<String>
        ) {
            val text = try {
                String(file.contentsToByteArray(), Charsets.UTF_8)
            } catch (_: Throwable) {
                return
            }
            // Qualified-namespace stack: each entry is the FULL qualified name of the enclosing
            // type (e.g. "File", "File::Info"), so a nested definition keys as
            // "<qualified>::<name>" and a nested member as "<qualified>#<method>".
            val stack = ArrayDeque<String>()
            // Parallel stack tracking WHY each frame was opened: "type" for a class/struct/
            // module/enum/lib/annotation, "other" for a method/block (`def`, `if`, `do`, `{`…).
            // Only a "type" close pops the NAMESPACE stack — a method body's `end` must NOT
            // pop the enclosing class, or every method after the first would lose its namespace
            // (this previously dropped `String#upcase`, `Array#size`, … from the symbol table).
            val openKinds = ArrayDeque<String>()
            val blockKwRe = Regex("""\b(def|macro|if|unless|while|until|case|begin|do)\b""")
            val endRe = Regex("""\bend\b""")
            val typeRe = Regex("""^\s*(?:(?:abstract|final|private)\s+)*(?:class|struct|module|enum|lib|annotation)\s+([A-Z][\w:]*)""")
            val aliasRe = Regex("""^\s*alias\s+([A-Z]\w*(?:::[A-Z]\w*)*)""")
            val constRe = Regex("""^\s*([A-Z][A-Z0-9_]*)\s*=""")
            val defRe = Regex("""^\s*(?:def|macro)\s+((?:self\.)?(?:[A-Z][\w:]*)?\.?[a-zA-Z_]\w*[!?]?|\[[\]=]?|<=>)""")
            val genRe = Regex("""^\s*(getter|setter|property)\b\s*(.+)$""")

            fun popOpen() {
                if (openKinds.isNotEmpty()) {
                    val k = openKinds.removeLast()
                    if (k == "type" && stack.isNotEmpty()) stack.removeLast()
                }
            }

            val lines = text.lines()
            var pos = 0
            for (raw in lines) {
                val lineStart = pos
                pos += raw.length + 1 // +1 for the newline separator
                // Type / annotation / lib definition — opens a NAMESPACE frame.
                typeRe.find(raw)?.let { m ->
                    val full = m.groupValues[1]
                    val name = full.substringAfterLast("::")
                    // Offset of the (last-segment) name identifier: group 1's start — NOT the
                    // whole-match start (which is the leading whitespace) — plus the prefix length.
                    val g1 = m.groups[1]!!
                    val offset = lineStart + g1.range.first + (full.length - name.length)
                    val enclosing = stack.lastOrNull()
                    val qualified = if (enclosing != null) "$enclosing::$full" else full
                    addSymbol(symbols, hasCanonical, relPath, offset, name, qualified, isType = true)
                    stack.addLast(qualified)
                    openKinds.addLast("type")
                    // Same-line `end`/`}` closes this frame immediately (e.g. `struct Foo; end`).
                    val closes = endRe.findAll(raw).count() + raw.count { it == '}' }
                    repeat(closes) { popOpen() }
                    continue
                }
                // alias Name (= ...)
                aliasRe.find(raw)?.let { m ->
                    val full = m.groupValues[1]
                    val name = full.substringAfterLast("::")
                    val g1 = m.groups[1]!!
                    val offset = lineStart + g1.range.first + (full.length - name.length)
                    val enclosing = stack.lastOrNull()
                    val qualified = if (enclosing != null) "$enclosing::$name" else name
                    addSymbol(symbols, hasCanonical, relPath, offset, name, qualified, isType = false)
                    continue
                }
                // SCREAMING_SNAKE constant assignment.
                constRe.find(raw)?.let { m ->
                    val name = m.groupValues[1]
                    val g1 = m.groups[1]!!
                    val offset = lineStart + g1.range.first
                    val enclosing = stack.lastOrNull()
                    val qualified = if (enclosing != null) "$enclosing::$name" else name
                    addSymbol(symbols, hasCanonical, relPath, offset, name, qualified, isType = false)
                    continue
                }
                // def / macro method definitions (column-0 or indented).
                defRe.find(raw)?.let { m ->
                    val sig = m.groupValues[1]
                    val (recv, mname) = parseDefSig(sig)
                    val g1 = m.groups[1]!!
                    val offset = lineStart + g1.range.first + (sig.length - mname.length)
                    val ns = recv ?: stack.lastOrNull()
                    // For bare top-level builtins (no enclosing namespace) force the simple key
                    // so e.g. `raise` jumps to raise.cr, not a private `def raise` elsewhere.
                    addMethodSymbol(symbols, relPath, offset, ns, mname)
                    // A method body owns its own `end`/`}`; push an "other" frame (and balance
                    // a same-line close) so the enclosing type's namespace frame survives it.
                    openKinds.addLast("other")
                    val closes = endRe.findAll(raw).count() + raw.count { it == '}' }
                    repeat(closes) { popOpen() }
                    continue
                }
                // `getter` / `setter` / `property` macros generate method definitions
                // (e.g. `getter size : Int32` → `def size` + `def size=`). These are pervasive
                // in the stdlib (Array#size, Hash#keys, …) and would otherwise be invisible to
                // the symbol table. Expand them into the generated reader/writer names so
                // receiver-typed lookups resolve. `getter`/`property` → reader `name`;
                // `setter`/`property` → writer `name=`. The offset points at the generated
                // name token (not the macro line start) so materialize lands on the identifier.

                genRe.find(raw)?.let { m ->
                    val kind = m.groupValues[1]
                    val names = m.groupValues[2].split(',').mapNotNull { tok ->
                        Regex("""\s*([a-zA-Z_]\w*[!?]?)""").find(tok)?.groupValues?.get(1)
                    }
                    val ns = stack.lastOrNull()
                    for (nm in names) {
                        val idx = raw.indexOf(nm)
                        val off = if (idx >= 0) lineStart + idx else lineStart
                        if (kind != "setter") addMethodSymbol(symbols, relPath, off, ns, nm)
                        if (kind != "getter") addMethodSymbol(symbols, relPath, off, ns, "$nm=")
                    }
                    continue
                }
                // Any other line: balance block opens (`def`/control-flow keywords, `{`)
                // against closes (`end`, `}`) so the namespace stack stays correct.
                val opens = blockKwRe.findAll(raw).count() + raw.count { it == '{' }
                val closes = endRe.findAll(raw).count() + raw.count { it == '}' }
                repeat(opens) { openKinds.addLast("other") }
                repeat(closes) { popOpen() }
            }
        }

        /** Split a `def` signature into (receiver, methodName). `self.foo` → (null, foo);
         *  `Foo.bar` → (Foo, bar); bare `foo` → (null, foo). */
        private fun parseDefSig(sig: String): Pair<String?, String> {
            if (sig.startsWith("self.")) return null to sig.substring(5)
            val dot = sig.lastIndexOf('.')
            if (dot > 0) return sig.substring(0, dot) to sig.substring(dot + 1)
            return null to sig
        }

        private fun addSymbol(
            symbols: MutableMap<String, SymbolLoc>,
            hasCanonical: MutableSet<String>,
            relPath: String,
            offset: Int,
            name: String,
            qualified: String,
            isType: Boolean
        ) {
            val loc = SymbolLoc(relPath, offset)
            symbols.putIfAbsent(qualified, loc)
            // Canonical: file base name matches the symbol (e.g. String -> string.cr), so
            // Ctrl+Click lands on the primary definition, not an arbitrary reopening.
            val canonical = isType && java.io.File(relPath).nameWithoutExtension.equals(name, ignoreCase = true)
            if (!symbols.containsKey(name) || (canonical && !hasCanonical.contains(name))) {
                symbols[name] = loc
                if (canonical) hasCanonical.add(name)
            }
        }

        // Register a `Class#method` (and bare `method` for top-level defs) symbol-table entry.
        // Used by both `def`/`macro` lines and expanded `getter`/`setter`/`property` macros.
        private fun addMethodSymbol(
            symbols: MutableMap<String, SymbolLoc>,
            relPath: String,
            offset: Int,
            ns: String?,
            mname: String
        ) {
            val loc = SymbolLoc(relPath, offset)
            val key = if (ns != null) "$ns#$mname" else mname
            if (ns == null) symbols.putIfAbsent(mname, loc)
            symbols.putIfAbsent(key, loc)
        }
    }
}
