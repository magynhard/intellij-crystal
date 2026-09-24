package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.psi.CrystalModuleDefinition
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

    internal fun addFileLevelConstants(file: PsiFile, result: CompletionResultSet) {
        val constants = PsiTreeUtil.findChildrenOfType(file, CrystalConstantAssignment::class.java)
        for (constant in constants) {
            val constantToken = constant.node.findChildByType(CrystalTypes.CONSTANT)
            if (constantToken != null) {
                val lookup = LookupElementBuilder.create(constantToken.text)
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText("constant", true)
                result.addElement(lookup)
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
        val constants = PsiTreeUtil.findChildrenOfType(classBody, CrystalConstantAssignment::class.java)
        for (constant in constants) {
            val constantToken = constant.node.findChildByType(CrystalTypes.CONSTANT)
            if (constantToken != null) {
                val lookup = LookupElementBuilder.create(constantToken.text)
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText("constant in $className", true)
                result.addElement(lookup)
            }
        }
    }
}
