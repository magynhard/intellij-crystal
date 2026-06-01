package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalAsmParameterInfoHandler

class CrystalAsmParameterInfoTest : BasePlatformTestCase() {

    fun testAsmSectionDetectionTemplate() {
        // Cursor inside the template string → section 0
        myFixture.configureByText("test.cr", "asm(\"no<caret>p\")")
        val handler = CrystalAsmParameterInfoHandler()
        val file = myFixture.file
        val offset = myFixture.editor.caretModel.offset
        val asmExpr = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            file.findElementAt(offset),
            de.magynhard.crystal.psi.CrystalAsmExpression::class.java
        )
        assertNotNull("Should find asm expression", asmExpr)
    }

    fun testAsmSectionDetectionOutputs() {
        // Cursor after first colon → section 1 (outputs)
        myFixture.configureByText("test.cr", "asm(\"rdtsc\" : <caret>\"=a\"(low))")
        val file = myFixture.file
        val offset = myFixture.editor.caretModel.offset
        val asmExpr = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            file.findElementAt(offset),
            de.magynhard.crystal.psi.CrystalAsmExpression::class.java
        )
        assertNotNull("Should find asm expression in outputs section", asmExpr)
    }

    fun testAsmSectionDetectionInputs() {
        // Cursor after second colon → section 2 (inputs)
        myFixture.configureByText("test.cr", "asm(\"addl\" : \"=r\"(result) : <caret>\"0\"(a))")
        val file = myFixture.file
        val offset = myFixture.editor.caretModel.offset
        val asmExpr = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            file.findElementAt(offset),
            de.magynhard.crystal.psi.CrystalAsmExpression::class.java
        )
        assertNotNull("Should find asm expression in inputs section", asmExpr)
    }
}
