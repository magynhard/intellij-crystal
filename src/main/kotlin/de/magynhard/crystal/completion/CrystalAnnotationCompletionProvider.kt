package de.magynhard.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons

/**
 * Provides annotation completions after `@[` in Crystal.
 */
object CrystalAnnotationCompletionProvider {

    data class AnnotationItem(
        val name: String,
        val description: String
    )

    private val ANNOTATIONS = listOf(
        // Built-in annotations
        AnnotationItem("Deprecated", "mark as deprecated"),
        AnnotationItem("Experimental", "mark as experimental"),
        AnnotationItem("Flags", "enum as bit flags"),
        AnnotationItem("Link", "link C library"),
        AnnotationItem("Primitive", "compiler primitive type"),

        // Serialization annotations
        AnnotationItem("JSON::Serializable", "JSON serialization"),
        AnnotationItem("JSON::Field", "JSON field configuration"),
        AnnotationItem("YAML::Serializable", "YAML serialization"),
        AnnotationItem("YAML::Field", "YAML field configuration"),

        // Other common annotations
        AnnotationItem("JSON::Serializable::Options", "JSON serialization options"),
        AnnotationItem("JSON::Serializable::Strict", "strict JSON parsing"),
        AnnotationItem("JSON::Serializable::Unmapped", "capture unmapped JSON fields"),
        AnnotationItem("YAML::Serializable::Options", "YAML serialization options"),
        AnnotationItem("YAML::Serializable::Strict", "strict YAML parsing"),
        AnnotationItem("YAML::Serializable::Unmapped", "capture unmapped YAML fields")
    )

    /**
     * Returns LookupElements for all annotations.
     */
    fun getAnnotationLookups(): List<LookupElementBuilder> {
        return ANNOTATIONS.map { annotation ->
            LookupElementBuilder.create(annotation.name)
                .withIcon(AllIcons.Nodes.Annotationtype)
                .withTypeText(annotation.description, true)
        }
    }
}
