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

    // ==================== Argumentless Direct Calls ====================

    fun testArgumentlessDirectCallReportsRequiredParameters() {
        myFixture.configureByText("test.cr", """
            def process(untyped, typed : Int32, nilable : String?)
            end
            <error descr="Missing required argument(s): 'untyped', 'typed', 'nilable'">process</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallAcceptsOptionalParameters() {
        myFixture.configureByText("test.cr", """
            def configure(value : String? = nil, *rest, **options)
            end
            configure
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallReportsRequiredParametersAroundVariadics() {
        myFixture.configureByText("test.cr", """
            def configure(nilable : String?, optional = 1, *rest, named_required, named_optional = 2, **options)
            end
            <error descr="Missing required argument(s): 'nilable', 'named_required'">configure</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallAcceptsMatchingOverload() {
        myFixture.configureByText("test.cr", """
            def process(value)
            end
            def process
            end
            process
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallUsesUniquelyClosestOverload() {
        myFixture.configureByText("test.cr", """
            def process(value)
            end
            def process(first, second)
            end
            <error descr="Missing required argument(s): 'value'">process</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallInsideAssignmentIsChecked() {
        myFixture.configureByText("test.cr", """
            def load(path)
            end
            result = <error descr="Missing required argument(s): 'path'">load</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallInsideReturnIsChecked() {
        myFixture.configureByText("test.cr", """
            def load(path)
            end
            def wrapper
              return <error descr="Missing required argument(s): 'path'">load</error>
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallInsideCallArgumentIsCheckedOnce() {
        myFixture.configureByText("test.cr", """
            def load(path)
            end
            def consume(value)
            end
            consume(<error descr="Missing required argument(s): 'path'">load</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByPriorAssignment() {
        myFixture.configureByText("test.cr", """
            def refresh(required)
            end
            refresh = ->{ nil }
            refresh
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByParameter() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            def wrapper(callback)
              callback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByBlockParameter() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            [1].each do |callback|
              callback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByInternalParameterName() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            def wrapper(external callback)
              callback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByDestructuredMethodParameter() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            def wrapper((callback, other))
              callback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByDestructuredBlockParameter() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            pairs.each do |(callback, other)|
              callback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsShadowedByDestructuredMacroParameter() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            macro wrapper((callback, other))
              {{ callback }}
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTypeDeclarationIsNotAnArgumentlessCall() {
        myFixture.configureByText("test.cr", """
            def callback(required)
            end
            callback : Proc(Int32, Nil)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIsSuppressedBySameNamedMacro() {
        myFixture.configureByText("test.cr", """
            def refresh(required)
            end
            macro refresh
            end
            refresh
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIgnoresUnrelatedEnclosedMethod() {
        myFixture.configureByText("test.cr", """
            class Other
              def refresh(required)
              end
            end
            refresh
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIgnoresSameLineDotReceiver() {
        myFixture.configureByText("test.cr", """
            def service(required)
            end
            service.fetch
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIgnoresMultilineDotReceiver() {
        myFixture.configureByText("test.cr", """
            def service(required)
            end
            service
              .fetch
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDirectCallIgnoresNamespaceRoot() {
        myFixture.configureByText("test.cr", """
            def service(required)
            end
            service::Client.fetch
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStandaloneTypeIsNotAnArgumentlessDirectCall() {
        myFixture.configureByText("test.cr", """
            class Service
            end
            Service
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownArgumentlessDirectCallHasNoError() {
        myFixture.configureByText("test.cr", "unknown_method")
        myFixture.checkHighlighting()
    }

    fun testExplicitCallsRemainOwnedByExistingVisitorPaths() {
        myFixture.configureByText("test.cr", """
            def greet(name)
            end
            <error descr="Missing required argument(s): 'name'">greet</error>()
            greet "Hans"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== DOT-calls ====================

    fun testDotCallMissingArg() {
        myFixture.configureByText("test.cr", """
            class Foo
              def self.create(name : String, age : Int32)
              end
            end
            Foo.<error descr="Missing required argument(s): 'age'">create</error>("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDotCallCorrect() {
        myFixture.configureByText("test.cr", """
            class Foo
              def self.create(name : String)
              end
            end
            Foo.create("Hans")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessClassMethodReportsRequiredParameter() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.create(name : String?)
              end
            end
            Factory.<error descr="Missing required argument(s): 'name'">create</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessKeywordClassMethodReportsRequiredParameter() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.class(name)
              end
            end
            Factory.<error descr="Missing required argument(s): 'name'">class</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessOperatorClassMethodReportsRequiredParameter() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.+(value)
              end
            end
            Factory.<error descr="Missing required argument(s): 'value'">+</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessAbsoluteDeeplyQualifiedClassMethodUsesExactReceiver() {
        myFixture.configureByText("test.cr", """
            module Outer
              module Inner
                class Factory
                  def self.create(name)
                  end
                end
              end
            end
            module Other
              class Factory
                def self.create
                end
              end
            end
            ::Outer::Inner::Factory.<error descr="Missing required argument(s): 'name'">create</error>
        """.trimIndent())
        myFixture.checkHighlighting()

    }

    fun testArgumentlessQualifiedClassMethodReportsRequiredParameter() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
            end
            Outer::Factory.<error descr="Missing required argument(s): 'name'">create</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessSimpleClassReceiverResolvesInLexicalNamespace() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
              Factory.<error descr="Missing required argument(s): 'name'">create</error>
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessSimpleClassReceiverResolvesInNestedLexicalNamespace() {
        myFixture.configureByText("test.cr", """
            module Outer
              module Inner
                class Factory
                  def self.create(name)
                  end
                end
                Factory.<error descr="Missing required argument(s): 'name'">create</error>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessLexicalClassReceiverIgnoresUnrelatedNamespace() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
              Factory.<error descr="Missing required argument(s): 'name'">create</error>
            end
            module Other
              class Factory
                def self.create
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessSimpleClassReceiverUsesNearestLexicalIdentity() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
              module Inner
                class Factory
                  def self.create(name)
                  end
                end
                Factory.<error descr="Missing required argument(s): 'name'">create</error>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroInterpolatedDotTargetIsNotAnArgumentlessStaticCall() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.create(name)
              end
            end
            macro invoke(method)
              Factory.{{ method }}
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessQualifiedClassMethodIgnoresUnrelatedQualifiedOverload() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
            end
            module Other
              class Factory
                def self.create
                end
              end
            end
            Outer::Factory.<error descr="Missing required argument(s): 'name'">create</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessClassMethodIgnoresInstanceAndTopLevelMethods() {
        myFixture.configureByText("test.cr", """
            def create
            end
            class Factory
              def create
              end
              def self.create(name)
              end
            end
            Factory.<error descr="Missing required argument(s): 'name'">create</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessClassMethodAcceptsMatchingSelfOverload() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.create(name)
              end
              def self.create
              end
            end
            Factory.create
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessDotCallsResolveInheritanceAndSuppressUnsupportedReceivers() {
        myFixture.configureByText("test.cr", """
            class Parent
              def self.create(name)
              end
            end
            class Child < Parent
            end
            class Factory
              def self.create(name)
              end
            end
            factory = Factory.new
            factory.create
            Child.<error descr="Missing required argument(s): 'name'">create</error>
            Unknown.create
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessAmbiguousSimpleClassReceiverIsIgnored() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Factory
                def self.create(name)
                end
              end
            end
            module Other
              class Factory
                def self.create(name)
                end
              end
            end
            Factory.create
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessConstructorAndInstanceCallReportRequiredParameters() {
        myFixture.configureByText("test.cr", """
            class Foos
              def initialize(age : Int32)
              end
              def dance(name : String)
              end
            end
            a = Foos.<error descr="Missing required argument(s): 'age'">new</error>
            a.<error descr="Missing required argument(s): 'name'">dance</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConstructorFormsRespectRequiredDefaultAndNilableParameters() {
        myFixture.configureByText("test.cr", """
            class Required
              def initialize(value : String?)
              end
            end
            Required.<error descr="Missing required argument(s): 'value'">new</error>
            Required.<error descr="Missing required argument(s): 'value'">new</error>()
            Required.new nil

            class Optional
              def initialize(value : String? = nil)
              end
            end
            Optional.new
            Optional.new()
            Optional.new "value"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInstanceCallFormsUseExactReceiverMethods() {
        myFixture.configureByText("test.cr", """
            class Foos
              def first(value : Int32)
              end
            end
            a = Foos.new
            a.<error descr="Missing required argument(s): 'value'">first</error>
            a.<error descr="Missing required argument(s): 'value'">first</error>()
            a.first 1
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTypedAndInferredReceiversUseExactInstanceMethods() {
        myFixture.configureByText("test.cr", """
            class Service
              def run(value)
              end
            end
            class Runner
              @typed : Service

              def initialize(@parameter_service : Service)
                @assigned = Service.new
              end

              def exercise(parameter : Service)
                parameter.<error descr="Missing required argument(s): 'value'">run</error>
                @parameter_service.<error descr="Missing required argument(s): 'value'">run</error>
                @typed.<error descr="Missing required argument(s): 'value'">run</error>
                @assigned.<error descr="Missing required argument(s): 'value'">run</error>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTransparentParenthesizedReceiversReportMissingArguments() {
        myFixture.configureByText("test.cr", """
            class Service
              def local_call(value)
              end

              def parameter_call(value)
              end

              def ivar_call(value)
              end

              def self.class_call(value)
              end
            end

            class Runner
              @service : Service

              def exercise(parameter : Service)
                local = Service.new
                (local).<error descr="Missing required argument(s): 'value'">local_call</error>
                ((parameter)).<error descr="Missing required argument(s): 'value'">parameter_call</error>
                (@service).<error descr="Missing required argument(s): 'value'">ivar_call</error>
                (Service).<error descr="Missing required argument(s): 'value'">class_call</error>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTransparentParenthesizedConstructorReportsMissingArguments() {
        myFixture.configureByText("test.cr", """
            class Service
              def initialize(value)
              end
            end
            (Service).<error descr="Missing required argument(s): 'value'">new</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testGroupedQualifiedAndAbsoluteReceiversReportMissingArguments() {
        myFixture.configureByText("test.cr", """
            class Service
              def self.absolute_call(value)
              end

              def initialize(global_value)
              end
            end

            module Outer
              class Service
                def self.qualified_call(value)
                end

                def self.nested_call(value)
                end

                def initialize(outer_value)
                end
              end
            end

            (Outer::Service).<error descr="Missing required argument(s): 'value'">qualified_call</error>
            ((Outer::Service)).<error descr="Missing required argument(s): 'value'">nested_call</error>
            (::Service).<error descr="Missing required argument(s): 'value'">absolute_call</error>
            (Outer::Service).<error descr="Missing required argument(s): 'outer_value'">new</error>
            ((Outer::Service)).<error descr="Missing required argument(s): 'outer_value'">new</error>
            (::Service).<error descr="Missing required argument(s): 'global_value'">new</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testGroupedQualifiedRecordCollisionUsesRecordPrecedence() {
        myFixture.configureByText("test.cr", """
            module Outer
              class Service
                def initialize(value)
                end
              end

              record Service, record_value : Int32
            end

            (Outer::Service).<error descr="Missing required argument(s): 'record_value'">new</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testGenericConstructorAssignmentUsesExactRootReceiver() {
        myFixture.configureByText("test.cr", """
            class Box(T)
              def unpack(value)
              end
            end
            value = Box(Int32).new
            value.<error descr="Missing required argument(s): 'value'">unpack</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDirectGenericConstructorReportsMissingInitializeArgumentsForAllCallForms() {
        myFixture.configureByText("test.cr", """
            class Box(T)
              def initialize(first, second)
              end
            end
            Box(Int32).<error descr="Missing required argument(s): 'first', 'second'">new</error>
            Box(Int32).<error descr="Missing required argument(s): 'first', 'second'">new</error>()
            Box(Int32).<error descr="Missing required argument(s): 'second'">new</error> 1
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDirectGenericConstructorUsesSelfNewPrecedence() {
        myFixture.configureByText("test.cr", """
            class Box(T)
              def self.new(first, second)
              end

              def initialize(first)
              end
            end
            Box(Int32).<error descr="Missing required argument(s): 'second'">new</error> 1
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDirectGenericImplicitConstructorReportsExcessArgument() {
        myFixture.configureByText("test.cr", """
            class Box(T)
            end
            Box(Int32).new <error descr="Too many arguments: expected at most 0, got 1">1</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDirectInexactGenericConstructorTargetsRemainSuppressed() {
        myFixture.configureByText("test.cr", """
            class Box(T)
              def initialize(value)
              end
            end
            Box(type_source()).new
            Box(Int32 | String).new
            Box(Int32?).new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNearestLocalAssignmentAndReassignmentControlResolution() {
        myFixture.configureByText("test.cr", """
            class First
              def run(first)
              end
            end
            class Second
              def run(second, extra)
              end
            end
            value = First.new
            value.<error descr="Missing required argument(s): 'first'">run</error>
            value = Second.new
            value.<error descr="Missing required argument(s): 'second', 'extra'">run</error>
            value = unknown
            value.run
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInheritedMethodsInitializersAndCompleteOverloadsAreEvaluated() {
        myFixture.configureByText("test.cr", """
            class Parent
              def initialize(age)
              end
              def run(parent_value)
              end
            end
            class Child < Parent
              def run(child_value, extra)
              end
            end
            Child.<error descr="Missing required argument(s): 'age'">new</error>
            child = Child.new 1
            child.<error descr="Missing required argument(s): 'parent_value'">run</error>
            child.run 1

            class Overloaded
              def choose(value)
              end
              def choose
              end
            end
            overloaded = Overloaded.new
            overloaded.choose
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConstructorDefinitionSetPriorityDoesNotFallThrough() {
        myFixture.configureByText("test.cr", """
            class Explicit
              def self.new(first, second)
              end
              def initialize(first)
              end
            end
            Explicit.<error descr="Missing required argument(s): 'second'">new</error> 1

            class Initialized
              def initialize(first, second)
              end
            end
            Initialized.<error descr="Missing required argument(s): 'second'">new</error> 1
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStructAndImplicitConstructorsUseConstructorRules() {
        myFixture.configureByText("test.cr", """
            struct Point
              def initialize(x, y)
              end
            end
            Point.<error descr="Missing required argument(s): 'y'">new</error> 1

            class Empty
            end
            Empty.new
            Empty.new()
            Empty.new <error descr="Too many arguments: expected at most 0, got 1">1</error>
            Empty.new(<error descr="Too many arguments: expected at most 0, got 2">1</error>, <error descr="Too many arguments: expected at most 0, got 2">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorNamedArgumentIsExcess() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            Empty.new(value: <error descr="Too many arguments: expected at most 0, got 1">1</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorResolvedSplatIsExcessOnMethodName() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            values = {1, 2}
            Empty.<error descr="Too many arguments: expected at most 0, got 2">new</error>(*values)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorUnresolvedSplatIsSuppressed() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            Empty.new(*unknown_values)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorResolvedDoubleSplatIsExcessOnMethodName() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            options = {first: 1, second: 2}
            Empty.<error descr="Too many arguments: expected at most 0, got 2">new</error>(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorUnresolvedDoubleSplatIsSuppressed() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            Empty.new(**unknown_options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testImplicitConstructorBlockPassDoesNotCountAsArgument() {
        myFixture.configureByText("test.cr", """
            class Empty
            end
            block = ->{ nil }
            Empty.new(&block)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testResolvedDotCallPreservesSplatAndDoubleSplatSemantics() {
        myFixture.configureByText("test.cr", """
            class Service
              def run(first, second)
              end
              def configure(host, port)
              end
            end
            service = Service.new
            values = {1, 2}
            options = {host: "localhost", port: 8080}
            service.run(*values)
            service.configure(**options)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInexactReceiversAndTargetsAreSuppressed() {
        myFixture.configureByText("test.cr", """
            class Service
              def run(value)
              end
            end
            class Other
              def run(unrelated)
              end
            end
            def unknown_receiver(value)
              value.run
            end
            def union_receiver(value : Service | Other)
              value.run
            end
            def nilable_receiver(value : Service?)
              value.run
            end
            class Conflicting
              def initialize(flag)
                @value = Service.new
                @value = Other.new if flag
              end
              def exercise
                @value.run
                @@value.run
              end
            end
            value = Service.new
            value = unresolved
            value.run
            Service.unknown
            Unknown.run
            Service.{{ method }}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNearestLexicalTargetResolvesWhileUnknownTargetsRemainSuppressed() {
        myFixture.configureByText("test.cr", """
            class Service
              def self.run(value)
              end
            end
            module Outer
              class Service
                def self.run(value)
                end
              end
              def self.exercise
                Service.<error descr="Missing required argument(s): 'value'">run</error>
              end
            end
            record Entry, value : Int32
            entry = Entry.new 1
            entry.run
            class Child < MissingParent
            end
            Child.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNearerNestedClassConstructorTakesPrecedenceOverGlobalRecord() {
        myFixture.configureByText("test.cr", """
            record Config, record_value : Int32

            module Other
              class Config
                def initialize(class_value)
                end
              end

              def self.exercise
                Config.<error descr="Missing required argument(s): 'class_value'">new</error>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroControlledRecordDoesNotProduceArgumentDiagnostic() {
        myFixture.configureByText("test.cr", """
            {% if false %}
            record Config, hidden_value : Int32
            {% end %}

            Config.new

            {% if unresolved_flag %}
            record DynamicConfig, hidden_value : Int32
            {% end %}

            DynamicConfig.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrelatedSameNamedMethodsDoNotAffectExactReceiver() {
        myFixture.configureByText("test.cr", """
            class Target
              def run(value)
              end
            end
            class Other
              def run
              end
            end
            target = Target.new
            target.<error descr="Missing required argument(s): 'value'">run</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDotCallOwnershipProducesOneDiagnosticPerCallForm() {
        myFixture.configureByText("test.cr", """
            class Service
              def run(value)
              end
            end
            service = Service.new
            service.<error descr="Missing required argument(s): 'value'">run</error>
            service.<error descr="Missing required argument(s): 'value'">run</error>()
            service.run 1, <error descr="Too many arguments: expected at most 1, got 2">2</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentBearingClassDotCallsRemainSingleOwned() {
        myFixture.configureByText("test.cr", """
            class Factory
              def self.create(name)
              end
            end
            Factory.<error descr="Missing required argument(s): 'name'">create</error>()
            Factory.create "value"
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

    // ==================== @param shorthand (instance var assignment) ====================

    fun testNewWithShorthandInstanceVar() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32, @y : String)
              end
            end
            Foo.new(1, "hi")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewWithShorthandTooManyArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32)
              end
            end
            Foo.new(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewWithShorthandMissingArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32, @y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error>(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMethodWithShorthandTooManyArgs() {
        myFixture.configureByText("test.cr", """
            def foo(@x : Int32)
            end
            foo(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Record macro support ====================

    fun testRecordNewWithValidNamedArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithTooManyArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new(<error descr="Unknown named argument 'ssl'">ssl: true</error>, host: "localhost", port: 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithMissingRequiredArg() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.<error descr="Missing required argument(s): 'host'">new</error>(port: 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArgumentlessRecordFallbackReportsMissingRequiredField() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.<error descr="Missing required argument(s): 'host'">new</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new("localhost", 8080)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewUnknownNamedArg() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new(<error descr="Unknown named argument 'unknown'">unknown: "value"</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithValidNamedArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithUnknownNamedArg() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new host: "localhost", port: 8080, <error descr="Unknown named argument 'unknown'">unknown: "value"</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithAssignment() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            config = Config.new host: "lol"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithMissingRequiredArg() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.<error descr="Missing required argument(s): 'host'">new</error> port: 8080
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithPositionalArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80
            Config.new "localhost", 8080
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMultiplicationInMethodCallArgs() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            add(2 * 3, 4 * 5)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMultiplicationInStructNewCall() {
        myFixture.configureByText("test.cr", """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            Vector2D.new(1.0 * 2.0, 3.0 * 4.0)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableMultiplicationInCallArgs() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 2
            y = 3
            add(x, y)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareDotCallWithBinaryMultiplication() {
        myFixture.configureByText("test.cr", """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            scalar = 2.0
            Vector2D.new x * scalar, y * scalar
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testParenthesizedDotCallWithBinaryMultiplication() {
        myFixture.configureByText("test.cr", """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            scalar = 2.0
            Vector2D.new(x * scalar, y * scalar)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareDotCallWithNamedArgs() {
        myFixture.configureByText("test.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareCallWithSplatArgument() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            args = {1, 2}
            add(*args)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Parameter variable shadowing ====================

    fun testParameterVariableNotConfusedWithMethod() {
        myFixture.configureByText("test.cr", """
            def dance(count : Int32)
              return count + 87
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testParameterVariableNotConfusedWithMethodInClass() {
        myFixture.configureByText("test.cr", """
            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLocalVariableAssignmentNotConfusedWithMethod() {
        myFixture.configureByText("test.cr", """
            count = 42
            result = count + 87
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Binary Operators with Literals in Arguments ====================

    fun testPlusWithStringLiteralInCallArgs() {
        myFixture.configureByText("test.cr", """
            def add(a : String, b : String)
            end
            name = "file"
            add(name + ".ext", name + ".bak")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPlusWithPercentLiteralInCallArgs() {
        myFixture.configureByText("test.cr", """
            def write_to_second_line(file : String, line : String)
            end
            cmd_path_no_ext = "test"
            write_to_second_line(cmd_path_no_ext + ".ps1", %Q{not working})
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPlusWithIntegerLiteralInCallArgs() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 10
            add(x + 5, x + 3)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMinusWithLiteralInCallArgs() {
        myFixture.configureByText("test.cr", """
            def sub(a : Int32, b : Int32) : Int32
              a - b
            end
            x = 10
            sub(x - 5, x - 3)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBinaryOpWithGroupedExpressionInCallArgs() {
        myFixture.configureByText("test.cr", """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 10
            add(x + (1), x + (2))
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBinaryOpWithArrayLiteralInCallArgs() {
        myFixture.configureByText("test.cr", """
            def process(a : String, b : String)
            end
            arr = ["hello"]
            process(arr[0] + "x", arr[0] + "y")
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
