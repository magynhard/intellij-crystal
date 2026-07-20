package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Computes the completion prefix from the raw document text before the caret,
 * treating a leading `@` or `@@` as part of the variable name.
 *
 * Examples (text before caret → returned prefix):
 *   "@"       → "@"
 *   "@@"      → "@@"
 *   "@@vari"  → "@@vari"
 *   "@foo"    → "@foo"
 *   "mein"    → "mein"
 *   "Str"     → "Str"
 *   "foo.bar" → "bar"
 */
internal fun computeCompletionPrefix(editor: com.intellij.openapi.editor.Editor, offset: Int): String {
    val text = editor.document.charsSequence.subSequence(0, offset).toString()
    val match = "([@]@?[A-Za-z0-9_]*)$".toRegex().find(text)
    return match?.groupValues?.get(1) ?: ""
}

/**
 * Code completion contributor for Crystal.
 *
 * Provides 3 completion modes:
 * 1. Dot after CONSTANT (Class.): static methods of that class
 * 2. Dot after identifier (var.): instance methods based on inferred type
 * 3. Free-text: all classes + all methods + local variables/parameters
 */
class CrystalCompletionContributor : CompletionContributor() {

    init {
        // General pattern: any identifier position in Crystal files
        val crystalPattern = PlatformPatterns.psiElement()
            .withLanguage(CrystalLanguage)

        extend(CompletionType.BASIC, crystalPattern, CrystalCompletionProvider())
    }

    /**
     * Single provider that dispatches based on context (dot-completion vs free-text).
     */
    private class CrystalCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = parameters.position
            val project = position.project

            // Require path-completion: caret inside the string of a `require "..."`.
            // Bypasses the global string-literal suppression below.
            val requirePathPrefix = CrystalRequireCompletionProvider.getPathPrefixInsideRequireString(
                position, parameters.editor, parameters.offset
            )
            if (requirePathPrefix != null) {
                val pathLookups = CrystalRequireCompletionProvider.getPathLookups(
                    position, project, requirePathPrefix, parameters.originalFile
                )
                if (pathLookups.isEmpty()) return
                // Use an empty-prefix matcher so our pre-filtered lookups pass
                // through. IntelliJ's CamelHumpMatcher would compute a prefix
                // like "src/" from the document text and reject lookups like
                // "main" / "test" that don't start with "src/". The custom
                // InsertHandler then rewrites the whole require-path content
                // to the full path (e.g. "./src/"), restoring path indicators.
                val pathResult = result.withPrefixMatcher(
                    result.prefixMatcher.cloneWithPrefix("")
                )
                for (lookup in pathLookups) {
                    pathResult.addElement(lookup)
                }
                return
            }

            // Suppress completion inside string literals (but not inside interpolation)
            if (isInsideStringLiteral(position)) return

            // Suppress completion after numeric literals (user is typing a number, not a name)
            if (isAfterNumericLiteral(position)) return

            // `require` keyword synthesis: typing `req…` at a top-level
            // statement position offers the `require` pseudo-keyword (no `def
            // require` exists in stdlib). Selecting it inserts `require ""`
            // and schedules the path-completion popup. See docs/specs/require.md.
            val requirePrefix = computeCompletionPrefix(parameters.editor, parameters.offset)
            val lowercaseRPrefix = requirePrefix.isEmpty() || requirePrefix[0].isLowerCase()
            if (lowercaseRPrefix && CrystalRequireCompletionProvider.isAtTopLevel(position) &&
                result.prefixMatcher.prefixMatches("require")
            ) {
                result.addElement(CrystalRequireCompletionProvider.getKeywordLookup())
            }

            // Case 4: After `def ` inside a class/struct body — offer override methods
            if (isAfterDefKeywordInClassBody(position)) {
                for (lookup in CrystalOverrideMethodProvider.getOverrideLookups()) {
                    result.addElement(lookup)
                }
                // Don't return — also allow normal free-text completion
            }

            // Case 5: Type annotation context — after `:` in parameter or return type position
            if (isInTypeAnnotationContext(position)) {
                for (lookup in CrystalTypeCompletionProvider.getTypeLookups(position, project)) {
                    result.addElement(lookup)
                }
                return
            }

            // Case 6: Class/struct/module body level — macros and keywords (not inside a method)
            if (isInClassBodyNotMethod(position)) {
                for (lookup in CrystalClassBodyCompletionProvider.getClassBodyLookups()) {
                    result.addElement(lookup)
                }
                // Don't return — also allow normal free-text completion
            }

            // Case 7: Annotation context — after `@[`
            if (isInAnnotationContext(position)) {
                for (lookup in CrystalAnnotationCompletionProvider.getAnnotationLookups()) {
                    result.addElement(lookup)
                }
                return
            }

            // Check if we're after a dot
            val prevLeaf = getPreviousNonWhitespaceLeaf(position)
            if (prevLeaf != null && prevLeaf.text == ".") {
                val beforeDot = getPreviousNonWhitespaceLeaf(prevLeaf)
                if (beforeDot != null) {
                    val beforeDotText = beforeDot.text

                    // Case 1: CONSTANT. (Class.method)
                    if (beforeDotText.isNotEmpty() && beforeDotText[0].isUpperCase()) {
                        // Check if this CONSTANT is part of a namespace_access (e.g. Foo::Sub.space)
                        val nsAccess = PsiTreeUtil.getParentOfType(beforeDot, CrystalNamespaceAccess::class.java, false)
                        val staticMethods = if (nsAccess != null) {
                            // Namespace receiver: build full path, filter by qualified enclosing class
                            val qualifiedName = de.magynhard.crystal.psi.CrystalPsiUtils.buildNamespacePath(nsAccess)
                            val allMethods = CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
                            allMethods.filter { method ->
                                val enclosing = de.magynhard.crystal.psi.CrystalPsiUtils.getEnclosingType(method)
                                enclosing != null && de.magynhard.crystal.psi.CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
                            }
                        } else {
                            // Simple constant receiver (e.g. Apfel.tanzen)
                            CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
                        }
                        for (method in staticMethods) {
                            result.addElement(CrystalCompletionHelper.buildMethodLookup(method))
                        }
                        if (staticMethods.none { it.name == "new" }) {
                            // Fallback 1: record macro — offer "new" with record parameters
                            val recordDef = CrystalCompletionHelper.findRecordDefinition(beforeDotText, parameters.originalFile)
                            if (recordDef != null) {
                                result.addElement(CrystalCompletionHelper.buildRecordNewLookup(recordDef, beforeDotText))
                            } else if (nsAccess == null && CrystalCompletionHelper.canInstantiate(beforeDotText, project)) {
                                // Fallback 2: only for simple constants, not namespace paths
                                result.addElement(CrystalCompletionHelper.buildNewLookup(beforeDotText, project, parameters.originalFile))
                            }
                        }
                        return
                    }

                    // Case 2: identifier. (variable.method or @instance_var.method)
                    val cleanedText = beforeDotText.removePrefix("@")
                    if (cleanedText.isNotEmpty() && cleanedText[0].isLowerCase()) {
                        val inferredType = CrystalTypeInference.inferType(cleanedText, beforeDot, project)
                        if (inferredType != null) {
                            for (lookup in CrystalCompletionHelper.getMethodsAsLookups(inferredType, project)) {
                                result.addElement(lookup)
                            }
                        }
                        return
                    }
                }
            }

            // Case 1b: Double-colon after CONSTANT (Foo::<caret>) — show nested types and class constants
            if (prevLeaf != null && prevLeaf.text == "::") {
                val beforeDoubleColon = getPreviousNonWhitespaceLeaf(prevLeaf)
                if (beforeDoubleColon != null) {
                    val beforeText = beforeDoubleColon.text
                    if (beforeText.isNotEmpty() && beforeText[0].isUpperCase()) {
                        for (lookup in CrystalTypeCompletionProvider.getEnclosingTypeLookups(beforeText, project)) {
                            result.addElement(lookup)
                        }
                        addClassConstants(beforeText, project, result)
                        return
                    }
                }
            }

            // Case 3: Free-text completion — scope items + classes (uppercase only)
            // Treat a leading @ / @@ as part of the variable name so instance (@foo)
            // and class (@@bar) variables are suggested even when only the sigil is typed.
            val actualPrefix = computeCompletionPrefix(parameters.editor, parameters.offset)
            val isVarPrefix = actualPrefix.startsWith("@")
            val effectiveResult = if (isVarPrefix) {
                result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(actualPrefix))
            } else {
                result
            }
            val isUppercase = actualPrefix.isNotEmpty() && actualPrefix[0].isUpperCase()

            addLocalCompletions(position, parameters, effectiveResult)

            // Only suggest classes and stdlib types when prefix starts with uppercase.
            // When the prefix is a variable sigil (@ / @@), skip classes entirely.
            if (actualPrefix.isEmpty() || isUppercase) {
                for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                    effectiveResult.addElement(lookup)
                }
                addAllClasses(project, effectiveResult)
                addFileLevelConstants(parameters.originalFile, effectiveResult)
            }
        }

        private fun addAllClasses(project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
            for (className in CrystalCompletionHelper.getAllClassNames(project)) {
                result.addElement(CrystalCompletionHelper.buildClassLookup(className))
            }
        }

        private fun addFileLevelConstants(file: com.intellij.psi.PsiFile, result: CompletionResultSet) {
            val constants = PsiTreeUtil.findChildrenOfType(file, CrystalConstantAssignment::class.java)
            for (constant in constants) {
                val constantToken = constant.node.findChildByType(CrystalTypes.CONSTANT)
                if (constantToken != null) {
                    val lookup = LookupElementBuilder.create(constantToken.text)
                        .withIcon(AllIcons.Nodes.Field)
                        .withTypeText("constant", true)
                    result.addElement(lookup)
                }
            }
        }

        private fun addClassConstants(className: String, project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
            val typeResult = CrystalCompletionHelper.findTypeByName(className, project) ?: return
            val classBody = when (val element = typeResult.element) {
                is CrystalClassDefinition -> element.classBody
                is CrystalStructDefinition -> element.classBody
                is CrystalModuleDefinition -> element.classBody
                else -> null
            } ?: return
            val constants = PsiTreeUtil.findChildrenOfType(classBody, CrystalConstantAssignment::class.java)
            for (constant in constants) {
                val constantToken = constant.node.findChildByType(CrystalTypes.CONSTANT)
                if (constantToken != null) {
                    val lookup = LookupElementBuilder.create(constantToken.text)
                        .withIcon(AllIcons.Nodes.Field)
                        .withTypeText("constant in $className", true)
                    result.addElement(lookup)
                }
            }
        }

        private fun addLocalCompletions(position: PsiElement, parameters: CompletionParameters, result: CompletionResultSet) {
            val seen = mutableSetOf<String>()

            // 1. Block parameters (highest priority) — from all enclosing blocks
            var currentBlock = PsiTreeUtil.getParentOfType(position, CrystalBlock::class.java)
            while (currentBlock != null) {
                val paramList = currentBlock.parameterList
                if (paramList != null) {
                    for (param in paramList.parameterList) {
                        val name = CrystalCompletionHelper.extractParameterName(param) ?: continue
                        if (seen.add(name)) {
                            result.addElement(prioritizedLookup(name, AllIcons.Nodes.Parameter, "parameter", 120.0))
                        }
                    }
                }
                currentBlock = PsiTreeUtil.getParentOfType(currentBlock.parent, CrystalBlock::class.java)
            }

            // 2. For-loop variables — collect IDENTIFIERs before IN keyword
            val forStmt = PsiTreeUtil.getParentOfType(position, CrystalForStatement::class.java)
            if (forStmt != null) {
                for (child in forStmt.children) {
                    if (child.node?.elementType == CrystalTypes.IDENTIFIER) {
                        if (seen.add(child.text)) {
                            result.addElement(prioritizedLookup(child.text, AllIcons.Nodes.Variable, "for variable", 90.0))
                        }
                    }
                }
            }

            // 3. Method parameters (priority 100)
            val method = PsiTreeUtil.getParentOfType(position, CrystalMethodDefinition::class.java)
            if (method != null) {
                for (param in method.parameterList?.parameterList ?: emptyList()) {
                    val name = CrystalCompletionHelper.extractParameterName(param) ?: continue
                    if (seen.add(name)) {
                        result.addElement(prioritizedLookup(name, AllIcons.Nodes.Parameter, "parameter", 100.0))
                    }
                }
            }

            // 4. Local variables (method/block scoped, forward-reference excluded)
            val scope = findCompletionScope(position)
            if (scope != null) {
                val assignments = PsiTreeUtil.findChildrenOfType(scope, CrystalAssignment::class.java)
                for (assignment in assignments) {
                    if (assignment.textOffset >= position.textOffset) continue
                    val child = assignment.firstChild ?: continue
                    // Handle raw IDENTIFIER tokens (local variables)
                    if (child.node?.elementType == CrystalTypes.IDENTIFIER) {
                        val text = child.text ?: continue
                        if (seen.add(text)) {
                            result.addElement(prioritizedLookup(text, AllIcons.Nodes.Variable, "local", 50.0))
                        }
                    }
                }
            }

            // 4b. Instance + class variables of the enclosing class (all methods).
            //     Walk the entire file, collect all @/@@ variable accesses, stop at
            //     nested type boundaries. This handles the bare `@` parse error where
            //     methods after the caret become loose file-level tokens outside the
            //     truncated class PSI node.
            collectClassVariables(parameters.originalFile) { name, typeText ->
                if (seen.add(name)) {
                    result.addElement(prioritizedLookup(name, AllIcons.Nodes.Variable, typeText, 40.0))
                }
            }

            // 5. Class methods of enclosing class (priority 30) + inherited (priority 20)
            if (method != null) {
                val enclosingClassName = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosingClassName != null) {
                    val project = position.project
                    val searchScope = GlobalSearchScope.allScope(project)
                    addClassMethods(enclosingClassName, 30.0, searchScope, project, seen, result)

                    // Inherited: direct superclass
                    val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
                    val superClassName = enclosingClass?.superclassClause?.typeReference?.text
                    if (superClassName != null && superClassName != enclosingClassName) {
                        addClassMethods(superClassName, 20.0, searchScope, project, seen, result)
                    }
                }
            }

            // 6. Top-level (global) methods — priority 0.
            //    Includes stdlib helpers (puts, pp, p, print, …) and project top-level defs.
            //    Offered in every context (top-level or inside a method/class body).
            addTopLevelMethods(position, position.project, seen, result)
        }

        /**
         * Adds all top-level `def` methods to the result.
         *
         * Two sources are merged (dedup via [seen]):
         * 1. **Current file PSI** — scanned immediately so that just-typed / unsaved
         *    top-level defs are suggested even before the stub index is built or
         *    updated. Top-level defs are hoisted in Crystal (callable above their
         *    definition site within the file), so the entire file is scanned without
         *    the forward-reference restriction that applies to local variables.
         * 2. **Stub index** (`CrystalTopLevelMethodIndex`) — cross-file project
         *    defs plus stdlib top-level helpers (puts, pp, …). Filtered by the
         *    result's prefix matcher before loading any PSI to keep the popup
         *    responsive.
         *
         * Methods already present in [seen] are skipped.
         */
        private fun addTopLevelMethods(
            position: PsiElement,
            project: com.intellij.openapi.project.Project,
            seen: MutableSet<String>,
            result: CompletionResultSet
        ) {
            // 1. Current file — immediate, no index dependency.
            val file = position.containingFile
            if (file != null) {
                for (method in PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)) {
                    // Skip methods inside a class/module/struct/enum — those are class methods,
                    // not top-level. They are reached via dot-completion on their enclosing type.
                    if (CrystalCompletionHelper.getEnclosingClassName(method) != null) continue
                    val name = method.name ?: continue
                    if (!seen.add(name)) continue
                    result.addElement(CrystalCompletionHelper.buildMethodLookup(method, 0.0))
                }
            }

            // 2. Cross-file + stdlib via stub index.
            //    Pre-filter by the prefix matcher (in-memory string match) before loading
            //    any PSI from the index, so non-matching names are skipped cheaply.
            //
            //    Skip the stub-index query entirely while the index is still
            //    being built (DumbService.isDumb). During a plugin update or
            //    project open, this avoids blocking the EDT on a synchronous
            //    StubIndex build; the user still gets step 1 (current-file
            //    top-level methods) immediately, and the cross-file/stdlib
            //    lookups resume once indexing finishes. Without this gate,
            //    typing a single character during indexing can trigger a
            //    "Analyzing project..." modal that hangs for minutes when
            //    the stdlib root is over-broad (Crystal 1.20's flat layout
            //    includes compiler/, lib_c/, llvm/ in the distribution).
            if (DumbService.isDumb(project)) return
            val matcher = result.prefixMatcher
            for (methodName in CrystalCompletionHelper.getAllTopLevelMethodNames(project)) {
                if (methodName in seen) continue
                if (!matcher.prefixMatches(methodName)) continue
                try {
                    val methods = CrystalCompletionHelper.getTopLevelMethodsByName(methodName, project)
                    val method = methods.firstOrNull() ?: continue
                    seen.add(methodName)
                    result.addElement(CrystalCompletionHelper.buildMethodLookup(method, 0.0))
                } catch (_: Throwable) {
                    // Skip a single broken element — don't let it abort the whole completion
                    // (which would fall back to word completion with no icons/signatures).
                }
            }
        }

        /**
         * Adds methods of a specific class (via CrystalMethodByClassIndex) to the result.
         */
        private fun addClassMethods(
            className: String,
            priority: Double,
            scope: GlobalSearchScope,
            project: com.intellij.openapi.project.Project,
            seen: MutableSet<String>,
            result: CompletionResultSet
        ) {
            val methods = CrystalIndexService.findMethodsByClass(className, project, scope)
            for (method in methods) {
                val name = method.name ?: continue
                if (seen.add(name)) {
                    result.addElement(CrystalCompletionHelper.buildMethodLookup(method, priority))
                }
            }
        }

        /**
         * Finds the scope element for local variable completion.
         * Crystal uses method-scoped local variables (like Ruby).
         */
        private fun findCompletionScope(element: PsiElement): PsiElement? {
            // Inside a method — scan the method body
            val method = PsiTreeUtil.getParentOfType(element, CrystalMethodDefinition::class.java)
            if (method != null) return method
            // Inside a block without method — scan the block
            val block = PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java)
            if (block != null) return block
            // Top level — scan the file
            return element.containingFile
        }

        /**
         * Collects all `@instance` and `@@class` variables reachable from [file].
         * Walks file-level PSI children and recurses into everything, but stops
         * at nested class/module/struct/enum boundaries to prevent variable leakage.
         *
         * This is intentionally file-level, not class-level — the bare `@` parse
         * error can cause the class PSI node to be truncated, leaving methods
         * defined after the caret as loose file-level tokens. A class-scoped search
         * would miss their variables.
         */
        private fun collectClassVariables(
            file: PsiElement,
            add: (name: String, typeText: String) -> Unit
        ) {
            var enteredEnclosing = false
            fun visit(element: PsiElement) {
                if (enteredEnclosing &&
                    (element is CrystalClassDefinition || element is CrystalModuleDefinition ||
                     element is CrystalStructDefinition || element is CrystalEnumDefinition)) {
                    return
                }
                when (element) {
                    is CrystalInstanceVarAccess -> {
                        val name = element.name ?: return
                        add(name, "instance variable")
                    }
                    is CrystalClassVarAccess -> {
                        val name = element.name ?: return
                        add(name, "class variable")
                    }
                }
                // Handle raw INSTANCE_VAR/CLASS_VAR tokens (e.g. after a parse error
                // where the parser didn't wrap them in CrystalInstanceVarAccess composites)
                val tokenType = element.node?.elementType
                if (tokenType == CrystalTypes.INSTANCE_VAR) {
                    add(element.text, "instance variable")
                } else if (tokenType == CrystalTypes.CLASS_VAR) {
                    add(element.text, "class variable")
                }
                if (!enteredEnclosing &&
                    (element is CrystalClassDefinition || element is CrystalModuleDefinition ||
                     element is CrystalStructDefinition || element is CrystalEnumDefinition)) {
                    enteredEnclosing = true
                }
                for (child in element.children) visit(child)
            }
            for (child in file.children) visit(child)
        }

        private fun prioritizedLookup(
            name: String,
            icon: javax.swing.Icon,
            typeText: String,
            priority: Double
        ): com.intellij.codeInsight.lookup.LookupElement {
            val lookup = LookupElementBuilder.create(name)
                .withIcon(icon)
                .withTypeText(typeText, true)
                .withBoldness(true)
            return PrioritizedLookupElement.withPriority(lookup, priority)
        }

        /**
         * Gets the previous non-whitespace leaf token.
         */
        private fun getPreviousNonWhitespaceLeaf(element: PsiElement): PsiElement? {
            var prev = PsiTreeUtil.prevLeaf(element)
            while (prev != null && prev.text.isBlank()) {
                prev = PsiTreeUtil.prevLeaf(prev)
            }
            return prev
        }

        /**
         * Checks if the caret is after `def ` keyword inside a class/struct body.
         */
        private fun isAfterDefKeywordInClassBody(position: PsiElement): Boolean {
            // Check if previous non-whitespace leaf is DEF keyword
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            if (prev.node.elementType != CrystalTypes.DEF) return false

            // Check if we're inside a class or struct body
            val classBody = PsiTreeUtil.getParentOfType(position, CrystalClassBody::class.java)
            if (classBody != null) return true
            // Also check struct body (uses same interface via generated PSI)
            val structDef = PsiTreeUtil.getParentOfType(position, CrystalStructDefinition::class.java)
            return structDef != null
        }

        /**
         * Checks if the caret is in a type annotation context (after `:` where a type is expected).
         * Matches: `def foo(x : <caret>)` and `def foo : <caret>`
         */
        private fun isInTypeAnnotationContext(position: PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            if (prev.node.elementType == CrystalTypes.COLON) {
                val beforeColon = getPreviousNonWhitespaceLeaf(prev) ?: return false
                val elementType = beforeColon.node.elementType
                return elementType == CrystalTypes.IDENTIFIER ||
                    elementType == CrystalTypes.RPAREN ||
                    elementType == CrystalTypes.INSTANCE_VAR ||
                    elementType == CrystalTypes.CLASS_VAR
            }
            // After PIPE in a union type context: `String | <caret>`
            if (prev.node.elementType == CrystalTypes.PIPE) {
                val beforePipe = getPreviousNonWhitespaceLeaf(prev) ?: return false
                val elementType = beforePipe.node.elementType
                return elementType == CrystalTypes.CONSTANT ||
                    elementType == CrystalTypes.QUESTION ||
                    elementType == CrystalTypes.RPAREN
            }
            // After LPAREN preceded by CONSTANT: `Array(<caret>)`
            if (prev.node.elementType == CrystalTypes.LPAREN) {
                val beforeParen = getPreviousNonWhitespaceLeaf(prev) ?: return false
                return beforeParen.node.elementType == CrystalTypes.CONSTANT
            }
            // After COMMA inside type_arguments: `Hash(String, <caret>)`
            if (prev.node.elementType == CrystalTypes.COMMA) {
                val parent = prev.parent
                return parent?.node?.elementType == CrystalTypes.TYPE_ARGUMENTS
            }
            return false
        }

        /**
         * Checks if the caret is in a class/struct/module body but NOT inside a method body.
         * Also excludes `def ` context (handled by Case 4).
         */
        private fun isInClassBodyNotMethod(position: PsiElement): Boolean {
            // Must be inside a class body
            val classBody = PsiTreeUtil.getParentOfType(position, CrystalClassBody::class.java)
                ?: return false

            // Must NOT be inside a method body
            val methodBody = PsiTreeUtil.getParentOfType(position, CrystalMethodBody::class.java)
            if (methodBody != null) return false

            // Must NOT be after `def` keyword (that's Case 4)
            val prev = getPreviousNonWhitespaceLeaf(position)
            if (prev != null && prev.node.elementType == CrystalTypes.DEF) return false

            return true
        }

        /**
         * Checks if the caret is in an annotation context (after `@[`).
         */
        private fun isInAnnotationContext(position: PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            if (prev.node.elementType != CrystalTypes.LBRACKET) return false

            val beforeBracket = getPreviousNonWhitespaceLeaf(prev) ?: return false
            return beforeBracket.node.elementType == CrystalTypes.AT
        }

        /**
         * Checks if the caret is right after a numeric literal (integer or float).
         * Only suppresses when on the same line — a newline means the user is typing a new identifier.
         */
        private fun isAfterNumericLiteral(position: PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            val tokenType = prev.node?.elementType
            if (tokenType != CrystalTypes.INTEGER_LITERAL && tokenType != CrystalTypes.FLOAT_LITERAL) return false
            // Only suppress if on the same line (no newline between literal and caret)
            val prevLine = prev.containingFile?.viewProvider?.document?.getLineNumber(prev.textRange.endOffset)
            val posLine = position.containingFile?.viewProvider?.document?.getLineNumber(position.textRange.startOffset)
            return prevLine == posLine
        }

        /**
         * Checks if the position is inside a string literal (not inside interpolation).
         * Returns true if completion should be suppressed.
         */
        private fun isInsideStringLiteral(position: PsiElement): Boolean {
            return de.magynhard.crystal.completion.isInsideStringLiteral(position)
        }
    }
}

/**
 * Checks if the position is inside a string literal (not inside interpolation).
 * Returns true if completion should be suppressed.
 */
internal fun isInsideStringLiteral(position: PsiElement): Boolean {
    // If the dummy identifier is placed inside a STRING_LITERAL token context
    val tokenType = position.node?.elementType
    if (tokenType == CrystalTypes.STRING_LITERAL) return true

    // Check if parent is a string_expression — we might be between string parts
    val parent = position.parent
    if (parent?.node?.elementType == CrystalTypes.STRING_EXPRESSION) {
        // If we're inside interpolation (between INTERPOLATION_BEGIN and END), allow completion
        var sibling = position.prevSibling
        while (sibling != null) {
            val sibType = sibling.node?.elementType
            if (sibType == CrystalTypes.STRING_INTERPOLATION_BEGIN) return false
            if (sibType == CrystalTypes.STRING_INTERPOLATION_END) break
            sibling = sibling.prevSibling
        }
        return true
    }
    return false
}
