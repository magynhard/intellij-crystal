package de.magynhard.crystal

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import de.magynhard.crystal.sdk.CrystalStdlibResolver

internal fun installSyntheticStdlib(
    project: Project,
    fixture: CodeInsightTestFixture,
    parentDisposable: Disposable,
) {
    val prelude = fixture.addFileToProject(".synthetic-stdlib/prelude.cr", "").virtualFile
    val root = requireNotNull(prelude.parent)
    CrystalStdlibResolver.installDiscoveryForTests(project, parentDisposable) { root }
    CrystalStdlibResolver.clearCachedStdlibPath(project)
    check(CrystalStdlibResolver.resolveStdlibPath(project) === root)
    Disposer.register(parentDisposable) { CrystalStdlibResolver.clearCachedStdlibPath(project) }
}
