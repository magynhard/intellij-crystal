package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeCheckInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    // ==================== Basic Type Mismatches ====================

    fun testIntLiteralWhereStringExpected() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStringLiteralWhereIntExpected() {
        myFixture.configureByText("test.cr", """
            def add(x : Int32)
            end
            add(<error descr="Type mismatch: expected 'Int32', got 'String'">"hello"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBoolWhereStringExpected() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet(<error descr="Type mismatch: expected 'String', got 'Bool'">true</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLiteralMatchesAnnotation() {
        myFixture.configureByText("test.cr", """
            def add(x : Int32)
            end
            add(42)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStringLiteralMatchesString() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet("world")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Nil and Nilable ====================

    fun testNilWhereNonNilExpected() {
        myFixture.configureByText("test.cr", """
            def add(x : Int32)
            end
            add(<error descr="Type mismatch: expected 'Int32', got 'Nil'">nil</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNilWhereNilableExpected() {
        myFixture.configureByText("test.cr", """
            def maybe(x : Int32?)
            end
            maybe(nil)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNilWhereUnionWithNilExpected() {
        myFixture.configureByText("test.cr", """
            def maybe(x : Int32 | Nil)
            end
            maybe(nil)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Union Types ====================

    fun testUnionAcceptsMember() {
        myFixture.configureByText("test.cr", """
            def flex(x : Int32 | String)
            end
            flex("hello")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnionRejectsMismatch() {
        myFixture.configureByText("test.cr", """
            def flex(x : Int32 | String)
            end
            flex(<error descr="Type mismatch: expected 'Int32 | String', got 'Bool'">true</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== No Annotation → No Check ====================

    fun testNoAnnotationNoError() {
        myFixture.configureByText("test.cr", """
            def duck(x)
            end
            duck(123)
            duck("anything")
            duck(true)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Overloads ====================

    fun testOverloadCompatible() {
        myFixture.configureByText("test.cr", """
            def process(x : Int32)
            end
            def process(x : String)
            end
            process("hello")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAllOverloadsIncompatible() {
        myFixture.configureByText("test.cr", """
            def process(x : Int32)
            end
            def process(x : String)
            end
            process(<error descr="Type mismatch: expected 'Int32' or 'String', got 'Bool'">true</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Numeric Autocasting ====================

    fun testUnsuffixedIntToInt64Autocast() {
        myFixture.configureByText("test.cr", """
            def big(x : Int64)
            end
            big(123)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnsuffixedIntToFloat64Autocast() {
        myFixture.configureByText("test.cr", """
            def calc(x : Float64)
            end
            calc(42)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSuffixedIntMatchesExactType() {
        myFixture.configureByText("test.cr", """
            def big(x : Int64)
            end
            big(123_i64)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSuffixedIntAutocastToLarger() {
        myFixture.configureByText("test.cr", """
            def big(x : Int64)
            end
            big(123_i32)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testFloat32ToFloat64Autocast() {
        myFixture.configureByText("test.cr", """
            def calc(x : Float64)
            end
            calc(1.5_f32)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Multiple Parameters ====================

    fun testMultipleParamsSecondMismatch() {
        myFixture.configureByText("test.cr", """
            def pair(a : Int32, b : String)
            end
            pair(1, <error descr="Type mismatch: expected 'String', got 'Int32'">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Unknown Type → No Error ====================

    fun testUnknownTypeNoError() {
        myFixture.configureByText("test.cr", """
            def process(x : MyCustomClass)
            end
            process(123)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Splat Parameters ====================

    fun testSplatParameterSkipped() {
        myFixture.configureByText("test.cr", """
            def variadic(*args)
            end
            variadic(1, "hello", true)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Default Values ====================

    fun testFewerArgsThanParamsWithDefaults() {
        myFixture.configureByText("test.cr", """
            def greet(name : String, greeting : String = "Hello")
            end
            greet("World")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Record macro type checking ====================

    fun testRecordNewWithCorrectTypes() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithStringTypeMismatch() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new(host: <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, port: 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithIntTypeMismatch() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new(host: "localhost", port: <error descr="Type mismatch: expected 'Int32', got 'String'">"wrong"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgsCorrectTypes() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32
            Config.new("localhost", 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgsTypeMismatch() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32
            Config.new(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithCorrectTypes() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithStringTypeMismatch() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new host: <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, port: 8080
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
