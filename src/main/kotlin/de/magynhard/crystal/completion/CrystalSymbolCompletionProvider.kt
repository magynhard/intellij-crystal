package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalEffectiveSourceSet
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalSymbolCompletionProvider {

    /**
     * Offers every indexed class/module/struct/enum name whose declaration is
     * visible through [context]'s require closure. The result's own prefix
     * matcher pre-filters names before any stub is loaded; declarations are
     * then kept only when a defining file belongs to the effective sources.
     * Injected fragments without their own require closure (ECR) keep the
     * legacy unfiltered behavior.
     */
    internal fun addAllClasses(project: Project, result: CompletionResultSet, context: PsiElement) {
        val service = CrystalRequireGraphService.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        val sources = if (service.isProgramLessInjection(context)) {
            null
        } else {
            service.effectiveSources(context).takeIf { it.files.isNotEmpty() }
        }
        val matcher = result.prefixMatcher
        for (className in CrystalCompletionHelper.getAllClassNames(project)) {
            if (!matcher.prefixMatches(className)) continue
            if (sources != null &&
                !CrystalRequireVisibility.isTypeNameVisible(className, project, scope, sources)
            ) {
                continue
            }
            result.addElement(CrystalCompletionHelper.buildClassLookup(className))
        }
    }

    internal fun addFileLevelConstants(file: PsiFile, result: CompletionResultSet): Set<String> {
        val seen = mutableSetOf<String>()
        val constants = PsiTreeUtil.findChildrenOfType(file, CrystalConstantAssignment::class.java)
        for (constant in constants) {
            val name = constant.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: continue
            if (!seen.add(name)) continue
            result.addElement(buildConstantLookup(name, "constant"))
        }
        return seen
    }

    /**
     * Offers every indexed constant visible through [context]'s require
     * closure: top-level constants by simple name, member constants through
     * their owner. Same-file declarations are already offered by
     * [addFileLevelConstants] and skipped here for deduplication; private
     * constants never leave their own file. The result's own prefix matcher
     * pre-filters names before any stub is loaded. Injected fragments without
     * their own require closure (ECR) keep the legacy unfiltered behavior.
     */
    internal fun addVisibleConstants(
        project: Project,
        file: PsiFile,
        result: CompletionResultSet,
        context: PsiElement,
    ) {
        val seen = addFileLevelConstants(file, result).toMutableSet()

        if (DumbService.isDumb(project)) return
        val service = CrystalRequireGraphService.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        val sources: CrystalEffectiveSourceSet? = if (service.isProgramLessInjection(context)) {
            null
        } else {
            service.effectiveSources(context).takeIf { it.files.isNotEmpty() }
        }
        val contextFile = file.originalFile.virtualFile
        val matcher = result.prefixMatcher
        for (constantName in CrystalCompletionHelper.getAllConstantNames(project)) {
            if (constantName in seen) continue
            if (!matcher.prefixMatches(constantName)) continue
            try {
                val candidates = CrystalCompletionHelper.findConstantsByName(constantName, project)
                val visible = candidates.filter { candidate ->
                    candidate.containingFile?.originalFile?.virtualFile != contextFile &&
                        (sources == null || CrystalRequireVisibility.isConstantCandidateVisible(
                            candidate,
                            sources,
                            contextFile,
                        ))
                }
                val candidate = visible.firstOrNull() ?: continue
                seen.add(constantName)
                val owner = candidate.stub?.ownerQualifiedName
                    ?: CrystalPsiUtils.constantOwnerQualifiedName(candidate)
                val typeText = if (owner != null) "constant in $owner" else "constant"
                result.addElement(buildConstantLookup(constantName, typeText))
            } catch (_: Throwable) {
                // Skip a single broken element rather than aborting completion.
            }
        }
    }

    internal fun addClassConstants(className: String, project: Project, context: PsiElement, result: CompletionResultSet) {
        val typeResult = CrystalCompletionHelper.findTypeByName(className, project) ?: return
        if (!CrystalRequireVisibility.isVisible(typeResult.element, context)) return
        val classBody = when (val element = typeResult.element) {
            is CrystalClassDefinition -> element.classBody
            is CrystalStructDefinition -> element.classBody
            is CrystalModuleDefinition -> element.classBody
            else -> null
        } ?: return
        val contextFile = context.containingFile?.originalFile?.virtualFile
        val constants = PsiTreeUtil.findChildrenOfType(classBody, CrystalConstantAssignment::class.java)
        for (constant in constants) {
            val constantToken = constant.node.findChildByType(CrystalTypes.CONSTANT)
            if (constantToken != null) {
                // Private members never leave their own file, exactly like
                // the compiler: `private BAR` in another file is not a member.
                val isPrivate = constant.stub?.isPrivate ?: CrystalPsiUtils.isPrivateConstant(constant)
                if (isPrivate && constant.containingFile?.originalFile?.virtualFile != contextFile) continue
                result.addElement(buildConstantLookup(constantToken.text, "constant in $className"))
            }
        }
    }

    private fun buildConstantLookup(name: String, typeText: String) =
        LookupElementBuilder.create(name)
            .withIcon(AllIcons.Nodes.Field)
            .withTypeText(typeText, true)
}
