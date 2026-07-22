package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.CrystalNamespaceAccess

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
        val crystalPattern = PlatformPatterns.psiElement()
            .withLanguage(CrystalLanguage)

        extend(CompletionType.BASIC, crystalPattern, CrystalCompletionProvider())
    }

    private class CrystalCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = parameters.position
            val project = position.project

            val requirePathPrefix = CrystalRequireCompletionProvider.getPathPrefixInsideRequireString(
                position, parameters.editor, parameters.offset
            )
            if (requirePathPrefix != null) {
                val pathLookups = CrystalRequireCompletionProvider.getPathLookups(
                    position, project, requirePathPrefix, parameters.originalFile
                )
                if (pathLookups.isEmpty()) return
                val pathResult = result.withPrefixMatcher(
                    result.prefixMatcher.cloneWithPrefix("")
                )
                for (lookup in pathLookups) {
                    pathResult.addElement(lookup)
                }
                return
            }

            if (isInsideStringLiteral(position)) return
            if (isAfterNumericLiteral(position)) return

            val requirePrefix = result.prefixMatcher.prefix
            val lowercaseRPrefix = requirePrefix.isEmpty() || requirePrefix[0].isLowerCase()
            val requirePrefixStart = parameters.offset - requirePrefix.length
            if (lowercaseRPrefix &&
                CrystalRequireCompletionProvider.isKeywordContext(position, requirePrefixStart) &&
                result.prefixMatcher.prefixMatches("require")
            ) {
                result.addElement(CrystalRequireCompletionProvider.getKeywordLookup())
            }

            if (isAfterDefKeywordInClassBody(position)) {
                for (lookup in CrystalOverrideMethodProvider.getOverrideLookups()) {
                    result.addElement(lookup)
                }
            }

            if (isInTypeAnnotationContext(position)) {
                for (lookup in CrystalTypeCompletionProvider.getTypeLookups(position, project)) {
                    result.addElement(lookup)
                }
                return
            }

            if (isInClassBodyNotMethod(position)) {
                for (lookup in CrystalClassBodyCompletionProvider.getClassBodyLookups()) {
                    result.addElement(lookup)
                }
            }

            if (isInAnnotationContext(position)) {
                for (lookup in CrystalAnnotationCompletionProvider.getAnnotationLookups()) {
                    result.addElement(lookup)
                }
                return
            }

            val prevLeaf = getPreviousNonWhitespaceLeaf(position)
            if (prevLeaf != null && prevLeaf.text == ".") {
                val beforeDot = getPreviousNonWhitespaceLeaf(prevLeaf)
                if (beforeDot != null) {
                    val beforeDotText = beforeDot.text

                    if (beforeDotText.isNotEmpty() && beforeDotText[0].isUpperCase()) {
                        val nsAccess = PsiTreeUtil.getParentOfType(beforeDot, CrystalNamespaceAccess::class.java, false)
                        val staticMethods = if (nsAccess != null) {
                            val qualifiedName = de.magynhard.crystal.psi.CrystalPsiUtils.buildNamespacePath(nsAccess)
                            val allMethods = CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
                            allMethods.filter { method ->
                                val enclosing = de.magynhard.crystal.psi.CrystalPsiUtils.getEnclosingType(method)
                                enclosing != null && de.magynhard.crystal.psi.CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
                            }
                        } else {
                            CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
                        }
                        for (method in staticMethods) {
                            result.addElement(CrystalCompletionHelper.buildMethodLookup(method))
                        }
                        if (staticMethods.none { it.name == "new" }) {
                            val recordDef = CrystalCompletionHelper.findRecordDefinition(beforeDotText, parameters.originalFile)
                            if (recordDef != null) {
                                result.addElement(CrystalCompletionHelper.buildRecordNewLookup(recordDef, beforeDotText))
                            } else if (nsAccess == null && CrystalCompletionHelper.canInstantiate(beforeDotText, project)) {
                                result.addElement(
                                    CrystalCompletionHelper.buildNewLookup(
                                        beforeDotText,
                                        project,
                                        parameters.originalFile
                                    )
                                )
                            }
                        }
                        return
                    }

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

            if (prevLeaf != null && prevLeaf.text == "::") {
                val beforeDoubleColon = getPreviousNonWhitespaceLeaf(prevLeaf)
                if (beforeDoubleColon != null) {
                    val beforeText = beforeDoubleColon.text
                    if (beforeText.isNotEmpty() && beforeText[0].isUpperCase()) {
                        for (lookup in CrystalTypeCompletionProvider.getEnclosingTypeLookups(beforeText, project)) {
                            result.addElement(lookup)
                        }
                        CrystalSymbolCompletionProvider.addClassConstants(beforeText, project, result)
                        return
                    }
                }
            }

            val actualPrefix = computeCompletionPrefix(parameters.editor, parameters.offset)
            val isVarPrefix = actualPrefix.startsWith("@")
            val effectiveResult = if (isVarPrefix) {
                result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(actualPrefix))
            } else {
                result
            }
            val isUppercase = actualPrefix.isNotEmpty() && actualPrefix[0].isUpperCase()

            CrystalLocalCompletionProvider.addLocalCompletions(
                position,
                parameters.originalFile,
                effectiveResult
            )

            if (actualPrefix.isEmpty() || isUppercase) {
                for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                    effectiveResult.addElement(lookup)
                }
                CrystalSymbolCompletionProvider.addAllClasses(project, effectiveResult)
                CrystalSymbolCompletionProvider.addFileLevelConstants(parameters.originalFile, effectiveResult)
            }
        }
    }
}
