package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.installSyntheticStdlib

class CrystalTypeCheckInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        installSyntheticStdlib(project, myFixture, testRootDisposable)
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

    fun testKeywordNamedArgsDoNotBindToPositionalParameters() {
        myFixture.configureByText("test.cr", """
            def flexible(value : String = "", **kwargs)
            end
            flexible(for: 1)
            flexible for: 1
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

    fun testUnsuffixedRadixIntegersAutocastToFloatAndWiderIntegers() {
        myFixture.configureByText("test.cr", """
            def float_value(x : Float64)
            end
            def wide_value(x : Int64)
            end
            float_value(0xff)
            float_value(0o77)
            float_value(0b1010)
            wide_value(0xff)
            wide_value(0o77)
            wide_value(0b1010)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSuffixedRadixIntegerDoesNotUseUnsuffixedFloatAutocast() {
        myFixture.configureByText("test.cr", """
            def float_value(x : Float64)
            end
            float_value(<error descr="Type mismatch: expected 'Float64', got 'Int64'">0xff_i64</error>)
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

    fun testConstructorNamedArgumentMatchesInstanceVariableParameter() {
        myFixture.configureByText("test.cr", """
            class Boo
              def initialize(@age : Int32)
              end
            end

            Boo.new age: <error descr="Type mismatch: expected 'Int32', got 'String'">"some string"</error>
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

    // ==================== Bare DOT-call type checking ====================

    fun testBareDotCallTypeMismatch() {
        myFixture.configureByText("test.cr", """
            def hika(name : String)
            end
            class Foo
              def self.bar(x : String)
              end
            end
            puts Foo.bar <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareDotCallCorrectType() {
        myFixture.configureByText("test.cr", """
            def hika(name : String)
            end
            class Foo
              def self.bar(x : String)
              end
            end
            puts Foo.bar "hello"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Scalar Literal Inference (Regression Guards) ====================

    fun testIntLiteralWhereStringExpectedStillWorks() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStringLiteralWhereStringExpectedNoError() {
        myFixture.configureByText("test.cr", """
            def greet(name : String)
            end
            greet("hello")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNilLiteralWhereNilableExpectedNoError() {
        myFixture.configureByText("test.cr", """
            def greet(name : String?)
            end
            greet(nil)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Bare DOT-call type checking ====================

    fun testBareDotCallAfterMethodCallTypeMismatch() {
        myFixture.configureByText("test.cr", """
            class RvmCli
              class Tools
                def self.hello(name : String)
                end
              end
            end
            puts RvmCli::Tools.hello <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Trivial Expression Types ====================

    fun testRegexLiteralMatchesRegexType() {
        myFixture.configureByText("test.cr", """
            def foo(r : Regex)
            end
            foo(/pattern/)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRegexLiteralMismatchString() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Regex'">/pattern/</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCommandExpressionMatchesStringType() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(`ls`)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCommandExpressionMismatchInt() {
        myFixture.configureByText("test.cr", """
            def foo(n : Int32)
            end
            foo(<error descr="Type mismatch: expected 'Int32', got 'String'">`ls`</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Macro context (v13) ====================

    fun testMacroInterpolationCallNotTypeChecked() {
        // Catalyst shape: top-level {{ run("…") }} must resolve to the builtin
        // macro run(filename, *args) — NOT to Catalyst::CLI.run(Array(String)).
        myFixture.configureByText("test.cr", """
            module Catalyst
              class CLI
                def self.run(args : Array(String)) : Int32
                  0
                end
              end
            end

            {{ run("../scripts/generate_rules_require.cr") }}
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Macro-context call must not be type-checked against ordinary methods",
            highlights.any { it.description?.contains("Type mismatch") == true
                || it.description?.contains("Missing required argument") == true
                || it.description?.contains("Too many arguments") == true })
    }

    fun testQualifiedMethodCallTypeMismatchStillReported() {
        myFixture.configureByText("test.cr", """
            module Catalyst
              class CLI
                def self.run(args : Array(String)) : Int32
                  0
                end
              end
            end

            Catalyst::CLI.run(<error descr="Type mismatch: expected 'Array(String)', got 'String'">"str"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHeredocLiteralMatchesStringType() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<<-HEREDOC
            hello
            HEREDOC
            )
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHeredocLiteralMismatchInt() {
        myFixture.configureByText("test.cr", """
            def foo(n : Int32)
            end
            foo(<error descr="Type mismatch: expected 'Int32', got 'String'"><<-HEREDOC</error>
            hello
            HEREDOC
            )
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSizeofExpressionMatchesInt32Type() {
        myFixture.configureByText("test.cr", """
            def foo(n : Int32)
            end
            foo(sizeof(Int32))
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSizeofExpressionMismatchString() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Int32'">sizeof(Int32)</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Array Literal Inference ====================

    fun testArrayLiteralOfTypeAnnotation() {
        myFixture.configureByText("test.cr", """
            def foo(a : Array(Int32))
            end
            foo([] of Int32)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralHomogeneousNoError() {
        myFixture.configureByText("test.cr", """
            def foo(a : Array(Int32))
            end
            foo([1, 2, 3])
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralHeterogeneousNoError() {
        myFixture.configureByText("test.cr", """
            def foo(a : Array(Int32 | String))
            end
            foo([1, "hi"])
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralTypeMismatch() {
        myFixture.configureByText("test.cr", """
            def foo(a : Array(Int32))
            end
            foo(<error descr="Type mismatch: expected 'Array(Int32)', got 'Array(String)'">["hello"]</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralWhereStringExpected() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Array(Int32)'">[1, 2]</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Hash Literal Inference ====================

    fun testHashLiteralOfTypeAnnotation() {
        myFixture.configureByText("test.cr", """
            def foo(h : Hash(String, Int32))
            end
            foo({} of String => Int32)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // {a: 1} is a NamedTuple literal in crystal — it does NOT satisfy Hash(Symbol, Int32).
    fun testNamedTupleLiteralAgainstHashParameterIsReported() {
        myFixture.configureByText("test.cr", """
            def foo(h : Hash(Symbol, Int32))
            end
            foo(<error descr="Type mismatch: expected 'Hash(Symbol, Int32)', got 'NamedTuple(a: Int32)'">{a: 1}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashLiteralArrowNoError() {
        myFixture.configureByText("test.cr", """
            def foo(h : Hash(String, Int32))
            end
            foo({"a" => 1})
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashLiteralTypeMismatch() {
        myFixture.configureByText("test.cr", """
            def foo(h : Hash(String, Int32))
            end
            foo(<error descr="Type mismatch: expected 'Hash(String, Int32)', got 'Hash(String, String)'">{"a" => "b"}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashLiteralWhereStringExpected() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'NamedTuple(a: Int32)'">{a: 1}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Tuple Literal Inference ====================

    fun testTupleLiteralNoError() {
        myFixture.configureByText("test.cr", """
            def foo(t : Tuple(Int32, String))
            end
            foo({1, "hi"})
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTupleLiteralTypeMismatch() {
        myFixture.configureByText("test.cr", """
            def foo(t : Tuple(Int32, String))
            end
            foo(<error descr="Type mismatch: expected 'Tuple(Int32, String)', got 'Tuple(String, Int32)'">{"hi", 1}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTupleLiteralWhereStringExpected() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Tuple(Int32, Int32)'">{1, 2}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Control-Flow Union Inference ====================

    fun testTernaryExpressionType() {
        myFixture.configureByText("test.cr", """
            def foo(n : Int32?)
            end
            foo(true ? 1 : nil)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Operator Result Type Inference ====================

    fun testIntegerAdditionType() {
        myFixture.configureByText("test.cr", """
            def foo(n : Int32)
            end
            foo(1 + 2)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testComparisonType() {
        myFixture.configureByText("test.cr", """
            def foo(b : Bool)
            end
            foo(1 == 2)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStringConcatType() {
        myFixture.configureByText("test.cr", """
            def foo(s : String)
            end
            foo("a" + "b")
        """.trimIndent())
        myFixture.checkHighlighting()
    }
    // ==================== Shared-resolver migration (no name-only fallback) ====================

    fun testSplatTupleCallOnGenericStructIsClean() {
        myFixture.configureByText("test.cr", """
            struct Event(*Args)
              @args : Tuple(*Args)

              def initialize(*args : *Args)
                @args = args
              end

              def call(handler : Proc(*Args, Nil))
                handler.call(*@args)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLowercaseReceiverWithKnownTypeReportsRealMismatch() {
        myFixture.configureByText("test.cr", """
            class Handler
              def call(payload : Int32)
              end
            end

            h = Handler.new
            h.call(<error descr="Type mismatch: expected 'Int32', got 'String'">"not an int"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownLowercaseReceiverStaysSilentDespiteNameCollisions() {
        myFixture.configureByText("test.cr", """
            class Handler
              def call(payload : Int32)
              end
            end

            mystery.call("whatever")
        """.trimIndent())
        // `mystery` has no assignment evidence: resolution is suppressed and the
        // inspection must stay silent instead of falling back to name-only lookup.
        myFixture.checkHighlighting()
    }
    // ==================== Require-graph visibility for unqualified calls ====================

    fun testTypeCheckAgainstUnrequiredFileStaysSilent() {
        myFixture.addFileToProject("mylib.cr", "def shout(x : String)\nend")
        myFixture.configureByText("main.cr", "shout(123)")
        // crystal: undefined method — must not report Int32/String mismatch.
        myFixture.checkHighlighting()
    }

    fun testTypeCheckAgainstRequiredFileStillReportsMismatch() {
        myFixture.addFileToProject("mylib.cr", "def shout(x : String)\nend")
        myFixture.configureByText("main.cr", """
            require "./mylib"

            shout(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }
    // ==================== NamedTuple literal vs {k: T} parameter notation ====================

    fun testNamedTupleLiteralMatchesBraceTupleParameter() {
        myFixture.configureByText("test.cr", """
            def greet(user : {name: String, title: String})
              puts "Hello, #{"x"}!"
            end

            greet({name: "Smith", title: "Dr."})
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNamedTupleLiteralWithWrongValueTypesIsReported() {
        myFixture.configureByText("test.cr", """
            def greet(user : {name: String, title: String})
              puts "Hello"
            end

            greet(<error descr="Type mismatch: expected '{name: String, title: String}', got 'NamedTuple(name: Int32, title: Int32)'">{name: 1, title: 2}</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }
    // ==================== Splat / double-splat expansion ====================

    fun testTupleSplatExpansionMatchesParameters() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end

            args = {1, 2, 3}
            result = add(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTupleSplatElementMismatchIsReported() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end

            args = {1, 2, 3}
            result = add(*<error descr="Type mismatch: expected 'Int32', got 'String'">args</error>)
        """.trimIndent().replace("{1, 2, 3}", "{1, \"x\", 3}"))
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionMatchesNamedParameters() {
        myFixture.configureByText("test.cr", """
            def setup(host : String, port : Int32)
            end

            options = {host: "localhost", port: 8080}
            setup(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatValueMismatchIsReported() {
        myFixture.configureByText("test.cr", """
            def setup(host : String, port : Int32)
            end

            options = {host: 8080, port: 8080}
            setup(**<error descr="Type mismatch: expected 'String', got 'Int32'">options</error>)
        """.trimIndent())
        // The host slot's value type (Int32) mismatches its named parameter.
        myFixture.checkHighlighting()
    }

    fun testDotCallWithTupleSplatIsClean() {
        myFixture.configureByText("test.cr", """
            class Calc
              def add(a : Int32, b : Int32, c : Int32) : Int32
                a + b + c
              end
            end

            calc = Calc.new
            args = {1, 2, 3}
            calc.add(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRestParameterAbsorbsExpandedTuple() {
        myFixture.configureByText("test.cr", """
            def g(a : Int32, *rest)
              a
            end

            values = {1, 2, 3}
            g(*values)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNonTupleSplatStaysSilent() {
        myFixture.configureByText("test.cr", """
            def f(x : Int32)
              x
            end

            f(*"not a tuple")
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
