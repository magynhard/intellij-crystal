package de.magynhard.crystal.navigation

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import de.magynhard.crystal.analysis.CrystalRequireCollector
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.psi.CrystalRequireStatement

/**
 * Resolves a Crystal `require` statement to the required file(s) for navigation
 * (Go to Declaration on the `require` keyword or its string argument).
 *
 * Reuses the existing require infrastructure:
 * - [CrystalRequireCollector.extractStaticPath] extracts the static require
 *   path (rejecting interpolated or malformed strings).
 * - [CrystalRequirePathResolver] applies the compiler's resolution semantics
 *   (relative requires against the containing file's directory; non-relative
 *   requires against the project `lib` directories and the standard library).
 */
object CrystalRequireResolver {

    /**
     * Returns the `PsiFile`s targeted by the given require statement.
     * Empty when the require path is dynamic (interpolation), malformed,
     * or does not match any existing file — never guesses.
     */
    fun resolveRequireTargets(requireStatement: CrystalRequireStatement): List<PsiFile> {
        val requirePath =
            CrystalRequireCollector.extractStaticPath(requireStatement) ?: return emptyList()
        val containingFile = requireStatement.containingFile ?: return emptyList()
        val requiringVirtualFile = containingFile.virtualFile ?: return emptyList()

        val resolution = CrystalRequirePathResolver(requireStatement.project)
            .resolve(requiringVirtualFile, requirePath)
        val psiManager = PsiManager.getInstance(requireStatement.project)
        return resolution.files.mapNotNull(psiManager::findFile)
    }
}
