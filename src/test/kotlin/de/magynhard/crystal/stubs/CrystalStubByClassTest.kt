package de.magynhard.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalStubByClassTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testMethodByClassIndexForSimpleFile() {
        myFixture.addFileToProject("test.cr", """
class Foo
  def bar
  end
  def baz(x : Int32)
  end
end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val fooMethods = StubIndex.getElements(
            CrystalMethodByClassIndex.KEY, "Foo", project, scope, CrystalMethodDefinition::class.java
        )
        println("Foo methods count: ${fooMethods.size}")
        for (m in fooMethods) {
            println("  Foo method: ${m.name}")
        }

        // Also check what the method index has
        val allMethodKeys = StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)
        println("All method keys: $allMethodKeys")

        // Check method by class keys
        val allMethodByClassKeys = StubIndex.getInstance().getAllKeys(CrystalMethodByClassIndex.KEY, project)
        println("All methodByClass keys: $allMethodByClassKeys")
    }
}
