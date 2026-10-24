package de.magynhard.crystal.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalConstantTypeInferenceTest : BasePlatformTestCase() {
    fun testPrivateConstantInReopeningDoesNotMergeUnrelatedOwner() {
        myFixture.addFileToProject("hasher.cr", """
            struct Crystal::Hasher
              private HASH_MODULUS = 7
            end
            struct Other::Hasher
              private HASH_MODULUS = "unrelated"
            end
        """.trimIndent())
        myFixture.configureByText("decimal.cr", """
            require "./hasher"
            struct Crystal::Hasher
              def self.reduce_num
                HASH_MODUL<caret>US
              end
            end
        """.trimIndent())
        val context = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(listOf("Int32"), CrystalConstantTypeInference.inferType("HASH_MODULUS", context))
    }

    fun testPrivateConstantInUnrelatedOwnerHasNoInferredType() {
        myFixture.addFileToProject("hasher.cr", """
            struct Crystal::Hasher
              private HASH_MODULUS = 7
            end
        """.trimIndent())
        myFixture.configureByText("other.cr", """
            require "./hasher"
            struct Other::Hasher
              def self.reduce_num
                HASH_MODUL<caret>US
              end
            end
        """.trimIndent())
        val context = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertNull(CrystalConstantTypeInference.inferType("HASH_MODULUS", context))
    }
}
