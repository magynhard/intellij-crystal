package de.magynhard.crystal.ecr.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.ecr.EmbeddedCrystalLanguage
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrBody
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrTag

/**
 * Mixin for `ecrBody` PSI elements (the Crystal code inside `<% %>` tags).
 *
 * Implements [PsiLanguageInjectionHost] so that a [MultiHostInjector] can inject
 * `CrystalLanguage` into the body text. This gives ECR templates full Crystal
 * code intelligence inside `<% %>` tags: syntax highlighting, code completion,
 * go-to-definition, parameter info, hover, inspections, etc.
 *
 * The [LiteralTextEscaper.createSimple] factory is used because ECR body content
 * is raw Crystal code with no escaping required — the `<%` and `%>` delimiters are
 * separate tokens (`ECR_TAG_BEGIN` / `ECR_TAG_END`) and are NOT part of the
 * `ecrBody` text range.
 */
abstract class EcrBodyInjectionHost(
    node: ASTNode
) : ASTWrapperPsiElement(node), CrystalEcrEcrBody, PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val tagName = "ecr_update_${System.nanoTime()}.ecr"
        val content = "<% $text %>"
        val factory = PsiFileFactory.getInstance(project)
        val tempFile = factory.createFileFromText(
            tagName,
            EmbeddedCrystalLanguage,
            content
        )
        val tag = PsiTreeUtil.findChildOfType(tempFile, CrystalEcrEcrTag::class.java)
            ?: error("Failed to parse updated ECR body: $content")
        val newBody = tag.ecrBody
        return newBody as PsiLanguageInjectionHost
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
