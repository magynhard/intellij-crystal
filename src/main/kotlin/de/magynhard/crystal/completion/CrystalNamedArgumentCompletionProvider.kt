package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.*
import de.magynhard.crystal.inspections.CrystalCallExtractor
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

internal object CrystalNamedArgumentCompletionProvider {
    private data class Candidate(val name: String, val type: String?, val required: Boolean, val namedOnly: Boolean = false)

    fun addCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (CrystalMacroContext.isInMacroContext(position) || CrystalMacroContext.isInsideMacroCallBlock(position)) return
        val holder = generateSequence(position.parent) { it.parent }
            .firstOrNull { it is CrystalCallArgs || it is CrystalBareArgumentList } ?: return
        val arguments = CrystalPsiCallArguments.argumentElements(holder)
        val current = arguments.firstOrNull { PsiTreeUtil.isAncestor(it, position, false) } ?: return
        val start = parameters.offset - result.prefixMatcher.prefix.length
        // Only the argument's initial identifier/label is a key position. Values, nested
        // expressions, block passes and splats must retain ordinary expression completion.
        val before = start - current.textRange.startOffset
        if (before < 0 || current.text.take(before).any { !it.isWhitespace() }) return
        if (arguments.any { it.node.getChildren(null).firstOrNull()?.elementType in setOf(CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR) }) return
        val others = arguments.filter { it !== current }
        val used = others.mapNotNull(CrystalPsiCallArguments::getNamedLabel).toSet()
        val positional = others.count {
            CrystalPsiCallArguments.getNamedLabel(it) == null &&
                it.node.getChildren(null).firstOrNull()?.elementType != CrystalTypes.AMPERSAND
        }
        val call = holder.parent
        if (call !is CrystalDotCallAccess && call !is CrystalMethodCallExpression && call !is CrystalBareMethodCallExpression) return
        if (call !is CrystalDotCallAccess && CrystalCallExtractor.extractMethodName(call)?.firstOrNull()?.isUpperCase() == true) return
        val session = CrystalTypeSetResolver.session(position)
        val candidates = when (call) {
            is CrystalDotCallAccess -> dotCandidates(call, session, used, positional)
            is CrystalMethodCallExpression, is CrystalBareMethodCallExpression ->
                bareCandidates(call, session).flatMap { methodCandidates(it, used, positional) }
            else -> emptyList()
        }
        for ((name, variants) in candidates.filter { it.name !in used }.groupBy { it.name }) {
            val label = if (Regex("[a-zA-Z_][a-zA-Z_0-9]*[!?]?").matches(name)) name else
                "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    .replace("\r", "\\r").replace("\t", "\\t").replace("#{", "\\#{") + "\""
            val required = variants.all { it.required }
            val lookup = LookupElementBuilder.create("$label:")
                .withIcon(AllIcons.Nodes.Parameter)
                .withTypeText(variants.mapNotNull { it.type }.distinct().joinToString(" | ").ifEmpty { null })
                .withTailText(if (required) " (required)" else "", true)
                .withInsertHandler { context, _ ->
                    context.setAddCompletionChar(false)
                    // Replacing an existing key must not duplicate its colon or spacing.
                    val tail = context.tailOffset
                    var end = tail
                    val chars = context.document.charsSequence
                    if (end < chars.length && chars[end] == ':') end++
                    while (end < chars.length && (chars[end] == ' ' || chars[end] == '\t')) end++
                    context.document.replaceString(tail, end, " ")
                    context.editor.caretModel.moveToOffset(tail + 1)
                }
            result.addElement(PrioritizedLookupElement.withPriority(lookup, if (required) 150.0 else 140.0))
        }
    }

    private fun dotCandidates(
        call: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
        used: Set<String>,
        positional: Int,
    ): List<Candidate> {
        val descriptor = CrystalCallExtractor.extractDotCall(call) ?: return emptyList()
        val root = CrystalReceiverExpression.extractExactConstantTypeRoot(descriptor.receiver)
        if (descriptor.methodName == "new" && root != null) {
            return when (val constructor = session.resolveConstructor(root, call)) {
                is CrystalConstructorResolution.Methods -> constructor.methods.flatMap { methodCandidates(it, used, positional) }
                is CrystalConstructorResolution.Record -> {
                    val fields = CrystalPsiUtils.recordFieldArguments(constructor.recordDefinition).map(CrystalPsiUtils::recordFieldInfo)
                    if (used.any { label -> fields.none { it.name == label } }) return emptyList()
                    fields.drop(positional).mapNotNull { field -> field.name?.let { Candidate(it, field.typeText, !field.hasDefault) } }
                }
                is CrystalConstructorResolution.Incomplete -> {
                    val written = constructor.knownMethods.flatMap { methodCandidates(it, used, positional) }
                    val fields = if (positional == 0) session.constructorFieldKeys(constructor.identity).map {
                        Candidate(it.name, it.typeText, it.required)
                    } else emptyList()
                    written + fields
                }
                else -> emptyList()
            }
        }
        return (CrystalDotCallTargetResolver.resolve(call, session) as? DotCallResolution.Methods)
            ?.methods.orEmpty().flatMap { methodCandidates(it, used, positional) }
    }

    private fun bareCandidates(call: PsiElement, session: CrystalTypeResolutionSession): List<CrystalMethodDefinition> {
        val name = CrystalCallExtractor.extractMethodName(call) ?: return emptyList()
        val local = call.reference as? CrystalReference
        if (local?.resolveLocalDeclaration() != null) return emptyList()
        val sources = session.effectiveSources
        val scope = GlobalSearchScope.allScope(call.project)
        if (CrystalIndexService.findMacros(name, call.project, scope).any(sources::contains)) return emptyList()
        val owner = CrystalPsiUtils.callSiteOwnerQualifiedName(call)
        if (owner != null) {
            val enclosing = PsiTreeUtil.getParentOfType(call, CrystalMethodDefinition::class.java)
            val mode = if (enclosing?.let(CrystalPsiUtils::isSelfMethod) == true) CrystalReceiverMode.STATIC else CrystalReceiverMode.INSTANCE
            val methods = session.collectNamedMethods(CrystalTypeIdentity(owner.substringAfterLast("::"), owner), mode, name)
            if (!methods.complete) return emptyList()
            if (methods.methods.isNotEmpty()) return methods.methods
        }
        return CrystalIndexService.findTopLevelMethods(name, call.project, scope).filter(sources::contains)
    }

    private fun methodCandidates(method: CrystalMethodDefinition, used: Set<String>, positional: Int): List<Candidate> {
        if (!CrystalStaticDeclarations.isConstructorHookMethod(method) && CrystalPsiUtils.isInsideMacroControlRegion(method)) return emptyList()
        val list = method.parameterList ?: return emptyList()
        if (list.parameterList.any { it.node.findChildByType(CrystalTypes.MACRO_INTERPOLATION) != null }) return emptyList()
        var namedOnly = false
        var hasSplat = false
        var hasDoubleSplat = false
        val candidates = buildList {
            for (child in list.node.getChildren(null)) {
                if (child.elementType == CrystalTypes.STAR || child.elementType == CrystalTypes.DOUBLE_STAR) namedOnly = true
                val parameter = child.psi as? CrystalParameter ?: continue
                when {
                    parameter.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null -> { hasDoubleSplat = true; namedOnly = true; continue }
                    parameter.node.findChildByType(CrystalTypes.STAR) != null -> { hasSplat = true; namedOnly = true; continue }
                    parameter.node.findChildByType(CrystalTypes.AMPERSAND) != null -> continue
                }
                val name = parameter.parameterNameInfo().callSiteName ?: return emptyList()
                add(Candidate(name, parameter.typeReference?.text, parameter.expression == null, namedOnly))
            }
        }
        val consumed = candidates.filterNot { it.namedOnly }.take(positional).toSet()
        if ((!hasSplat && positional > candidates.count { !it.namedOnly }) ||
            (!hasDoubleSplat && used.any { label -> candidates.none { it.name == label } }) ||
            consumed.any { it.name in used }) return emptyList()
        return candidates.filter { it !in consumed && it.name != "_" }
    }
}
