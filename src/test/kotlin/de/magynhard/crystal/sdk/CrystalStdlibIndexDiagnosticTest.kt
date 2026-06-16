package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

class CrystalStdlibIndexDiagnosticTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun setupStdlib() {
        myFixture.addFileToProject("main.cr", "puts 1")
        val configurator = CrystalStdlibSourceRootConfigurator()
        runBlocking { configurator.execute(project) }
    }

    fun testStdlibPathResolves() {
        val path = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Stdlib path should resolve", path)
        println("Stdlib path: ${path!!.path}")
        println("Is directory: ${path.isDirectory}")
        val crFiles = path.children?.filter { it.extension == "cr" } ?: emptyList()
        println(".cr files in root: ${crFiles.size}")
        crFiles.take(10).forEach { println("  ${it.name}") }
    }

    fun testStdlibClassesInIndex() {
        setupStdlib()
        val keys = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
        println("All class index keys: ${keys.size}")
        val stdlibClasses = listOf("Array", "Hash", "String", "File", "IO", "HTTP", "URI", "Time", "ENV")
        for (name in stdlibClasses) {
            val found = name in keys
            println("  $name: ${if (found) "FOUND" else "MISSING"}")
        }
    }

    fun testStdlibMethodsInIndex() {
        setupStdlib()
        val keys = StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)
        println("All method index keys: ${keys.size}")
        val expectedMethods = listOf("read", "write", "exists?", "open", "push", "includes?", "get", "close", "parse")
        for (name in expectedMethods) {
            val found = name in keys
            println("  $name: ${if (found) "FOUND" else "MISSING"}")
        }
    }

    fun testModuleHasStdlibLibrary() {
        setupStdlib()
        val modules = ModuleManager.getInstance(project).modules
        assertTrue("Should have at least one module", modules.isNotEmpty())
        val module = modules[0]
        val model = ModuleRootManager.getInstance(module).modifiableModel
        val libraries = model.moduleLibraryTable.libraries
        println("Module libraries: ${libraries.map { it.name }}")
        val hasStdlib = libraries.any { it.name == "Crystal StdLib" }
        println("Has Crystal StdLib library: $hasStdlib")
        model.dispose()
    }

    /**
     * Diagnostic: finds all methods named "read" or "write" in the index,
     * prints their enclosing class name and file location.
     */
    fun testFindReadAndWriteMethods() {
        setupStdlib()
        val scope = GlobalSearchScope.allScope(project)
        val targetNames = setOf("read", "write")

        println("=== Searching for methods named 'read' or 'write' in StubIndex ===")
        for (key in StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)) {
            if (key !in targetNames) continue
            val elements = StubIndex.getElements(CrystalMethodIndex.KEY, key, project, scope, CrystalMethodDefinition::class.java)
            for (method in elements) {
                val enclosingClass = CrystalCompletionHelper.getEnclosingClassName(method)
                val isStatic = CrystalCompletionHelper.isStaticMethod(method)
                val file = method.containingFile?.virtualFile?.path ?: "?"
                val signature = CrystalCompletionHelper.getParameterSignature(method)
                println("  Method: ${method.name} | enclosingClass: $enclosingClass | static: $isStatic | sig: $signature | file: $file")
            }
        }
    }

    /**
     * Diagnostic: calls getStaticMethods("File") and getInstanceMethods("File"),
     * prints every method name found (or none).
     */
    fun testFileCompletionMethods() {
        setupStdlib()

        println("=== getStaticMethods(\"File\") ===")
        val staticMethods = CrystalCompletionHelper.getStaticMethods("File", project)
        println("  Count: ${staticMethods.size}")
        for (m in staticMethods) {
            val sig = CrystalCompletionHelper.getParameterSignature(m)
            val ret = CrystalCompletionHelper.getReturnType(m)
            println("  - ${m.name}$sig -> $ret")
        }

        println("=== getInstanceMethods(\"File\") ===")
        val instanceMethods = CrystalCompletionHelper.getInstanceMethods("File", project)
        println("  Count: ${instanceMethods.size}")
        for (m in instanceMethods) {
            val sig = CrystalCompletionHelper.getParameterSignature(m)
            val ret = CrystalCompletionHelper.getReturnType(m)
            println("  - ${m.name}$sig -> $ret")
        }
    }

    /**
     * Diagnostic: prints the full hierarchy for File via collectFullHierarchy.
     */
    fun testFileHierarchy() {
        setupStdlib()

        println("=== Hierarchy for File ===")
        val typeResult = CrystalCompletionHelper.findTypeByName("File", project)
        if (typeResult == null) {
            println("  File type NOT FOUND in class index!")
            return
        }
        println("  Found File: kind=${typeResult.kind}, name=${typeResult.element.name}")

        // Print the class index key
        val classKeys = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
        println("  Class index keys matching 'File': ${classKeys.filter { it.contains("File") }}")

        // Try to find each expected ancestor
        for (name in listOf("File", "FileDescriptor", "IO::FileDescriptor", "IO", "IO::Syscall")) {
            val result = CrystalCompletionHelper.findTypeByName(name, project)
            println("  findTypeByName(\"$name\"): ${if (result != null) "FOUND (${result.kind}, element.name=${result.element.name})" else "NOT FOUND"}")
        }
    }

    /**
     * Diagnostic: for every method named "read" or "write", trace getEnclosingClassName
     * and check if it would match the File hierarchy.
     */
    fun testReadWriteEnclosingClassVsHierarchy() {
        setupStdlib()
        val scope = GlobalSearchScope.allScope(project)
        val targetNames = setOf("read", "write")

        println("=== Hierarchy for File (via CompletionHelper) ===")
        val typeResult = CrystalCompletionHelper.findTypeByName("File", project)
        if (typeResult == null) {
            println("  File NOT FOUND — cannot compute hierarchy")
            return
        }

        // Use reflection-free approach: just call getStaticMethods + getInstanceMethods and check names
        val allFileMethods = CrystalCompletionHelper.getStaticMethods("File", project) +
                CrystalCompletionHelper.getInstanceMethods("File", project)
        val fileMethodNames = allFileMethods.mapNotNull { it.name }.toSet()
        println("  All File method names via CompletionHelper: $fileMethodNames")
        println("  'read' in File methods: ${"read" in fileMethodNames}")
        println("  'write' in File methods: ${"write" in fileMethodNames}")

        println("")
        println("=== All methods named 'read' or 'write' and their enclosing class ===")
        for (key in StubIndex.getInstance().getAllKeys(CrystalMethodIndex.KEY, project)) {
            if (key !in targetNames) continue
            val elements = StubIndex.getElements(CrystalMethodIndex.KEY, key, project, scope, CrystalMethodDefinition::class.java)
            for (method in elements) {
                val enclosingClass = CrystalCompletionHelper.getEnclosingClassName(method)
                val file = method.containingFile?.virtualFile?.name ?: "?"
                println("  ${method.name} | enclosingClass=$enclosingClass | file=$file")
            }
        }
    }

    private fun runBlocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
