package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.*

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
                        val staticMethods = CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
                        for (method in staticMethods) {
                            val lookup = CrystalCompletionHelper.buildMethodLookup(method)
                            if (lookup != null) result.addElement(lookup)
                        }
                        // Offer "new" with initialize parameters only for classes/structs (not modules/enums)
                        if (staticMethods.none { it.name == "new" } && CrystalCompletionHelper.canInstantiate(beforeDotText, project)) {
                            result.addElement(CrystalCompletionHelper.buildNewLookup(beforeDotText, project))
                        }
                        return
                    }

                    // Case 2: identifier. (variable.method)
                    if (beforeDotText.isNotEmpty() && beforeDotText[0].isLowerCase()) {
                        val inferredType = CrystalTypeInference.inferType(beforeDotText, position, project)
                        if (inferredType != null) {
                            val instanceMethods = CrystalCompletionHelper.getInstanceMethods(inferredType, project)
                            for (method in instanceMethods) {
                                val lookup = CrystalCompletionHelper.buildMethodLookup(method)
                                if (lookup != null) result.addElement(lookup)
                            }
                        }
                        // Even without type inference, show all project methods as fallback
                        if (inferredType == null) {
                            addAllMethods(project, result)
                        }
                        return
                    }
                }
            }

            // Case 3: Free-text completion — classes + methods + local vars/params + stdlib types
            for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                result.addElement(lookup)
            }
            addAllClasses(project, result)
            addAllMethods(project, result)
            addLocalVariablesAndParameters(position, result)
        }

        private fun addAllClasses(project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
            for (className in CrystalCompletionHelper.getAllClassNames(project)) {
                result.addElement(CrystalCompletionHelper.buildClassLookup(className))
            }
        }

        private fun addAllMethods(project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
            for (method in CrystalCompletionHelper.getAllMethods(project)) {
                val lookup = CrystalCompletionHelper.buildMethodLookup(method)
                if (lookup != null) result.addElement(lookup)
            }
        }

        private fun addLocalVariablesAndParameters(position: PsiElement, result: CompletionResultSet) {
            // Find enclosing method and collect parameter names
            val method = PsiTreeUtil.getParentOfType(position, CrystalMethodDefinition::class.java)
            if (method != null) {
                val paramList = method.parameterList
                if (paramList != null) {
                    for (param in paramList.parameterList) {
                        val name = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: continue
                        result.addElement(
                            LookupElementBuilder.create(name)
                                .withIcon(AllIcons.Nodes.Parameter)
                                .withTypeText("parameter", true)
                        )
                    }
                }
            }

            // Collect local variable assignments before the cursor position
            val containingFile = position.containingFile ?: return
            val assignments = PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)
            val seen = mutableSetOf<String>()
            for (assignment in assignments) {
                if (assignment.textOffset >= position.textOffset) continue
                val identNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER)
                val name = identNode?.text ?: continue
                if (seen.add(name)) {
                    result.addElement(
                        LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Variable)
                            .withTypeText("local", true)
                    )
                }
            }
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
    }
}
