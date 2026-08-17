package de.magynhard.crystal.inspections

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.CrystalStdlibVfsAccess
import de.magynhard.crystal.psi.CrystalAssignment

class CrystalExpressionTypeResolverTest : BasePlatformTestCase() {

    private lateinit var stdlibVfsAccess: CrystalStdlibVfsAccess

    override fun setUp() {
        super.setUp()
        stdlibVfsAccess = CrystalStdlibVfsAccess.allow(project)
    }

    override fun tearDown() {
        try {
            stdlibVfsAccess.restore()
        } finally {
            super.tearDown()
        }
    }

    fun testAssignmentResolvesToRightHandSideType() {
        val file = myFixture.configureByText("test.cr", "value = \"text\"")
        val assignment = PsiTreeUtil.findChildOfType(file, CrystalAssignment::class.java)
            ?: error("No assignment PSI")

        assertEquals("String", CrystalExpressionTypeResolver.resolveType(assignment)?.typeName)
    }
}
