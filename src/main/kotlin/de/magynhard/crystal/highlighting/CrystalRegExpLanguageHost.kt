package de.magynhard.crystal.highlighting

import org.intellij.lang.regexp.RegExpLanguageHost
import org.intellij.lang.regexp.psi.*

/**
 * RegExp language host for Crystal's PCRE-based regex dialect.
 *
 * Crystal uses the PCRE2 library for regex. This host tells IntelliJ's built-in
 * RegExp language which features the Crystal dialect supports so the platform
 * can provide syntax highlighting, inspections, and completion for regex literals.
 *
 * @see org.intellij.lang.regexp.RegExpLanguageHost
 */
class CrystalRegExpLanguageHost : RegExpLanguageHost {

    override fun supportsPerl5EmbeddedComments(): Boolean = true
    override fun supportsPossessiveQuantifiers(element: RegExpElement): Boolean = true
    override fun supportsPythonConditionalRefs(): Boolean = false

    override fun supportsNamedGroupSyntax(group: RegExpGroup): Boolean = true
    override fun supportsNamedGroupRefSyntax(ref: RegExpNamedGroupRef): Boolean = true
    override fun supportsExtendedHexCharacter(ch: RegExpChar): Boolean = true

    override fun getSupportedNamedGroupTypes(context: RegExpElement?): java.util.EnumSet<RegExpGroup.Type> {
        return java.util.EnumSet.of(
            RegExpGroup.Type.NAMED_GROUP,
            RegExpGroup.Type.ATOMIC,
            RegExpGroup.Type.NON_CAPTURING,
            RegExpGroup.Type.POSITIVE_LOOKAHEAD,
            RegExpGroup.Type.NEGATIVE_LOOKAHEAD,
            RegExpGroup.Type.POSITIVE_LOOKBEHIND,
            RegExpGroup.Type.NEGATIVE_LOOKBEHIND
        )
    }

    override fun supportsLookbehind(group: RegExpGroup): RegExpLanguageHost.Lookbehind {
        return RegExpLanguageHost.Lookbehind.FIXED_LENGTH_ALTERNATION
    }

    override fun isValidCategory(category: String): Boolean {
        return category.length == 2 || category in KNOWN_CATEGORIES
    }

    override fun isValidPropertyName(name: String): Boolean {
        return name.isNotEmpty() && (name in KNOWN_PROPERTY_NAMES || name[0].isUpperCase())
    }

    override fun isValidPropertyValue(name: String, value: String): Boolean {
        if (name == "General_Category" || name == "gc") return isValidCategory(value)
        if (name == "Script" || name == "sc") return value.length >= 3
        return true
    }

    override fun getAllKnownProperties(): Array<Array<String>> {
        return arrayOf(
            arrayOf("General_Category", "gc"),
            arrayOf("Script", "sc"),
            arrayOf("Block", "blk"),
            arrayOf("Age"),
            arrayOf("Bidi_Class"),
            arrayOf("Bidi_Paired_Bracket_Type"),
            arrayOf("Case_Folding"),
            arrayOf("Decomposition_Type"),
            arrayOf("East_Asian_Width"),
            arrayOf("Grapheme_Cluster_Break"),
            arrayOf("Hangul_Syllable_Type"),
            arrayOf("Indic_Syllabic_Category"),
            arrayOf("Joining_Group"),
            arrayOf("Joining_Type"),
            arrayOf("Line_Break"),
            arrayOf("Numeric_Type"),
            arrayOf("Numeric_Value"),
            arrayOf("Sentence_Break"),
            arrayOf("Word_Break"),
            arrayOf("Emoji"),
            arrayOf("Emoji_Presentation"),
            arrayOf("Emoji_Modifier"),
            arrayOf("Emoji_Modifier_Base"),
            arrayOf("Extended_Pictographic"),
            arrayOf("ID_Continue"),
            arrayOf("ID_Start"),
            arrayOf("ASCII_Hex_Digit"),
            arrayOf("Alphabetic"),
            arrayOf("Bidi_Control"),
            arrayOf("Bidi_Mirrored"),
            arrayOf("Case_Ignorable"),
            arrayOf("Cased"),
            arrayOf("Changes_When_Casefolded"),
            arrayOf("Changes_When_Casemapped"),
            arrayOf("Changes_When_Lowercased"),
            arrayOf("Changes_When_NFKC_Casefolded"),
            arrayOf("Changes_When_Titlecased"),
            arrayOf("Changes_When_Uppercased"),
            arrayOf("Dash"),
            arrayOf("Default_Ignorable_Code_Point"),
            arrayOf("Deprecated"),
            arrayOf("Diacritic"),
            arrayOf("Extender"),
            arrayOf("Grapheme_Base"),
            arrayOf("Grapheme_Extend"),
            arrayOf("Grapheme_Link"),
            arrayOf("Hex_Digit"),
            arrayOf("Hyphen"),
            arrayOf("IDS_Binary_Operator"),
            arrayOf("IDS_Trinary_Operator"),
            arrayOf("Ideographic"),
            arrayOf("Join_Control"),
            arrayOf("Logical_Order_Exception"),
            arrayOf("Lowercase"),
            arrayOf("Math"),
            arrayOf("Noncharacter_Code_Point"),
            arrayOf("Pattern_Syntax"),
            arrayOf("Pattern_White_Space"),
            arrayOf("Quotation_Mark"),
            arrayOf("Radical"),
            arrayOf("Regional_Indicator"),
            arrayOf("Sentence_Terminal"),
            arrayOf("Soft_Dotted"),
            arrayOf("Terminal_Punctuation"),
            arrayOf("Unified_Ideograph"),
            arrayOf("Uppercase"),
            arrayOf("Variation_Selector"),
            arrayOf("White_Space"),
            arrayOf("XID_Continue"),
            arrayOf("XID_Start")
        )
    }

    override fun getPropertyDescription(name: String?): String? {
        val descriptions = mapOf(
            "General_Category" to "General Category",
            "Script" to "Script",
            "Block" to "Unicode Block",
            "Age" to "Age of the character",
            "Emoji" to "Emoji property",
            "ASCII_Hex_Digit" to "ASCII Hex Digit",
            "White_Space" to "White Space"
        )
        return if (name != null) descriptions.getOrDefault(name, name) else null
    }

    override fun getKnownCharacterClasses(): Array<Array<String>> {
        return arrayOf(
            arrayOf("d", "digit [0-9]"),
            arrayOf("D", "non-digit [^0-9]"),
            arrayOf("w", "word character [a-zA-Z0-9_]"),
            arrayOf("W", "non-word character"),
            arrayOf("s", "whitespace [ \\t\\r\\n\\f]"),
            arrayOf("S", "non-whitespace"),
            arrayOf("h", "horizontal whitespace"),
            arrayOf("H", "non-horizontal whitespace"),
            arrayOf("v", "vertical whitespace"),
            arrayOf("V", "non-vertical whitespace"),
            arrayOf("R", "line break"),
            arrayOf("N", "non-line break"),
            arrayOf("K", "reset match start"),
            arrayOf("X", "extended Unicode grapheme cluster"),
            arrayOf("b", "word boundary"),
            arrayOf("B", "non-word boundary"),
            arrayOf("A", "start of string"),
            arrayOf("Z", "end of string, before final newline"),
            arrayOf("z", "absolute end of string"),
            arrayOf("G", "first matching position in subject")
        )
    }

    companion object {
        private val KNOWN_CATEGORIES = setOf(
            "L", "Lu", "Ll", "Lt", "Lm", "Lo",
            "M", "Mn", "Mc", "Me",
            "N", "Nd", "Nl", "No",
            "P", "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po",
            "S", "Sm", "Sc", "Sk", "So",
            "Z", "Zs", "Zl", "Zp",
            "C", "Cc", "Cf", "Cs", "Co", "Cn"
        )

        private val KNOWN_PROPERTY_NAMES = setOf(
            "General_Category", "Script", "Block",
            "Alpha", "ASCII", "Assigned", "Upper", "Lower",
            "Alnum", "XPosixPrint", "XPosixGraph", "XPosixPunct",
            "Any", "Cc", "Cf", "Cn", "Co", "Cs", "Ll", "Lm", "Lo", "Lt", "Lu",
            "Mc", "Me", "Mn", "Nd", "Nl", "No", "Pc", "Pd", "Pe", "Pf", "Pi",
            "Po", "Ps", "Sc", "Sk", "Sm", "So", "Zl", "Zp", "Zs",
            "Arabic", "Armenian", "Balinese", "Bengali", "Bopomofo", "Braille",
            "Buginese", "Buhid", "Canadian_Aboriginal", "Cherokee", "Common",
            "Coptic", "Cuneiform", "Cypriot", "Cyrillic", "Deseret", "Devanagari",
            "Ethiopic", "Georgian", "Glagolitic", "Gothic", "Greek", "Gujarati",
            "Gurmukhi", "Han", "Hangul", "Hanunoo", "Hebrew", "Hiragana",
            "Inherited", "Kannada", "Katakana", "Kharoshthi", "Khmer", "Lao",
            "Latin", "Limbu", "Linear_B", "Malayalam", "Mongolian", "Myanmar",
            "New_Tai_Lue", "Nko", "Ogham", "Old_Italic", "Old_Persian", "Oriya",
            "Osmanya", "Phags_Pa", "Phoenician", "Runic", "Shavian", "Sinhala",
            "Syloti_Nagri", "Syriac", "Tagalog", "Tagbanwa", "Tai_Le", "Tamil",
            "Telugu", "Thaana", "Thai", "Tibetan", "Tifinagh", "Ugaritic",
            "Yi", "CJK", "Hira", "Kana"
        )
    }
}
