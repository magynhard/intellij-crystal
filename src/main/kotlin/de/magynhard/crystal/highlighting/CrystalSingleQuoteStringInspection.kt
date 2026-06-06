package de.magynhard.crystal.highlighting

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Inspection that reports invalid single-quote strings in Crystal code.
 * In Crystal, single quotes can only contain exactly one character (char literal).
 * Strings must use double quotes.
 * 
 * This is implemented as an inspection rather than an annotator because
 * BAD_CHARACTER tokens are not passed to annotators by IntelliJ's framework.
 */
class CrystalSingleQuoteStringInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                // Check for BAD_CHARACTER tokens that are invalid single-quote strings
                if (element.node.elementType == CrystalTokenTypes.BAD_CHARACTER) {
                    val text = element.text
                    if (text.startsWith("'") && text.endsWith("'")) {
                        holder.registerProblem(
                            element,
                            "Unterminated char literal — single quotes can only contain one character. Use double quotes for strings."
                        )
                    }
                }
                super.visitElement(element)
            }
        }
    }
}
