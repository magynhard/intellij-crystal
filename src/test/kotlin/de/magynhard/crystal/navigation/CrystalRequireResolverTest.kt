package de.magynhard.crystal.navigation

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver

class CrystalRequireResolverTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        CrystalStdlibResolver.resolveStdlibPath(project)?.path
            ?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it) }
    }

    private fun requireTargets(code: String, path: String): List<String> {
        val file = myFixture.addFileToProject(path, code)
        val targets = PsiTreeUtil.findChildrenOfType(file, CrystalRequireStatement::class.java)
        return targets.flatMap { CrystalRequireResolver.resolve(it, project) }
            .map { it.virtualFile?.path ?: it.name }
    }

    fun testRelativeRequire() {
        myFixture.addFileToProject("src/foo.cr", "module Foo; end")
        val targets = requireTargets("require \"./foo\"\n", "src/main.cr")
        assertNotEmpty(targets)
        assertTrue("expected src/foo.cr, got $targets", targets.any { it.endsWith("src/foo.cr") })
    }

    fun testProjectSrcRequire() {
        myFixture.addFileToProject("src/thing.cr", "module Thing; end")
        val targets = requireTargets("require \"thing\"\n", "src/main.cr")
        assertNotEmpty(targets)
        assertTrue("expected src/thing.cr, got $targets", targets.any { it.endsWith("src/thing.cr") })
    }

    fun testShardRequire() {
        myFixture.addFileToProject("lib/colorize/src/colorize.cr", "module Colorize; end")
        val targets = requireTargets("require \"colorize\"\n", "src/main.cr")
        assertNotEmpty(targets)
        assertTrue("expected lib/colorize/src/colorize.cr, got $targets",
            targets.any { it.endsWith("lib/colorize/src/colorize.cr") })
    }

    fun testShardNestedRequire() {
        myFixture.addFileToProject("lib/ameba/src/ameba/cli.cr", "module Ameba::Cli; end")
        val targets = requireTargets("require \"ameba/cli\"\n", "src/main.cr")
        assertNotEmpty(targets)
        assertTrue("expected lib/ameba/src/ameba/cli.cr, got $targets",
            targets.any { it.endsWith("lib/ameba/src/ameba/cli.cr") })
    }
}
