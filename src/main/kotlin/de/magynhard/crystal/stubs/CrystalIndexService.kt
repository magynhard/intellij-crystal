package de.magynhard.crystal.stubs

import com.intellij.openapi.project.Project
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
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalClassIndex.KEY,
        scope,
        filter,
        processor
    )

    fun processMethodNames(
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalMethodIndex.KEY,
        scope,
        filter,
        processor
    )

    fun processMacroNames(
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalMacroIndex.KEY,
        scope,
        filter,
        processor
    )

    fun processAliasNames(
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalAliasIndex.KEY,
        scope,
        filter,
        processor
    )

    fun processAnnotationNames(
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalAnnotationIndex.KEY,
        scope,
        filter,
        processor
    )

    fun processLibNames(
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = processNames(
        CrystalLibIndex.KEY,
        scope,
        filter,
        processor
    )

    fun getAllTypeNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)

    fun getAllTopLevelMethodNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(CrystalTopLevelMethodIndex.KEY, project)

    private fun processNames(
        key: StubIndexKey<String, *>,
        scope: GlobalSearchScope,
        filter: IdFilter?,
        processor: Processor<in String>
    ): Boolean = StubIndex.getInstance().processAllKeys(key, processor, scope, filter)
}
