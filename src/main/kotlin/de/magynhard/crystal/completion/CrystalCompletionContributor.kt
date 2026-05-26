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
                        // Also offer "new" if not already a defined static method
                        if (staticMethods.none { it.name == "new" }) {
                            result.addElement(
                                LookupElementBuilder.create("new")
                                    .withIcon(AllIcons.Nodes.Method)
                                    .withTypeText(beforeDotText, true)
                            )
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

            // Case 3: Free-text completion — classes + methods + local vars/params
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
    }
}
