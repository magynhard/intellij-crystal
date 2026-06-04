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
}
