package de.magynhard.crystal.analysis

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/** Read-only declaration views, never inserted into the physical PSI/stub tree. */
internal object CrystalStaticDeclarations {
    // Each cache belongs to one source element. Concurrent computations produce
    // equivalent declarations but distinct nonphysical PSI objects: compare the
    // source revision, not those temporary object identities, for CachedValue's
    // idempotence contract.
    private class View<T>(val elements: List<T>, private val source: PsiFile) {
        private val stamp = source.modificationStamp
        override fun equals(other: Any?): Boolean = other is View<*> && source == other.source && stamp == other.stamp
        override fun hashCode(): Int = 31 * source.hashCode() + stamp.hashCode()
    }
    private data class FinishedBody(val file: PsiFile, val declarations: List<CrystalNamedElement>)
    private data class Origin(val body: SmartPsiElementPointer<CrystalMacroBody>, val prefixLength: Int)
    private val ORIGIN = Key.create<Origin>("crystal.static.finished.origin")
    private val ACCESSOR = Key.create<SmartPsiElementPointer<PsiElement>>("crystal.static.accessor.origin")
    private val VIEW = Key.create<Boolean>("crystal.static.declaration.view")
    private val TYPE_PATH = Regex("(?:::)?[A-Z][A-Za-z0-9_]*(?:::[A-Z][A-Za-z0-9_]*)*\\??")

    fun finishedTypes(context: PsiElement, sources: CrystalEffectiveSourceSet): List<CrystalNamedElement> =
        CrystalIndexService.findMacros("finished", context.project, GlobalSearchScope.allScope(context.project))
            .filter(sources::contains)
            .flatMap { finishedView(it)?.declarations.orEmpty() }

    /** Syntax can be projected even while a static body's declarations are incomplete. */
    fun finishedSyntax(macro: CrystalMacroDefinition): PsiFile? = finishedView(macro)?.file

    /** Inverse source mapping for editor actions inside an opaque macro token. */
    fun syntaxElementAt(body: CrystalMacroBody, offset: Int): PsiElement? {
        if (offset < body.textRange.startOffset || offset >= body.textRange.endOffset) return null
        val macro = body.parent as? CrystalMacroDefinition ?: return null
        val file = finishedSyntax(macro) ?: return null
        val origin = file.getUserData(ORIGIN) ?: return null
        return file.findElementAt(offset - body.textRange.startOffset + origin.prefixLength)
    }

    private fun finishedView(macro: CrystalMacroDefinition): FinishedBody? =
        CachedValuesManager.getCachedValue(macro) {
            CachedValueProvider.Result.create(View(listOfNotNull(parseFinished(macro)), macro.containingFile), macro.containingFile)
        }.elements.singleOrNull()

    private fun parseFinished(macro: CrystalMacroDefinition): FinishedBody? {
        if (macro.name != "finished" || macro.parameterList != null ||
            macro.node.findChildByType(CrystalTypes.DOT) != null ||
            CrystalPsiUtils.isInsideMacroControlRegion(macro)) return null
        val owner = CrystalPsiUtils.getEnclosingType(macro) as? CrystalModuleDefinition ?: return null
        // A nested macro or a generic module is not a fixed, once-per-module hook.
        if (PsiTreeUtil.getParentOfType(macro, CrystalMacroDefinition::class.java) != null) return null
        var namespace: PsiElement? = owner
        while (namespace != null) {
            if (namespace !is CrystalModuleDefinition ||
                namespace.node.findChildByType(CrystalTypes.TYPE_PARAMETERS) != null) return null
            namespace = CrystalPsiUtils.getEnclosingType(namespace)
        }
        val body = macro.macroBody ?: return null
        val text = body.text
        if (text.contains("{{") || text.contains("{%") || text.contains("@type")) return null
        val namespaceName = CrystalPsiUtils.buildQualifiedName(owner) ?: return null
        val prefix = "module $namespaceName\n"
        val file = createFile(macro, prefix + text + "\nend")
        file.putUserData(ORIGIN, Origin(SmartPointerManager.createPointer(body), prefix.length))
        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) return FinishedBody(file, emptyList())
        val wrapper = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java) ?: return FinishedBody(file, emptyList())
        val declarations = wrapper.classBody?.children.orEmpty().flatMap(::members)
        // No calls, conditionals, or nested hooks at the hook's outer level.
        if (declarations.isEmpty() || declarations.any { it !is CrystalClassDefinition }) return FinishedBody(file, emptyList())
        if (declarations.any { !staticClass(it as CrystalClassDefinition) }) return FinishedBody(file, emptyList())
        return FinishedBody(file, declarations.filterIsInstance<CrystalNamedElement>())
    }

    private fun staticClass(declaration: CrystalClassDefinition): Boolean {
        if (declaration.macroInterpolation != null ||
            declaration.node.findChildByType(CrystalTypes.TYPE_PARAMETERS) != null) return false
        return declaration.classBody?.children.orEmpty().flatMap(::members).all { member ->
            when (member) {
                is CrystalMethodDefinition -> member.macroInterpolation == null
                is CrystalAssignment -> member.instanceVarAccess != null
                is CrystalMethodCallExpression, is CrystalBareMethodCallExpression ->
                    CrystalAccessorCoupling.isAccessorMacroCall(member)
                else -> false
            }
        }
    }

    private fun members(element: PsiElement): List<PsiElement> = when {
        element is PsiWhiteSpace || element is PsiComment || element.node.elementType == CrystalTypes.NEWLINE -> emptyList()
        element is CrystalStatement || element is CrystalExpressionStatement || element is CrystalExpression ->
            element.children.flatMap(::members)
        else -> listOf(element)
    }

    /** Typed plain and query accessor signatures share the ordinary method pipeline. */
    fun accessorMethods(declaration: CrystalNamedElement): List<CrystalMethodDefinition> =
        CachedValuesManager.getCachedValue(declaration) {
            val source = sourceFile(declaration) ?: declaration.containingFile
            CachedValueProvider.Result.create(View(buildAccessors(declaration), source), declaration.containingFile)
        }.elements

    private fun buildAccessors(declaration: CrystalNamedElement): List<CrystalMethodDefinition> {
        val typeParameters = generateSequence(declaration as PsiElement?) { CrystalPsiUtils.getEnclosingType(it) }
            .flatMap { type ->
                type.node.findChildByType(CrystalTypes.TYPE_PARAMETERS)?.getChildren(null).orEmpty()
                    .filter { it.elementType == CrystalTypes.CONSTANT }.map { it.text }
            }.toSet()
        val owner = CrystalPsiUtils.buildQualifiedName(declaration) ?: return emptyList()
        return CrystalAccessorCoupling.findAccessorCallsIn(declaration).flatMap { call ->
            val macro = CrystalAccessorCoupling.accessorMacroName(call) ?: return@flatMap emptyList()
            val plain = macro.removePrefix("class_")
            if (plain !in setOf("getter", "property", "setter", "getter?", "property?")) return@flatMap emptyList()
            if (CrystalPsiUtils.isInsideMacroControlRegion(call)) return@flatMap emptyList()
            val arguments = when (call) {
                is CrystalMethodCallExpression -> call.bareArgumentList?.bareArgumentList.orEmpty()
                is CrystalBareMethodCallExpression -> call.bareArgumentList?.bareArgumentList.orEmpty()
                else -> emptyList()
            }
            arguments.flatMap { argument ->
                if (argument.node.findChildByType(CrystalTypes.COLON) == null) return@flatMap emptyList()
                val name = CrystalAccessorCoupling.accessorArgName(argument) ?: return@flatMap emptyList()
                val type = PsiTreeUtil.getChildOfType(argument, CrystalTypeReference::class.java)?.text
                    ?.filterNot(Char::isWhitespace) ?: return@flatMap emptyList()
                if (!TYPE_PATH.matches(type)) return@flatMap emptyList()
                if (type.removePrefix("::").substringBefore("::").removeSuffix("?") in typeParameters &&
                    !type.startsWith("::")) return@flatMap emptyList()
                val receiver = if (macro.startsWith("class_")) "self." else ""
                val reader = if (plain.endsWith('?')) "$name?" else name
                val signatures = buildList {
                    if (plain != "setter") add("def $receiver$reader : $type\nend")
                    if (plain !in setOf("getter", "getter?")) add("def $receiver$name=(value : $type) : $type\nend")
                }
                val file = parse(declaration, "class $owner\n${signatures.joinToString("\n")}\nend")
                    ?: return@flatMap emptyList()
                PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java).onEach {
                    it.putUserData(ACCESSOR, SmartPointerManager.createPointer(argument as PsiElement))
                }.toList()
            }
        }
    }

    fun accessorOrigin(method: CrystalMethodDefinition): PsiElement? = method.getUserData(ACCESSOR)?.element

    fun isView(element: PsiElement): Boolean = element.containingFile?.getUserData(VIEW) == true

    fun accessorReader(argument: PsiElement): CrystalMethodDefinition? {
        val type = CrystalPsiUtils.getEnclosingType(argument) as? CrystalNamedElement ?: return null
        val name = CrystalAccessorCoupling.accessorArgName(argument) ?: return null
        return accessorMethods(type).firstOrNull {
            it.name in setOf(name, "$name?") && accessorOrigin(it) === argument
        }
    }

    fun sourceOffset(element: PsiElement): Int {
        accessorOriginIfMethod(element)?.let { return sourceOffset(it) }
        val origin = element.containingFile?.getUserData(ORIGIN) ?: return element.textOffset
        val body = origin.body.element ?: return element.textOffset
        return body.textOffset + element.textOffset - origin.prefixLength
    }

    fun sourceFile(element: PsiElement): PsiFile? {
        accessorOriginIfMethod(element)?.let { return sourceFile(it) }
        val file = element.containingFile ?: return null
        return file.getUserData(ORIGIN)?.body?.element?.containingFile ?: file
    }

    private fun accessorOriginIfMethod(element: PsiElement): PsiElement? =
        (element as? CrystalMethodDefinition)?.let(::accessorOrigin)

    fun navigation(element: PsiElement): PsiElement? {
        accessorOriginIfMethod(element)?.let { return navigation(it) ?: it }
        val origin = element.containingFile?.getUserData(ORIGIN) ?: return null
        val body = origin.body.element ?: return null
        val offset = element.textOffset - origin.prefixLength
        if (offset !in 0 until body.textLength) return null
        return SourceNavigation(body, offset, (element as? PsiNamedElement)?.name ?: element.text)
    }

    private fun parse(context: PsiElement, text: String): PsiFile? =
        createFile(context, text).takeIf { PsiTreeUtil.findChildOfType(it, PsiErrorElement::class.java) == null }

    private fun createFile(context: PsiElement, text: String): PsiFile {
        val file = PsiFileFactory.getInstance(context.project).createFileFromText(
            "static-declarations.cr", CrystalFileType, text,
        )
        file.putUserData(VIEW, true)
        return file
    }

    private class SourceNavigation(body: CrystalMacroBody, private val relativeOffset: Int, private val label: String) :
        LightElement(body.manager, CrystalLanguage), NavigatablePsiElement, PsiNamedElement {
        private val pointer = SmartPointerManager.createPointer(body)
        override fun getParent(): PsiElement? = pointer.element
        override fun getContainingFile(): PsiFile? = pointer.element?.containingFile
        override fun getName(): String = label
        override fun setName(name: String): PsiElement = throw IncorrectOperationException("Static macro declarations are read-only")
        override fun isWritable(): Boolean = false
        override fun getTextOffset(): Int = (pointer.element?.textOffset ?: 0) + relativeOffset
        override fun getTextRange(): TextRange = TextRange(textOffset, textOffset + label.length)
        override fun getText(): String = label
        override fun isValid(): Boolean = pointer.element?.text?.let { text ->
            relativeOffset >= 0 && text.startsWith(label, relativeOffset)
        } == true
        override fun canNavigate(): Boolean = containingFile?.virtualFile != null && isValid
        override fun canNavigateToSource(): Boolean = canNavigate()
        override fun navigate(requestFocus: Boolean) {
            if (!canNavigate()) return
            val file = containingFile?.virtualFile ?: return
            OpenFileDescriptor(project, file, textOffset).navigate(requestFocus)
        }
        override fun toString(): String = "Crystal finished declaration: $label"
    }
}
