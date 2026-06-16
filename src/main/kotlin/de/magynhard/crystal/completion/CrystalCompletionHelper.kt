package de.magynhard.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Helper for building completion lookup elements from Crystal PSI.
 */
object CrystalCompletionHelper {

    /**
     * Result of finding a type definition — carries the PSI element and its kind.
     */
    enum class TypeKind { CLASS, MODULE, STRUCT, ENUM }

    data class TypeLookupResult(
        val element: CrystalNamedElement,
        val kind: TypeKind
    )

    /**
     * Finds a class/module/struct/enum definition by name.
     * Returns the element and its kind, or null if not found.
     * If currentFile is provided, prefers the definition from that file.
     */
    fun findTypeByName(name: String, project: Project, currentFile: PsiFile? = null): TypeLookupResult? {
        val scope = GlobalSearchScope.allScope(project)
        val elements = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java
        )
        // Prefer the definition from the current file
        val element = if (currentFile != null) {
            elements.firstOrNull { it.containingFile?.virtualFile == currentFile.virtualFile }
                ?: elements.firstOrNull()
        } else {
            elements.firstOrNull()
        } ?: return null
        val kind = when (element) {
            is CrystalClassDefinition -> TypeKind.CLASS
            is CrystalModuleDefinition -> TypeKind.MODULE
            is CrystalStructDefinition -> TypeKind.STRUCT
            is CrystalEnumDefinition -> TypeKind.ENUM
            else -> return null
        }
        return TypeLookupResult(element, kind)
    }

    /**
     * Returns all static methods (def self.xxx) of a type definition.
     */
    fun getStaticMethods(typeName: String, project: Project): List<CrystalMethodDefinition> {
        val result = findTypeByName(typeName, project) ?: return emptyList()
        return getMethodsFromType(result).filter { isStaticMethod(it) }
    }

    /**
     * Returns all instance methods (def xxx, not self.xxx) of a type definition.
     */
    fun getInstanceMethods(typeName: String, project: Project): List<CrystalMethodDefinition> {
        val result = findTypeByName(typeName, project) ?: return emptyList()
        return getMethodsFromType(result).filter { !isStaticMethod(it) }
    }

    /**
     * Returns all methods belonging to a type, using the stub index.
     * For stdlib types, methods may be spread across multiple required files
     * and inherited via include — PSI tree traversal won't find them.
     * Instead, we search all indexed methods and filter by enclosing class name.
     */
    private fun getMethodsFromType(typeResult: TypeLookupResult): List<CrystalMethodDefinition> {
        val project = typeResult.element.project

        // 1. Collect the full type hierarchy (self + parents via inheritance/include)
        val hierarchyNames = collectFullHierarchy(typeResult)

        // 2. Search all indexed methods and filter by enclosing class
        val scope = GlobalSearchScope.allScope(project)
        val result = mutableListOf<CrystalMethodDefinition>()
        val seen = mutableSetOf<String>()

        for (key in StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)) {
            val elements = StubIndex.getElements(CrystalMethodIndex.KEY, key, project, scope, CrystalMethodDefinition::class.java)
            for (method in elements) {
                val enclosingClass = getEnclosingClassName(method) ?: continue
                if (enclosingClass in hierarchyNames) {
                    val name = method.name ?: continue
                    if (seen.add(name)) result.add(method)
                }
            }
        }
        return result
    }

    /**
     * Collects the full type hierarchy: the type itself, plus all parents
     * reachable via superclass clauses and include/extend statements.
     */
    private fun collectFullHierarchy(typeResult: TypeLookupResult): Set<String> {
        val project = typeResult.element.project
        val names = mutableSetOf<String>()
        val queue = ArrayDeque<TypeLookupResult>()
        queue.addLast(typeResult)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val typeName = current.element.name ?: continue
            if (!names.add(typeName)) continue

            val parentNames = collectParentTypeNames(current.element, current.kind)
            for (parentName in parentNames) {
                if (parentName in names) continue
                val parentResult = findTypeByName(parentName, project)
                    ?: findTypeByName(parentName.substringAfterLast("::"), project)
                if (parentResult != null) queue.addLast(parentResult)
            }
        }
        return names
    }

    /**
     * Collects parent class/module names from superclass/include clauses.
     * Handles: class Foo < Bar, class Foo < Bar::Baz, include Bar, extend Bar
     */
    private fun collectParentTypeNames(element: CrystalNamedElement, kind: TypeKind): Set<String> {
        val parentNames = mutableSetOf<String>()

        // Superclass from class/struct definition header: class Foo < Bar
        when (element) {
            is CrystalClassDefinition -> {
                val superClause = element.superclassClause
                if (superClause != null) {
                    extractTypeNameFromReference(superClause)?.let { parentNames.add(it) }
                }
            }
            is CrystalStructDefinition -> {
                val superClause = element.superclassClause
                if (superClause != null) {
                    extractTypeNameFromReference(superClause)?.let { parentNames.add(it) }
                }
            }
        }

        // Include/Extend from class body: include Bar, extend Bar
        val body: PsiElement = when (kind) {
            TypeKind.CLASS -> (element as CrystalClassDefinition).classBody ?: return parentNames
            TypeKind.MODULE -> (element as CrystalModuleDefinition).classBody ?: return parentNames
            TypeKind.STRUCT -> (element as CrystalStructDefinition).classBody ?: return parentNames
            TypeKind.ENUM -> return parentNames
        }

        for (child in body.children) {
            when (child) {
                is CrystalIncludeStatement -> {
                    val typeRef = child.typeReference
                    if (typeRef != null) {
                        val text = typeRef.text.trim()
                        if (text.isNotEmpty() && text[0].isUpperCase()) {
                            parentNames.add(text)
                        }
                    }
                }
                is CrystalExtendStatement -> {
                    val typeRef = child.typeReference
                    if (typeRef != null) {
                        val text = typeRef.text.trim()
                        if (text.isNotEmpty() && text[0].isUpperCase()) {
                            parentNames.add(text)
                        }
                    }
                }
            }
        }

        return parentNames
    }

    /**
     * Extracts the type name from a superclass clause.
     */
    private fun extractTypeNameFromReference(superClause: CrystalSuperclassClause): String? {
        val typeRef = superClause.typeReference
        val text = typeRef.text.trim()
        return text.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
    }

    /**
     * Returns whether a type can be instantiated with .new (classes and structs only).
     */
    fun canInstantiate(typeName: String, project: Project): Boolean {
        val result = findTypeByName(typeName, project) ?: return true // unknown type — offer new as fallback
        return result.kind == TypeKind.CLASS || result.kind == TypeKind.STRUCT
    }

    /**
     * Returns all method names from the project-wide StubIndex.
     */
    fun getAllMethods(project: Project): Collection<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(project)
        val allKeys = StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)
        return allKeys.flatMap { key ->
            StubIndex.getElements(CrystalMethodIndex.KEY, key, project, scope, CrystalMethodDefinition::class.java)
        }
    }

    /**
     * Returns all class/module/struct/enum names from the project-wide StubIndex.
     */
    fun getAllClassNames(project: Project): Collection<String> {
        return StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
    }

    /**
     * Finds the `initialize` method of a class/struct (the Crystal constructor).
     */
    fun getInitializeMethod(typeName: String, project: Project, currentFile: PsiFile? = null): CrystalMethodDefinition? {
        val result = findTypeByName(typeName, project, currentFile) ?: return null
        return getMethodsFromType(result).firstOrNull { it.name == "initialize" }
    }

    /**
     * Builds a LookupElement for `new` with initialize parameters.
     */
    fun buildNewLookup(className: String, project: Project, currentFile: PsiFile? = null): LookupElementBuilder {
        val initMethod = getInitializeMethod(className, project, currentFile)
        val signature = if (initMethod != null) getParameterSignature(initMethod) else "()"
        val tailText = if (signature == "()") "" else signature

        return LookupElementBuilder.create("new")
            .withIcon(AllIcons.Nodes.Method)
            .withTailText(tailText, true)
            .withTypeText(className, true)
    }

    /**
     * Checks whether a method is a class method (def self.xxx).
     */
    fun isStaticMethod(method: CrystalMethodDefinition): Boolean {
        val methodName = method.methodName ?: return false
        return methodName.node.findChildByType(CrystalTypes.SELF) != null
    }

    /**
     * Returns the enclosing type name of a method, or null if top-level.
     */
    fun getEnclosingClassName(method: CrystalMethodDefinition): String? {
        val classDef = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
        if (classDef != null) return classDef.name
        val moduleDef = PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)
        if (moduleDef != null) return moduleDef.name
        val structDef = PsiTreeUtil.getParentOfType(method, CrystalStructDefinition::class.java)
        if (structDef != null) return structDef.name
        val enumDef = PsiTreeUtil.getParentOfType(method, CrystalEnumDefinition::class.java)
        if (enumDef != null) return enumDef.name
        return null
    }

    /**
     * Formats the parameter list of a method as a string like "(a, b, c)".
     */
    fun getParameterSignature(method: CrystalMethodDefinition): String {
        val paramList = method.parameterList ?: return "()"
        val params = paramList.parameterList
        if (params.isEmpty()) return "()"

        val paramStrings = params.map { param ->
            val nameNode = param.node.findChildByType(CrystalTypes.IDENTIFIER)
            val name = nameNode?.text ?: "?"
            val typeRef = param.typeReference
            if (typeRef != null) "$name : ${typeRef.text}" else name
        }
        return "(${paramStrings.joinToString(", ")})"
    }

    /**
     * Returns the return type annotation of a method, or null.
     */
    fun getReturnType(method: CrystalMethodDefinition): String? {
        return method.typeReference?.text
    }

    /**
     * Builds a LookupElement for a method.
     */
    fun buildMethodLookup(method: CrystalMethodDefinition): LookupElementBuilder? {
        val name = method.name ?: return null
        val signature = getParameterSignature(method)
        val className = getEnclosingClassName(method)
        val returnType = getReturnType(method)

        var builder = LookupElementBuilder.create(name)
            .withIcon(AllIcons.Nodes.Method)
            .withTailText(signature, true)

        if (className != null) {
            val typeText = if (returnType != null) "$className → $returnType" else className
            builder = builder.withTypeText(typeText, true)
        } else if (returnType != null) {
            builder = builder.withTypeText(returnType, true)
        }

        return builder
    }

    /**
     * Builds a LookupElement for a class/module/struct/enum name.
     */
    fun buildClassLookup(name: String): LookupElementBuilder {
        return LookupElementBuilder.create(name)
            .withIcon(AllIcons.Nodes.Class)
    }
}
