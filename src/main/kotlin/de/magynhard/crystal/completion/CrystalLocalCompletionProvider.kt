package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService
import javax.swing.Icon

internal object CrystalLocalCompletionProvider {

    internal fun addLocalCompletions(
        position: PsiElement,
        originalFile: PsiFile,
        result: CompletionResultSet
    ) {
        val seen = mutableSetOf<String>()

        var currentBlock = PsiTreeUtil.getParentOfType(position, CrystalBlock::class.java)
        while (currentBlock != null) {
            val paramList = currentBlock.parameterList
            if (paramList != null) {
                for (param in paramList.parameterList) {
                    val name = CrystalCompletionHelper.extractParameterName(param) ?: continue
                    if (seen.add(name)) {
                        result.addElement(prioritizedLookup(name, AllIcons.Nodes.Parameter, "parameter", 120.0))
                    }
                }
            }
            currentBlock = PsiTreeUtil.getParentOfType(currentBlock.parent, CrystalBlock::class.java)
        }

        val forStmt = PsiTreeUtil.getParentOfType(position, CrystalForStatement::class.java)
        if (forStmt != null) {
            for (child in forStmt.children) {
                if (child.node?.elementType == CrystalTypes.IDENTIFIER && seen.add(child.text)) {
                    result.addElement(prioritizedLookup(child.text, AllIcons.Nodes.Variable, "for variable", 90.0))
                }
            }
        }

        val method = PsiTreeUtil.getParentOfType(position, CrystalMethodDefinition::class.java)
        if (method != null) {
            for (param in method.parameterList?.parameterList ?: emptyList()) {
                val name = CrystalCompletionHelper.extractParameterName(param) ?: continue
                if (seen.add(name)) {
                    result.addElement(prioritizedLookup(name, AllIcons.Nodes.Parameter, "parameter", 100.0))
                }
            }
        }

        val scope = findCompletionScope(position)
        if (scope != null) {
            val assignments = PsiTreeUtil.findChildrenOfType(scope, CrystalAssignment::class.java)
            for (assignment in assignments) {
                if (assignment.textOffset >= position.textOffset) continue
                val child = assignment.firstChild ?: continue
                if (child.node?.elementType == CrystalTypes.IDENTIFIER) {
                    val text = child.text ?: continue
                    if (seen.add(text)) {
                        result.addElement(prioritizedLookup(text, AllIcons.Nodes.Variable, "local", 50.0))
                    }
                }
            }
        }

        collectClassVariables(originalFile) { name, typeText ->
            if (seen.add(name)) {
                result.addElement(prioritizedLookup(name, AllIcons.Nodes.Variable, typeText, 40.0))
            }
        }

        if (method != null) {
            val enclosingClassName = CrystalCompletionHelper.getEnclosingClassName(method)
            if (enclosingClassName != null) {
                val project = position.project
                val searchScope = GlobalSearchScope.allScope(project)
                addClassMethods(enclosingClassName, 30.0, searchScope, project, seen, result)

                val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
                val superClassName = enclosingClass?.superclassClause?.typeReference?.text
                if (superClassName != null && superClassName != enclosingClassName) {
                    addClassMethods(superClassName, 20.0, searchScope, project, seen, result)
                }
            }
        }

        addTopLevelMethods(position, position.project, seen, result)
    }

    private fun addTopLevelMethods(
        position: PsiElement,
        project: Project,
        seen: MutableSet<String>,
        result: CompletionResultSet
    ) {
        val file = position.containingFile
        if (file != null) {
            for (method in PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)) {
                if (CrystalCompletionHelper.getEnclosingClassName(method) != null) continue
                val name = method.name ?: continue
                if (!seen.add(name)) continue
                result.addElement(CrystalCompletionHelper.buildMethodLookup(method, 0.0))
            }
        }

        if (DumbService.isDumb(project)) return
        val matcher = result.prefixMatcher
        for (methodName in CrystalCompletionHelper.getAllTopLevelMethodNames(project)) {
            if (methodName in seen) continue
            if (!matcher.prefixMatches(methodName)) continue
            try {
                val methods = CrystalCompletionHelper.getTopLevelMethodsByName(methodName, project)
                val method = methods.firstOrNull() ?: continue
                seen.add(methodName)
                result.addElement(CrystalCompletionHelper.buildMethodLookup(method, 0.0))
            } catch (_: Throwable) {
                // Skip a single broken element rather than aborting completion.
            }
        }
    }

    private fun addClassMethods(
        className: String,
        priority: Double,
        scope: GlobalSearchScope,
        project: Project,
        seen: MutableSet<String>,
        result: CompletionResultSet
    ) {
        val methods = CrystalIndexService.findMethodsByClass(className, project, scope)
        for (method in methods) {
            val name = method.name ?: continue
            if (seen.add(name)) {
                result.addElement(CrystalCompletionHelper.buildMethodLookup(method, priority))
            }
        }
    }

    private fun findCompletionScope(element: PsiElement): PsiElement? {
        val method = PsiTreeUtil.getParentOfType(element, CrystalMethodDefinition::class.java)
        if (method != null) return method
        val block = PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java)
        if (block != null) return block
        return element.containingFile
    }

    private fun collectClassVariables(
        file: PsiElement,
        add: (name: String, typeText: String) -> Unit
    ) {
        var enteredEnclosing = false
        fun visit(element: PsiElement) {
            if (enteredEnclosing &&
                (element is CrystalClassDefinition || element is CrystalModuleDefinition ||
                    element is CrystalStructDefinition || element is CrystalEnumDefinition)
            ) {
                return
            }
            when (element) {
                is CrystalInstanceVarAccess -> {
                    val name = element.name ?: return
                    add(name, "instance variable")
                }
                is CrystalClassVarAccess -> {
                    val name = element.name ?: return
                    add(name, "class variable")
                }
            }
            val tokenType = element.node?.elementType
            if (tokenType == CrystalTypes.INSTANCE_VAR) {
                add(element.text, "instance variable")
            } else if (tokenType == CrystalTypes.CLASS_VAR) {
                add(element.text, "class variable")
            }
            if (!enteredEnclosing &&
                (element is CrystalClassDefinition || element is CrystalModuleDefinition ||
                    element is CrystalStructDefinition || element is CrystalEnumDefinition)
            ) {
                enteredEnclosing = true
            }
            for (child in element.children) visit(child)
        }
        for (child in file.children) visit(child)
    }

    private fun prioritizedLookup(
        name: String,
        icon: Icon,
        typeText: String,
        priority: Double
    ): LookupElement {
        val lookup = LookupElementBuilder.create(name)
            .withIcon(icon)
            .withTypeText(typeText, true)
            .withBoldness(true)
        return PrioritizedLookupElement.withPriority(lookup, priority)
    }
}
