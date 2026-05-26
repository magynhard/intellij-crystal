package de.magynhard.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Project-wide definition lookup for Go to Definition and Reference resolution.
 *
 * Uses two strategies:
 * 1. StubIndex (fast, but may be stale or not yet built)
 * 2. FileTypeIndex + PSI tree walk (always works, scans all .cr files)
 */
object CrystalDefinitionFinder {

    /**
     * Find all definitions (class/module/struct/enum/method/macro) with the given name
     * across the entire project.
     */
    fun findDefinitions(name: String, project: Project): List<PsiElement> {
        val scope = GlobalSearchScope.allScope(project)
        val results = mutableListOf<PsiElement>()

        // 1. StubIndex lookup (fast path)
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

        // 2. FileTypeIndex + PSI tree walk fallback (always works)
        if (results.isEmpty()) {
            val psiManager = PsiManager.getInstance(project)
            FileTypeIndex.processFiles(CrystalFileType, { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile) ?: return@processFiles true
                findDefinitionsInTree(psiFile, name, results)
                true
            }, scope)
        }

        return results
    }

    /**
     * Find definitions in a single PSI tree (recursive).
     */
    fun findDefinitionsInTree(root: PsiElement, name: String, results: MutableList<PsiElement>) {
        for (child in root.children) {
            when (child) {
                is CrystalMethodDefinition -> {
                    if (child.name == name) results.add(child)
                }
                is CrystalMacroDefinition -> {
                    if (child.name == name) results.add(child)
                }
                is CrystalClassDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findDefinitionsInTree(it, name, results) }
                }
                is CrystalModuleDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findDefinitionsInTree(it, name, results) }
                }
                is CrystalStructDefinition -> {
                    if (child.name == name) results.add(child)
                    child.classBody?.let { findDefinitionsInTree(it, name, results) }
                }
                is CrystalEnumDefinition -> {
                    if (child.name == name) results.add(child)
                }
                else -> findDefinitionsInTree(child, name, results)
            }
        }
    }
}
