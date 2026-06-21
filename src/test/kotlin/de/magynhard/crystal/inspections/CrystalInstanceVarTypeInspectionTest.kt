package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInstanceVarTypeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalInstanceVarTypeInspection::class.java)
    }

    // ==================== Forbidden Types ====================

    fun testIntAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Int' cannot be used as the type of instance variable '@x', use a more specific type">Int</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testFloatAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Float' cannot be used as the type of instance variable '@x', use a more specific type">Float</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNumberAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Number' cannot be used as the type of instance variable '@x', use a more specific type">Number</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testValueAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Value' cannot be used as the type of instance variable '@x', use a more specific type">Value</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testObjectAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Object' cannot be used as the type of instance variable '@x', use a more specific type">Object</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStructAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Struct' cannot be used as the type of instance variable '@x', use a more specific type">Struct</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testEnumAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Enum' cannot be used as the type of instance variable '@x', use a more specific type">Enum</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayWithoutTypeArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Array' cannot be used as the type of instance variable '@x', use a more specific type">Array</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashWithoutTypeArgs() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Hash' cannot be used as the type of instance variable '@x', use a more specific type">Hash</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPointerAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Pointer' cannot be used as the type of instance variable '@x', use a more specific type">Pointer</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTupleAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Tuple' cannot be used as the type of instance variable '@x', use a more specific type">Tuple</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNamedTupleAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'NamedTuple' cannot be used as the type of instance variable '@x', use a more specific type">NamedTuple</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStaticArrayAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'StaticArray' cannot be used as the type of instance variable '@x', use a more specific type">StaticArray</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testClassAsInstanceVarType() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Class' cannot be used as the type of instance variable '@x', use a more specific type">Class</error>)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Allowed Types ====================

    fun testInt32IsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testStringIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : String)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testArrayWithTypeArgsIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Array(String))
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHashWithTypeArgsIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Hash(String, Int32))
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCustomClassIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Bar)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNamespacedTypeIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Foo::Bar)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNilableTypeIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32?)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnionOfValidTypesIsAllowed() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32 | String)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Union with Forbidden Type ====================

    fun testUnionWithForbiddenTypeReportsError() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : <error descr="'Int' cannot be used as the type of instance variable '@x', use a more specific type">Int</error> | String)
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Non-InstanceVar Property Declarations ====================

    fun testClassVarWithTypeIsNotChecked() {
        // Class variables have different rules — only instance vars are checked
        myFixture.configureByText("test.cr", """
            class Foo
              @@x : Int = 0
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
