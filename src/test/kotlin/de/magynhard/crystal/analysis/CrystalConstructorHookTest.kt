package de.magynhard.crystal.analysis

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.psi.CrystalMacroDefinition
import java.io.File

class CrystalConstructorHookTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testRealJsonConstructorHookCanBeParsed() {
        val source = File("/usr/lib/crystal/json/serialization.cr")
        if (!source.isFile) return
        val file = myFixture.configureByText("serialization.cr", source.readText())
        val hook = PsiTreeUtil.findChildrenOfType(file, CrystalMacroDefinition::class.java).first { it.name == "included" }
        val template = PsiFileFactory.getInstance(project).createFileFromText(
            "template.cr", CrystalFileType, "class Template\n${hook.macroBody!!.text}\nend",
        )
        val errors = PsiTreeUtil.findChildrenOfType(template, PsiErrorElement::class.java)
        assertTrue(errors.joinToString("\n") { "${it.textOffset}: ${it.errorDescription} (${it.text})" }, errors.isEmpty())
        val methods = CrystalStaticDeclarations.constructorHookMethods(hook)
        assertNotNull(methods)
        assertTrue(methods!!.any { it.name == "new" })
    }

    fun testRealJsonHookAndKeywordInitializerHaveExactConstructorResult() {
        val root = File("/usr/lib/crystal")
        val paths = listOf("object.cr", "value.cr", "struct.cr", "reference.cr", "string.cr", "json/serialization.cr")
        if (paths.any { !File(root, it).isFile }) return
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.copyFileToProject("analysis/constructor_initializer.cr", "initializer.cr")
        for (path in paths) myFixture.addFileToProject("sdk/$path", File(root, path).readText())
        val requires = paths.joinToString("\n") { "require \"./sdk/${it.removeSuffix(".cr")}\"" }
        myFixture.configureByText("completion.cr", """
            $requires
            require "./initializer"
            struct CompletionItem
              include LSP::Initializer
              include JSON::Serializable
              property label : String
            end
            item = CompletionItem.new(label: "item")
            it<caret>em
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("CompletionItem", CrystalTypeSetResolver.session(leaf).resolveVariable("item", leaf).render())
    }

    fun testPrivateInitializerProjectionKeepsFollowingConstructorSourceOffsets() {
        val file = myFixture.configureByText("hook.cr", """
            module FactoryTrait
              macro included
                private def initialize(args : NamedTuple)
                  {% for ivar in @type.instance_vars %}
                    @{{ivar.id}} = args["{{ivar.id}}"]? {% if ivar.has_default_value? %}|| {{ivar.default_value}}{% end %}
                  {% end %}
                end
                def self.new(**args)
                  instance = allocate
                  instance.initialize(args)
                  instance
                end
              end
            end
        """.trimIndent())
        val hook = PsiTreeUtil.findChildrenOfType(file, CrystalMacroDefinition::class.java).single()
        val methods = CrystalStaticDeclarations.constructorHookMethods(hook)
        assertNotNull(methods)
        val constructor = methods!!.single { it.name == "new" }
        val target = CrystalStaticDeclarations.navigation(constructor)
        assertNotNull(target)
        assertEquals(file, target!!.containingFile)
        assertTrue(file.text.substring(target.textOffset).startsWith("new"))
    }
}
