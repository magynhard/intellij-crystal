package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalTypes

class CrystalCallExtractorTest : BasePlatformTestCase() {

    fun testExtractMethodNameForBareCall() {
        myFixture.configureByText("test.cr", "greet \"Ada\"")
        val call = findElement(CrystalMethodCallExpression::class.java)

        assertEquals("greet", CrystalCallExtractor.extractMethodName(call))
    }

    fun testExtractMethodNameForDotCall() {
        myFixture.configureByText("test.cr", "Widget.build(1)")
        val call = findElement(CrystalDotCallAccess::class.java)

        assertEquals("build", CrystalCallExtractor.extractMethodName(call))
    }

    fun testFindMethodNameElement() {
        myFixture.configureByText("test.cr", "Widget.build(1)")
        val call = findElement(CrystalDotCallAccess::class.java)

        val methodName = CrystalCallExtractor.findMethodNameElement(call)

        assertNotNull(methodName)
        assertEquals("build", methodName!!.text)
        assertEquals(CrystalTypes.IDENTIFIER, methodName.node.elementType)
    }

    fun testFindClassNameBeforeNew() {
        myFixture.configureByText("test.cr", "Widget.new(1)")
        val call = findElement(CrystalDotCallAccess::class.java)

        assertEquals("Widget", CrystalCallExtractor.findClassNameBeforeNew(call))
    }

    fun testDetectDotCallForParenthesizedArguments() {
        myFixture.configureByText("test.cr", "Widget.build(1)")
        val arguments = findElement(CrystalCallArgs::class.java)

        val call = CrystalCallExtractor.detectDotCall(arguments)

        assertNotNull(call)
        assertEquals("Widget", call!!.receiverName)
        assertEquals("build", call.methodName)
        assertEquals("build", call.methodNameElement?.text)
        assertEquals(CrystalTypes.IDENTIFIER, call.methodNameElement?.node?.elementType)
    }

    fun testDetectDotCallForBareArguments() {
        myFixture.configureByText("test.cr", "Widget.build 1")
        val arguments = findElement(CrystalBareArgumentList::class.java)

        val call = CrystalCallExtractor.detectDotCall(arguments)

        assertNotNull(call)
        assertEquals("Widget", call!!.receiverName)
        assertEquals("build", call.methodName)
        assertEquals("build", call.methodNameElement?.text)
        assertEquals(CrystalTypes.IDENTIFIER, call.methodNameElement?.node?.elementType)
    }

    fun testDetectDotCallReturnsNullForDirectCallArguments() {
        myFixture.configureByText("test.cr", "build(1)")
        val arguments = findElement(CrystalCallArgs::class.java)

        assertNull(CrystalCallExtractor.detectDotCall(arguments))
    }

    private fun <T : PsiElement> findElement(type: Class<T>): T =
        PsiTreeUtil.findChildOfType(myFixture.file, type)
            ?: error("Expected ${type.simpleName} in ${myFixture.file.text}")
}
