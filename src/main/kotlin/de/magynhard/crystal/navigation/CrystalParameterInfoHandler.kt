package de.magynhard.crystal.navigation

import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Provides parameter info (Ctrl+P) for Crystal method calls.
 * Supports:
 * - Parenthesized calls: foo(a, b)
 * - Bare (parenthesis-free) calls: foo a, b
 * - Dot-calls with parens: obj.method(a, b)
 * - Dot-calls bare: obj.method a, b
 * - Class method calls: Foo.bar(a, b)
 *
 * Handles cursor positions after comma with/without whitespace,
 * including incomplete expressions where the PSI tree is broken,
 * and bare calls where no argument has been typed yet.
 */
class CrystalParameterInfoHandler : ParameterInfoHandler<PsiElement, Any> {

    /**
     * Synthetic anchor element for parameter info that provides an extended text range
     * covering from the method name token to the end of the statement (or cursor position).
     * This ensures IntelliJ's ParameterInfoController considers the cursor "inside" the anchor.
     */
    class CrystalParameterInfoAnchor(
        psiManager: PsiManager,
        /** The IDENTIFIER/CONSTANT leaf token representing the method name. */
        val nameToken: PsiElement,
        /** End offset of the call statement (typically next newline or file end). */
        private val endOffset: Int,
        /** The offset of the unmatched LPAREN, or -1 for bare calls. */
        val lparenOffset: Int = -1
    ) : LightElement(psiManager, CrystalLanguage) {
        override fun getTextRange(): TextRange = TextRange(nameToken.textRange.startOffset, endOffset)
        override fun getContainingFile(): PsiFile = nameToken.containingFile
        override fun toString(): String = "CrystalParameterInfoAnchor(${nameToken.text})"
        override fun isValid(): Boolean = nameToken.isValid
        override fun getText(): String {
            val file = nameToken.containingFile ?: return nameToken.text
            val fileText = file.text ?: return nameToken.text
            val start = nameToken.textRange.startOffset.coerceIn(0, fileText.length)
            val end = endOffset.coerceIn(start, fileText.length)
            return fileText.substring(start, end)
        }
        override fun getTextOffset(): Int = nameToken.textOffset
        override fun getStartOffsetInParent(): Int = 0

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CrystalParameterInfoAnchor) return false
            return nameToken.textOffset == other.nameToken.textOffset
                && nameToken.containingFile == other.nameToken.containingFile
                && lparenOffset == other.lparenOffset
        }

        override fun hashCode(): Int {
            var result = nameToken.textOffset
            result = 31 * result + lparenOffset
            return result
        }
    }

    // ==================== Record Parameter Info ====================

    /** Wrapper for record parameters to display in Ctrl+P. */
    data class RecordParameterInfo(val params: List<RecordParam>)
    data class RecordParam(val text: String) {
        override fun toString() = text
    }

    /**
     * Extracts a parameter list from a `record` macro call for parameter info display.
     */
    private fun extractRecordParameterList(recordCall: CrystalMethodCallExpression): RecordParameterInfo? {
        val bareArgList = recordCall.bareArgumentList ?: return null
        val args = bareArgList.bareArgumentList
        if (args.size <= 1) return RecordParameterInfo(emptyList())

        val params = mutableListOf<RecordParam>()
        for (i in 1 until args.size) {
            val arg = args[i]
            val children = arg.node.getChildren(null)
            var name: String? = null
            var typeText: String? = null
            var defaultText: String? = null
            var pastColon = false
            var pastAssign = false
            for (child in children) {
                when (child.elementType) {
                    CrystalTypes.IDENTIFIER -> name = child.text
                    CrystalTypes.COLON -> pastColon = true
                    CrystalTypes.ASSIGN -> pastAssign = true
                    else -> {
                        if (child.elementType == com.intellij.psi.TokenType.WHITE_SPACE) continue
                        if (pastAssign) {
                            defaultText = (defaultText ?: "") + child.text
                        } else if (pastColon) {
                            typeText = (typeText ?: "") + child.text
                        }
                    }
                }
            }
            if (name != null) {
                val param = buildString {
                    append(name)
                    if (typeText != null) append(" : ").append(typeText)
                    if (defaultText != null) append(" = ").append(defaultText)
                }
                params.add(RecordParam(param))
            }
        }
        return RecordParameterInfo(params)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val argsHolder = findArgsHolder(context.file, context.offset) ?: return null
        val methodName = findMethodNameForArgs(argsHolder) ?: return null

        val project = context.project

        // Special case: "new" on a class → resolve directly to "initialize" parameters
        // via CrystalMethodByClassIndex (O(1)) instead of searching CrystalMethodIndex
        // for ALL methods named "new" across the entire stdlib (expensive + causes freezes).
        if (methodName == "new") {
            return findNewParameterInfo(argsHolder, context)
        }

        val scope = GlobalSearchScope.allScope(project)

        var methods = CrystalIndexService.findMethods(methodName, project, scope).toTypedArray()

        // Filter by receiver type for DOT-calls on CONSTANT receivers.
        // This prevents stdlib methods like ENV.fetch from showing params of Hash#fetch etc.
        val receiverName = findReceiverNameFromSiblings(argsHolder)
        if (receiverName != null && receiverName.isNotEmpty() && receiverName[0].isUpperCase()) {
            methods = methods.filter { method ->
                CrystalCompletionHelper.getEnclosingClassName(method) == receiverName
            }.toTypedArray()
        }

        if (methods.isEmpty()) return null
        context.itemsToShow = methods
        return argsHolder
    }

    /**
     * Resolves parameter info for ClassName.new(...) calls.
     * Skips the expensive CrystalMethodIndex search for "new" (which would load ALL
     * stdlib .new methods) and goes directly to initialize resolution via
     * CrystalMethodByClassIndex (O(1) lookup).
     */
    private fun findNewParameterInfo(
        argsHolder: PsiElement,
        context: CreateParameterInfoContext
    ): PsiElement? {
        val className = CrystalDotCallParameterLocator.findClassNameBeforeNew(argsHolder) ?: return null

        // 1. Try initialize method (most common case)
        val project = context.project
        val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, argsHolder.containingFile)
        if (initMethod != null) {
            context.itemsToShow = arrayOf(initMethod)
            return argsHolder
        }

        // 2. Try record macro (record Foo, bar : String, baz : Int32)
        val file = argsHolder.containingFile ?: return null
        val recordDef = CrystalCompletionHelper.findRecordDefinition(className, file)
        if (recordDef != null) {
            val recordParams = extractRecordParameterList(recordDef)
            if (recordParams != null) {
                context.itemsToShow = arrayOf(recordParams)
                return argsHolder
            }
        }

        // No initialize method, no record → no parameter info
        return null
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        var result = findArgsHolder(context.file, context.offset)
        if (result == null && context.offset > 0) {
            // IntelliJ sometimes calls with offset-1; try offset+1
            result = findArgsHolder(context.file, context.offset + 1)
        }
        return result
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val offset = context.offset
        val index = computeCurrentParameterIndex(parameterOwner, offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(method: Any?, context: ParameterInfoUIContext) {
        if (method == null) {
            context.isUIComponentEnabled = false
            return
        }

        val paramList = when (method) {
            is CrystalMethodDefinition -> method.parameterList?.parameterList ?: emptyList()
            is RecordParameterInfo -> method.params
            else -> return
        }

        if (paramList.isEmpty()) {
            context.setupUIComponentPresentation(
                "<no parameters>",
                -1, -1,
                false, false, false,
                context.defaultParameterColor
            )
            return
        }

        val params = paramList.map {
            when (it) {
                is CrystalParameter -> it.text.trim()
                is RecordParam -> it.text
                else -> it.toString().trim()
            }
        }
        val text = params.joinToString(", ")

        val currentIndex = context.currentParameterIndex
        var startHighlight = -1
        var endHighlight = -1

        if (currentIndex in params.indices) {
            startHighlight = params.take(currentIndex).sumOf { it.length + 2 }
            endHighlight = startHighlight + params[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight, endHighlight,
            false, false, false,
            context.defaultParameterColor
        )
    }

    // ==================== Anchor Search ====================

    fun findArgsHolder(file: PsiFile, offset: Int): PsiElement? {
        return CrystalParameterCallLocator.findArgsHolder(file, offset)
    }

    fun scanBackwardsForBareCall(file: PsiFile, offset: Int): PsiElement? {
        return CrystalBareCallParameterLocator.scanBackwardsForBareCall(file, offset)
    }

    // ==================== Method Name Resolution ====================

    /**
     * Resolves the method name for a given args holder by examining its PSI context.
     */
    fun findMethodNameForArgs(argsHolder: PsiElement): String? {
        // Case: synthetic anchor from bare-call or broken paren-call
        if (argsHolder is CrystalParameterInfoAnchor) {
            return argsHolder.nameToken.text
        }

        // Case: bare-call backtracking returned the IDENTIFIER/CONSTANT leaf as anchor (legacy)
        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            return argsHolder.text
        }

        val parent = argsHolder.parent

        // Case: proper CrystalCallArgs/BareArgumentList inside method_call_expression
        if (argsHolder is CrystalCallArgs || argsHolder is CrystalBareArgumentList) {
            if (parent is CrystalMethodCallExpression) {
                return CrystalDotCallParameterLocator.extractIdentifierFromCallExpression(parent)
            }
            if (parent is CrystalBareMethodCallExpression) {
                return CrystalDotCallParameterLocator.extractIdentifierFromCallExpression(parent)
            }
            val fromSiblings = CrystalDotCallParameterLocator.findMethodNameFromSiblings(argsHolder)
            if (fromSiblings != null) return fromSiblings
        }

        // Case: broken PSI
        val fromSiblings = CrystalDotCallParameterLocator.findMethodNameFromSiblings(argsHolder)
        if (fromSiblings != null) return fromSiblings

        if (argsHolder is CrystalMethodCallExpression) {
            return CrystalDotCallParameterLocator.extractIdentifierFromCallExpression(argsHolder)
        }

        // Look for IDENTIFIER child before LPAREN
        var child = argsHolder.firstChild
        var lastIdentifier: String? = null
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
                lastIdentifier = child.text
            }
            if (type == CrystalTypes.LPAREN) {
                return lastIdentifier
            }
            child = child.nextSibling
        }

        // Try parent as method_call_expression
        if (parent is CrystalMethodCallExpression) {
            return CrystalDotCallParameterLocator.extractIdentifierFromCallExpression(parent)
        }
        if (parent is CrystalBareMethodCallExpression) {
            return CrystalDotCallParameterLocator.extractIdentifierFromCallExpression(parent)
        }

        return null
    }

    internal fun findReceiverNameFromSiblings(argsHolder: PsiElement): String? {
        return CrystalDotCallParameterLocator.findReceiverNameFromSiblings(argsHolder)
    }

    // ==================== Parameter Index Computation ====================

    fun computeCurrentParameterIndex(argsHolder: PsiElement, offset: Int): Int {
        return CrystalParameterIndexUtil.computeCurrentParameterIndex(argsHolder, offset)
    }
}
