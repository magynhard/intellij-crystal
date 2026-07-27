package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.TokenType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalMacroControl
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalExtendStatement
import de.magynhard.crystal.psi.CrystalIncludeStatement
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalParameterList
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypeReference
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.CrystalVisibilityModifier
import de.magynhard.crystal.stubs.CrystalIndexService

sealed interface DotCallResolution {
    data class Methods(
        val call: DotCallDescriptor,
        val receiverType: ExactReceiverType,
        val methods: List<CrystalMethodDefinition>
    ) : DotCallResolution

    data class ImplicitConstructor(
        val call: DotCallDescriptor,
        val receiverType: ExactReceiverType
    ) : DotCallResolution

    data class RecordFallback(
        val call: DotCallDescriptor,
        val receiverName: String,
        val qualifiedName: String,
        val recordDefinition: CrystalMethodCallExpression
    ) : DotCallResolution

    data object Unresolved : DotCallResolution
    data object Suppressed : DotCallResolution
}

object CrystalDotCallTargetResolver {

    fun resolve(access: CrystalDotCallAccess): DotCallResolution {
        val call = CrystalCallExtractor.extractDotCall(access) ?: return DotCallResolution.Unresolved
        if (containsMacroInterpolation(call.receiver) || containsMacroInterpolation(call.methodNameElement)) {
            return DotCallResolution.Suppressed
        }

        val constantReceiver = isConstantReceiver(call.receiverText)
        if (constantReceiver && call.methodName == "new" && !call.receiverText.contains("::")) {
            return resolveSimpleConstructor(call)
        }
        val receiverType = if (constantReceiver) {
            when (val identity = resolveTypeIdentity(call.receiverText, call.access)) {
                is IdentityResolution.Exact -> identity.type
                IdentityResolution.Ambiguous -> return DotCallResolution.Suppressed
                IdentityResolution.Missing -> return resolveRecordFallback(call)
            }
        } else {
            CrystalExactReceiverTypeResolver.resolve(call.receiver, call.access)
                ?: return DotCallResolution.Suppressed
        }

        if (constantReceiver && call.methodName == "new") {
            if (call.receiverText.contains("::") && hasExactQualifiedRecord(call)) {
                return DotCallResolution.Suppressed
            }
            return resolveConstructor(call, receiverType)
        }

        val mode = if (constantReceiver) ReceiverMode.STATIC else ReceiverMode.INSTANCE
        val collection = collectMethods(receiverType, mode, call.methodName, call.access)
        if (!collection.complete) return DotCallResolution.Suppressed
        return if (collection.methods.isEmpty()) {
            DotCallResolution.Unresolved
        } else {
            DotCallResolution.Methods(call, receiverType, collection.methods)
        }
    }

    private fun resolveConstructor(
        call: DotCallDescriptor,
        receiverType: ExactReceiverType
    ): DotCallResolution {
        val declarations = findExactTypeDeclarations(receiverType, call.access)
        if (declarations.none { it is CrystalClassDefinition || it is CrystalStructDefinition }) {
            return DotCallResolution.Unresolved
        }
        if (declarations.any { it.node.findChildByType(CrystalTypes.ABSTRACT) != null }) {
            return DotCallResolution.Suppressed
        }

        val selfNew = collectMethods(
            receiverType,
            ReceiverMode.STATIC,
            "new",
            call.access,
            actualSelf = true,
            includeModuleEdges = false
        )
        if (!selfNew.complete) return DotCallResolution.Suppressed
        if (selfNew.methods.isNotEmpty()) return DotCallResolution.Methods(call, receiverType, selfNew.methods)

        val initializers = collectMethods(
            receiverType,
            ReceiverMode.INSTANCE,
            "initialize",
            call.access,
            actualSelf = false
        )
        if (!initializers.complete) return DotCallResolution.Suppressed
        if (initializers.methods.isNotEmpty()) {
            return DotCallResolution.Methods(call, receiverType, initializers.methods)
        }

        return DotCallResolution.ImplicitConstructor(call, receiverType)
    }

    private fun resolveSimpleConstructor(call: DotCallDescriptor): DotCallResolution {
        val lexicalIdentities = CrystalPsiUtils.buildLexicalQualifiedNameCandidates(
            call.receiverText,
            call.access
        ).sortedByDescending { it.count { character -> character == ':' } }
        val recordsByIdentity = CrystalPsiUtils.findRecordDefinitions(
            call.receiverText,
            call.access.containingFile
        ).groupBy { it.qualifiedName }
        val typeIdentities = findTypesBySimpleName(call.receiverText, call.access)
            .mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .toSet()

        for (identity in lexicalIdentities) {
            val records = recordsByIdentity[identity].orEmpty()
            if (records.any { CrystalPsiUtils.isInsideMacroControlRegion(it.call) }) {
                return DotCallResolution.Suppressed
            }
            if (records.size > 1) return DotCallResolution.Suppressed
            records.singleOrNull()?.let { record ->
                return DotCallResolution.RecordFallback(
                    call,
                    call.receiverText,
                    record.qualifiedName,
                    record.call
                )
            }
            if (identity in typeIdentities) {
                return resolveConstructor(
                    call,
                    ExactReceiverType(call.receiverText, identity)
                )
            }
        }

        return DotCallResolution.Unresolved
    }

    private fun resolveRecordFallback(call: DotCallDescriptor): DotCallResolution {
        if (call.methodName != "new" || !call.receiverText.contains("::")) {
            return DotCallResolution.Unresolved
        }
        return if (hasExactQualifiedRecord(call)) {
            DotCallResolution.Suppressed
        } else {
            DotCallResolution.Unresolved
        }
    }

    private fun hasExactQualifiedRecord(call: DotCallDescriptor): Boolean {
        val qualifiedName = call.receiverText.removePrefix("::")
        val simpleName = qualifiedName.substringAfterLast("::")
        return CrystalPsiUtils.findRecordDefinitions(simpleName, call.access.containingFile)
            .any { it.qualifiedName == qualifiedName }
    }

    private fun collectMethods(
        receiverType: ExactReceiverType,
        mode: ReceiverMode,
        methodName: String,
        context: PsiElement,
        actualSelf: Boolean? = null,
        includeModuleEdges: Boolean = true
    ): MethodCollection {
        val hierarchy = collectHierarchy(receiverType, mode, methodName, includeModuleEdges, context)
        if (!hierarchy.complete) return MethodCollection(emptyList(), false)
        val result = mutableListOf<CrystalMethodDefinition>()
        val seenSignatures = mutableSetOf<String>()
        for (exposure in hierarchy.exposures) {
            val methods = findExactMethods(exposure.type, methodName, context)
                .filter { CrystalPsiUtils.isSelfMethod(it) == (exposure.declarationMode == ReceiverMode.STATIC) }
                .filter { actualSelf == null || CrystalPsiUtils.isSelfMethod(it) == actualSelf }
            val signatures = methods.groupBy(::canonicalSignature)
            if (signatures.values.any { group ->
                    group.map { it.containingFile?.virtualFile?.path }.distinct().size > 1
                }) {
                return MethodCollection(emptyList(), false)
            }
            for (method in methods) {
                if (seenSignatures.add(canonicalSignature(method))) result.add(method)
            }
        }
        return MethodCollection(result, true)
    }

    private fun collectHierarchy(
        root: ExactReceiverType,
        mode: ReceiverMode,
        methodName: String,
        includeModuleEdges: Boolean,
        context: PsiElement
    ): HierarchyCollection {
        val result = mutableListOf<TypeExposure>()
        val visited = mutableSetOf<TypeExposure>()
        var complete = true

        fun visit(exposure: TypeExposure) {
            if (!complete || !visited.add(exposure)) return
            result.add(exposure)

            val metadata = collectMetadata(
                exposure.type,
                exposure.declarationMode,
                methodName,
                includeModuleEdges,
                context
            )
            if (!metadata.complete) {
                complete = false
                return
            }
            val moduleEdges = if (includeModuleEdges) {
                when (exposure.declarationMode) {
                    ReceiverMode.INSTANCE -> metadata.includes
                    ReceiverMode.STATIC -> metadata.extends
                }
            } else {
                emptyList()
            }
            moduleEdges.forEach {
                visit(TypeExposure(it, ReceiverMode.INSTANCE))
            }
            metadata.superclass?.let {
                visit(TypeExposure(it, exposure.declarationMode))
            }
        }

        visit(TypeExposure(root, mode))
        return HierarchyCollection(result, complete)
    }

    private fun collectMetadata(
        type: ExactReceiverType,
        mode: ReceiverMode,
        methodName: String,
        includeModuleEdges: Boolean,
        context: PsiElement
    ): TypeMetadata {
        val declarations = findExactTypeDeclarations(type, context).sortedWith(sourceOrder())
        val includes = mutableListOf<ExactReceiverType>()
        val extends = mutableListOf<ExactReceiverType>()
        val superclasses = mutableListOf<ExactReceiverType>()
        val edgeFiles = mutableSetOf<String>()
        var complete = true

        for (declaration in declarations) {
            if (CrystalPsiUtils.isInsideMacroControlRegion(declaration)) complete = false
            val body = when (declaration) {
                is CrystalClassDefinition -> declaration.classBody
                is de.magynhard.crystal.psi.CrystalModuleDefinition -> declaration.classBody
                is CrystalStructDefinition -> declaration.classBody
                else -> null
            }
            if (body != null) {
                var macroDepth = 0
                body.children.forEach { child ->
                    if (child is CrystalMacroControl) {
                        macroDepth = updateMacroDepth(macroDepth, child)
                        return@forEach
                    }
                    semanticMembers(child).forEach { member ->
                        if (macroDepth > 0 && isRelevantMacroControlledMember(
                                member,
                                mode,
                                methodName,
                                includeModuleEdges
                            )) {
                            complete = false
                            return@forEach
                        }
                        when (member) {
                        is CrystalIncludeStatement -> if (includeModuleEdges && mode == ReceiverMode.INSTANCE) {
                            when (val edge = resolveEdge(member.typeReference, member)) {
                                is IdentityResolution.Exact -> {
                                    includes.add(edge.type)
                                    edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                                }
                                else -> complete = false
                            }
                        }
                        is CrystalExtendStatement -> if (includeModuleEdges && mode == ReceiverMode.STATIC) {
                            if (member.node.findChildByType(CrystalTypes.SELF) != null ||
                                member.text.filterNot(Char::isWhitespace) == "extendself") {
                                extends.add(type)
                                edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                            } else {
                                when (val edge = resolveEdge(member.typeReference, member)) {
                                    is IdentityResolution.Exact -> {
                                        extends.add(edge.type)
                                        edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                                    }
                                    else -> complete = false
                                }
                            }
                        }
                        }
                    }
                }
            }
            val superclass = when (declaration) {
                is CrystalClassDefinition -> declaration.superclassClause?.typeReference
                is CrystalStructDefinition -> declaration.superclassClause?.typeReference
                else -> null
            }
            if (superclass != null) {
                when (val edge = resolveEdge(superclass, declaration)) {
                    is IdentityResolution.Exact -> superclasses.add(edge.type)
                    else -> complete = false
                }
            }
        }

        val distinctSuperclasses = superclasses.distinct()
        if (distinctSuperclasses.size > 1) complete = false
        if (edgeFiles.size > 1) complete = false

        return TypeMetadata(
            includes.asReversed().distinct(),
            extends.asReversed().distinct(),
            distinctSuperclasses.singleOrNull(),
            complete
        )
    }

    private fun resolveEdge(reference: CrystalTypeReference?, context: PsiElement): IdentityResolution {
        val name = reference?.text?.filterNot(Char::isWhitespace) ?: return IdentityResolution.Missing
        return resolveTypeIdentity(name, context)
    }

    private fun findExactMethods(
        type: ExactReceiverType,
        methodName: String,
        context: PsiElement
    ): List<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(context.project)
        return CrystalIndexService.findMethodsByClass(type.simpleName, context.project, scope)
            .asSequence()
            .filter { it.name == methodName }
            .filter { method ->
                CrystalPsiUtils.getEnclosingType(method)
                    ?.let(CrystalPsiUtils::buildQualifiedName) == type.qualifiedName
            }
            .sortedWith(sourcePrecedence())
            .toList()
    }

    private fun findExactTypeDeclarations(
        type: ExactReceiverType,
        context: PsiElement
    ): List<CrystalNamedElement> = findTypesBySimpleName(type.simpleName, context)
        .filter { CrystalPsiUtils.buildQualifiedName(it) == type.qualifiedName }

    private fun findTypesBySimpleName(simpleName: String, context: PsiElement): List<CrystalNamedElement> {
        val scope = GlobalSearchScope.allScope(context.project)
        return CrystalIndexService.findTypes(simpleName, context.project, scope).toList()
    }

    private fun resolveTypeIdentity(typeName: String, context: PsiElement): IdentityResolution {
        val compact = typeName.filterNot(Char::isWhitespace)
        val normalized = compact.removePrefix("::")
        val simpleName = normalized.substringAfterLast("::")
        if (simpleName.isEmpty()) return IdentityResolution.Missing

        val candidates = if (compact.startsWith("::") || normalized.contains("::")) {
            setOf(normalized)
        } else {
            CrystalPsiUtils.buildLexicalQualifiedNameCandidates(simpleName, context)
        }
        val identities = findTypesBySimpleName(simpleName, context)
            .mapNotNull(CrystalPsiUtils::buildQualifiedName)
            .filter { it in candidates }
            .distinct()

        return when (identities.size) {
            0 -> IdentityResolution.Missing
            1 -> IdentityResolution.Exact(ExactReceiverType(simpleName, identities.single()))
            else -> IdentityResolution.Ambiguous
        }
    }

    private fun canonicalSignature(method: CrystalMethodDefinition): String {
        val parameterList = method.parameterList
        val parameters = parameterList?.parameterList.orEmpty().map { parameter ->
            canonicalParameter(parameter, parameterList)
        }
        return encodeFields(listOf(method.name.orEmpty(), parameters.size.toString()) + parameters)
    }

    private fun canonicalParameter(parameter: CrystalParameter, parameterList: CrystalParameterList?): String {
        val kind = when {
            parameter.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null ||
                parameter.node.findChildByType(CrystalTypes.WRAP_DOUBLE_STAR) != null -> "double-splat"
            parameter.node.findChildByType(CrystalTypes.STAR) != null ||
                parameter.node.findChildByType(CrystalTypes.WRAP_STAR) != null -> "splat"
            parameter.node.findChildByType(CrystalTypes.AMPERSAND) != null -> "block"
            parameterList != null && isNamedOnly(parameter, parameterList) -> "named-only"
            else -> "regular"
        }
        val identifiers = parameter.node.getChildren(null)
            .filter { it.elementType == CrystalTypes.IDENTIFIER }
            .map { it.text }
        val internalName = (parameter as? PsiNameIdentifierOwner)?.name
            ?: parameter.instanceVarAccess?.name
            ?: identifiers.lastOrNull().orEmpty()
        val externalName = identifiers.takeIf { it.size > 1 }?.first().orEmpty()
        val type = canonicalTypeRestriction(parameter, kind)
        val hasDefault = parameter.expression != null
        return encodeFields(listOf(kind, externalName, internalName, type, hasDefault.toString()))
    }

    private fun isNamedOnly(parameter: CrystalParameter, parameterList: CrystalParameterList): Boolean {
        val parameters = parameterList.parameterList
        val index = parameters.indexOf(parameter)
        if (parameters.take(index).any { it.node.findChildByType(CrystalTypes.STAR) != null }) return true
        return parameterList.node.getChildren(null).any { child ->
            child.startOffset < parameter.node.startOffset && child.elementType == CrystalTypes.STAR
        }
    }

    private fun canonicalTypeRestriction(parameter: CrystalParameter, kind: String): String {
        parameter.typeReference?.let { return it.text.filterNot(Char::isWhitespace) }
        if (kind != "block") return ""
        val children = parameter.node.getChildren(null).toList()
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        if (colonIndex < 0) return ""
        return children.drop(colonIndex + 1).joinToString("") { node ->
            when (node.elementType) {
                TokenType.WHITE_SPACE, CrystalTypes.NEWLINE, CrystalTypes.LINE_COMMENT -> ""
                else -> node.text.filterNot(Char::isWhitespace)
            }
        }
    }

    private fun semanticMembers(element: PsiElement): List<PsiElement> = when (element) {
        is CrystalIncludeStatement, is CrystalExtendStatement, is CrystalMethodDefinition -> listOf(element)
        is CrystalVisibilityModifier -> element.children.flatMap(::semanticMembers)
        is de.magynhard.crystal.psi.CrystalStatement -> element.children.flatMap(::semanticMembers)
        else -> emptyList()
    }

    private fun isRelevantMacroControlledMember(
        member: PsiElement,
        mode: ReceiverMode,
        methodName: String,
        includeModuleEdges: Boolean
    ): Boolean = when (member) {
        is CrystalIncludeStatement -> includeModuleEdges && mode == ReceiverMode.INSTANCE
        is CrystalExtendStatement -> includeModuleEdges && mode == ReceiverMode.STATIC
        is CrystalMethodDefinition -> member.name == methodName &&
            CrystalPsiUtils.isSelfMethod(member) == (mode == ReceiverMode.STATIC)
        else -> false
    }

    private fun updateMacroDepth(depth: Int, control: CrystalMacroControl): Int {
        val keyword = control.text.removePrefix("{%")
            .removeSuffix("%}")
            .trim()
            .takeWhile { !it.isWhitespace() }
        return when (keyword) {
            "end" -> (depth - 1).coerceAtLeast(0)
            "if", "unless", "for", "case", "begin" -> depth + 1
            else -> depth
        }
    }

    private fun encodeFields(fields: List<String>): String =
        fields.joinToString("|") { "${it.length}:$it" }

    private fun containsMacroInterpolation(element: PsiElement): Boolean =
        element is de.magynhard.crystal.psi.CrystalMacroInterpolation ||
            PsiTreeUtil.findChildOfType(
                element,
                de.magynhard.crystal.psi.CrystalMacroInterpolation::class.java,
                false
            ) != null

    private fun isConstantReceiver(receiverText: String): Boolean =
        receiverText.removePrefix("::").firstOrNull()?.isUpperCase() == true

    private fun <T : PsiElement> sourceOrder(): Comparator<T> =
        compareBy<T>({ it.containingFile?.virtualFile?.path.orEmpty() }, { it.textOffset })

    private fun <T : PsiElement> sourcePrecedence(): Comparator<T> =
        compareBy<T> { it.containingFile?.virtualFile?.path.orEmpty() }
            .thenByDescending { it.textOffset }

    private enum class ReceiverMode { STATIC, INSTANCE }

    private data class TypeExposure(
        val type: ExactReceiverType,
        val declarationMode: ReceiverMode
    )

    private data class TypeMetadata(
        val includes: List<ExactReceiverType>,
        val extends: List<ExactReceiverType>,
        val superclass: ExactReceiverType?,
        val complete: Boolean
    )

    private data class HierarchyCollection(
        val exposures: List<TypeExposure>,
        val complete: Boolean
    )

    private data class MethodCollection(
        val methods: List<CrystalMethodDefinition>,
        val complete: Boolean
    )

    private sealed interface IdentityResolution {
        data class Exact(val type: ExactReceiverType) : IdentityResolution
        data object Ambiguous : IdentityResolution
        data object Missing : IdentityResolution
    }
}
