package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalLibFunParameterTypeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalLibFunParameterTypeInspection::class.java)
    }

    fun testParameterWithType() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun exit(status : Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testParameterWithoutType() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun exit(<error descr="Parameter in lib fun must have a type annotation">status</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMultipleParametersMixed() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun printf(<error descr="Parameter in lib fun must have a type annotation">format</error>, <error descr="Parameter in lib fun must have a type annotation">args</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariadicNoError() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun printf(format : UInt8*, ...) : Int32
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnnamedTypeOnlyParametersNoError() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun strerror_r(Int, Char*, SizeT) : Int
              fun mixed(Int32, output : Char*, LibC::Timeval*)
              fun callback(BioMethod*, (Bio*, Char*, Int) -> Int)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMixedNamedUntypedParameterStillReported() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun mixed(Int32, <error descr="Parameter in lib fun must have a type annotation">output</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAliasParametersStillChecked() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun native = Native(<error descr="Parameter in lib fun must have a type annotation">value</error>)
              fun typed = Native2(value : Int32)
              fun untyped = "native3"(Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUppercaseNameParametersStillChecked() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun BIO_new(BioMethod*) : Bio*
              fun Upper(<error descr="Parameter in lib fun must have a type annotation">value</error>)
              fun Foo = Bar(Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testKeywordNameParametersStillChecked() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun select(nfds : Int, readfds : FdSet*) : Int
              fun select(<error descr="Parameter in lib fun must have a type annotation">value</error>)
              fun select = c_select(Int32) : Int
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTypedKeywordParametersHaveNoError() {
        myFixture.configureByText("test.cr", """
            lib LibC
              fun keyword_parameters(out : Int, class : Int, then : Int, catch : Int)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAnnotatedFunParametersStillChecked() {
        myFixture.configureByText("test.cr", """
            lib LibC
              @[ReturnsTwice]
              fun fork : PidT
              @[Raises]
              fun risky(<error descr="Parameter in lib fun must have a type annotation">value</error>)
              @[Flags]
              enum FlockOp
                SH = 0x1
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplicedNameParametersStillChecked() {
        myFixture.configureByText("test.cr", """
            lib LibLLVM
              fun initialize_{{name}}_target = LLVMInitialize{{target.id}}Target
              fun initialize_{{name}}_target(<error descr="Parameter in lib fun must have a type annotation">value</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
