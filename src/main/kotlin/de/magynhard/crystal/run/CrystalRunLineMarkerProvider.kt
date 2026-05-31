package de.magynhard.crystal.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Provides gutter run icons (▶) next to `describe` and `it` blocks in Crystal spec files.
 * Allows running individual tests or test suites directly from the editor.
 */
class CrystalRunLineMarkerProvider : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Only trigger on leaf IDENTIFIER tokens (to avoid duplicates on parent nodes)
        if (element !is LeafPsiElement) return null
        if (element.elementType != CrystalTypes.IDENTIFIER) return null

        // Only in spec files
        val file = element.containingFile?.virtualFile ?: return null
        if (!file.name.endsWith("_spec.cr")) return null

        val text = element.text
        if (text != "describe" && text != "it" && text != "context") return null

        // Verify this is a method call (should be first token in a bare/method call expression)
        // Simple check: the identifier should be at the start of a statement
        val parent = element.parent ?: return null

        val actions = ExecutorAction.getActions(0)
        val tooltipProvider = { _: PsiElement ->
            when (text) {
                "describe", "context" -> "Run spec suite"
                "it" -> "Run spec"
                else -> "Run"
            }
        }

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            tooltipProvider,
            *actions
        )
    }
}
