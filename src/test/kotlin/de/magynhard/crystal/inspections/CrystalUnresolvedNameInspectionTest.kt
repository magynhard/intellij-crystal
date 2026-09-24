package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalUnresolvedNameInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
    }

    // ==================== Bare identifiers ====================

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
