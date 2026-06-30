package de.magynhard.crystal.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalEnumDefinition

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

    private fun findStruct(fileText: String, structName: String): CrystalStructDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val structs = PsiTreeUtil.findChildrenOfType(file, CrystalStructDefinition::class.java)
        return structs.first { it.name == structName }
    }

    private fun findEnum(fileText: String, enumName: String): CrystalEnumDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val enums = PsiTreeUtil.findChildrenOfType(file, CrystalEnumDefinition::class.java)
        return enums.first { it.name == enumName }
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

    // ==================== Signature Format Tests ====================

    fun testInstanceMethodShowsEnclosingClass() {
        val method = findMethod("""
            class Foo
              def bar(x : Int32)
              end
            end
        """.trimIndent(), "bar")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        // New format: class name on line 1, method name on line 2 (no "def " prefix, no "#")
        assertTrue("Should contain class name 'Foo'", doc!!.contains("Foo"))
        assertTrue("Should contain method name 'bar'", doc.contains("bar"))
        // The class name should be hyperlinked
        assertTrue("Class name should be hyperlinked", doc.contains("psi_element://class:Foo"))
    }

    fun testClassMethodShowsEnclosingClass() {
        val method = findMethod("""
            class Foo
              def self.create(name : String)
              end
            end
        """.trimIndent(), "create")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        assertTrue("Should contain class name 'Foo'", doc!!.contains("Foo"))
        assertTrue("Should contain method name 'create'", doc.contains("create"))
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

    fun testTopLevelMethodShowsObjectAsEnclosingClass() {
        val method = findMethod("""
            def sahne(bonbon : String)
              return bonbon
            end
        """.trimIndent(), "sahne")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        // Top-level methods show "Object" as the enclosing class
        assertTrue("Should show 'Object' as enclosing class", doc!!.contains("Object"))
        assertTrue("Should contain method name 'sahne'", doc.contains("sahne"))
    }

    fun testNoDefPrefixInMethodSignature() {
        val method = findMethod("""
            class Foo
              def bar(x : Int32)
              end
            end
        """.trimIndent(), "bar")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        // The signature line should NOT start with "def "
        assertFalse("Should NOT contain 'def ' prefix in signature", doc!!.contains("def "))
    }

    fun testParameterTypeIsHyperlinked() {
        val method = findMethod("""
            class Foo
              def greet(name : Foo)
              end
            end
        """.trimIndent(), "greet")
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        // Foo (a project-defined class) should be hyperlinked
        assertTrue("Should contain 'Foo' in parameter", doc!!.contains("Foo"))
        assertTrue("Foo should be hyperlinked", doc.contains("psi_element://class:Foo"))
    }

    fun testSuperclassIsHyperlinkedInClassSignature() {
        val classDef = findClass("""
            class Animal
            end
            class Dog < Animal
            end
        """.trimIndent(), "Dog")
        val doc = provider.generateDoc(classDef, null)
        assertNotNull(doc)
        assertTrue("Should contain 'class' keyword", doc!!.contains("class"))
        assertTrue("Should contain class name 'Dog'", doc!!.contains("Dog"))
        assertTrue("Should contain superclass 'Animal'", doc.contains("Animal"))
        assertTrue("Superclass should be hyperlinked", doc.contains("psi_element://class:Animal"))
    }

    fun testOwnClassNameIsNotHyperlinked() {
        val classDef = findClass("""
            class Foo
            end
        """.trimIndent(), "Foo")
        val doc = provider.generateDoc(classDef, null)
        assertNotNull(doc)
        // The class's own name should NOT be hyperlinked (no self-link)
        assertFalse("Own class name should NOT be hyperlinked", doc!!.contains("psi_element://class:Foo"))
    }

    // ==================== Struct/Enum Tests ====================

    fun testStructDefinitionShowsStructSignature() {
        val structDef = findStruct("""
            struct Point
              property x : Int32
              property y : Int32
            end
        """.trimIndent(), "Point")
        val doc = provider.generateDoc(structDef, null)
        assertNotNull(doc)
        assertTrue("Should contain 'struct' keyword", doc!!.contains("struct"))
        assertTrue("Should contain struct name 'Point'", doc.contains("Point"))
    }

    fun testEnumDefinitionShowsEnumSignature() {
        val enumDef = findEnum("""
            enum Color
              RED
              GREEN
              BLUE
            end
        """.trimIndent(), "Color")
        val doc = provider.generateDoc(enumDef, null)
        assertNotNull(doc)
        assertTrue("Should contain 'enum' keyword", doc!!.contains("enum"))
        assertTrue("Should contain enum name 'Color'", doc.contains("Color"))
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
        val callOffset = myFixture.editor.document.text.lastIndexOf("wichtig")
        val leaf = file.findElementAt(callOffset)!!
        val doc = provider.generateDoc(leaf, null)
        assertTrue("Should not crash", doc == null || doc.isNotEmpty())
    }

    // ==================== Resolution from DOT-call Sites ====================

    private fun hoverDoc(code: String): String? {
        myFixture.configureByText("test.cr", code)
        val offset = myFixture.caretOffset
        val leaf = myFixture.file.findElementAt(offset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, offset)
        if (target != null) {
            val doc = provider.generateDoc(target, leaf)
            if (doc != null) return doc
        }
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
        assertTrue("Should contain 'initialize' or 'Senf'", doc!!.contains("initialize") || doc.contains("Senf"))
    }

    // ==================== Link Infrastructure Tests ====================

    fun testGetDocumentationElementForLinkResolvesToClassViaIndex() {
        val file = myFixture.configureByText("test.cr", """
            class Tesa
              def self.hika(name : String)
              end
            end
        """.trimIndent())
        val project = myFixture.project
        val resolved = provider.getDocumentationElementForLink(
            com.intellij.psi.PsiManager.getInstance(project),
            "class:Tesa",
            file
        )
        assertNotNull("Should resolve Tesa link to class definition", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
    }

    fun testGetDocumentationElementForLinkReturnsNullForUnknownName() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
        """.trimIndent())
        val project = myFixture.project
        val resolved = provider.getDocumentationElementForLink(
            com.intellij.psi.PsiManager.getInstance(project),
            "class:DoesNotExist",
            file
        )
        assertNull("Should return null for unknown class name", resolved)
    }

    fun testGetDocumentationElementForLinkIgnoresNonClassPrefix() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
        """.trimIndent())
        val project = myFixture.project
        val resolved = provider.getDocumentationElementForLink(
            com.intellij.psi.PsiManager.getInstance(project),
            "method:foo",
            file
        )
        assertNull("Should return null for non-class prefix", resolved)
    }
}
