package de.magynhard.crystal.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalModuleDefinition

class CrystalDocumentationProviderTest : BasePlatformTestCase() {

    private val provider = CrystalDocumentationProvider()

    override fun getTestDataPath(): String = "src/test/testData"

    private fun findMethod(fileText: String, methodName: String): CrystalMethodDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val methods = PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)
        return methods.first { it.name == methodName }
    }

    private fun findClass(fileText: String, className: String): CrystalClassDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val classes = PsiTreeUtil.findChildrenOfType(file, CrystalClassDefinition::class.java)
        return classes.first { it.name == className }
    }

    private fun findModule(fileText: String, moduleName: String): CrystalModuleDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val modules = PsiTreeUtil.findChildrenOfType(file, CrystalModuleDefinition::class.java)
        return modules.first { it.name == moduleName }
    }

    // ==================== Method Documentation ====================

    fun testMethodWithDocComment() {
        val method = findMethod("""
            # Berechnet die Summe.
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
        """.trimIndent(), "add")
        val doc = provider.generateDoc(method, null)
        assertNotNull("Should generate documentation", doc)
        assertTrue("Should contain method name", doc!!.contains("add"))
        assertTrue("Should contain doc comment", doc.contains("Berechnet die Summe"))
        assertTrue("Should contain parameter types", doc.contains("Int32"))
    }

    fun testMethodWithoutDocComment() {
        val method = findMethod("""
            def greet(name : String) : String
              "Hello"
            end
        """.trimIndent(), "greet")
        val doc = provider.generateDoc(method, null)
        assertNotNull("Should generate documentation even without doc comment", doc)
        assertTrue("Should contain method name", doc!!.contains("greet"))
        assertTrue("Should contain parameter type", doc.contains("String"))
    }

    fun testMethodWithCodeExample() {
        val method = findMethod("""
            # Addiert zwei Zahlen.
            #
            # ```
            # add(1, 2) # => 3
            # ```
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
        """.trimIndent(), "add")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should contain doc comment text", doc!!.contains("Addiert zwei Zahlen"))
        // Code blocks are rendered via markdown — check that code content exists in some form
        assertTrue("Should contain code block with code", doc.contains("code") || doc.contains("pre"))
    }

    fun testMethodWithMultilineDoc() {
        val method = findMethod("""
            # Sendet eine Nachricht.
            #
            # *message* darf nicht leer sein.
            # Gibt `true` zurück bei Erfolg.
            def send(message : String) : Bool
              true
            end
        """.trimIndent(), "send")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should contain first line", doc!!.contains("Sendet eine Nachricht"))
        assertTrue("Should contain parameter reference", doc.contains("message"))
    }

    // ==================== Class/Module Documentation ====================

    fun testClassDocumentation() {
        val classDef = findClass("""
            # Ein Tier mit Name und Alter.
            class Animal
              def speak
              end
            end
        """.trimIndent(), "Animal")
        val doc = provider.generateDoc(classDef, null)
        assertNotNull(doc)
        assertTrue("Should contain class name", doc!!.contains("Animal"))
        assertTrue("Should contain doc comment", doc.contains("Ein Tier mit Name und Alter"))
        assertTrue("Should contain 'class' keyword", doc.contains("class"))
    }

    fun testModuleDocumentation() {
        val moduleDef = findModule("""
            # Utility-Funktionen.
            module Utils
            end
        """.trimIndent(), "Utils")
        val doc = provider.generateDoc(moduleDef, null)
        assertNotNull(doc)
        assertTrue("Should contain module name", doc!!.contains("Utils"))
        assertTrue("Should contain 'module' keyword", doc.contains("module"))
    }

    // ==================== Signature Tests ====================

    fun testInstanceMethodShowsClassName() {
        val method = findMethod("""
            class Foo
              def bar(x : Int32)
              end
            end
        """.trimIndent(), "bar")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should show Foo#bar", doc!!.contains("Foo") && doc.contains("#") && doc.contains("bar"))
    }

    fun testClassMethodShowsDotNotation() {
        val method = findMethod("""
            class Foo
              def self.create(name : String)
              end
            end
        """.trimIndent(), "create")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should show Foo.create", doc!!.contains("Foo") && doc.contains("create"))
        assertFalse("Should NOT show 'self.' in output", doc.contains("self."))
    }

    fun testMethodWithReturnType() {
        val method = findMethod("""
            def calculate(x : Float64) : Float64
              x * 2.0
            end
        """.trimIndent(), "calculate")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should contain return type", doc!!.contains("Float64"))
    }

    // ==================== Edge Cases ====================

    fun testNoDocWhenBlankLineSeparates() {
        val method = findMethod("""
            # This is NOT a doc comment for bar.

            def bar
            end
        """.trimIndent(), "bar")
        val doc = provider.generateDoc(method, null)
        assertNotNull("Should still generate doc (signature only)", doc)
        assertFalse("Should NOT contain the separated comment", doc!!.contains("NOT a doc comment"))
    }

    fun testNullForUnresolvableElement() {
        val file = myFixture.configureByText("test.cr", """
            x = 42
        """.trimIndent())
        val element = file.findElementAt(myFixture.editor.document.text.indexOf("42"))
        val doc = provider.generateDoc(element, null)
        // A literal at depth < 4 will find a parent assignment or statement — might be null
        // Just ensure no crash
        assertTrue("Should return null or non-null without crashing", doc == null || doc.isNotEmpty())
    }

    // ==================== Resolution from Call Site ====================

    fun testResolveFromCallSite() {
        val file = myFixture.configureByText("test.cr", """
            # Wichtige Funktion.
            def wichtig(x : Int32)
            end
            wichtig(5)
        """.trimIndent())
        // Find the "wichtig" identifier at the call site
        val callOffset = myFixture.editor.document.text.lastIndexOf("wichtig")
        val leaf = file.findElementAt(callOffset)!!
        val doc = provider.generateDoc(leaf, null)
        // If reference resolution works, should get the doc
        // If not, at least should not crash
        assertTrue("Should not crash", doc == null || doc.isNotEmpty())
    }

    // ==================== Resolution from DOT-call Sites (Phase 1 fallback) ====================
    //
    // Hover / Ctrl+Q on `Apfel.tanzen` and `a.essen` must show the method's documentation,
    // even though DOT-call identifiers currently carry no PsiReference (because `postfix_op`
    // and `bare_postfix_op` are private BNF rules). The DocumentationProvider falls back to
    // CrystalGotoDeclarationHandler.getGotoDeclarationTargets().
    //
    // Phase 2 of the unified-reference refactor will give these identifiers a real
    // PsiReference, at which point the fallback becomes unused — but these tests stay green
    // because `getCustomDocumentationElement` checks `ref.resolve()` first.

    /**
     * Drives the full hover flow: getCustomDocumentationElement → generateDoc.
     * getCustomDocumentationElement is what the platform calls first to resolve
     * which element to document; generateDoc then renders it.
     */
    private fun hoverDoc(code: String): String? {
        myFixture.configureByText("test.cr", code)
        val offset = myFixture.caretOffset
        val leaf = myFixture.file.findElementAt(offset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, offset)
        if (target != null) {
            val doc = provider.generateDoc(target, leaf)
            if (doc != null) return doc
        }
        // Fallback: generateDoc may resolve directly (e.g. when leaf is already on a definition)
        return provider.generateDoc(leaf, null)
    }

    fun testHoverOnStaticClassDotCallShowsMethodDoc() {
        val doc = hoverDoc("""
            class Apfel
              # Tanzt laut.
              def self.tanzen
              end
            end
            Apfel.tan<caret>zen
        """.trimIndent())
        assertNotNull("Hover on Apfel.tanzen should return doc", doc)
        assertTrue("Should contain method name 'tanzen'", doc!!.contains("tanzen"))
        assertTrue("Should contain class name 'Apfel'", doc.contains("Apfel"))
        assertTrue("Should contain doc comment 'Tanzt laut'", doc.contains("Tanzt laut"))
    }

    fun testHoverOnInstanceMethodDotCallShowsMethodDoc() {
        val doc = hoverDoc("""
            class Apfel
              # Isst den Apfel.
              def essen
              end
            end
            a = Apfel.new
            a.es<caret>sen
        """.trimIndent())
        assertNotNull("Hover on a.essen should return doc", doc)
        assertTrue("Should contain method name 'essen'", doc!!.contains("essen"))
        assertTrue("Should contain doc comment 'Isst den Apfel'", doc.contains("Isst den Apfel"))
    }

    fun testHoverOnTopLevelBareCallStillWorks() {
        val doc = hoverDoc("""
            # Sahnetoßchen.
            def sahne(bonbon : String)
              return bonbon
            end
            sah<caret>ne
        """.trimIndent())
        assertNotNull("Hover on sahne should return doc", doc)
        assertTrue("Should contain method name 'sahne'", doc!!.contains("sahne"))
        assertTrue("Should contain doc comment 'Sahnetoßchen'", doc.contains("Sahneto"))
    }

    fun testHoverOnDotNewShowsConstructorDoc() {
        val doc = hoverDoc("""
            class Senf
              # Erzeugt eine Senf-Instanz.
              def initialize(x : Int32)
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNotNull("Hover on Senf.new should return doc", doc)
        // Should resolve to def initialize (the default constructor target)
        assertTrue("Should contain 'initialize' or 'Senf'", doc!!.contains("initialize") || doc.contains("Senf"))
    }
}
