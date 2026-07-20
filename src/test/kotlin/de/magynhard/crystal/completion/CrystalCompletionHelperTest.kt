package de.magynhard.crystal.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalCompletionHelperTest : BasePlatformTestCase() {

    fun testFindTypeByNamePrefersCurrentFileAndFindsProjectTypes() {
        myFixture.addFileToProject("other.cr", """
            class SharedType
            end

            struct ProjectType
            end
        """.trimIndent())
        val currentFile = myFixture.addFileToProject("current.cr", """
            module SharedType
            end
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(currentFile.virtualFile)

        val preferred = CrystalCompletionHelper.findTypeByName("SharedType", project, myFixture.file)
        val projectType = CrystalCompletionHelper.findTypeByName("ProjectType", project)

        assertNotNull(preferred)
        assertEquals(CrystalCompletionHelper.TypeKind.MODULE, preferred!!.kind)
        assertEquals(myFixture.file.virtualFile, preferred.element.containingFile.virtualFile)
        assertNotNull(projectType)
        assertEquals(CrystalCompletionHelper.TypeKind.STRUCT, projectType!!.kind)
        assertEquals("other.cr", projectType.element.containingFile.name)
    }
}
