package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalCallExtractor
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.CrystalDotCallAccess
import java.io.File

class CrystalStdlibEnumPredicateTest : BasePlatformTestCase() {
    fun testRealDelimiterKindCompletionHoverAndResolution() {
        val root = File("/usr/lib/crystal")
        val paths = listOf("object.cr", "object/properties.cr", "value.cr", "struct.cr",
            "reference.cr", "bool.cr", "enum.cr", "comparable.cr",
            "compiler/crystal/syntax/token.cr", "compiler/crystal/syntax/location.cr")
        if (paths.any { !File(root, it).isFile }) return
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        for (path in paths) myFixture.addFileToProject("sdk/$path", File(root, path).readText())
        val requires = paths.joinToString("\n") { "require \"./sdk/${it.removeSuffix(".cr")}\"" }
        for (expression in listOf(
            "if token.delimiter_state.kind.<caret>\ntrue\nend",
            "consume_literal_body(token.delimiter_state) unless token.delimiter_state.kind.<caret>",
        )) {
            myFixture.configureByText("signature_help.cr", "$requires\ndef consume(token : Crystal::Token)\n$expression\nend")
            val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
            assertTrue("Expected real DelimiterKind predicates among $names", names.containsAll(listOf("heredoc?", "string_array?", "array?")))
        }
        myFixture.configureByText("signature_help.cr", "$requires\ndef consume(token : Crystal::Token)\ntoken.delimiter_state.kind.he<caret>redoc?\nend")
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "heredoc?" }
        assertTrue(CrystalDotCallTargetResolver.resolve(access) is DotCallResolution.Accessor)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc, doc.contains("heredoc?") && doc.contains("Bool"))
    }
}
