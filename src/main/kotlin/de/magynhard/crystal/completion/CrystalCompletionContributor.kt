package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeSetResolver

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

            val session = CrystalTypeSetResolver.session(position)
            when (val receiver = CrystalCompletionReceiverResolver.resolve(position, session)) {
                is CompletionReceiver.TypeObject -> {
                    CrystalTypeObjectCompletionProvider.addCompletions(receiver, parameters, result, session)
                    return
                }
                is CompletionReceiver.ValueTypes -> {
                    CrystalCompletionHelper.getMethodsAsLookups(receiver.typeNames, position, session)
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
        result: CompletionResultSet,
        session: CrystalTypeResolutionSession
    ) {
        val staticLookups = CrystalCompletionHelper.getStaticMethodsAsLookups(
            receiver.qualifiedName,
            parameters.position,
            session
        )
        staticLookups.forEach(result::addElement)

        if (staticLookups.any { it.lookupString == "new" }) return
        val record = receiver.recordDefinition
        if (record != null) {
            result.addElement(CrystalCompletionHelper.buildRecordNewLookup(record, receiver.qualifiedName))
        } else if (!receiver.explicitIdentity) {
            addSyntheticNew(receiver, session, result)
        }
    }

    /**
     * Offers a synthetic `new` derived from the shared constructor metadata of the exact
     * selected type identity, so same-simple-name types in other namespaces can never leak
     * their eligibility or `initialize` signature into this receiver's presentation.
     */
    private fun addSyntheticNew(
        receiver: CompletionReceiver.TypeObject,
        session: CrystalTypeResolutionSession,
        result: CompletionResultSet
    ) {
        when (val constructor = session.resolveConstructor(
            CrystalTypeIdentity(receiver.simpleName, receiver.qualifiedName)
        )) {
            is CrystalConstructorResolution.Methods -> result.addElement(
                CrystalCompletionHelper.buildResolvedNewLookup(constructor.methods.first(), receiver.simpleName)
            )
            is CrystalConstructorResolution.Implicit -> result.addElement(
                CrystalCompletionHelper.buildResolvedNewLookup(null, receiver.simpleName)
            )
            else -> Unit
        }
    }
}
