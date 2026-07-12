package de.magynhard.crystal.ecr

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrBody

/**
 * Injects [CrystalLanguage] into the body of ECR `<% %>` tags.
 *
 * Each `ecrBody` PSI element (the Crystal code between `<%` and `%>`) is a
 * [PsiLanguageInjectionHost][de.magynhard.crystal.ecr.psi.impl.EcrBodyInjectionHost].
 * This injector registers the full text range of the body as an injection site
 * for `CrystalLanguage`, causing IntelliJ to parse the body content as Crystal
 * and provide full code intelligence: syntax highlighting, code completion,
 * go-to-definition, parameter info, hover, inspections, etc.
 *
 * Registered in `plugin.xml` via `<multiHostInjector>`.
 */
class CrystalEcrInjector : MultiHostInjector {

    override fun getLanguagesToInject(
        registrar: MultiHostRegistrar,
        context: PsiElement
    ) {
        if (context !is CrystalEcrEcrBody) return
        val host = context as? com.intellij.psi.PsiLanguageInjectionHost ?: return
        val text = context.text
        if (text.isBlank()) return

        val textRange = TextRange(0, text.length)
        registrar
            .startInjecting(CrystalLanguage)
            .addPlace(null, null, host, textRange)
            .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(CrystalEcrEcrBody::class.java)
}
