package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalUnresolvedNameInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
    }

    // ==================== Bare identifiers ====================

    fun testIfConditionAssignmentBindsInsideNestedBlock() {
        myFixture.configureByText("channels.cr", """
            def get_batch_channels(finished_channel, channels)
              final = [] of String
              channels.size.times do
                if ucid = finished_channel.receive
                  final << ucid
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testGeneratedTypeNamesAsCallArgumentsStayClean() {
        myFixture.configureByText("big_int.cr", """
            def to_primitive_u(type)
            end
            def to_primitive_u!(type)
            end
            {% for n in [8, 16, 32, 64, 128] %}
              def to_u{{n}} : UInt{{n}}
                to_primitive_u(UInt{{n}})
              end
              def to_u{{n}}! : UInt{{n}}
                to_primitive_u!(UInt{{n}})
              end
            {% end %}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForallConstantsUsedAsDotReceiversStayClean() {
        myFixture.configureByText("big_int.cr", """
            private def to_primitive_i_positive(type : T.class) : T forall T
              mask = T::MAX >> 1
              mask
            end
            private def to_primitive_i_negative(type : T.class) : T forall T
              limit = T::MIN.abs_unsigned
              limit
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroArgumentWithWhitespaceDoesNotHideUnknownCallee() {
        myFixture.configureByText("test.cr", """
            def consume(type)
            end
            consume(<warning descr="Cannot find 'UInt'">UInt</warning> {{8}})
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForallDoesNotHealAbsoluteOrOutOfScopeConstantPaths() {
        myFixture.configureByText("test.cr", """
            def convert(type : T.class) forall T
              T::MAX
              ::<warning descr="Cannot find 'T'">T</warning>::MAX
            end
            <warning descr="Cannot find 'T'">T</warning>::MIN
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroInterpolatedReturnTypeDoesNotReportItsStaticPrefix() {
        myFixture.configureByText("big_int.cr", """
            {% for n in [8, 16, 32, 64, 128] %}
              def to_u{{n}} : UInt{{n}}
              end
            {% end %}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForallTypeParameterIsKnownInSignatureAndBody() {
        myFixture.configureByText("big_int.cr", """
            struct BigInt
              private def to_primitive_i(type : T.class) : T forall T
                value : T
                T.zero
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForallTypeParametersDoNotLeakToOtherMethodsOrTopLevel() {
        myFixture.configureByText("test.cr", """
            def convert(type : T.class) : T forall T
            end
            def other(type : <warning descr="Cannot find 'T'">T</warning>.class) : <warning descr="Cannot find 'T'">T</warning>
            end
            value : <warning descr="Cannot find 'T'">T</warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForallBindsEveryDeclaredNameButNotUnknownTypes() {
        myFixture.configureByText("test.cr", """
            def convert(left : T.class, right : U.class) : U forall T, U
              value : <warning descr="Cannot find 'MissingType'">MissingType</warning>
              U.zero
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNonInterpolatedTypesStillWarnInsideMacroLoops() {
        myFixture.configureByText("test.cr", """
            {% for n in [8, 16] %}
              def generated{{n}} : UInt{{n}}
              end
              def fixed : <warning descr="Cannot find 'UInt'">UInt</warning>
              end
            {% end %}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConditionalFfiAliasConstructorReceiverStaysClean() {
        myFixture.addFileToProject("lib_gmp.cr", """
            lib LibGMP
              {% if flag?(:win32) %}
                alias MpLimb = UInt64
              {% else %}
                alias MpLimb = UInt32
              {% end %}
            end
        """.trimIndent())
        myFixture.configureByText("big_int.cr", """
            require "./lib_gmp"
            struct BigInt
              def self.build(num : Int32)
                LibGMP::MpLimb.new!(num)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testFfiAliasInOtherLibraryDoesNotHealReceiver() {
        myFixture.addFileToProject("libs.cr", "lib LibGMP\nend\nlib Other\nalias MpLimb = UInt64\nend")
        myFixture.configureByText("main.cr", """
            require "./libs"
            LibGMP::<warning descr="Cannot find 'MpLimb'">MpLimb</warning>.new!(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredFfiAliasDoesNotHealReceiver() {
        myFixture.addFileToProject("lib_gmp.cr", "lib LibGMP\nalias MpLimb = UInt64\nend")
        myFixture.configureByText("main.cr", """
            <weak_warning descr="Cannot find 'LibGMP'">LibGMP</weak_warning>::<warning descr="Cannot find 'MpLimb'">MpLimb</warning>.new!(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLexicalFfiAliasReceiverStaysClean() {
        myFixture.addFileToProject("lib_gmp.cr", "module Big\nlib LibGMP\nalias MpLimb = UInt64\nend\nend")
        myFixture.configureByText("main.cr", """
            require "./lib_gmp"
            module Big
              LibGMP::MpLimb.new!(1)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAbsoluteFfiAliasReceiverBypassesLexicalLibrary() {
        myFixture.addFileToProject("lib_gmp.cr", """
            lib LibGMP
              alias MpLimb = UInt64
            end
            module Big
              lib LibGMP
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./lib_gmp"
            module Big
              ::LibGMP::MpLimb.new!(1)
              LibGMP::<warning descr="Cannot find 'MpLimb'">MpLimb</warning>.new!(1)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBangPropertiesExposePlainReadersInBenchmarkStatistics() {
        myFixture.configureByText("test.cr", """
            module Benchmark
              module IPS
                class Entry
                  property! cycles : Int32
                  property! mean : Float64
                  property! variance : Float64
                  property! stddev : Float64
                  property! slower : Float64

                  def calculate
                    @cycles = 1 if cycles <= 0
                    @variance = mean ** 2
                    @stddev = variance
                    @relative_stddev = 100.0 * (stddev / mean)
                    iteration_time = 1.0 / mean
                    if slower == 1.0
                      "fastest"
                    else
                      sprintf "%5.2f× slower", slower
                    end
                  end
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBangGetterAndPropertyExposePlainAndQueryReaders() {
        myFixture.configureByText("test.cr", """
            class Entry
              getter! cycles : Int32
              property! mean : Float64
              def use
                puts cycles
                puts cycles?
                puts mean
                puts mean?
              end
              def use_other(other : Entry)
                puts other.cycles
                puts other.cycles?
                puts other.mean
                puts other.mean?
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSetterAndQueryOnlyMacrosDoNotDeclarePlainReader() {
        myFixture.configureByText("test.cr", """
            class Entry
              setter cycles : Int32
              getter? ready : Bool
              def use
                puts <warning descr="Cannot find 'cycles'">cycles</warning>
                puts <warning descr="Cannot find 'ready'">ready</warning>
                puts ready?
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBangPropertyInUnrelatedTypeDoesNotHealMissingName() {
        myFixture.configureByText("test.cr", """
            class Other
              property! cycles : Int32
            end
            class Entry
              def use
                puts <warning descr="Cannot find 'cycles'">cycles</warning>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCompilerAnnotationsStayKnownWithoutSdk() {
        myFixture.configureByText("test.cr", """
            @[Deprecated("Use another method")]
            @[AlwaysInline]
            def run
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMisspelledAnnotationWarns() {
        myFixture.configureByText("test.cr", """
            @[<warning descr="Cannot find 'Deprecatted'">Deprecatted</warning>]
            def run
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testIncompleteAnnotationDoesNotReportMissingName() {
        myFixture.configureByText("test.cr", "@[Deprecatted(")
        assertFalse(myFixture.doHighlighting().any { it.description?.startsWith("Cannot find") == true })
    }

    fun testNamespacedBuiltinSpellingIsNotAutomaticallyKnown() {
        myFixture.configureByText("test.cr", """
            @[Other::<warning descr="Cannot find 'Deprecated'">Deprecated</warning>]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAnnotationArgumentsAreNotOrdinaryNameReads() {
        myFixture.configureByText("test.cr", """
            annotation Mark
            end
            @[Mark(UnknownMetadata, unknown_metadata(), MissingType.new)]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredQualifiedAnnotationStaysClean() {
        myFixture.addFileToProject("marks.cr", "module Outer\nannotation Mark\nend\nend")
        myFixture.configureByText("test.cr", """
            require "./marks"
            @[Outer::Mark]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredAnnotationIsWeakWarning() {
        myFixture.addFileToProject("marks.cr", "annotation Mark\nend")
        myFixture.configureByText("test.cr", """
            @[<weak_warning descr="Cannot find 'Mark'">Mark</weak_warning>]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOrdinaryTypeDoesNotHideUnknownAnnotation() {
        myFixture.configureByText("test.cr", """
            class Mark
            end
            @[<warning descr="Cannot find 'Mark'">Mark</warning>]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testKnownNamespaceDoesNotHideUnknownAnnotationMember() {
        myFixture.configureByText("test.cr", """
            module Outer
            end
            @[Outer::<warning descr="Cannot find 'Missing'">Missing</warning>]
            class Job
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLexicallyVisibleNestedConstructorStaysClean() {
        myFixture.configureByText("test.cr", """
            class Hash
              struct Entry(K, V)
              end
            end
            module Benchmark
              module IPS
                class Job
                  def report(label, action) : Benchmark::IPS::Entry
                    Entry.new(label, action)
                  end
                end
                class Entry
                  def initialize(@label : String, @action : ->)
                  end
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredLexicalConstructorStillWarns() {
        myFixture.addFileToProject("entry.cr", """
            module Benchmark
              module IPS
                class Entry
                end
              end
            end
        """.trimIndent())
        myFixture.configureByText("test.cr", """
            module Benchmark
              module IPS
                class Job
                  def report
                    <weak_warning descr="Cannot find 'Entry'">Entry</weak_warning>.new
                  end
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownBareIdentifierIsWarning() {
        myFixture.configureByText("test.cr", """
            puts <warning descr="Cannot find 'abc'">abc</warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownConstantIsWarning() {
        myFixture.configureByText("test.cr", """
            puts <warning descr="Cannot find 'CBA'">CBA</warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownCallWithArgumentsIsWarning() {
        myFixture.configureByText("test.cr", """
            <warning descr="Cannot find 'frobnicate'">frobnicate</warning>(1, 2)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLocalsParametersAndPreludeStayClean() {
        myFixture.configureByText("test.cr", """
            def greet(name)
              local_var = name
              puts local_var
              puts name
              puts "literal"
              pp local_var
              puts sizeof(Int32)
              puts __DIR__
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForLoopRescueMultiAndGroupedBindingsStayClean() {
        myFixture.configureByText("test.cr", """
            for item in [1, 2]
              puts item
            end

            begin
              raise "boom"
            rescue error
              puts error
            end

            first, second = [1, 2]
            puts first
            puts second

            (grouped = 1)
            puts grouped

            [1, 2].each do |block_param|
              puts block_param
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSameFileConstantAssignmentStaysClean() {
        myFixture.configureByText("test.cr", """
            CBA = 42
            puts CBA
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForwardMethodReferenceStaysClean() {
        myFixture.configureByText("test.cr", """
            foo
            def foo
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashKeysAndSymbolsStayClean() {
        myFixture.configureByText("test.cr", """
            data = {error: "x", ok: true}
            puts data
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRecordAndAccessorMacrosStayClean() {
        myFixture.configureByText("test.cr", """
            record Config, host : String

            class Service
              getter name : String

              def show
                puts name
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroContextStaysClean() {
        myFixture.configureByText("test.cr", """
            {{ not_a_real_name }}

            macro my_macro
              not_a_real_name
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Require visibility ====================

    fun testRequiredShardSymbolStaysClean() {
        myFixture.addFileToProject("helper.cr", """
            class Helper
            end

            def helper_method
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./helper"

            x = Helper.new
            helper_method
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredShardSymbolIsWeakWarning() {
        myFixture.addFileToProject("helper.cr", """
            class Hidden
            end

            def hidden_helper
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            x = <weak_warning descr="Cannot find 'Hidden'">Hidden</weak_warning>.new
            <weak_warning descr="Cannot find 'hidden_helper'">hidden_helper</weak_warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredShardReceiverIsWeakWarning() {
        myFixture.addFileToProject("widget.cr", """
            class Widget
              def render
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            <weak_warning descr="Cannot find 'Widget'">Widget</weak_warning>.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSameFileRecordNewStaysClean() {
        myFixture.configureByText("test.cr", """
            record Config, host : String

            Config.new(host: "localhost")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredGenericNewStaysClean() {
        myFixture.addFileToProject("box.cr", """
            class Box(T)
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./box"

            Box(Int32).new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredConstantStaysClean() {
        myFixture.addFileToProject("a.cr", "KODORRA = 123\n")
        myFixture.configureByText("b.cr", """
            require "./a"

            puts KODORRA
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredConstantIsWarning() {
        myFixture.addFileToProject("a.cr", "KODORRA = 123\n")
        myFixture.configureByText("b.cr", """
            puts <weak_warning descr="Cannot find 'KODORRA'">KODORRA</weak_warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateConstantAcrossFilesIsWarning() {
        myFixture.addFileToProject("a.cr", "private SEKRIT = 1\n")
        myFixture.configureByText("b.cr", """
            require "./a"

            puts <weak_warning descr="Cannot find 'SEKRIT'">SEKRIT</weak_warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateConstantSameFileStaysClean() {
        myFixture.configureByText("test.cr", """
            private SEKRIT = 1
            puts SEKRIT
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredLibNamespaceInBigFloatParameterStaysClean() {
        myFixture.addFileToProject("big/lib_gmp.cr", "lib LibGMP\n  struct MPF\n  end\nend")
        myFixture.addFileToProject("big.cr", "require \"./big/lib_gmp\"")
        myFixture.configureByText("big_float.cr", """
            require "./big"
            struct BigFloat < Float
              def initialize(@mpf : LibGMP::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredLibNamespaceInParameterIsWarning() {
        myFixture.addFileToProject("lib_gmp.cr", "lib LibGMP\n  struct MPF\n  end\nend")
        myFixture.configureByText("big_float.cr", """
            struct BigFloat < Float
              def initialize(@mpf : <weak_warning descr="Cannot find 'LibGMP'">LibGMP</weak_warning>::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownLibNamespaceInParameterIsWarning() {
        myFixture.configureByText("big_float.cr", """
            struct BigFloat < Float
              def initialize(@mpf : <warning descr="Cannot find 'LibMissing'">LibMissing</warning>::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrelatedLibNamespaceDoesNotHealParameterRoot() {
        myFixture.addFileToProject("other.cr", """
            module Other
              lib LibGMP
                struct MPF
                end
              end
            end
        """.trimIndent())
        myFixture.configureByText("big_float.cr", """
            require "./other"
            struct BigFloat < Float
              def initialize(@mpf : <weak_warning descr="Cannot find 'LibGMP'">LibGMP</weak_warning>::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLexicallyNestedLibNamespaceInParameterStaysClean() {
        myFixture.addFileToProject("lib_gmp.cr", """
            module Big
              lib LibGMP
                struct MPF
                end
              end
            end
        """.trimIndent())
        myFixture.configureByText("big_float.cr", """
            require "./lib_gmp"
            struct Big::BigFloat
              def initialize(@mpf : LibGMP::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAbsoluteLibNamespaceDoesNotFallBackToLexicalLib() {
        myFixture.addFileToProject("lib_gmp.cr", """
            module Big
              lib LibGMP
                struct MPF
                end
              end
            end
        """.trimIndent())
        myFixture.configureByText("big_float.cr", """
            require "./lib_gmp"
            struct Big::BigFloat
              def initialize(@mpf : ::<weak_warning descr="Cannot find 'LibGMP'">LibGMP</weak_warning>::MPF)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantInQualifiedReopeningStaysClean() {
        myFixture.addFileToProject("hasher.cr", """
            struct Crystal::Hasher
              private HASH_MODULUS = 7
            end
        """.trimIndent())
        myFixture.configureByText("decimal.cr", """
            require "./hasher"
            struct Crystal::Hasher
              def self.reduce_num
                value = HASH_MODULUS
                value + HASH_MODULUS
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantDoesNotLeakToUnrelatedOwner() {
        myFixture.addFileToProject("hasher.cr", """
            struct Crystal::Hasher
              private HASH_MODULUS = 7
            end
        """.trimIndent())
        myFixture.configureByText("other.cr", """
            require "./hasher"
            struct Other::Hasher
              def self.reduce_num
                <weak_warning descr="Cannot find 'HASH_MODULUS'">HASH_MODULUS</weak_warning>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantInUnrequiredReopeningIsWarning() {
        myFixture.addFileToProject("hasher.cr", """
            struct Crystal::Hasher
              private HASH_MODULUS = 7
            end
        """.trimIndent())
        myFixture.configureByText("other.cr", """
            struct Crystal::Hasher
              def self.reduce_num
                <weak_warning descr="Cannot find 'HASH_MODULUS'">HASH_MODULUS</weak_warning>
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantInNestedQualifiedReopeningStaysClean() {
        myFixture.addFileToProject("owner.cr", """
            module Owner
              private SECRET = 7
            end
        """.trimIndent())
        myFixture.configureByText("nested.cr", """
            require "./owner"
            struct Owner::Nested
              def self.value
                SECRET
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantInSubclassStaysClean() {
        myFixture.addFileToProject("parent.cr", """
            class Object
            end
            class Parent
              private SECRET = 7
            end
        """.trimIndent())
        myFixture.configureByText("child.cr", """
            require "./parent"
            class Child < Parent
              def self.value
                SECRET
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPrivateTypeConstantInIncludedModuleStaysClean() {
        myFixture.addFileToProject("owner.cr", """
            class Object
            end
            module Owner
              private SECRET = 7
            end
        """.trimIndent())
        myFixture.configureByText("including.cr", """
            require "./owner"
            class Including
              include Owner
              def self.value
                SECRET
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredMemberConstantStaysClean() {
        myFixture.addFileToProject("a.cr", """
            class Owner
              MEMBER = 1
            end
        """.trimIndent())
        myFixture.configureByText("b.cr", """
            require "./a"

            puts Owner::MEMBER
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredMemberConstantFlagsRoot() {
        myFixture.addFileToProject("a.cr", """
            class Owner
              MEMBER = 1
            end
        """.trimIndent())
        myFixture.configureByText("b.cr", """
            puts <weak_warning descr="Cannot find 'Owner'">Owner</weak_warning>::MEMBER
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== DOT calls ====================

    fun testUnknownDotMethodWithExactReceiverIsWarning() {
        myFixture.configureByText("test.cr", """
            class Apfel
              def essen
              end
            end

            a = Apfel.new
            a.<warning descr="Cannot find 'verschenken'">verschenken</warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testKnownDotMethodStaysClean() {
        myFixture.configureByText("test.cr", """
            class Apfel
              def essen
              end
            end

            a = Apfel.new
            a.essen
            Apfel.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroOnlyDotCallStaysClean() {
        myFixture.configureByText("test.cr", """
            class Apfel
              macro essen(a)
              end
            end
            Apfel.essen(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testChainedCallUnknownOuterMethodIsWarning() {
        myFixture.configureByText("test.cr", """
            class Response
              def status(code : Symbol) : Response
                self
              end
            end
            env = Response.new
            env.status(:not_found).<warning descr="Cannot find 'nope'">nope</warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testChainedCallUnknownInnerFlagsInnerOnly() {
        myFixture.configureByText("test.cr", """
            class Response
              def status(code : Symbol) : Response
                self
              end
              def json(payload : String)
              end
            end
            env = Response.new
            env.<warning descr="Cannot find 'bogus'">bogus</warning>.json("ok")
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownDotReceiverFlagsReceiver() {
        myFixture.configureByText("test.cr", """
            <warning descr="Cannot find 'Missing'">Missing</warning>.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownDynamicReceiverStaysSilent() {
        myFixture.configureByText("test.cr", """
            def fetch(key)
              store = {} of String => String
              store[key]
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testKnownLibFunCallStaysClean() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun exit(status : Int32) : NoReturn
            end
            LibC.exit(1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Aliases ====================

    fun testPreludeAliasReceiverBytesStaysClean() {
        myFixture.configureByText("test.cr", """
            bytes = Bytes.new(4)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSameFileAliasReceiverStaysClean() {
        myFixture.configureByText("test.cr", """
            alias Foo = String

            foo = Foo.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredAliasReceiverStaysClean() {
        myFixture.addFileToProject("aliases.cr", """
            alias Foo = String
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./aliases"

            foo = Foo.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredAliasReceiverIsWeakWarning() {
        myFixture.addFileToProject("aliases.cr", """
            alias Foo = String
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            foo = <weak_warning descr="Cannot find 'Foo'">Foo</weak_warning>.new
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSameFileAliasTypeAnnotationStaysClean() {
        myFixture.configureByText("test.cr", """
            alias Foo = String

            def render(value : Foo) : Foo
              value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Namespaces ====================

    fun testUnknownNamespaceRootIsWarning() {
        myFixture.configureByText("test.cr", """
            puts <warning descr="Cannot find 'Missing'">Missing</warning>::Thing
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testKnownNamespaceRootStaysSilent() {
        myFixture.configureByText("test.cr", """
            enum Color
              Red
              Green
            end

            puts Color::Red
            puts Color::Typo
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Type paths ====================

    fun testUnrequiredTypeInAnnotationIsWeakWarning() {        myFixture.addFileToProject("widget.cr", """
            class Widget
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            def render(view : <weak_warning descr="Cannot find 'Widget'">Widget</weak_warning>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredTypeInAnnotationStaysClean() {
        myFixture.addFileToProject("widget.cr", """
            class Widget
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./widget"

            def render(view : Widget) : Widget
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStdlibTypeInAnnotationStaysClean() {
        myFixture.configureByText("test.cr", """
            def render(view : String) : Int32
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnrequiredConstantIsWeakWarning() {
        myFixture.addFileToProject("config.cr", """
            WIDGET_SIZE = 42
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            puts <weak_warning descr="Cannot find 'WIDGET_SIZE'">WIDGET_SIZE</weak_warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnknownAndUnrequiredSeveritiesSideBySide() {
        myFixture.addFileToProject("helper.cr", """
            def helper_method
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            <warning descr="Cannot find 'typo_name'">typo_name</warning>
            <weak_warning descr="Cannot find 'helper_method'">helper_method</weak_warning>
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
