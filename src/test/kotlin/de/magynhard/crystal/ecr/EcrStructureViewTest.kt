package de.magynhard.crystal.ecr

import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.ecr.structure.CrystalInstanceVariablesGroupElement
import de.magynhard.crystal.ecr.structure.EcrStructureViewElement
import de.magynhard.crystal.ecr.structure.EcrStructureViewModel

class EcrStructureViewTest : BasePlatformTestCase() {

    private fun configureTemplate(): TemplateLanguageStructureViewBuilder {
        myFixture.configureByText(
            "index.html.ecr",
            """
            <html>
            <body>
            <% x = 1 %>
            <h1><%=@title%></h1>
            <p><%= @user_name %></p>
            </body>
            </html>
            """.trimIndent()
        )
        // The ECR factory is registered via plugin-structureView.xml, which is only loaded
        // when the intellij.platform.structureView module is installed. The platform test
        // application does not install bundled content modules, so invoke the factory directly.
        val builder = EcrStructureViewFactory().getStructureViewBuilder(myFixture.file)
        assertNotNull("Should return a structure view builder for .ecr files", builder)
        return builder as TemplateLanguageStructureViewBuilder
    }

    fun testBuilderIsTemplateLanguageStructureViewBuilder() {
        assertTrue(configureTemplate() is TemplateLanguageStructureViewBuilder)
    }

    fun testCompositeHasEcrAndHtmlSections() {
        val view = configureTemplate().createStructureView(null, project)
        try {
            val composite = view as StructureViewComposite
            val descriptors = composite.structureViews
            assertEquals("Should contain exactly two sections", 2, descriptors.size)
            assertEquals("ECR", descriptors[0].title)
            assertEquals("HTML", descriptors[1].title)
        } finally {
            view.dispose()
        }
    }

    fun testEcrModelRootContainsTagsAndInstanceVariablesGroup() {
        configureTemplate()
        val model = EcrStructureViewModel(myFixture.file, null)
        val children = model.root.children.toList()

        val tags = children.filterIsInstance<EcrStructureViewElement>()
        assertEquals("Should contain three ECR tag elements", 3, tags.size)

        val groups = children.filterIsInstance<CrystalInstanceVariablesGroupElement>()
        assertEquals("Should contain exactly one instance variables group", 1, groups.size)

        val variableNames = groups.single().children
            .map { it.presentation.presentableText }
        assertEquals(listOf("@title", "@user_name"), variableNames)
    }

    fun testNoTagsAndNoVariablesYieldNoChildren() {
        myFixture.configureByText("plain.ecr", "just text\n")
        val model = EcrStructureViewModel(myFixture.file, null)
        assertTrue(
            "Should have no children without tags or variables",
            model.root.children.isEmpty()
        )
    }

    fun testVariablesWithoutTagsStillShowGroup() {
        myFixture.configureByText("only-vars.ecr", "<%= @solo %>")
        val model = EcrStructureViewModel(myFixture.file, null)
        val children = model.root.children.toList()
        assertTrue(children.any { it is CrystalInstanceVariablesGroupElement })
    }
}
