package de.magynhard.crystal.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import de.magynhard.crystal.psi.CrystalNamedElement
import com.intellij.util.Consumer

/**
 * Highlights all usages of a Crystal definition (class, module, struct, enum,
 * method, macro) in the current file when the cursor is on the definition name.
 *
 * Uses ReferencesSearch with LocalSearchScope to find all PsiReferences pointing
 * to the target element within the current file. Also highlights the definition's
 * own name identifier.
 */
class CrystalHighlightUsagesHandler(
    editor: Editor,
    file: PsiFile,
    private val target: CrystalNamedElement
) : HighlightUsagesHandlerBase<CrystalNamedElement>(editor, file) {

    override fun getTargets(): MutableList<CrystalNamedElement> = mutableListOf(target)

    override fun selectTargets(
        targets: List<CrystalNamedElement>,
        selectionConsumer: Consumer<in List<CrystalNamedElement>>
    ) {
        selectionConsumer.accept(targets)
    }

    override fun computeUsages(targets: MutableList<out CrystalNamedElement>) {
        val t = targets.firstOrNull() ?: return

        // Highlight the definition's own name
        val nameId = t.nameIdentifier
        if (nameId != null) {
            addOccurrence(nameId)
        }

        // Find all references to this element in the current file
        val scope = LocalSearchScope(myFile)
        val refs = ReferencesSearch.search(t, scope, false).findAll()
        for (ref in refs) {
            addOccurrence(ref.element)
        }
    }
}
