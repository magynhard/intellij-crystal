package de.magynhard.crystal.injection

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalHeredocBodies
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Marker-to-language mapping for embedded-language highlighting in heredocs
 * (`a = <<-SQL … SQL`), following RubyMine's convention that the heredoc
 * marker is (an alias of) a language ID.
 *
 * Two resolution layers:
 * 1. The curated [ALIASES] table maps common markers to candidate language
 *    IDs (e.g. `JAVASCRIPT`/`JS` → `JavaScript`, `CR`/`CRYSTAL` → Crystal).
 * 2. A generic fallback matches the marker case-insensitively against every
 *    registered language ID (e.g. `<<-KOTLIN` in IDEA, `<<-PYTHON` in
 *    PyCharm) — mirroring RubyMine's "language IDs are generally intuitive".
 *
 * Languages that are not installed resolve to null and the heredoc keeps its
 * ordinary string highlighting (no injection, no guesswork).
 */
object CrystalHeredocInjection {

    private val MARKER_REGEX = Regex("""<<-('?)((?:[A-Za-z_])(?:[A-Za-z0-9_])*)\1""")

    /** Uppercase marker alias → candidate language IDs, tried case-sensitively in order. */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "SQL" to listOf("SQL"),
        "JS" to listOf("JavaScript"),
        "JAVASCRIPT" to listOf("JavaScript"),
        "ECMASCRIPT" to listOf("JavaScript"),
        "TS" to listOf("TypeScript"),
        "TYPESCRIPT" to listOf("TypeScript"),
        "CSS" to listOf("CSS"),
        "HTML" to listOf("HTML"),
        "XML" to listOf("XML"),
        "JSON" to listOf("JSON"),
        "YAML" to listOf("yaml"),
        "YML" to listOf("yaml"),
        "MD" to listOf("Markdown"),
        "MARKDOWN" to listOf("Markdown"),
        "SH" to listOf("Shell Script"),
        "SHELL" to listOf("Shell Script"),
        "BASH" to listOf("Shell Script"),
        "REGEX" to listOf("RegExp"),
        "REGEXP" to listOf("RegExp"),
        "CR" to listOf("Crystal"),
        "CRYSTAL" to listOf("Crystal"),
        "RUBY" to listOf("ruby", "Ruby"),
        "HAML" to listOf("Haml", "HAML"),
    )

    /**
     * Synthetic prefix inserted before a fragment place that follows an
     * interpolation gap, keyed by language ID, so the injected document stays
     * syntactically valid where the interpolation was cut out.
     */
    private val PLACEHOLDERS: Map<String, String> = mapOf(
        "SQL" to "NULL",
        "JavaScript" to "null",
        "TypeScript" to "null",
        "JSON" to "0",
        "CSS" to "0",
        "HTML" to "<!-- -->",
        "XML" to "<!-- -->",
        "yaml" to "null",
    )

    /**
     * Extracts the marker identifier from a header [CrystalTypes.HEREDOC_START]
     * token text (`<<-SQL`, `<<-'SQL'`). Returns null for malformed headers.
     */
    fun extractMarker(startTokenText: String): String? =
        MARKER_REGEX.find(startTokenText)?.groupValues?.get(2)

    /**
     * Resolves the marker that owns this body literal.
     *
     * The body literal's own `HEREDOC_START` token is the newline body opener
     * (the lexer queues bodies that open at the next newline); the actual
     * `<<-MARKER` header token lives in the owning statement/call expression.
     * Headers and bodies are paired by document order (one body per marker).
     */
    fun resolveBodyMarker(literal: CrystalHeredocLiteral): String? {
        val bodies = literal.parent as? CrystalHeredocBodies ?: return null
        val owner = bodies.parent ?: return null
        val headers = collectHeaderStartTokens(owner)
        val allLiterals = PsiTreeUtil.findChildrenOfType(owner, CrystalHeredocLiteral::class.java)
            .sortedBy { it.textOffset }
        val index = allLiterals.indexOf(literal)
        if (index < 0 || index >= headers.size) return null
        return extractMarker(headers[index].text)
    }

    /**
     * Header `HEREDOC_START` tokens under [owner] in document order — tokens
     * whose text starts with `<<-`. Body openers (newline-only `HEREDOC_START`
     * tokens) live inside `heredoc_bodies` subtrees and are skipped.
     */
    private fun collectHeaderStartTokens(owner: PsiElement): List<com.intellij.lang.ASTNode> {
        val headers = mutableListOf<com.intellij.lang.ASTNode>()
        fun walk(node: com.intellij.lang.ASTNode) {
            for (child in node.getChildren(null)) {
                when (child.elementType) {
                    CrystalTypes.HEREDOC_START ->
                        if (child.text.startsWith("<<-")) headers.add(child)
                    CrystalTypes.HEREDOC_BODIES -> {} // bodies subtree — body openers only
                    else -> walk(child)
                }
            }
        }
        walk(owner.node)
        return headers
    }

    /**
     * Resolves the marker to an installed [Language], or null when the
     * language is unknown or not installed.
     */
    fun resolveLanguage(marker: String): Language? {
        for (id in ALIASES[marker.uppercase()].orEmpty()) {
            Language.findLanguageByID(id)?.let { return it }
        }
        val matches = Language.getRegisteredLanguages()
            .filter { it.id.isNotBlank() && it.id.equals(marker, ignoreCase = true) }
        return matches.firstOrNull { it.id == marker }
            ?: matches.minByOrNull { it.id }
    }

    /** True when this heredoc's marker resolves to an installed language. */
    fun willInject(literal: CrystalHeredocLiteral): Boolean {
        val marker = resolveBodyMarker(literal) ?: return false
        return resolveLanguage(marker) != null
    }

    /** Synthetic prefix for a place that follows an interpolation gap. */
    fun placeholderFor(language: Language): String = PLACEHOLDERS[language.id] ?: ""

    /**
     * Injectable content segments of the heredoc body, relative to the literal.
     * Consecutive [CrystalTypes.HEREDOC_CONTENT]/[CrystalTypes.STRING_ESCAPE]
     * leaves form one segment; [CrystalTypes.INTERPOLATION_EXPRESSION] children
     * (and any unexpected node) terminate a segment. Empty segments are omitted.
     * [Segment.gapsBefore] counts the interpolation gaps that precede the
     * segment (0 for a segment at the very start of the body).
     */
    data class Segment(val range: TextRange, val gapsBefore: Int)

    fun computeBodySegments(literal: CrystalHeredocLiteral): List<Segment> {
        val base = literal.textRange.startOffset
        val segments = mutableListOf<Segment>()
        var segmentStart = -1
        var segmentEnd = -1
        var gaps = 0
        var gapsBefore = 0

        fun closeSegment() {
            if (segmentStart >= 0 && segmentEnd > segmentStart) {
                segments.add(Segment(TextRange(segmentStart, segmentEnd), gapsBefore))
            }
            segmentStart = -1
            segmentEnd = -1
        }

        for (child in literal.node.getChildren(null)) {
            val elementType = child.elementType
            if (elementType == CrystalTypes.HEREDOC_START) continue
            if (elementType == CrystalTypes.HEREDOC_END) break
            val isContent = elementType == CrystalTypes.HEREDOC_CONTENT ||
                elementType == CrystalTypes.STRING_ESCAPE
            if (isContent) {
                if (segmentStart < 0) {
                    segmentStart = child.startOffset - base
                    gapsBefore = gaps
                }
                segmentEnd = child.startOffset - base + child.textLength
            } else {
                // interpolation_expression is a private (inlined) rule: an
                // interpolation appears as flattened STRING_INTERPOLATION_BEGIN /
                // expression nodes / STRING_INTERPOLATION_END children.
                if (elementType == CrystalTypes.STRING_INTERPOLATION_BEGIN) gaps++
                closeSegment()
            }
        }
        closeSegment()
        return segments
    }
}
