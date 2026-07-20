package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalConstantAssignment
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalSymbolCompletionProvider {

    internal fun addAllClasses(project: Project, result: CompletionResultSet) {
        for (className in CrystalCompletionHelper.getAllClassNames(project)) {
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

    internal fun addClassConstants(className: String, project: Project, result: CompletionResultSet) {
        val typeResult = CrystalCompletionHelper.findTypeByName(className, project) ?: return
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
