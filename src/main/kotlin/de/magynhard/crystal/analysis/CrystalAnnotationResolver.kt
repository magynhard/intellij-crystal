package de.magynhard.crystal.analysis

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/** Annotation names have their own index; ordinary types are never substitutes. */
internal object CrystalAnnotationResolver {
    // Created by the compiler itself (compiler/crystal/program.cr), even without a prelude.
    private val BUILTINS = setOf(
        "AlwaysInline", "CallConvention", "Extern", "Flags", "Link", "Naked", "NoInline",
        "Packed", "Primitive", "Raises", "ReturnsTwice", "ThreadLocal", "Deprecated",
        "Experimental", "TargetFeature"
    )
    private val PATH = Regex("(?:::)?[\\p{Lu}][\\p{L}\\p{N}_]*(?:::[\\p{Lu}][\\p{L}\\p{N}_]*)*")

    data class Lookup(
        val target: CrystalAnnotationDefinition?,
        val known: Boolean,
        val indexed: Boolean,
    )

    /** Null means that the name cannot safely be judged, not that it is unknown. */
    fun lookup(path: CrystalTypePath): Lookup? = try {
        lookupAvailable(path)
    } catch (_: IndexNotReadyException) {
        null
    }

    private fun lookupAvailable(path: CrystalTypePath): Lookup? {
        if (path.parent !is CrystalAnnotationUsage || DumbService.isDumb(path.project)) return null
        if (path.parent.node.findChildByType(CrystalTypes.RBRACKET) == null) return null
        if (PsiTreeUtil.findChildOfType(path.parent, PsiErrorElement::class.java) != null) return null
        val name = path.text.filterNot(Char::isWhitespace)
        if (!PATH.matches(name)) return null
        val root = name.removePrefix("::")
        val builtin = root in BUILTINS
        val service = CrystalRequireGraphService.getInstance(path.project)
        if (service.isProgramLessInjection(path)) return null
        val sources = service.effectiveSources(path)
        if (sources.files.isEmpty()) return null
        val identities = if (name.startsWith("::") || root.contains("::")) listOf(root)
            else CrystalPsiUtils.buildLexicalQualifiedNameCandidates(root, path).toList()
        val candidates = CrystalIndexService.findAnnotations(
            root.substringAfterLast("::"), path.project, GlobalSearchScope.allScope(path.project)
        ).filter { CrystalPsiUtils.buildQualifiedName(it) in identities }
        for (identity in identities) {
            val visible = candidates.filter {
                CrystalPsiUtils.buildQualifiedName(it) == identity && sources.contains(it)
            }
            if (visible.isNotEmpty()) return Lookup(visible.singleOrNull(), true, true)
        }
        return Lookup(null, builtin, candidates.isNotEmpty())
    }
}
