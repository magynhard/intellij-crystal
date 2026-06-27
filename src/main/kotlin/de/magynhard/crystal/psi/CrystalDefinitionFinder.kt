package de.magynhard.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Project-wide definition lookup for Go to Definition and Reference resolution.
 *
 * Uses StubIndex for fast lookups. No FileTypeIndex fallback — that scans all
 * .cr files in the project and causes 90+ second delays on every right-click.
 */
object CrystalDefinitionFinder {

    /**
     * Find all definitions (class/module/struct/enum/method/macro) with the given name
     * across the entire project via StubIndex.
     */
    fun findDefinitions(name: String, project: Project): List<PsiElement> {
        val scope = GlobalSearchScope.allScope(project)
        val results = mutableListOf<PsiElement>()

        val types = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project, scope,
            CrystalNamedElement::class.java
        )
        results.addAll(types)

        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, name, project, scope,
            CrystalMethodDefinition::class.java
        )
        results.addAll(methods)

        return results
    }
}
