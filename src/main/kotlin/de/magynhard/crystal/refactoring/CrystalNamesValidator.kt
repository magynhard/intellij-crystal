package de.magynhard.crystal.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

class CrystalNamesValidator : NamesValidator {

    private val keywords = setOf(
        "abstract", "alias", "annotation", "as", "as?", "asm",
        "begin", "break", "case", "class", "def", "do",
        "else", "elsif", "end", "ensure", "enum", "extend",
        "false", "for", "fun", "if", "in", "include",
        "instance_sizeof", "is_a?", "lib", "macro", "module",
        "next", "nil", "nil?", "of", "offsetof", "out",
        "pointerof", "private", "protected", "require", "rescue",
        "responds_to?", "return", "select", "self", "sizeof",
        "struct", "super", "then", "true", "type", "typeof",
        "uninitialized", "union", "unless", "until", "verbatim",
        "when", "while", "with", "yield"
    )

    override fun isKeyword(name: String, project: Project?): Boolean = name in keywords

    override fun isIdentifier(name: String, project: Project?): Boolean {
        if (name.isEmpty()) return false
        // Crystal identifiers: start with letter/underscore, contain alphanumeric/underscore
        // Constants: start with uppercase
        // Instance variables: start with @, followed by letter/underscore
        // Class variables: start with @@, followed by letter/underscore
        var offset = 0
        if (name.startsWith("@@")) {
            offset = 2
        } else if (name.startsWith("@")) {
            offset = 1
        }
        if (offset > 0) {
            // After @/@@ prefix, must start with letter/underscore
            if (name.length <= offset) return false
            val first = name[offset]
            if (!first.isLetter() && first != '_') return false
            return name.substring(offset).all { it.isLetterOrDigit() || it == '_' || it == '?' || it == '!' }
        }
        val first = name[0]
        if (!first.isLetter() && first != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' || it == '?' || it == '!' }
    }
}
