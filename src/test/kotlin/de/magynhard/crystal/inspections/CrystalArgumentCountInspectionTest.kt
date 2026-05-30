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
            <warning descr="Missing required argument(s): 'age'">greet</warning>("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMissingAllRequiredArgs() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, age : Int32)
            end
            <warning descr="Missing required argument(s): 'name', 'age'">greet</warning>()
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
            greet("Hans", <warning descr="Too many arguments: expected at most 1, got 2">"extra"</warning>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTooManyArgsMultipleExcess() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet("Hans", <warning descr="Too many arguments: expected at most 1, got 3">"extra1"</warning>, <warning descr="Too many arguments: expected at most 1, got 3">"extra2"</warning>)
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
            greet(<warning descr="Unknown named argument 'unknown'">unknown: "value"</warning>)
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
            <warning descr="Missing required argument(s): 'age'">greet</warning> "Hans"
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
            Foo.<warning descr="Missing required argument(s): 'age'">create</warning>("Hans")
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
            <warning descr="Missing required argument(s): 'b'">process</warning>(42)
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
}
