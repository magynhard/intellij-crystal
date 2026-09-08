package de.magynhard.crystal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalMacroBlockContextTest : BasePlatformTestCase() {

    fun testMacroCallBlockIsGated() {
        myFixture.configureByText("props.cr", """
            macro properties(&block)
              {{ block.body }}
            end
        """.trimIndent())
        val file = myFixture.configureByText("rule.cr", """
            require "./props"

            class Rule
              properties do
                bin_path nil, as: String?
              end
            end
        """.trimIndent())
        val call = PsiTreeUtil.collectElements(file) {
            (it is CrystalBareMethodCallExpression || it is CrystalMethodCallExpression) &&
                it.text.startsWith("bin_path")
        }.firstOrNull() ?: error("bare call missing")
        assertTrue(
            "Block statements of a macro invocation are macro data",
            CrystalMacroContext.isInsideMacroCallBlock(call),
        )
    }

    fun testMethodCallBlockIsNotGated() {
        myFixture.configureByText("helper.cr", "class Ag\n  def each(&block)\n    block\n  end\nend\n")
        val file = myFixture.configureByText("use.cr", """
            require "./helper"

            class User
              def work(a)
                a.each do
                  name
                end
              end

              def name
                1
              end
            end
        """.trimIndent())
        val block = PsiTreeUtil.findChildOfType(file, CrystalBlock::class.java)
            ?: error("block missing")
        val blockStatement = block.statementList?.firstChild ?: error("statement missing")
        assertFalse(
            "Blocks owned by real methods keep ordinary semantics",
            CrystalMacroContext.isInsideMacroCallBlock(blockStatement),
        )
    }
}
