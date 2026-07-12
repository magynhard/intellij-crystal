package de.magynhard.crystal.ecr

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EmbeddedCrystalHtmlPsiTest : BasePlatformTestCase() {
    
    fun testHtmlPsiFileIsCreated() {
        println("DEBUG: Starting testHtmlPsiFileIsCreated")
        
        val file = myFixture.configureByText(
            "test.html.ecr",
            """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test</title>
            </head>
            <body>
                <div class="container">
                    <h1>Welcome</h1>
                    <% if @show_message %>
                        <p>Hello, <%= @user_name %>!</p>
                    <% end %>
                </div>
            </body>
            </html>
            """.trimIndent()
        )
        
        println("DEBUG: File type: ${file.fileType.name}")
        println("DEBUG: File class: ${file.javaClass.name}")
        
        val viewProvider = file.viewProvider
        println("DEBUG: ViewProvider class: ${viewProvider.javaClass.name}")
        println("DEBUG: ViewProvider languages: ${viewProvider.languages}")
        
        // Manually create the view provider using the factory
        println("DEBUG: Manually creating view provider using factory")
        val factory = EmbeddedCrystalFileViewProviderFactory()
        val manualProvider = factory.createFileViewProvider(
            file.virtualFile,
            EmbeddedCrystalLanguage,
            file.manager,
            true
        )
        println("DEBUG: Manual provider class: ${manualProvider.javaClass.name}")
        println("DEBUG: Manual provider languages: ${manualProvider.languages}")
        
        val htmlFile = manualProvider.getPsi(HTMLLanguage.INSTANCE)
        println("DEBUG: HTML file from manual provider: ${htmlFile?.javaClass?.name}")
        
        if (htmlFile != null) {
            println("DEBUG: HTML file text length: ${htmlFile.textLength}")
            println("DEBUG: HTML file children count: ${htmlFile.children.size}")
            
            htmlFile.children.forEach { child ->
                println("DEBUG: HTML child: ${child.javaClass.simpleName} - ${child.text.take(50)}")
            }
        } else {
            println("DEBUG: HTML file is NULL!")
        }
        
        assertNotNull("HTML PSI file should be created", htmlFile)
    }
    
    fun testEcrPsiFileIsCreated() {
        println("DEBUG: Starting testEcrPsiFileIsCreated")
        
        val file = myFixture.configureByText(
            "test.html.ecr",
            """
            <div>
                <% if @show %>
                    <p>Content</p>
                <% end %>
            </div>
            """.trimIndent()
        )
        
        val viewProvider = file.viewProvider
        val ecrFile = viewProvider.getPsi(EmbeddedCrystalLanguage)
        
        println("DEBUG: ECR file: ${ecrFile?.javaClass?.name}")
        println("DEBUG: ECR file children count: ${ecrFile?.children?.size}")
        
        ecrFile?.children?.forEach { child ->
            println("DEBUG: ECR child: ${child.javaClass.simpleName}")
        }
        
        assertNotNull("ECR PSI file should be created", ecrFile)
    }
}
