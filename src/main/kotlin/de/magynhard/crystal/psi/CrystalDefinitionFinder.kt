package de.magynhard.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.stubs.*

object CrystalDefinitionFinder {

    fun findDefinitions(name: String, project: Project): List<PsiElement> {
        val scope = GlobalSearchScope.allScope(project)
        val results = mutableListOf<PsiElement>()
        results.addAll(StubIndex.getElements(CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java))
        results.addAll(StubIndex.getElements(CrystalMethodIndex.KEY, name, project, scope, CrystalMethodDefinition::class.java))
        results.addAll(StubIndex.getElements(CrystalMacroIndex.KEY, name, project, scope, CrystalMacroDefinition::class.java))
        return results
    }

    fun findMacros(name: String, project: Project): List<CrystalMacroDefinition> =
        StubIndex.getElements(CrystalMacroIndex.KEY, name, project, GlobalSearchScope.allScope(project), CrystalMacroDefinition::class.java).toList()

    fun findAliases(name: String, project: Project): List<CrystalAliasDefinition> =
        StubIndex.getElements(CrystalAliasIndex.KEY, name, project, GlobalSearchScope.allScope(project), CrystalAliasDefinition::class.java).toList()

    fun findAnnotations(name: String, project: Project): List<CrystalAnnotationDefinition> =
        StubIndex.getElements(CrystalAnnotationIndex.KEY, name, project, GlobalSearchScope.allScope(project), CrystalAnnotationDefinition::class.java).toList()

    fun findLibs(name: String, project: Project): List<CrystalLibDefinition> =
        StubIndex.getElements(CrystalLibIndex.KEY, name, project, GlobalSearchScope.allScope(project), CrystalLibDefinition::class.java).toList()
}
