package de.magynhard.crystal.stubs

import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.CrystalParserDefinition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CrystalTypeInheritorIndexTest : BasePlatformTestCase() {
    private val source = """
        module Contract
        end
        module Wrapper
          include Contract
        end
        class Base
        end
        class Child(T) < Base
          include Wrapper
          def run
            include Wrong
          end
          class Nested
            include NestedContract
          end
        end
        struct Value
          include Contract
        end
        enum Status : UInt8
          Ready
        end
        module Generic(T)
          include T
        end
        struct {{type_name}}
          include Contract
        end
    """.trimIndent()

    fun testReverseIndexContainsOnlyDeclaredCandidateEdges() {
        myFixture.configureByText("types.cr", source)
        fun inheritors(name: String) = CrystalIndexService.findTypeInheritors(
            name, project, GlobalSearchScope.allScope(project),
        ).map { it.name }.toSet()
        assertEquals(setOf("Wrapper", "Value"), inheritors("Contract"))
        assertEquals(setOf("Child"), inheritors("Base"))
        assertEquals(setOf("Child"), inheritors("Wrapper"))
        assertEquals(setOf("Nested"), inheritors("NestedContract"))
        assertEmpty(inheritors("Wrong"))
        assertEmpty(inheritors("T"))
        assertEmpty(inheritors("UInt8"))
    }

    fun testAncestorMetadataSurvivesPlatformStubSerialization() {
        val file = myFixture.configureByText("types.cr", source)
        val stub = CrystalParserDefinition.FILE.builder.buildStubTree(file)
        fun metadata(root: StubElement<*>): Map<String?, List<String>> {
            val result = linkedMapOf<String?, List<String>>()
            fun visit(element: StubElement<*>) {
                if (element is CrystalTypeStub) result[element.name] = element.ancestorNames
                element.childrenStubs.forEach(::visit)
            }
            visit(root)
            return result
        }
        val before = metadata(stub)
        assertEquals(listOf("Base", "Wrapper"), before["Child"])
        assertEquals(listOf("Contract"), before["Value"])
        assertEquals(emptyList<String>(), before["Status"])
        val bytes = ByteArrayOutputStream()
        SerializationManagerEx.getInstanceEx().serialize(stub, bytes)
        val restored = SerializationManagerEx.getInstanceEx().deserialize(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(before, metadata(restored as StubElement<*>))
    }
}
