package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalArrayLiteral
import de.magynhard.crystal.psi.CrystalExpressionList
import de.magynhard.crystal.psi.CrystalPropertyDeclaration
import de.magynhard.crystal.psi.CrystalTypes

class CrystalArrayTypeDeclarationTest : BasePlatformTestCase() {

    fun testSingleTypeDeclarationElement() {
        val file = assertParsesCleanly("x = [value : Int64]")
        assertDeclarationNames(file, listOf("value"))
    }

    fun testMultipleTypeDeclarationElements() {
        val file = assertParsesCleanly("x = [a : Int32, b : Int32]")
        assertDeclarationNames(file, listOf("a", "b"))
    }

    fun testTypeDeclarationWithDefaultValue() {
        val file = assertParsesCleanly("x = [value : Int64 = 0]")
        assertDeclarationNames(file, listOf("value"))
    }

    fun testIvarTypeDeclarationElement() {
        val file = assertParsesCleanly("x = [@pending : Bool]")
        assertDeclarationNames(file, listOf("@pending"))
    }

    fun testPlainArrayUnchanged() {
        val file = assertParsesCleanly("x = [value]")
        assertTrue(
            "Plain elements must stay expressions, not declarations",
            PsiTreeUtil.findChildrenOfType(file, CrystalPropertyDeclaration::class.java).isEmpty(),
        )
    }

    fun testTypedEmptyArrayUnchanged() {
        assertParsesCleanly("x = [] of Int32")
    }

    fun testTernaryInArrayUnchanged() {
        assertParsesCleanly("x = [flag ? first : second]")
    }

    fun testRejectsMissingType() {
        assertParsesWithError("x = [value : ]")
    }

    fun testRejectsSelfTarget() {
        assertParsesWithError("x = [self.value : Int64]")
    }

    private fun assertDeclarationNames(file: PsiFile, expected: List<String?>): List<String?> {
        val array = PsiTreeUtil.findChildOfType(file, CrystalArrayLiteral::class.java)
        assertNotNull("Expected an array literal in:\n${file.text}", array)
        val list = PsiTreeUtil.findChildOfType(array, CrystalExpressionList::class.java)
        assertNotNull("Expected the shared expression-list wrapper", list)
        val declarations = PsiTreeUtil.findChildrenOfType(list, CrystalPropertyDeclaration::class.java)
        val names = declarations.map { declarationTargetText(it) }
        assertEquals(expected, names)
        return names
    }

    private fun declarationTargetText(decl: CrystalPropertyDeclaration): String? =
        decl.instanceVarAccess?.text
            ?: decl.classVarAccess?.text
            ?: decl.node.findChildByType(CrystalTypes.IDENTIFIER)?.text

    private fun assertParsesCleanly(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        return file
    }

    private fun assertParsesWithError(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
