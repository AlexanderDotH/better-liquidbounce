/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

internal data class KotlinFunction(
    val name: String,
    val startOffset: Int,
    val endOffset: Int,
    val bodyStartOffset: Int,
    val bodyEndOffset: Int,
)

internal object KotlinFunctionParser {
    private val FUN = Regex("""\bfun\b""")
    private val IDENTIFIER = Regex("""`[^`]+`|[A-Za-z_][A-Za-z0-9_]*""")

    fun parse(masked: String): List<KotlinFunction> = FUN.findAll(masked)
        .filterNot { match -> masked.isEscapedIdentifier(match.range.first, match.range.last + 1) }
        .mapNotNull { match -> parseFunction(masked, match.range.first, match.range.last + 1) }
        .toList()

    private fun String.isEscapedIdentifier(start: Int, end: Int) =
        getOrNull(start - 1) == '`' && getOrNull(end) == '`'

    private fun parseFunction(masked: String, start: Int, headerStart: Int): KotlinFunction? {
        val marker = findBodyMarker(masked, headerStart) ?: return null
        val header = masked.substring(headerStart, marker.offset)
        if (header.trimStart().startsWith("interface ")) return null
        val name = functionName(header) ?: return null
        val end = if (marker.character == '{') matchingBraceEnd(masked, marker.offset) else expressionEnd(masked, marker.offset + 1)
        if (end <= marker.offset) return null
        val bodyStart = marker.offset + 1
        val bodyEnd = if (marker.character == '{') end - 1 else end
        return KotlinFunction(name, start, end, bodyStart, bodyEnd)
    }

    private fun functionName(header: String): String? {
        val parameterStart = header.indexOf('(').takeIf { it >= 0 } ?: return null
        return IDENTIFIER.findAll(header.substring(0, parameterStart)).lastOrNull()?.value?.trim('`')
    }

    private fun findBodyMarker(masked: String, start: Int): BodyMarker? {
        val state = HeaderScanState(start)
        for (index in start until masked.length) {
            if (state.atRoot && masked[index] == '}') return null
            if (state.atRoot && masked.isFunctionKeyword(index)) return null
            when (val result = state.inspect(masked[index], index)) {
                HeaderScanResult.Continue -> Unit
                HeaderScanResult.Stop -> return null
                is HeaderScanResult.Marker -> return result.marker
            }
        }
        return null
    }

    private fun String.isFunctionKeyword(index: Int): Boolean {
        if (!startsWith("fun", index)) return false
        val before = getOrNull(index - 1)
        val after = getOrNull(index + 3)
        val escaped = before == '`' && after == '`'
        return !escaped && before?.isIdentifierPart() != true && after?.isIdentifierPart() != true
    }

    private fun Char.isIdentifierPart() = isLetterOrDigit() || this == '_'

    private fun matchingBraceEnd(masked: String, opening: Int): Int {
        var depth = 0
        for (index in opening until masked.length) {
            if (masked[index] == '{') depth++
            if (masked[index] == '}') depth--
            if (depth == 0) return index + 1
        }
        return masked.length
    }

    private fun expressionEnd(masked: String, start: Int): Int {
        var parentheses = 0
        var brackets = 0
        var braces = 0
        for (index in start until masked.length) {
            when (masked[index]) {
                '(' -> parentheses++
                ')' -> parentheses--
                '[' -> brackets++
                ']' -> brackets--
                '{' -> braces++
                '}' -> if (braces == 0) return index else braces--
                '\n', ';' -> if (parentheses == 0 && brackets == 0 && braces == 0) return index
            }
        }
        return masked.length
    }

    private data class BodyMarker(val offset: Int, val character: Char)

    private class HeaderScanState(private val start: Int) {
        private var parentheses = 0
        private var brackets = 0

        val atRoot: Boolean
            get() = parentheses == 0 && brackets == 0

        fun inspect(character: Char, index: Int): HeaderScanResult {
            updateDepth(character)
            if (parentheses < 0 || brackets < 0) return HeaderScanResult.Stop
            if ((character == '{' || character == '=') && atRoot) {
                return HeaderScanResult.Marker(BodyMarker(index, character))
            }
            if (character == ';' && atRoot) return HeaderScanResult.Stop
            if (index - start > MAX_HEADER_LENGTH) return HeaderScanResult.Stop
            return HeaderScanResult.Continue
        }

        private fun updateDepth(character: Char) {
            when (character) {
                '(' -> parentheses++
                ')' -> parentheses--
                '[' -> brackets++
                ']' -> brackets--
            }
        }
    }

    private sealed interface HeaderScanResult {
        data object Continue : HeaderScanResult
        data object Stop : HeaderScanResult
        data class Marker(val marker: BodyMarker) : HeaderScanResult
    }

    private const val MAX_HEADER_LENGTH = 4_096
}

internal object KotlinSourceMask {
    fun mask(source: String): String {
        val output = source.toCharArray()
        var index = 0
        while (index < source.length) {
            index = when {
                source.startsWith("//", index) -> maskLine(source, output, index)
                source.startsWith("/*", index) -> maskBlockComment(source, output, index)
                source.startsWith("\"\"\"", index) -> maskDelimited(source, output, index, "\"\"\"", escaped = false)
                source[index] == '"' -> maskDelimited(source, output, index, "\"", escaped = true)
                source[index] == '\'' -> maskDelimited(source, output, index, "'", escaped = true)
                else -> index + 1
            }
        }
        return output.concatToString()
    }

    private fun maskLine(source: String, output: CharArray, start: Int): Int {
        var index = start
        while (index < source.length && source[index] != '\n') output[index++] = ' '
        return index
    }

    private fun maskBlockComment(source: String, output: CharArray, start: Int): Int {
        var index = start
        var depth = 0
        while (index < source.length) {
            if (source.startsWith("/*", index)) depth++
            if (source.startsWith("*/", index)) {
                maskCharacter(output, source, index++)
                maskCharacter(output, source, index++)
                depth--
                if (depth == 0) return index
                continue
            }
            maskCharacter(output, source, index++)
        }
        return index
    }

    private fun maskDelimited(
        source: String,
        output: CharArray,
        start: Int,
        delimiter: String,
        escaped: Boolean,
    ): Int {
        var index = start
        repeat(delimiter.length) { maskCharacter(output, source, index++) }
        while (index < source.length) {
            if (source.startsWith(delimiter, index) && (!escaped || !source.isEscaped(index))) {
                repeat(delimiter.length) { maskCharacter(output, source, index++) }
                return index
            }
            maskCharacter(output, source, index++)
        }
        return index
    }

    private fun String.isEscaped(offset: Int): Boolean {
        var slashes = 0
        var index = offset - 1
        while (index >= 0 && this[index--] == '\\') slashes++
        return slashes % 2 == 1
    }

    private fun maskCharacter(output: CharArray, source: String, index: Int) {
        if (source[index] != '\n') output[index] = ' '
    }
}
