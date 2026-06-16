package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalArgumentCountInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    // ==================== Missing Required Arguments ====================

    fun testMissingOneRequiredArg() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'age'">greet</error>("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMissingAllRequiredArgs() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'name', 'age'">greet</error>()
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNoArgsMissingWithDefaults() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32 = 30)
            end
            greet("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAllDefaultsNoArgsRequired() {
        myFixture.configureByText("test.cr", """
            def config(host : String = "localhost", port : Int32 = 8080)
            end
            config()
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Too Many Arguments ====================

    fun testTooManyArgs() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet("Hans", <error descr="Too many arguments: expected at most 1, got 2">"extra"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTooManyArgsMultipleExcess() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet("Hans", <error descr="Too many arguments: expected at most 1, got 3">"extra1"</error>, <error descr="Too many arguments: expected at most 1, got 3">"extra2"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Splat Absorbs Extra Args ====================

    fun testSplatAcceptsAnyCount() {
        myFixture.configureByText("test.cr", """
            def variadic(*args)
            end
            variadic(1, 2, 3, 4, 5)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplatWithRequiredBefore() {
        myFixture.configureByText("test.cr", """
            def log(level : String, *messages)
            end
            log("info", "msg1", "msg2", "msg3")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Named Arguments ====================

    fun testNamedArgSatisfiesRequired() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32)
            end
            greet(age: 30, name: "Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownNamedArg() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet(<error descr="Unknown named argument 'unknown'">unknown: "value"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatAcceptsAnyNamedArg() {
        myFixture.configureByText("test.cr", """
            def flexible(**kwargs)
            end
            flexible(foo: 1, bar: 2, baz: 3)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Block Parameter Not Counted ====================

    fun testBlockParamNotCounted() {
        myFixture.configureByText("test.cr", """
            def each(arr : String, &block)
            end
            each("hello")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Correct Argument Count ====================

    fun testExactArgCount() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32)
            end
            add(1, 2)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNoParamsNoArgs() {
        myFixture.configureByText("test.cr", """
            def hello
            end
            hello()
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Bare Calls ====================

    fun testBareCallMissingArg() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'age'">greet</error> "Hans"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareCallCorrect() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet "Hans"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== DOT-calls ====================

    fun testDotCallMissingArg() {
        myFixture.configureByText("test.cr", """
            def self.create(name : String, age : Int32)
            end
            Foo.<error descr="Missing required argument(s): 'age'">create</error>("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDotCallCorrect() {
        myFixture.configureByText("test.cr", """
            def self.create(name : String)
            end
            Foo.create("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Overloads ====================

    fun testOverloadOneMatches() {
        myFixture.configureByText("test.cr", """
            def process(a : Int32)
            end
            def process(a : Int32, b : String)
            end
            process(42)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOverloadNoneMatches() {
        myFixture.configureByText("test.cr", """
            def process(a : Int32, b : String)
            end
            def process(a : Int32, b : String, c : Float64)
            end
            <error descr="Missing required argument(s): 'b'">process</error>(42)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Mixed Named and Positional ====================

    fun testMixedNamedAndPositional() {
        myFixture.configureByText("test.cr", """
            def connect(host : String, port : Int32, ssl : Bool)
            end
            connect("localhost", ssl: true, port: 443)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Unknown Method (no false positive) ====================

    fun testUnknownMethodNoError() {
        myFixture.configureByText("test.cr", """
            unknown_method(1, 2, 3, 4, 5)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Splat Expansion ====================

    fun testSplatExpansionCorrectCount() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2, 3}
            add(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    private fun debugPsiTree(element: com.intellij.psi.PsiElement, depth: Int): String {
        val sb = StringBuilder()
        sb.append("  ".repeat(depth))
        sb.append(element.node.elementType.toString())
        if (element.children.isEmpty()) sb.append(" '${element.text.take(30)}'")
        sb.append("\n")
        for (child in element.children) {
            sb.append(debugPsiTree(child, depth + 1))
        }
        return sb.toString()
    }

    fun testSplatExpansionTooFew() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2}
            <error descr="Missing required argument(s): 'c'">add</error>(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionCorrect() {
        myFixture.configureByText("test.cr", """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost", port: 8080}
            setup(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionMissingKey() {
        myFixture.configureByText("test.cr", """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost"}
            <error descr="Missing required argument(s): 'port'">setup</error>(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplatUnresolvableNoWarning() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            add(*get_args())
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplatExpansionTooMany() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2, 3, 4}
            <error descr="Too many arguments: expected at most 3, got 4">add</error>(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionUnknownKey() {
        myFixture.configureByText("test.cr", """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost", ort: 8080}
            <error descr="Unknown named argument(s): 'ort'">setup</error>(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBlockPassNotCountedAsArgument() {
        myFixture.configureByText("test.cr", """
            def get(path : String, &block : ->)
            end
            def forward(path : String, &block : ->)
              get(path, &block)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== .new Resolves to initialize ====================

    fun testNewResolvesToInitialize() {
        myFixture.configureByText("test.cr", """
            class Apfelsaft
              def initialize(cool : String, other : Int32)
                super
              end
            end
            a = Apfelsaft.new("lol", 123)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewTooManyArgsForInitialize() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(x : Int32)
              end
            end
            Foo.new(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewMissingArgsForInitialize() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(x : Int32, y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error>(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== .new Bare Call (no parens) ====================

    fun testNewBareCallResolvesToInitialize() {
        myFixture.configureByText("test.cr", """
            class Apfelsaft
              def initialize(cool : String, other : Int32)
              end
            end
            a = Apfelsaft.new "lol", 123
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewBareCallTooManyArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(x : Int32)
              end
            end
            Foo.new 1, <error descr="Too many arguments: expected at most 1, got 2">2</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewBareCallMissingArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(x : Int32, y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error> 1
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
