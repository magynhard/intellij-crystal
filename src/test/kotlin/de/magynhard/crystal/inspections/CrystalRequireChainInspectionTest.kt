package de.magynhard.crystal.inspections

import com.intellij.openapi.Disposable
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.run.CrystalRunConfiguration
import de.magynhard.crystal.run.CrystalRunConfigurationType
import de.magynhard.crystal.run.CrystalRunFactory

/**
 * crystalline `src/crystalline/ext/compiler.cr:156`: a shard reopens the
 * compiler's `Crystal::TypeDeclarationProcessor` and forwards to the stdlib
 * overload with an extra named argument (`location:`). The accepting stdlib
 * overload is reachable only through requires *inside* stdlib files
 * (`require "compiler/crystal/compiler"` then
 * `require "./semantic/type_declaration_processor"`), so a require closure that
 * stops at the project/stdlib boundary leaves the pool holding the shard's local
 * override alone and blames `location` as an unknown named argument.
 *
 * The require path resolver reads the stdlib root from
 * [CrystalStdlibResolver.cachedStdlibPath], which is empty until the stdlib path
 * has been published — the stdlib library provider publishes it at project open.
 */
class CrystalRequireChainInspectionTest : BasePlatformTestCase() {

    private val compilerStdlib = """
        require "./semantic/type_declaration_processor"

        module Crystal
          class Compiler
          end
        end
    """.trimIndent()

    private val semanticStdlib = """
        module Crystal
          struct TypeDeclarationProcessor
            private def declare_meta_type_var(vars, owner, name, type : Type, location : Location? = nil, instance_var = false, freeze_type = true, annotations = nil)
            end
          end
        end
    """.trimIndent()

    private val crystallineRequires = """
        require "compiler/crystal/compiler"
    """.trimIndent()

    private val crystallineExt = """
        module Crystal
          class Compiler
          end

          struct TypeDeclarationProcessor
            private def declare_meta_type_var(vars, owner, name, info : TypeInfo, freeze_type = true)
              declare_meta_type_var(vars, owner, name, info.type, freeze_type: freeze_type, location: info.location)
            end
          end
        end
    """.trimIndent()

    private lateinit var entryFile: VirtualFile
    private val listenerDisposables = mutableListOf<Disposable>()
    private lateinit var requireGraph: CrystalRequireGraphService
    private var previousConfiguration: RunnerAndConfigurationSettings? = null
    private var programConfiguration: RunnerAndConfigurationSettings? = null

    override fun setUp() {
        super.setUp()
        previousConfiguration = RunManager.getInstance(project).selectedConfiguration
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        requireGraph = CrystalRequireGraphService.installForTests(
            project,
            CrystalRequirePathResolver(project) { CrystalStdlibResolver.cachedStdlibPath(project) },
            Disposer.newDisposable().also(listenerDisposables::add),
        )
        myFixture.addFileToProject("fake-stdlib/prelude.cr", "module Prelude\nend\n")
        // fake-stdlib/compiler/crystal/compiler.cr -> fake-stdlib is the stdlib root
        val stdlibDir = myFixture.addFileToProject(
            "fake-stdlib/compiler/crystal/compiler.cr",
            compilerStdlib,
        ).virtualFile.parent!!.parent!!.parent!!
        myFixture.addFileToProject(
            "fake-stdlib/compiler/crystal/semantic/type_declaration_processor.cr",
            semanticStdlib,
        )
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { stdlibDir }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        // crystalline src/crystalline/main.cr requires the shard's require hub
        // before the extension tree, so the wildcard pull is what makes the
        // stdlib compiler tree reachable from the call site.
        entryFile = myFixture.addFileToProject(
            "main.cr",
            """
                require "./crystal/requires"
                require "./crystal/ext/*"
            """.trimIndent(),
        ).virtualFile
        myFixture.addFileToProject("crystal/requires.cr", crystallineRequires)
        myFixture.addFileToProject("crystal/ext/compiler.cr", crystallineExt)
    }

    fun testStdlibCompilerFileJoinsRequireClosure() {
        val callSite = myFixture.addFileToProject("crystal/ext/leaf.cr", crystallineExt).virtualFile
        publishStdlibPath()

        val sources = requireGraph.effectiveSources(psiFileOf(callSite))

        assertTrue(
            "crystal/requires.cr -> compiler/crystal/compiler -> ./semantic/type_declaration_processor " +
                "must be part of the closure",
            sources.contains(psiFileOf(semanticStdlibFile())),
        )
    }


    fun testForwardingCallWithStdlibLocationIsNotFlagged() {
        val file = myFixture.addFileToProject("crystal/ext/forwarding.cr", crystallineExt)
        publishStdlibPath()
        myFixture.openFileInEditor(file.virtualFile)

        val flags = myFixture.doHighlighting().filter {
            it.description?.contains("Unknown named argument") == true
        }

        assertTrue(
            "The stdlib overload with `location : Location? = nil` accepts the call: " +
                flags.joinToString { it.text },
            flags.isEmpty(),
        )
    }

    /** No containing entry is selected: querying another program must not merge it. */
    fun testForwardOnlyViewWithoutQueriedEntryFile() {
        val callSite = myFixture.addFileToProject("crystal/ext/isolated.cr", crystallineExt).virtualFile
        CrystalStdlibResolver.resolveStdlibPath(project)
        requireGraph.effectiveSources(psiFileOf(entryFile))

        val sources = requireGraph.effectiveSources(psiFileOf(callSite))

        assertFalse(
            "An unselected entry must not contribute sources to an isolated file",
            sources.contains(psiFileOf(semanticStdlibFile())),
        )
    }

    /**
     * The require graph is a project singleton whose nodes survive between the
     * tests of one class, so the cached view is dropped once the stdlib path is
     * published — otherwise a closure built before publication would be reused.
     */
    override fun tearDown() {
        try {
            val manager = RunManager.getInstance(project)
            manager.selectedConfiguration = previousConfiguration
            programConfiguration?.let(manager::removeConfiguration)
            listenerDisposables.forEach(Disposer::dispose)
            listenerDisposables.clear()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
        } finally {
            super.tearDown()
        }
    }

    private fun publishStdlibPath() {
        flushFixtureFiles()
        CrystalStdlibResolver.resolveStdlibPath(project)
        val manager = RunManager.getInstance(project)
        val settings = manager.createConfiguration("Compiler forwarding", CrystalRunFactory(CrystalRunConfigurationType()))
        (settings.configuration as CrystalRunConfiguration).filePath = entryFile.path
        manager.addConfiguration(settings)
        programConfiguration = settings
        manager.selectedConfiguration = settings
        requireGraph.effectiveSources(psiFileOf(entryFile))
    }

    /** Fixture files are only in PSI after the documents are committed. */
    private fun flushFixtureFiles() {
        ApplicationManager.getApplication().invokeAndWait {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }



    private fun semanticStdlibFile(): VirtualFile = requireNotNull(
        stdlibRoot().findChild("compiler")
            ?.findChild("crystal")
            ?.findChild("semantic")
            ?.findChild("type_declaration_processor.cr")
    )

    private fun stdlibRoot(): VirtualFile = requireNotNull(
        CrystalRequirePathResolver(project).currentStdlibRoot()
    )

    private fun psiFileOf(virtualFile: VirtualFile): PsiFile =
        com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)!!
}
