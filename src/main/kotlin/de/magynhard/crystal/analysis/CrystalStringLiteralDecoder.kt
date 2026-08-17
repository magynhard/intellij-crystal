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
                '\n' -> index = skipContinuationWhitespace(content, index) ?: return null
                '\r' -> {
                    if (index >= content.length || content[index] != '\n') return null
                    index = skipContinuationWhitespace(content, index + 1) ?: return null
                }
                'x' -> {
                    if (index + 2 > content.length) return null
                    val hex = content.substring(index, index + 2)
                    if (hex.any { !it.isHexDigit() }) return null
                    result.append(hex.toInt(16).toChar())
                    index += 2
                }
                'u' -> {
                    if (index < content.length && content[index] == '{') {
                        index++
                        while (true) {
                            val start = index
                            while (index < content.length && index - start < 6 && content[index].isHexDigit()) index++
                            if (index == start) return null
                            val codepoint = content.substring(start, index).toInt(16)
                                .takeIf(::isValidCodepoint) ?: return null
                            result.appendCodePoint(codepoint)
                            if (index >= content.length) return null
                            when (content[index++]) {
                                '}' -> break
                                ' ' -> if (index - start - 1 == 6) return null
                                else -> return null
                            }
                        }
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
                    while (index < content.length && index - start < 4 && content[index] in '0'..'7') index++
                    val value = content.substring(start, index).toInt(8)
                    if (value >= 256) return null
                    result.append(value.toChar())
                }
                else -> result.append(escaped)
            }
        }
        return result.toString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun skipContinuationWhitespace(content: String, start: Int): Int? {
        var index = start
        while (index < content.length && content[index].isAsciiWhitespace()) index++
        return index.takeIf { it < content.length }
    }

    private fun Char.isAsciiWhitespace(): Boolean = this == ' ' || this in '\t'..'\r'

    private fun isValidCodepoint(value: Int): Boolean = value in 0..0x10ffff && value !in 0xd800..0xdfff
}
