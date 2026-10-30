package de.magynhard.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.StubElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.CrystalParserDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CrystalRecordIndexTest : BasePlatformTestCase() {
    fun testOnlyStaticDeclarationCallsEnterTheRecordIndex() {
        myFixture.configureByText("records.cr", """
            record Top, name : String
            module Outer::Inner
              record Entry, id : Int32 do
                def read
                end
                class Nested
                end
              end
              record Qualified::Item, value : Int32
              record ::Global, value : Int32
              record {{name}}, value : Int32
              {% if flag?(:feature) %}
                record Conditional, value : Int32
              {% end %}
              def run
                record Local, value : Int32
              end
              consume do
                record InBlock, value : Int32
              end
              ordinary(Top)
            end
        """.trimIndent())
        fun names(name: String) = CrystalIndexService.findRecords(name, project, GlobalSearchScope.allScope(project))
            .map(CrystalPsiUtils::stubAwareQualifiedName).toSet()
        assertEquals(setOf("Top"), names("Top"))
        assertEquals(setOf("Outer::Inner::Entry"), names("Entry"))
        assertEquals(setOf("Outer::Inner::Qualified::Item"), names("Item"))
        assertEquals(setOf("Global"), names("Global"))
        for (name in listOf("Conditional", "Local", "InBlock", "ordinary")) assertEmpty(names(name))
        val nested = CrystalIndexService.findTypes("Nested", project, GlobalSearchScope.allScope(project)).single()
        assertEquals("Outer::Inner::Entry::Nested", CrystalPsiUtils.stubAwareQualifiedName(nested))
        val method = CrystalIndexService.findMethodsByClass("Entry", project, GlobalSearchScope.allScope(project)).single()
        assertEquals("Outer::Inner::Entry", method.stub?.ownerQualifiedName ?: CrystalPsiUtils.methodOwnerQualifiedName(method))
    }

    fun testRecordIdentityAndNestedMethodOwnershipSurvivePlatformSerialization() {
        val file = myFixture.configureByText("records.cr", """
            module Outer::Inner
              record Entry, id : Int32 do
                def read
                end
              end
            end
        """.trimIndent())
        val stub = CrystalParserDefinition.FILE.builder.buildStubTree(file)
        val bytes = ByteArrayOutputStream()
        val serializer = SerializationManagerEx.getInstanceEx()
        serializer.serialize(stub, bytes)
        val restored = serializer.deserialize(ByteArrayInputStream(bytes.toByteArray())) as StubElement<*>
        fun descendants(root: StubElement<*>): List<StubElement<*>> =
            listOf(root) + root.childrenStubs.flatMap(::descendants)
        val elements = descendants(restored)
        val record = elements.filterIsInstance<CrystalRecordStub>().single()
        assertEquals("Outer::Inner::Entry", record.qualifiedName)
        assertEquals("Outer::Inner::Entry", CrystalPsiUtils.buildQualifiedNameFromStub(record))
        val method = elements.filterIsInstance<CrystalMethodDefinitionStub>().single()
        assertSame(record, method.parentStub)
        assertEquals("Outer::Inner::Entry", method.ownerQualifiedName)
    }
}
