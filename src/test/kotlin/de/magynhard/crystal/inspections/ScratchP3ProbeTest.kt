package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScratchP3ProbeTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    fun testProbe() {
        val responseCr = java.nio.file.Files.readString(java.nio.file.Path.of("/usr/lib/crystal/http/server/response.cr"))
        val contextCr = java.nio.file.Files.readString(java.nio.file.Path.of("/home/magynhard/dev/github.com/kemalcr/kemal/src/kemal/ext/context.cr"))
        val dslDef = """
            def get(path : String, &block : HTTP::Server::Context -> _)
              Kemal::RouteHandler::INSTANCE.add_route("GET", path, &block)
            end

            def error(status_code : Int32, &block)
            end

            def error(status : HTTP::Status, &block)
            end

            def error(exception : Exception.class, &block)
            end
        """.trimIndent()
        myFixture.addFileToProject("http_server_response.cr", responseCr)
        myFixture.addFileToProject("kemal_context.cr", contextCr)
        myFixture.addFileToProject("dsl.cr", dslDef)
        myFixture.configureByText("spec.cr", """
            get "/halt-status-json" do |env|
              env.status(500).json({error: "Something went wrong"})
            end

            env.status(:not_found).json({error: "User not found"})
        """.trimIndent())

        val highlights = myFixture.doHighlighting()
        val relevant = highlights.filter { it.description?.contains("argument") == true || it.description?.contains("Type mismatch") == true }
        val out = StringBuilder("findings=").append(relevant.size).append('\n')
        val fileText = myFixture.file.text
        for (h in relevant) {
            val off = h.startOffset.coerceIn(0, fileText.length)
            val lineNo = fileText.substring(0, off).count { it == '\n' } + 1
            val lineEnd = fileText.indexOf('\n', off).let { if (it < 0) fileText.length else it }
            out.append("  line ").append(lineNo).append(": '")
                .append(fileText.substring(off, lineEnd.coerceAtMost(off + 40)).trim())
                .append("' << ").append(h.description).append('\n')
        }
        java.io.File("/tmp/opencode/p3-probe2.txt").writeText(out.toString())
    }
}
