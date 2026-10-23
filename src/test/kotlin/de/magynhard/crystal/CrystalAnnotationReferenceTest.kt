package de.magynhard.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.psi.*

class CrystalAnnotationReferenceTest : BasePlatformTestCase() {
    fun testLexicalAnnotationWinsOverGlobalAndOrdinaryType() {
        myFixture.configureByText("test.cr", """
            annotation Mark
            end
            module Outer
              # Metadata for the inner API.
              annotation Mark
              end
              class Job
                @[<caret>Mark]
                def run
                end
              end
            end
            module Other
              class Mark
              end
            end
        """.trimIndent())
        val target = annotationPath().reference?.resolve()
        assertTrue(target is CrystalAnnotationDefinition)
        assertEquals("Outer::Mark", CrystalPsiUtils.buildQualifiedName(target!!))
        val provider = CrystalDocumentationProvider()
        assertEquals(target, provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, myFixture.file.findElementAt(myFixture.caretOffset),
            myFixture.caretOffset
        ))
        val documentation = provider.generateDoc(target, myFixture.file.findElementAt(myFixture.caretOffset))
        assertTrue(documentation.orEmpty().contains("Metadata for the inner API."))
    }

    fun testRequiredQualifiedAnnotationResolvesExactly() {
        myFixture.addFileToProject("marks.cr", "module Outer\nannotation Mark\nend\nend")
        myFixture.configureByText("test.cr", "require \"./marks\"\n@[Outer::<caret>Mark]\nclass Job\nend")
        val target = annotationPath().reference?.resolve()
        assertTrue(target is CrystalAnnotationDefinition)
        assertEquals("Outer::Mark", CrystalPsiUtils.buildQualifiedName(target!!))
    }

    fun testAbsoluteAnnotationBypassesLexicalShadow() {
        myFixture.configureByText("test.cr", """
            annotation Mark
            end
            module Outer
              annotation Mark
              end
              @[::<caret>Mark]
              class Job
              end
            end
        """.trimIndent())
        val target = annotationPath().reference?.resolve()
        assertNotNull(target)
        assertEquals("Mark", CrystalPsiUtils.buildQualifiedName(target!!))
    }

    fun testUnrequiredAnnotationDoesNotNavigate() {
        myFixture.addFileToProject("marks.cr", "annotation Mark\nend")
        myFixture.configureByText("test.cr", "@[<caret>Mark]\nclass Job\nend")
        assertNull(annotationPath().reference?.resolve())
    }

    fun testOrdinaryTypeCannotCaptureAnnotationOrDocumentation() {
        myFixture.configureByText("test.cr", "class Mark\nend\n@[<caret>Mark]\nclass Job\nend")
        assertNull(annotationPath().reference?.resolve())
        assertNull(CrystalDocumentationProvider().getCustomDocumentationElement(
            myFixture.editor, myFixture.file, myFixture.file.findElementAt(myFixture.caretOffset),
            myFixture.caretOffset
        ))
    }

    private fun annotationPath(): CrystalTypePath = PsiTreeUtil.getParentOfType(
        myFixture.file.findElementAt(myFixture.caretOffset), CrystalTypePath::class.java
    )!!
}
