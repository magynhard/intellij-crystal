package de.magynhard.crystal.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.CrystalArgument
import de.magynhard.crystal.psi.CrystalBareArgument
import de.magynhard.crystal.psi.CrystalPsiCallArguments
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalStaticDeclarations
import de.magynhard.crystal.analysis.render

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

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val leaf = context.file.findElementAt(context.startOffset) ?: return
        val argument = PsiTreeUtil.getParentOfType(leaf, CrystalArgument::class.java, CrystalBareArgument::class.java) ?: return
        if (CrystalPsiCallArguments.getNamedLabel(argument) != null &&
            argument.node.findChildByType(CrystalTypes.COLON)?.startOffset?.let { context.startOffset <= it } == true) {
            // A named label's colon must be tight. The default dummy's trailing
            // space otherwise breaks its argument PSI before providers can run.
            context.dummyIdentifier = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
        }
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

            val languageValuePrefix =
                CrystalLanguageCommentCompletionProvider.getLanguageValuePrefix(position, parameters.offset)
            if (languageValuePrefix != null) {
                CrystalLanguageCommentCompletionProvider.addCompletions(
                    languageValuePrefix, result
                )
                return
            }

            if (isInsideStringLiteral(position)) return

            if (isDotCompletion(position)) {
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
                    CompletionReceiver.Unknown -> return
                }
            }

            if (isAfterNumericLiteral(position)) return

            if (isAfterOfKeyword(position) || isAfterHashArrow(position)) {
                // After `of` (and in the hash `=>` chain) only class names
                // make sense — skip require/locals/constant scans entirely.
                val beforeOf = getPreviousNonWhitespaceLeaf(position)
                    ?.let(::getPreviousNonWhitespaceLeaf)
                val hashChain = isAfterOfKeyword(position) &&
                    beforeOf?.node?.elementType == CrystalTypes.RBRACE
                for (lookup in CrystalTypeCompletionProvider.getTypeLookups(position, project, result.prefixMatcher)) {
                    result.addElement(if (hashChain) lookup.chainHashArrow() else lookup)
                }
                return
            }

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

            CrystalNamedArgumentCompletionProvider.addCompletions(parameters, result)

            if (isInTypeAnnotationContext(position)) {
                for (lookup in CrystalTypeCompletionProvider.getTypeLookups(position, project, result.prefixMatcher)) {
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
                        for (lookup in CrystalTypeCompletionProvider.getEnclosingTypeLookups(beforeText, project, position)) {
                            result.addElement(lookup)
                        }
                        CrystalSymbolCompletionProvider.addClassConstants(beforeText, project, position, result)
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
                for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups(position, effectiveResult.prefixMatcher)) {
                    effectiveResult.addElement(lookup)
                }
                CrystalSymbolCompletionProvider.addAllClasses(project, effectiveResult, position)
                CrystalSymbolCompletionProvider.addVisibleConstants(
                    project,
                    parameters.originalFile,
                    effectiveResult,
                    position,
                )
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

        val identity = CrystalTypeIdentity(receiver.simpleName, receiver.qualifiedName)
        val generated = (session.resolveConstructor(identity) as? CrystalConstructorResolution.Incomplete)
            ?.knownMethods.orEmpty().filter { it.name == "new" && CrystalStaticDeclarations.isConstructorHookMethod(it) }
        generated.forEach { method ->
            val returns = session.resolveConstructorMethodReturn(method, identity, receiver.qualifiedName).render()
            result.addElement(CrystalCompletionHelper.buildResolvedNewLookup(method, receiver.simpleName)
                .withTypeText(returns ?: "Unknown"))
        }
        if (staticLookups.any { it.lookupString == "new" } || generated.isNotEmpty()) return
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

/**
 * After `{} of `, confirming a class in the popup inserts the ` => `
 * separator behind it and immediately reopens completion for the value type.
 * The reopening is posted past the current dispatch so the selecting
 * lookup's own disposal can never tear the new popup down again.
 */
private fun LookupElementBuilder.chainHashArrow(): LookupElementBuilder =
    withInsertHandler { context, _ ->
        val tail = " => "
        context.document.insertString(context.tailOffset, tail)
        context.editor.caretModel.moveToOffset(context.tailOffset + tail.length)
        context.commitDocument()
        val editor = context.editor
        val project = context.project
        ApplicationManager.getApplication().invokeLater {
            if (!editor.isDisposed) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
        }
    }
