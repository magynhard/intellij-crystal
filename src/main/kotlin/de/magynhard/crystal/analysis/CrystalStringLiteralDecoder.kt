package de.magynhard.crystal.analysis

internal object CrystalStringLiteralDecoder {
    fun decode(content: String): String? {
        val result = StringBuilder(content.length)
        var index = 0
        while (index < content.length) {
            val character = content[index++]
            if (character != '\\') {
                result.append(character)
                continue
            }
            if (index >= content.length) return null
            when (val escaped = content[index++]) {
                'a' -> result.append('\u0007')
                'b' -> result.append('\b')
                'e' -> result.append('\u001b')
                'f' -> result.append('\u000c')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'v' -> result.append('\u000b')
                '\n' -> Unit
                '\r' -> if (index < content.length && content[index] == '\n') index++ else return null
                'x' -> {
                    val start = index
                    while (index < content.length && index - start < 2 && content[index].isHexDigit()) index++
                    if (index == start) return null
                    result.append(content.substring(start, index).toInt(16).toChar())
                }
                'u' -> {
                    if (index < content.length && content[index] == '{') {
                        index++
                        val end = content.indexOf('}', index)
                        if (end < 0) return null
                        val codepoints = content.substring(index, end).trim().split(Regex("\\s+"))
                        if (codepoints.size == 1 && codepoints.single().isEmpty()) return null
                        for (hex in codepoints) {
                            if (hex.isEmpty() || hex.any { !it.isHexDigit() }) return null
                            val codepoint = hex.toIntOrNull(16)?.takeIf(::isValidCodepoint) ?: return null
                            result.appendCodePoint(codepoint)
                        }
                        index = end + 1
                    } else {
                        if (index + 4 > content.length) return null
                        val hex = content.substring(index, index + 4)
                        if (hex.any { !it.isHexDigit() }) return null
                        val codepoint = hex.toInt(16).takeIf(::isValidCodepoint) ?: return null
                        result.appendCodePoint(codepoint)
                        index += 4
                    }
                }
                in '0'..'7' -> {
                    val start = index - 1
                    while (index < content.length && index - start < 3 && content[index] in '0'..'7') index++
                    result.append(content.substring(start, index).toInt(8).toChar())
                }
                else -> result.append(escaped)
            }
        }
        return result.toString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun isValidCodepoint(value: Int): Boolean = value in 0..0x10ffff && value !in 0xd800..0xdfff
}
