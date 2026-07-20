package de.magynhard.crystal.stubs

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.psi.CrystalAliasDefinition
import de.magynhard.crystal.psi.CrystalAnnotationDefinition
import de.magynhard.crystal.psi.CrystalLibDefinition
import de.magynhard.crystal.psi.CrystalMacroDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalNamedElement

object CrystalIndexService {

    fun findTypes(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalNamedElement> =
        StubIndex.getElements(CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java)

    fun processTypes(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in CrystalNamedElement>
    ): Boolean = StubIndex.getInstance().processElements(
        CrystalClassIndex.KEY,
        name,
        project,
        scope,
        null,
        CrystalNamedElement::class.java,
        processor
    )

    fun findMethods(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalMethodDefinition> =
        StubIndex.getElements(CrystalMethodIndex.KEY, name, project, scope, CrystalMethodDefinition::class.java)

    fun findMethodsByClass(
        className: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalMethodDefinition> =
        StubIndex.getElements(
            CrystalMethodByClassIndex.KEY,
            className,
            project,
            scope,
            CrystalMethodDefinition::class.java
        )

    fun findTopLevelMethods(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalMethodDefinition> =
        StubIndex.getElements(
            CrystalTopLevelMethodIndex.KEY,
            name,
            project,
            scope,
            CrystalMethodDefinition::class.java
        )

    fun findNestedTypes(
        enclosingTypeName: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalNamedElement> =
        StubIndex.getElements(
            CrystalClassByEnclosingIndex.KEY,
            enclosingTypeName,
            project,
            scope,
            CrystalNamedElement::class.java
        )

    fun findMacros(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalMacroDefinition> =
        StubIndex.getElements(CrystalMacroIndex.KEY, name, project, scope, CrystalMacroDefinition::class.java)

    fun findAliases(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalAliasDefinition> =
        StubIndex.getElements(CrystalAliasIndex.KEY, name, project, scope, CrystalAliasDefinition::class.java)

    fun findAnnotations(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalAnnotationDefinition> =
        StubIndex.getElements(
            CrystalAnnotationIndex.KEY,
            name,
            project,
            scope,
            CrystalAnnotationDefinition::class.java
        )

    fun findLibs(
        name: String,
        project: Project,
        scope: GlobalSearchScope
    ): Collection<CrystalLibDefinition> =
        StubIndex.getElements(CrystalLibIndex.KEY, name, project, scope, CrystalLibDefinition::class.java)

    fun processTypeNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalClassIndex.KEY,
        CrystalNamedElement::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun processMethodNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalMethodIndex.KEY,
        CrystalMethodDefinition::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun processMacroNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalMacroIndex.KEY,
        CrystalMacroDefinition::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun processAliasNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalAliasIndex.KEY,
        CrystalAliasDefinition::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun processAnnotationNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalAnnotationIndex.KEY,
        CrystalAnnotationDefinition::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun processLibNames(
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalLibIndex.KEY,
        CrystalLibDefinition::class.java,
        project,
        scope,
        filter,
        processor
    )

    fun getAllTypeNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)

    fun getAllTopLevelMethodNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(CrystalTopLevelMethodIndex.KEY, project)

    private fun <T : PsiElement> processNames(
        key: StubIndexKey<String, T>,
        elementClass: Class<T>,
        project: Project,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean {
        val names = mutableListOf<String>()
        StubIndex.getInstance().processAllKeys(key, Processor { names.add(it) }, scope, filter)

        for (name in names) {
            var presentInScope = false
            StubIndex.getInstance().processElements(
                key,
                name,
                project,
                scope,
                filter,
                elementClass,
                Processor {
                    presentInScope = true
                    false
                }
            )
            if (presentInScope && !processor.process(name)) return false
        }
        return true
    }
}
