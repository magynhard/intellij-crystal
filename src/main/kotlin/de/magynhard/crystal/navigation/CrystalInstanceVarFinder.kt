package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*

/**
 * Finds definitions and usages of instance variables (@name) and class variables (@@name)
 * within the enclosing class/struct.
 *
 * Priority for Go to Definition:
 * 1. Property declarations (@name : Type)
 * 2. getter/setter/property macro calls (getter name, property name : Type)
 * 3. All assignments (@name = ...) as fallback
 */
object CrystalInstanceVarFinder {

    /**
     * Find the definition target(s) for an instance/class variable.
     * Returns property declarations first, then getter/setter/property calls, then assignments.
     */
    fun findDefinitionTargets(varName: String, context: PsiElement): List<PsiElement> {
        val enclosingClass = findEnclosingClass(context) ?: return emptyList()
        val classBody = getClassBody(enclosingClass) ?: return emptyList()

        // Strip @ or @@ prefix for matching against getter/setter/property names
        val bareName = varName.removePrefix("@@").removePrefix("@")

        val propertyDecls = mutableListOf<PsiElement>()
        val macroDecls = mutableListOf<PsiElement>()
        val assignments = mutableListOf<PsiElement>()

        collectTargets(classBody, varName, bareName, propertyDecls, macroDecls, assignments)

        // Priority: property declarations > macro declarations > assignments
        if (propertyDecls.isNotEmpty()) return propertyDecls
        if (macroDecls.isNotEmpty()) return macroDecls
        return assignments
    }

    /**
     * Find all usages (reads and writes) of an instance/class variable within the enclosing class.
     */
    fun findAllUsages(varName: String, context: PsiElement): List<PsiElement> {
        val enclosingClass = findEnclosingClass(context) ?: return emptyList()
        val classBody = getClassBody(enclosingClass) ?: return emptyList()

        val usages = mutableListOf<PsiElement>()
        collectAllOccurrences(classBody, varName, usages)
        return usages
    }

    private fun collectTargets(
        element: PsiElement,
        varName: String,
        bareName: String,
        propertyDecls: MutableList<PsiElement>,
        macroDecls: MutableList<PsiElement>,
        assignments: MutableList<PsiElement>
    ) {
        var astChild = element.node.firstChildNode
        while (astChild != null) {
            val child = astChild.psi

            // Property declaration: @name : Type or name : Type
            if (child is CrystalPropertyDeclaration) {
                val declName = child.node.findChildByType(CrystalTypes.INSTANCE_VAR)?.text
                    ?: child.node.findChildByType(CrystalTypes.CLASS_VAR)?.text
                    ?: child.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                if (declName == bareName || declName == varName) {
                    propertyDecls.add(child)
                }
            }

            // Method calls (with or without parens): getter name, setter name, property name.
            // Bare commands (getter name : String) also parse as CrystalBareCommandExpression.
            // property_macro (getter name : Type) is its own PSI element since the grammar fix.
            if (child is CrystalMethodCallExpression || child is CrystalBareMethodCallExpression ||
                child is CrystalBareCommandExpression || child is CrystalPropertyMacro) {
                val callName = child.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                if (callName in PROPERTY_MACROS) {
                    val argText = getFirstArgumentTextGeneric(child)
                    if (argText == bareName) {
                        macroDecls.add(child)
                    }
                }
            }

            // Check for assignment: @name = ...
            val tokenType = astChild.elementType
            if (tokenType == CrystalTypes.INSTANCE_VAR || tokenType == CrystalTypes.CLASS_VAR) {
                if (astChild.text == varName) {
                    val nextMeaningful = skipWhitespaceAst(astChild.treeNext)
                    if (nextMeaningful != null && nextMeaningful.elementType == CrystalTypes.ASSIGN) {
                        assignments.add(child)
                    }
                }
            }

            // Always recurse (but don't cross into nested class/struct/module definitions)
            if (child !is CrystalClassDefinition && child !is CrystalStructDefinition &&
                child !is CrystalModuleDefinition && astChild.firstChildNode != null) {
                collectTargets(child, varName, bareName, propertyDecls, macroDecls, assignments)
            }

            astChild = astChild.treeNext
        }
    }

    private fun skipWhitespaceAst(node: com.intellij.lang.ASTNode?): com.intellij.lang.ASTNode? {
        var current = node
        while (current != null && current.elementType.toString() == "WHITE_SPACE") {
            current = current.treeNext
        }
        return current
    }

    private fun collectAllOccurrences(element: PsiElement, varName: String, usages: MutableList<PsiElement>) {
        // Check for CrystalInstanceVarAccess / CrystalClassVarAccess composite elements
        if ((element is CrystalInstanceVarAccess || element is CrystalClassVarAccess) &&
            element.text == varName) {
            usages.add(element)
            return // No need to recurse into leaf
        }
        // Also check leaf tokens (for @name in property_declaration or parameter where it's still a raw token)
        val nodeType = element.node.elementType
        if ((nodeType == CrystalTypes.INSTANCE_VAR || nodeType == CrystalTypes.CLASS_VAR) &&
            element.text == varName &&
            element.parent !is CrystalInstanceVarAccess && element.parent !is CrystalClassVarAccess) {
            usages.add(element)
            return
        }
        // Recurse via AST children
        var child = element.node.firstChildNode
        while (child != null) {
            collectAllOccurrences(child.psi, varName, usages)
            child = child.treeNext
        }
    }

    private fun findEnclosingClass(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            element,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java
        )
    }

    private fun getClassBody(classDef: PsiElement): CrystalClassBody? {
        return when (classDef) {
            is CrystalClassDefinition -> classDef.classBody
            is CrystalStructDefinition -> classDef.classBody
            is CrystalModuleDefinition -> classDef.classBody
            else -> null
        }
    }

    private fun getFirstArgumentTextGeneric(element: PsiElement): String? {
        // Look for the first IDENTIFIER in argument position (after the method name)
        // Works for both call_args (parens) and bare_argument_list (no parens)
        var foundMethodName = false
        var astChild = element.node.firstChildNode
        while (astChild != null) {
            if (!foundMethodName) {
                if (astChild.elementType == CrystalTypes.IDENTIFIER || astChild.elementType == CrystalTypes.CONSTANT) {
                    foundMethodName = true
                }
            } else {
                // Find first IDENTIFIER in children (the argument name)
                val ident = findFirstIdentInTree(astChild)
                if (ident != null) return ident
            }
            astChild = astChild.treeNext
        }
        return null
    }

    private fun findFirstIdentInTree(node: com.intellij.lang.ASTNode): String? {
        if (node.elementType == CrystalTypes.IDENTIFIER) return node.text
        var child = node.firstChildNode
        while (child != null) {
            val result = findFirstIdentInTree(child)
            if (result != null) return result
            child = child.treeNext
        }
        return null
    }

    private val PROPERTY_MACROS = setOf(
        "getter", "getter!", "getter?",
        "setter", "setter!",
        "property", "property!", "property?"
    )
}
