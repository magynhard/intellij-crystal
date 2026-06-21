package de.magynhard.crystal.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFileFactory
import de.magynhard.crystal.CrystalLanguage

/**
 * Creates a leaf AST node from text by parsing it as a Crystal file and finding
 * the leaf with the expected token type.
 *
 * PsiFileFactory.createFileFromText() wraps the text in statement/expression_statement
 * composites, so we can't just use firstChildNode — we must walk the tree to find
 * the actual leaf token.
 *
 * @param project the project context
 * @param text the text for the new token (e.g. "@pump", "x", "MyClass")
 * @param tokenType the expected leaf token type (e.g. CrystalTypes.INSTANCE_VAR)
 * @return the leaf ASTNode, or null if not found
 */
fun createLeafFromText(project: com.intellij.openapi.project.Project, text: String, tokenType: com.intellij.psi.tree.IElementType): ASTNode? {
    val psiFile = PsiFileFactory.getInstance(project)
        .createFileFromText("dummy.cr", CrystalLanguage, text)
    return findNodeOfType(psiFile.node, tokenType)
}

private fun findNodeOfType(node: ASTNode, type: com.intellij.psi.tree.IElementType): ASTNode? {
    if (node.elementType == type) return node
    var child = node.firstChildNode
    while (child != null) {
        val found = findNodeOfType(child, type)
        if (found != null) return found
        child = child.treeNext
    }
    return null
}
