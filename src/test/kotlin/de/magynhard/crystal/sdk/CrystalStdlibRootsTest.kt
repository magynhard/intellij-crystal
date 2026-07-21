package de.magynhard.crystal.sdk

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertTrue

/**
 * Tests for [CrystalStdlibRoots] — the helper that enumerates the
 * user-facing stdlib source roots while excluding the compiler/CLI/C ABI
 * bindings subtrees from indexing.
 */
class CrystalStdlibRootsTest : BasePlatformTestCase() {

    fun testEnumerateExcludesRegularStdlibSubdirs() {
        val childList = mutableListOf<LightVirtualFile>()
        childList.add(file("array.cr"))
        childList.add(file("string.cr"))
        childList.add(dir("compiler", file("compile.cr")))
        childList.add(dir("crystal", file("tool.cr")))
        childList.add(dir("lib_c", file("errno.cr")))
        childList.add(dir("lib_z", file("zlib.cr")))
        childList.add(dir("llvm", file("llvm_bindings.cr")))
        childList.add(dir("ll", file("ll_bindings.cr")))
        childList.add(dir("gc", file("boehm.cr")))
        childList.add(dir("samples", file("hello_world.cr")))
        // Stdlib subdirectories — MUST be included
        childList.add(dir("json", file("parser.cr"), file("from_yaml.cr")))
        childList.add(dir("http", file("client.cr")))
        childList.add(dir("spec", file("context.cr")))
        childList.add(dir("crypto", file("aes.cr")))
        val childArray: Array<out VirtualFile> = childList.toTypedArray()

        val fakeStdlibLight = object : LightVirtualFile("fake-stdlib-root") {
            override fun isDirectory(): Boolean = true
            override fun getChildren(): Array<out VirtualFile> = childArray
        }

        val roots = CrystalStdlibRoots.enumerate(fakeStdlibLight).map { it.name }

        assertTrue("Top-level `array.cr` included: $roots", roots.contains("array.cr"))
        assertTrue("Top-level `string.cr` included: $roots", roots.contains("string.cr"))
        assertTrue("`json` included: $roots", roots.contains("json"))
        assertTrue("`http` included: $roots", roots.contains("http"))
        assertTrue("`spec` included: $roots", roots.contains("spec"))
        assertTrue("`crypto` included: $roots", roots.contains("crypto"))

        assertFalse("compiler/ excluded: $roots", roots.contains("compiler"))
        assertFalse("crystal/ excluded: $roots", roots.contains("crystal"))
        assertFalse("lib_c/ excluded: $roots", roots.contains("lib_c"))
        assertFalse("lib_z/ excluded: $roots", roots.contains("lib_z"))
        assertFalse("llvm/ excluded: $roots", roots.contains("llvm"))
        assertFalse("ll/ excluded: $roots", roots.contains("ll"))
        assertFalse("gc/ excluded: $roots", roots.contains("gc"))
        assertFalse("samples/ excluded: $roots", roots.contains("samples"))
    }

    fun testEnumerateFallsBackToSrcDirForPre120Layout() {
        val inner = object : LightVirtualFile("src") {
            override fun isDirectory(): Boolean = true
        }
        val fakeStdlibLight = object : LightVirtualFile("fake-stdlib-root") {
            override fun isDirectory(): Boolean = true
            private val onlyChild = inner

            override fun getChildren(): Array<out VirtualFile> = arrayOf(onlyChild)
        }

        val roots = CrystalStdlibRoots.enumerate(fakeStdlibLight)
        assertEquals("Single src/ root returned: $roots", 1, roots.size)
        assertEquals("src", roots.first().name)
    }

    fun testEnumerateNonStdlibFileReturnsItself() {
        val file = object : LightVirtualFile("array.cr") {
            override fun isDirectory(): Boolean = false
        }
        val roots = CrystalStdlibRoots.enumerate(file)
        assertEquals("Non-stdlib file returned itself: $roots", 1, roots.size)
        assertSame(file, roots.first())
    }

    // ------- helpers to build fake VFS trees -------

    private fun file(name: String): LightVirtualFile =
        object : LightVirtualFile(name) {
            override fun isDirectory(): Boolean = false
        }

    private fun dir(name: String, vararg children: LightVirtualFile): LightVirtualFile {
        val childArray: Array<out VirtualFile> = children
        return object : LightVirtualFile(name) {
            override fun isDirectory(): Boolean = true
            override fun getChildren(): Array<out VirtualFile> = childArray
        }
    }
}
