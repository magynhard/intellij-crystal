package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.CrystalPsiUtils

/**
 * Code completion contributor for Crystal.
 *
 * Provides 3 completion modes:
 * 1. Dot after a type object: static methods of that type
 * 2. Dot after a value expression: instance methods based on resolved types
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

            when (val receiver = CrystalCompletionReceiverResolver.resolve(position)) {
                is CompletionReceiver.TypeObject -> {
                    CrystalTypeObjectCompletionProvider.addCompletions(receiver, parameters, result)
                    return
                }
                is CompletionReceiver.ValueTypes -> {
                    CrystalCompletionHelper.getMethodsAsLookups(receiver.typeNames, project)
                        .forEach(result::addElement)
                    return
                }
                CompletionReceiver.Unknown -> if (isDotCompletion(position)) return
            }

            if (isAfterNumericLiteral(position)) return

            val requirePrefix = result.prefixMatcher.prefix
            val lowercaseRPrefix = requirePrefix.isEmpty() || requirePrefix[0].isLowerCase()
            val requirePrefixStart = parameters.offset - requirePrefix.length
            if (lowercaseRPrefix &&
                CrystalRequireCompletionProvider.isStatementContext(position, requirePrefixStart) &&
                result.prefixMatcher.prefixMatches("require")
            ) {
                result.addElement(CrystalRequireCompletionProvider.getStatementLookup())
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

internal object CrystalTypeObjectCompletionProvider {
    fun addCompletions(
        receiver: CompletionReceiver.TypeObject,
        parameters: CompletionParameters,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val staticMethods = CrystalCompletionHelper.getStaticMethods(receiver.simpleName, project).filter { method ->
            if (!receiver.explicitIdentity) return@filter true
            val enclosing = CrystalPsiUtils.getEnclosingType(method) ?: return@filter false
            CrystalPsiUtils.buildQualifiedName(enclosing) == receiver.qualifiedName
        }
        staticMethods.map(CrystalCompletionHelper::buildMethodLookup).forEach(result::addElement)

        if (staticMethods.any { it.name == "new" }) return
        val record = CrystalCompletionHelper.findRecordDefinition(receiver.simpleName, parameters.originalFile)
        if (record != null) {
            result.addElement(CrystalCompletionHelper.buildRecordNewLookup(record, receiver.simpleName))
        } else if (!receiver.explicitIdentity && CrystalCompletionHelper.canInstantiate(receiver.simpleName, project)) {
            result.addElement(
                CrystalCompletionHelper.buildNewLookup(receiver.simpleName, project, parameters.originalFile)
            )
        }
    }
}
