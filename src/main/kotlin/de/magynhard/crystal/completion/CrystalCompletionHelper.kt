package de.magynhard.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
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
     * Finds a class/module/struct/enum definition by name.
     */
    fun findClassByName(name: String, project: Project): CrystalClassDefinition? {
        val scope = GlobalSearchScope.projectScope(project)
        val results = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project, scope, CrystalClassDefinition::class.java
        )
        return results.firstOrNull()
    }

    /**
     * Returns all static methods (def self.xxx) of a class definition.
     * Also searches in module/struct/enum bodies.
     */
    fun getStaticMethods(className: String, project: Project): List<CrystalMethodDefinition> {
        val classDef = findClassByName(className, project) ?: return emptyList()
        return getMethodsFromBody(classDef).filter { isStaticMethod(it) }
    }

    /**
     * Returns all instance methods (def xxx, not self.xxx) of a class definition.
     */
    fun getInstanceMethods(className: String, project: Project): List<CrystalMethodDefinition> {
        val classDef = findClassByName(className, project) ?: return emptyList()
        return getMethodsFromBody(classDef).filter { !isStaticMethod(it) }
    }

    /**
     * Returns all methods defined inside the body of a class/module/struct/enum.
     */
    private fun getMethodsFromBody(classDef: CrystalClassDefinition): List<CrystalMethodDefinition> {
        val body = classDef.classBody ?: return emptyList()
        return PsiTreeUtil.getChildrenOfTypeAsList(body, CrystalMethodDefinition::class.java)
    }

    /**
     * Returns all method names from the project-wide StubIndex.
     */
    fun getAllMethods(project: Project): Collection<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.projectScope(project)
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
     * Finds the `initialize` method of a class (the Crystal constructor).
     */
    fun getInitializeMethod(className: String, project: Project): CrystalMethodDefinition? {
        val classDef = findClassByName(className, project) ?: return null
        return getMethodsFromBody(classDef).firstOrNull { it.name == "initialize" }
    }

    /**
     * Builds a LookupElement for `new` with initialize parameters.
     */
    fun buildNewLookup(className: String, project: Project): LookupElementBuilder {
        val initMethod = getInitializeMethod(className, project)
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
     * Returns the enclosing class name of a method, or null if top-level.
     */
    fun getEnclosingClassName(method: CrystalMethodDefinition): String? {
        val classDef = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
        return classDef?.name
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
