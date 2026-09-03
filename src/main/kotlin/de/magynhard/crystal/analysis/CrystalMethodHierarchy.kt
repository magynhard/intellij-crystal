package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*

internal data class CrystalTypeIdentity(val simpleName: String, val qualifiedName: String)

internal enum class CrystalReceiverMode { STATIC, INSTANCE }

internal data class CrystalCollectedMethod(
    val method: CrystalMethodDefinition,
    val signatureKey: String,
    val receiverType: CrystalTypeIdentity,
    val declarationMode: CrystalReceiverMode,
    val depth: Int,
    val precedence: Int
)

internal data class CrystalAllMethodCollection(
    val methods: List<CrystalCollectedMethod>,
    val complete: Boolean
)

internal data class CrystalMethodCollection(
    val methods: List<CrystalMethodDefinition>,
    val complete: Boolean
)

internal sealed interface CrystalConstructorResolution {
    val identity: CrystalTypeIdentity

    data class Methods(
        override val identity: CrystalTypeIdentity,
        val methods: List<CrystalMethodDefinition>
    ) : CrystalConstructorResolution

    data class Record(
        override val identity: CrystalTypeIdentity,
        val recordDefinition: CrystalMethodCallExpression
    ) : CrystalConstructorResolution

    data class Implicit(override val identity: CrystalTypeIdentity) : CrystalConstructorResolution
    data class Unavailable(override val identity: CrystalTypeIdentity) : CrystalConstructorResolution
    data class Abstract(override val identity: CrystalTypeIdentity) : CrystalConstructorResolution
    data class Incomplete(override val identity: CrystalTypeIdentity) : CrystalConstructorResolution
}

internal class CrystalMethodHierarchy(
    private val context: PsiElement,
    private val findTypesByName: (String) -> List<CrystalNamedElement>,
    private val findMethodsByTypeName: (String) -> List<CrystalMethodDefinition>
) {
    private val declarations = mutableMapOf<CrystalTypeIdentity, List<CrystalNamedElement>>()
    private val methods = mutableMapOf<CrystalTypeIdentity, List<CrystalMethodDefinition>>()
    private val metadata = mutableMapOf<MetadataKey, TypeMetadata>()
    private val allCollections = mutableMapOf<CollectionKey, CrystalAllMethodCollection>()
    private val namedCollections = mutableMapOf<NamedCollectionKey, CrystalMethodCollection>()

    fun collectMethods(
        receiverType: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        includeModuleEdges: Boolean = true
    ): CrystalAllMethodCollection = allCollections.getOrPut(CollectionKey(receiverType, mode, includeModuleEdges)) {
        val hierarchy = collectHierarchy(receiverType, mode, includeModuleEdges)
        val result = mutableListOf<CrystalCollectedMethod>()
        val seenSignatures = mutableSetOf<String>()
        for (exposure in hierarchy.exposures) {
            val typeMetadata = metadata(exposure.type, exposure.declarationMode, includeModuleEdges)
            val exposedMethods = findExactMethods(exposure.type)
                .filter { CrystalPsiUtils.isSelfMethod(it) == (exposure.declarationMode == CrystalReceiverMode.STATIC) }
                .filter { it.macroInterpolation == null && it.name !in typeMetadata.uncertainMethodNames }
            val signatures = exposedMethods.groupBy(::canonicalSignature)
            if (hierarchy.complete && signatures.values.any { group ->
                    group.map { it.containingFile?.virtualFile?.path }.distinct().size > 1
                }) {
                // Strict mode (used by resolution-backed consumers): cross-file
                // reopenings with identical signatures cannot be precedence-proven.
                return@getOrPut CrystalAllMethodCollection(emptyList(), false)
            }
            // Lenient mode (incomplete hierarchy — macro-generated members in
            // stdlib types like Number): offer every explicitly written method;
            // macro-generated names and edges beyond incomplete types are absent.
            // First occurrence per signature wins.
            for (group in signatures.values) {
                val method = group.first()
                val signature = canonicalSignature(method)
                if (seenSignatures.add(signature)) {
                    result.add(
                        CrystalCollectedMethod(
                            method,
                            signature,
                            exposure.type,
                            exposure.declarationMode,
                            exposure.depth,
                            result.size
                        )
                    )
                }
            }
        }
        CrystalAllMethodCollection(result, hierarchy.complete)
    }

    fun collectNamedMethods(
        receiverType: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        methodName: String,
        actualSelf: Boolean? = null,
        includeModuleEdges: Boolean = true
    ): CrystalMethodCollection = namedCollections.getOrPut(
        NamedCollectionKey(receiverType, mode, methodName, actualSelf, includeModuleEdges)
    ) {
        val hierarchy = collectHierarchy(receiverType, mode, includeModuleEdges)
        if (!hierarchy.complete) return@getOrPut CrystalMethodCollection(emptyList(), false)
        val result = mutableListOf<CrystalMethodDefinition>()
        val seen = mutableSetOf<String>()
        for (exposure in hierarchy.exposures) {
            val typeMetadata = metadata(exposure.type, exposure.declarationMode, includeModuleEdges)
            if (methodName in typeMetadata.uncertainMethodNames) {
                return@getOrPut CrystalMethodCollection(emptyList(), false)
            }
            val candidates = findExactMethods(exposure.type).filter { method ->
                method.name == methodName &&
                    CrystalPsiUtils.isSelfMethod(method) == (exposure.declarationMode == CrystalReceiverMode.STATIC) &&
                    (actualSelf == null || CrystalPsiUtils.isSelfMethod(method) == actualSelf)
            }
            // Macro-generated method names (def {{...}}) make a type's member set
            // uncertain — but only for names that are NOT explicitly defined. A
            // textually written `def self.new` is real regardless of what the type's
            // {% for %} loops additionally generate (stdlib http/client.cr).
            if (candidates.isEmpty() && typeMetadata.hasMacroGeneratedMethodNames) {
                return@getOrPut CrystalMethodCollection(emptyList(), false)
            }
            if (candidates.groupBy(::canonicalSignature).values.any { group ->
                    group.map { it.containingFile?.virtualFile?.path }.distinct().size > 1
                }) {
                return@getOrPut CrystalMethodCollection(emptyList(), false)
            }
            candidates.forEach { if (seen.add(canonicalSignature(it))) result.add(it) }
        }
        CrystalMethodCollection(result, true)
    }

    fun findExactTypeDeclarations(type: CrystalTypeIdentity): List<CrystalNamedElement> =
        declarations.getOrPut(type) {
            findTypesByName(type.simpleName).filter { CrystalPsiUtils.buildQualifiedName(it) == type.qualifiedName }
        }

    private fun collectHierarchy(
        root: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        includeModuleEdges: Boolean
    ): HierarchyCollection {
        val result = mutableListOf<TypeExposure>()
        val visited = mutableSetOf<Pair<CrystalTypeIdentity, CrystalReceiverMode>>()
        var complete = true

        fun visit(type: CrystalTypeIdentity, declarationMode: CrystalReceiverMode, depth: Int) {
            if (!complete || !visited.add(type to declarationMode)) return
            result.add(TypeExposure(type, declarationMode, depth))
            val typeMetadata = metadata(type, declarationMode, includeModuleEdges)
            if (!typeMetadata.complete) {
                complete = false
                return
            }
            if (includeModuleEdges) {
                val edges = when (declarationMode) {
                    CrystalReceiverMode.INSTANCE -> typeMetadata.includes
                    CrystalReceiverMode.STATIC -> typeMetadata.extends
                }
                edges.forEach { visit(it, CrystalReceiverMode.INSTANCE, depth + 1) }
            }
            typeMetadata.superclass?.let { visit(it, declarationMode, depth + 1) }
        }

        visit(root, mode, 0)
        return HierarchyCollection(result, complete)
    }

    private fun metadata(
        type: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        includeModuleEdges: Boolean
    ): TypeMetadata = metadata.getOrPut(MetadataKey(type, mode, includeModuleEdges)) {
        collectMetadata(type, mode, includeModuleEdges)
    }

    private fun collectMetadata(
        type: CrystalTypeIdentity,
        mode: CrystalReceiverMode,
        includeModuleEdges: Boolean
    ): TypeMetadata {
        val exactDeclarations = findExactTypeDeclarations(type).sortedWith(sourceOrder())
        val includes = mutableListOf<CrystalTypeIdentity>()
        val extends = mutableListOf<CrystalTypeIdentity>()
        val superclasses = mutableListOf<CrystalTypeIdentity>()
        val uncertainMethodNames = mutableSetOf<String>()
        val edgeFiles = mutableSetOf<String>()
        var hasMacroGeneratedMethodNames = false
        var complete = exactDeclarations.isNotEmpty()

        for (declaration in exactDeclarations) {
            if (CrystalPsiUtils.isInsideMacroControlRegion(declaration)) { complete = false }
            val body = when (declaration) {
                is CrystalClassDefinition -> declaration.classBody
                is CrystalModuleDefinition -> declaration.classBody
                is CrystalStructDefinition -> declaration.classBody
                is CrystalEnumDefinition -> declaration.enumBody
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
                        if (member is CrystalMethodDefinition &&
                            CrystalPsiUtils.isSelfMethod(member) == (mode == CrystalReceiverMode.STATIC)) {
                            if (member.macroInterpolation != null) {
                                hasMacroGeneratedMethodNames = true
                            } else if (macroDepth > 0) {
                                member.name?.let(uncertainMethodNames::add) ?: run { hasMacroGeneratedMethodNames = true }
                            }
                        }
                        if (macroDepth > 0 && isRelevantMacroControlledEdge(member, mode, includeModuleEdges)) {
                            complete = false
                            return@forEach
                        }
                        when (member) {
                            is CrystalIncludeStatement -> if (includeModuleEdges && mode == CrystalReceiverMode.INSTANCE) {
                                resolveEdge(member.typeReference, member)?.let {
                                    includes.add(it)
                                    edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                                } ?: run { complete = false }
                            }
                            is CrystalExtendStatement -> if (includeModuleEdges && mode == CrystalReceiverMode.STATIC) {
                                if (member.node.findChildByType(CrystalTypes.SELF) != null ||
                                    member.text.filterNot(Char::isWhitespace) == "extendself") {
                                    extends.add(type)
                                    edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                                } else {
                                    resolveEdge(member.typeReference, member)?.let {
                                        extends.add(it)
                                        edgeFiles.add(member.containingFile.virtualFile?.path.orEmpty())
                                    } ?: run { complete = false }
                                }
                            }
                        }
                    }
                }
            }
            val superclass = when (declaration) {
                is CrystalClassDefinition -> declaration.superclassClause?.typeReference
                is CrystalStructDefinition -> declaration.superclassClause?.typeReference
                // An enum's optional `: Type` suffix is the base enum type
                // (`enum Color : UInt8`), never a nominal superclass — enums
                // always inherit the compiler-imposed `Enum` struct.
                is CrystalEnumDefinition -> null
                else -> null
            }
            if (superclass != null) {
                resolveEdge(superclass, declaration)?.let(superclasses::add) ?: run { complete = false }
            }
        }

        if (superclasses.isEmpty()) {
            implicitPrimitiveSuperclass(type)?.let(superclasses::add)
                ?: implicitDeclaredKindSuperclass(type, exactDeclarations)?.let(superclasses::add)
        }

        val distinctSuperclasses = superclasses.distinct()
        if (distinctSuperclasses.size > 1 || edgeFiles.size > 1) complete = false
        return TypeMetadata(
            includes.asReversed().distinct(),
            extends.asReversed().distinct(),
            distinctSuperclasses.singleOrNull(),
            complete,
            uncertainMethodNames,
            hasMacroGeneratedMethodNames
        )
    }

    private fun resolveEdge(reference: CrystalTypeReference?, edgeContext: PsiElement): CrystalTypeIdentity? {
        val root = reference?.text?.let(::normalizeExactTypeRoot) ?: return null
        val simpleName = root.substringAfterLast("::")
        val candidates = if (root.contains("::")) {
            listOf(root)
        } else {
            lexicalTypeCandidates(simpleName, edgeContext)
        }
        for (candidate in candidates) {
            val identities = findTypesByName(simpleName).mapNotNull(CrystalPsiUtils::buildQualifiedName)
                .filter { it == candidate }.distinct()
            if (identities.size > 1) return null
            identities.singleOrNull()?.let { return CrystalTypeIdentity(simpleName, it) }
        }
        return null
    }

    private fun implicitPrimitiveSuperclass(type: CrystalTypeIdentity): CrystalTypeIdentity? {
        if (type.qualifiedName != type.simpleName) return null
        val parentName = IMPLICIT_PRIMITIVE_SUPERCLASSES[type.simpleName] ?: return null
        val declarations = findTypesByName(parentName).filter {
            CrystalPsiUtils.buildQualifiedName(it) == parentName
        }
        return if (declarations.isEmpty()) null else CrystalTypeIdentity(parentName, parentName)
    }

    /**
     * Every Crystal class/struct/enum without a declared superclass inherits the
     * compiler-imposed base (class → Reference → Object, struct → Struct →
     * Value → Object, enum → Enum → Value → Object). Neither Reference, Value,
     * Struct, nor Enum declare their superclass in stdlib source, so these edges
     * are invisible to source-only resolution and must be implicit. Modules
     * cannot inherit. An enum's `: Type` suffix is the underlying integer type,
     * not a nominal superclass, so it is never treated as one.
     */
    private fun implicitDeclaredKindSuperclass(
        type: CrystalTypeIdentity,
        declarations: List<CrystalNamedElement>
    ): CrystalTypeIdentity? {
        // Object is the hierarchy root (Object.superclass == nil) and must not
        // fall through to the generic class → Reference edge.
        if (type.simpleName == "Object") return null
        val parentName = when {
            declarations.any { it is CrystalEnumDefinition } -> "Enum"
            declarations.any { it is CrystalStructDefinition } -> "Struct"
            declarations.any { it is CrystalClassDefinition } -> "Reference"
            else -> return null
        }
        if (parentName == type.simpleName) return null
        val parentDeclarations = findTypesByName(parentName).filter {
            CrystalPsiUtils.buildQualifiedName(it) == parentName
        }
        return if (parentDeclarations.isEmpty()) null else CrystalTypeIdentity(parentName, parentName)
    }

    private fun findExactMethods(type: CrystalTypeIdentity): List<CrystalMethodDefinition> = methods.getOrPut(type) {
        findMethodsByTypeName(type.simpleName).asSequence().filter { method ->
            CrystalPsiUtils.getEnclosingType(method)?.let(CrystalPsiUtils::buildQualifiedName) == type.qualifiedName
        }.sortedWith(sourcePrecedence()).toList()
    }

    internal fun canonicalSignature(method: CrystalMethodDefinition): String {
        val parameterList = method.parameterList
        val parameters = parameterList?.parameterList.orEmpty().map { canonicalParameter(it, parameterList) }
        return encodeFields(listOf(method.name.orEmpty(), parameters.size.toString()) + parameters)
    }

    internal fun constructorDispatchSignatures(method: CrystalMethodDefinition): Set<String> {
        val parameterList = method.parameterList ?: return setOf(encodeFields(emptyList()))
        var signatures = setOf(emptyList<String>())
        for (parameter in parameterList.parameterList) {
            val descriptor = constructorDispatchParameter(parameter, parameterList)
            val withParameter = signatures.mapTo(linkedSetOf()) { it + descriptor }
            signatures = if (parameter.expression != null) signatures + withParameter else withParameter
        }
        return signatures.mapTo(linkedSetOf()) { encodeFields(it) }
    }

    private fun constructorDispatchParameter(
        parameter: CrystalParameter,
        parameterList: CrystalParameterList
    ): String {
        val kind = when {
            parameter.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null ||
                parameter.node.findChildByType(CrystalTypes.WRAP_DOUBLE_STAR) != null -> "double-splat"
            parameter.node.findChildByType(CrystalTypes.STAR) != null ||
                parameter.node.findChildByType(CrystalTypes.WRAP_STAR) != null -> "splat"
            parameter.node.findChildByType(CrystalTypes.AMPERSAND) != null -> "block"
            isNamedOnly(parameter, parameterList) -> "named-only"
            else -> "regular"
        }
        val callName = if (kind == "named-only") {
            parameter.parameterNameInfo().callSiteName.orEmpty()
        } else {
            ""
        }
        return encodeFields(listOf(kind, callName, canonicalTypeRestriction(parameter, kind)))
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
        return encodeFields(
            listOf(kind, parameter.parameterNameInfo().callSiteName.orEmpty(), "",
                canonicalTypeRestriction(parameter, kind),
                (parameter.expression != null).toString())
        )
    }

    private fun isNamedOnly(parameter: CrystalParameter, parameterList: CrystalParameterList): Boolean {
        val index = parameterList.parameterList.indexOf(parameter)
        if (parameterList.parameterList.take(index).any { it.node.findChildByType(CrystalTypes.STAR) != null }) return true
        return parameterList.node.getChildren(null).any {
            it.startOffset < parameter.node.startOffset && it.elementType == CrystalTypes.STAR
        }
    }

    private fun canonicalTypeRestriction(parameter: CrystalParameter, kind: String): String {
        parameter.typeReference?.let { return it.text.filterNot(Char::isWhitespace) }
        if (kind != "block") return ""
        val children = parameter.node.getChildren(null).toList()
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        if (colonIndex < 0) return ""
        return children.drop(colonIndex + 1).joinToString("") {
            when (it.elementType) {
                TokenType.WHITE_SPACE, CrystalTypes.NEWLINE, CrystalTypes.LINE_COMMENT -> ""
                else -> it.text.filterNot(Char::isWhitespace)
            }
        }
    }

    private fun semanticMembers(element: PsiElement): List<PsiElement> = when (element) {
        is CrystalIncludeStatement, is CrystalExtendStatement, is CrystalMethodDefinition -> listOf(element)
        is CrystalVisibilityModifier, is CrystalStatement -> element.children.flatMap(::semanticMembers)
        else -> emptyList()
    }

    private fun isRelevantMacroControlledEdge(
        member: PsiElement,
        mode: CrystalReceiverMode,
        includeModuleEdges: Boolean
    ): Boolean = when (member) {
        is CrystalIncludeStatement -> includeModuleEdges && mode == CrystalReceiverMode.INSTANCE
        is CrystalExtendStatement -> includeModuleEdges && mode == CrystalReceiverMode.STATIC
        else -> false
    }

    private fun updateMacroDepth(depth: Int, control: CrystalMacroControl): Int {
        val keyword = control.text.removePrefix("{%").removeSuffix("%}").trim().takeWhile { !it.isWhitespace() }
        return when (keyword) {
            "end" -> (depth - 1).coerceAtLeast(0)
            "if", "unless", "for", "case", "begin" -> depth + 1
            else -> depth
        }
    }

    private fun encodeFields(fields: List<String>): String = fields.joinToString("|") { "${it.length}:$it" }

    private fun <T : PsiElement> sourceOrder(): Comparator<T> =
        compareBy<T>({ it.containingFile?.virtualFile?.path.orEmpty() }, { it.textOffset })

    private fun <T : PsiElement> sourcePrecedence(): Comparator<T> =
        compareBy<T> { it.containingFile?.virtualFile?.path.orEmpty() }.thenByDescending { it.textOffset }

    private data class CollectionKey(
        val type: CrystalTypeIdentity,
        val mode: CrystalReceiverMode,
        val includeModuleEdges: Boolean
    )
    private data class NamedCollectionKey(
        val type: CrystalTypeIdentity,
        val mode: CrystalReceiverMode,
        val methodName: String,
        val actualSelf: Boolean?,
        val includeModuleEdges: Boolean
    )
    private data class MetadataKey(
        val type: CrystalTypeIdentity,
        val mode: CrystalReceiverMode,
        val includeModuleEdges: Boolean
    )
    private data class TypeExposure(
        val type: CrystalTypeIdentity,
        val declarationMode: CrystalReceiverMode,
        val depth: Int
    )
    private data class TypeMetadata(
        val includes: List<CrystalTypeIdentity>,
        val extends: List<CrystalTypeIdentity>,
        val superclass: CrystalTypeIdentity?,
        val complete: Boolean,
        val uncertainMethodNames: Set<String>,
        val hasMacroGeneratedMethodNames: Boolean
    )
    private data class HierarchyCollection(val exposures: List<TypeExposure>, val complete: Boolean)
}

private val IMPLICIT_PRIMITIVE_SUPERCLASSES = mapOf(
    "Int8" to "Int",
    "Int16" to "Int",
    "Int32" to "Int",
    "Int64" to "Int",
    "Int128" to "Int",
    // The source never declares an abstract `UInt` and the compiler-imposed
    // parent is Int (crystal eval: UInt32.superclass == Int, UInt32 < Int) —
    // mapping to "UInt" resolved to nothing and left UInt receivers chainless.
    "UInt8" to "Int",
    "UInt16" to "Int",
    "UInt32" to "Int",
    "UInt64" to "Int",
    "UInt128" to "Int",
    "Float32" to "Float",
    "Float64" to "Float",
    // The source never declares these supers (compiler-imposed primitive
    // hierarchy): without the extra hop, Number's methods (round, clamp, step)
    // are unreachable from Float64/Float receivers.
    "Float" to "Number",
    // No stdlib base type declares its superclass either (compiler-imposed,
    // verified via crystal eval: Reference < Object, Value < Object,
    // Struct < Value, Number < Value, Int < Number, Enum/Bool/Char/Symbol < Value).
    // Without these edges the hierarchy walk never reaches Object, so methods
    // written inside a reopened `class Object` (e.g. Object#to_json from
    // `require "json"`) are invisible to every receiver.
    "Int" to "Number",
    "Number" to "Value",
    "Enum" to "Value",
    "Bool" to "Value",
    "Char" to "Value",
    "Symbol" to "Value",
    "Struct" to "Value",
    "Value" to "Object",
    "Reference" to "Object",
)

internal fun normalizeExactTypeRoot(typeText: String): String? {
    val compact = typeText.filterNot(Char::isWhitespace).removePrefix("::")
    if (compact.isEmpty()) return null
    var depth = 0
    var genericStart = -1
    for (index in compact.indices) {
        when (compact[index]) {
            '(' -> {
                if (depth == 0) genericStart = index
                depth++
            }
            ')' -> {
                depth--
                if (depth < 0) return null
                if (depth == 0 && index != compact.lastIndex) return null
            }
            '|' -> if (depth == 0) return null
        }
    }
    if (depth != 0) return null
    val root = if (genericStart >= 0) compact.substring(0, genericStart) else compact
    return root.takeIf { it.split("::").all { part -> part.firstOrNull()?.isUpperCase() == true } }
}

private fun lexicalTypeCandidates(simpleName: String, context: PsiElement): List<String> {
    val enclosing = CrystalPsiUtils.getEnclosingType(context)?.let(CrystalPsiUtils::buildQualifiedName)
        ?.split("::").orEmpty()
    return buildList {
        for (size in enclosing.size downTo 1) {
            add(enclosing.take(size).joinToString("::") + "::$simpleName")
        }
        add(simpleName)
    }
}
