package de.magynhard.crystal.inspections

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver

class CrystalRespondsToGuardTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        myFixture.addFileToProject("errors.cr", """
            class Object
            end
            module Crystal
              module ErrorFormat
              end
              class TypeException < Object
                include ErrorFormat
                getter inner : TypeException?
              end
            end
        """.trimIndent())
    }

    private fun configure(source: String) {
        myFixture.configureByText("diagnostics.cr", "require \"./errors\"\n" + source.trimIndent())
    }

    fun testDiagnosticsExceptionUnwrappingKeepsGuardedInnerKnown() {
        configure("""
            def append_from_exception(error : Crystal::ErrorFormat)
              loop do
                if error.responds_to? :inner
                  break unless (error = error.inner)
                else
                  break
                end
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testBareAndParenthesizedSymbolGuardsProveOnlyTheirReceiverAndMethod() {
        for (guard in listOf("error.responds_to? :inner", "error.responds_to?(:inner)")) {
            configure("""
                def read(error : Crystal::ErrorFormat, other : Crystal::ErrorFormat)
                  if $guard
                    error.inner
                    error.<warning descr="Cannot find 'missing'">missing</warning>
                    other.<warning descr="Cannot find 'inner'">inner</warning>
                  else
                    error.<warning descr="Cannot find 'inner'">inner</warning>
                  end
                  error.<warning descr="Cannot find 'inner'">inner</warning>
                end
            """)
            myFixture.checkHighlighting()
        }
    }

    fun testUnlessElseAndEarlyExitKeepCorrectPolarity() {
        configure("""
            def read(error : Crystal::ErrorFormat)
              unless error.responds_to?(:inner)
                error.<warning descr="Cannot find 'inner'">inner</warning>
              else
                error.inner
              end
              return unless error.responds_to?(:inner)
              error.inner
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testReassignmentInvalidatesTheMemberGuarantee() {
        configure("""
            def read(error : Crystal::ErrorFormat, replacement : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                error.inner
                error = replacement
                error.<warning descr="Cannot find 'inner'">inner</warning>
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testCapturedReadInsideANestedBlockKeepsTheGuard() {
        configure("""
            def consume
              yield
            end
            def read(error : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                consume { error.inner }
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testCommonGuaranteeSurvivesAnAllPathsMerge() {
        configure("""
            def read(error : Crystal::ErrorFormat, flag : Bool)
              if flag
                return unless error.responds_to?(:inner)
              else
                return unless error.responds_to?(:inner)
              end
              error.inner
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testOtherTypeGuardsPreserveTheExistingMemberGuarantee() {
        configure("""
            def read(error : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                if error.is_a?(Crystal::ErrorFormat)
                  case error
                  when Crystal::ErrorFormat
                    error.inner
                  end
                end
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testBlockParameterShadowingDoesNotInheritTheGuard() {
        configure("""
            def consume
              yield nil
            end
            def read(error : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                consume do |error|
                  error.inner
                end
              end
            end
        """)
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "inner" }
        val descriptor = CrystalCallExtractor.extractDotCall(call)!!
        assertFalse(CrystalTypeSetResolver.session(call).isMethodGuaranteed(descriptor.receiver, "inner"))
    }

    fun testOtherShadowingBindingsDoNotBorrowTheOuterGuarantee() {
        for (body in listOf(
            "->(error : Crystal::ErrorFormat) { error.inner }",
            "begin\nraise \"failure\"\nrescue error\nerror.inner\nend",
            "for error in items\nerror.inner\nend",
        )) {
            configure("""
                def read(error : Crystal::ErrorFormat, items)
                  if error.responds_to?(:inner)
                    $body
                  end
                end
            """)
            assertNull(PsiTreeUtil.findChildOfType(myFixture.file, com.intellij.psi.PsiErrorElement::class.java))
            val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
                .single { CrystalCallExtractor.extractMethodName(it) == "inner" }
            val descriptor = CrystalCallExtractor.extractDotCall(call)!!
            assertFalse("A new binding in $body must not inherit the outer fact",
                CrystalTypeSetResolver.session(call).isMethodGuaranteed(descriptor.receiver, "inner"))
        }
    }

    fun testWritingAShadowedParameterDoesNotInvalidateTheOuterReceiver() {
        configure("""
            def consume
              yield nil
            end
            def read(error : Crystal::ErrorFormat, replacement : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                consume do |error|
                  error = replacement
                end
                error.inner
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testAssignmentRhsReadsKeepTheGuardUntilTheValueIsReplaced() {
        configure("""
            def read(error : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                error = error.inner
              end
            end
        """)
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "inner" }
        val descriptor = CrystalCallExtractor.extractDotCall(call)!!
        assertTrue(CrystalTypeSetResolver.session(call).isMethodGuaranteed(descriptor.receiver, "inner"))
    }

    fun testNonSymbolOrMalformedGuardDoesNotProveAMember() {
        for (guard in listOf("error.responds_to?(name)", "error.responds_to?(\"inner\")", "error.responds_to?(:inner, :missing)")) {
            configure("""
                def read(error : Crystal::ErrorFormat, name : Symbol)
                  if $guard
                    error.<warning descr="Cannot find 'inner'">inner</warning>
                  end
                end
            """)
            myFixture.checkHighlighting()
        }
    }

    fun testRepeatedMethodCallsAndInstanceVariablesDoNotGetLocalGuardProofs() {
        configure("""
            def current_error : Crystal::ErrorFormat
              raise "no error"
            end
            def read
              if current_error.responds_to?(:inner)
                current_error.<warning descr="Cannot find 'inner'">inner</warning>
              end
            end
            class Holder < Object
              @error : Crystal::ErrorFormat
              def read
                if @error.responds_to?(:inner)
                  @error.<warning descr="Cannot find 'inner'">inner</warning>
                end
              end
            end
        """)
        myFixture.checkHighlighting()
    }

    fun testGuardDoesNotInventAnImplementationOrSignature() {
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java, CrystalTypeCheckInspection::class.java)
        configure("""
            class Unrelated < Object
              def inner(value : Int32)
              end
            end
            def read(error : Crystal::ErrorFormat)
              if error.responds_to?(:inner)
                error.inner("value", true)
              end
            end
        """)
        myFixture.checkHighlighting()
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "inner" }
        assertTrue("Availability does not establish an exact declaration", CrystalDotCallTargetResolver.resolve(call) is DotCallResolution.Suppressed)
        assertNull(call.reference!!.resolve())
    }
}
