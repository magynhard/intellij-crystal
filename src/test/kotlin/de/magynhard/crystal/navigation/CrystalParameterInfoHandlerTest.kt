package de.magynhard.crystal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalParameterInfoHandlerTest : BasePlatformTestCase() {

    private val handler = CrystalParameterInfoHandler()

    override fun getTestDataPath(): String = "src/test/testData"

    // ==================== Anchor Search Tests ====================

    fun testFindArgsParenthesized() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet(<caret>"John", 30)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder in parenthesized call", argsHolder)
    }

    fun testFindArgsBareCall() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet <caret>"John", 30
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder in bare call", argsHolder)
    }

    fun testFindArgsAfterCommaNoSpace() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet("John",<caret>)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder after comma without space", argsHolder)
    }

    fun testFindArgsAfterCommaWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet("John", <caret>)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder after comma with space", argsHolder)
    }

    fun testFindArgsAtOpeningParen() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet(<caret>)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder at opening paren", argsHolder)
    }

    // ==================== Method Name Resolution Tests ====================

    fun testMethodNameParenthesized() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet(<caret>"John", 30)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }

    fun testMethodNameBareCall() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet <caret>"John", 30
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }

    // ==================== Parameter Index Tests ====================

    fun testParameterIndexFirstArg() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet(<caret>"John", 30)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testParameterIndexSecondArg() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet("John", <caret>30)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexAfterCommaEmpty() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet("John",<caret>)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexAfterCommaSpaceEmpty() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet("John", <caret>)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexThirdArg() {
        val file = myFixture.configureByText("test.cr", """
            def foo(a, b, c)
            end
            foo(1, 2, <caret>3)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(2, index)
    }

    fun testParameterIndexNestedCall() {
        val file = myFixture.configureByText("test.cr", """
            def foo(a, b)
            end
            def bar(x, y)
            end
            foo(bar(1, 2), <caret>3)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    // ==================== Bare Call Parameter Index Tests ====================

    fun testBareCallParameterIndexFirst() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet <caret>"John", 30
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallParameterIndexSecond() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet "John", <caret>30
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = handler.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    // ==================== Dot-Call Tests ====================

    fun testDotCallFindArgs() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def bar(x, y)
              end
            end
            f = Foo.new
            f.bar(<caret>1, 2)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args in dot-call", argsHolder)
    }

    fun testDotCallMethodName() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def bar(x, y)
              end
            end
            f = Foo.new
            f.bar(<caret>1, 2)
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
    }

    fun testClassMethodCallName() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def self.create(name)
              end
            end
            Foo.create(<caret>"test")
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("create", name)
    }

    // ==================== Bare-Call Backtracking Tests (NEW) ====================

    fun testBareCallNoArgsYet() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for bare call with no args yet", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallTrailingCommaNoSpace() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet "John",<caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor after trailing comma", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallTrailingCommaWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name, age)
            end
            greet "John", <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor after trailing comma with space", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallMultipleArgs() {
        val file = myFixture.configureByText("test.cr", """
            def foo(a, b, c)
            end
            foo 1, 2, <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for bare call with multiple args", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("foo", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(2, index)
    }

    fun testBareCallDotNoArgs() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def bar(x, y)
              end
            end
            f = Foo.new
            f.bar <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for dot-call bare with no args", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallDotTrailingComma() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def bar(x, y)
              end
            end
            f = Foo.new
            f.bar 1, <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for dot-call bare with trailing comma", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallClassMethodNoArgs() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def self.create(name)
              end
            end
            Foo.create <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for class method bare call", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("create", name)
        val index = handler.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    // ==================== Negative Tests ====================

    fun testNoCallAfterOperator() {
        val file = myFixture.configureByText("test.cr", """
            x = 5 + <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should be null or at least not resolve to a method
        if (argsHolder != null) {
            val name = handler.findMethodNameForArgs(argsHolder)
            // If it finds something, it shouldn't be "x" or "5"
            assertNull("Should not find method name after operator", name)
        }
    }

    fun testNoCallAtEmptyLine() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name)
            end
            <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should be null — empty line is not a call
        if (argsHolder != null) {
            val name = handler.findMethodNameForArgs(argsHolder)
            assertNull("Should not find method name at empty line", name)
        }
    }

    fun testNoCallInsideBlockBody() {
        val file = myFixture.configureByText("test.cr", """
            [1].each do <caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should not trigger parameter info inside block body
        if (argsHolder != null) {
            val name = handler.findMethodNameForArgs(argsHolder)
            // "do" is a block opener — should not find "each" as method
            assertNull("Should not find method name inside block body", name)
        }
    }

    fun testEnvFetchDoesNotShowUnrelatedFetchParams() {
        myFixture.configureByText("fetch_source.cr", """
            class HttpClient
              def fetch(url : String, timeout : Int32 = 30, &block : String ->)
              end
            end
        """.trimIndent())
        val file = myFixture.configureByText("test.cr", """
            ENV.fetch(<caret>"HOME")
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for ENV.fetch", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("fetch", name)
        val receiverName = handler.findReceiverNameFromSiblings(argsHolder)
        assertEquals("ENV", receiverName)
    }

    fun testClassNewParameterInfo() {
        myFixture.configureByText("test.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end
            Apfelsaft.new(<caret>"hi", 1)
        """.trimIndent())
        val file = myFixture.file
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Class.new(...)", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("new", name)

        // Check that getInitializeMethod resolves the class
        val project = myFixture.project
        val initMethod = de.magynhard.crystal.completion.CrystalCompletionHelper.getInitializeMethod(
            "Apfelsaft", project, argsHolder.containingFile
        )
        assertNotNull("Should find initialize method for Apfelsaft", initMethod)
        val params = initMethod!!.parameterList?.parameterList ?: emptyList()
        assertEquals("initialize should have 2 params", 2, params.size)
    }

    // ==================== DOT-call after method call (regression tests) ====================

    fun testFindArgsDotCallAfterMethodCallWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            def puts(x)
            end
            class Tesa
              def self.hika(name : String)
              end
            end
            puts Tesa.hika <caret>"test"
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika after puts", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsDotCallAfterMethodCallNoSpace() {
        val file = myFixture.configureByText("test.cr", """
            def puts(x)
            end
            class Tesa
              def self.hika(name : String)
              end
            end
            puts Tesa.hika<caret>
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika after puts (no space)", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsNamespaceDotCallAfterMethodCallWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            def puts(x)
            end
            class RvmCli
              class Tools
                def self.hello(name : String)
                end
              end
            end
            puts RvmCli::Tools.hello <caret>"test"
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for RvmCli::Tools.hello after puts", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("hello", name)
    }

    fun testFindArgsDotCallAloneWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            class Tesa
              def self.hika(name : String)
              end
            end
            Tesa.hika <caret>"test"
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika (no puts, with space)", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsBareCallAfterMethodCallWithSpace() {
        val file = myFixture.configureByText("test.cr", """
            def puts(x)
            end
            def greet(name : String)
            end
            puts greet <caret>"test"
        """.trimIndent())
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for greet after puts", argsHolder)
        val name = handler.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }
}
