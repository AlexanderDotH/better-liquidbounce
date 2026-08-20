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

package net.ccbluex.liquidbounce.features.module.modules.combat

/**
 * Lazy, one-line diagnostics for SpearKill's runtime state.
 *
 * Both the transition fingerprint and its fields are deliberately supplied as lambdas. This keeps
 * entity inspection, vector formatting, and message allocation out of the normal runtime while
 * Debug is disabled.
 */
internal class SpearKillDebugConsole(
    private val enabled: () -> Boolean,
    private val sink: (String) -> Unit,
) {

    private val transitionFingerprints = mutableMapOf<String, Any?>()
    private var wasEnabled = false

    fun log(
        event: String,
        fields: () -> List<Pair<String, Any?>>,
    ) {
        if (!isLoggingEnabled()) return
        sink(format(event, fields()))
    }

    fun logChanged(
        channel: String,
        event: String,
        fingerprint: () -> Any?,
        fields: () -> List<Pair<String, Any?>>,
    ) {
        if (!isLoggingEnabled()) return

        val nextFingerprint = fingerprint()
        if (transitionFingerprints.containsKey(channel) &&
            transitionFingerprints[channel] == nextFingerprint
        ) {
            return
        }
        transitionFingerprints[channel] = nextFingerprint
        sink(format(event, fields()))
    }

    fun clearTransitions() {
        transitionFingerprints.clear()
    }

    private fun isLoggingEnabled(): Boolean {
        if (!enabled()) {
            clearTransitions()
            wasEnabled = false
            return false
        }
        if (!wasEnabled) {
            clearTransitions()
            wasEnabled = true
        }
        return true
    }

    private fun format(event: String, fields: List<Pair<String, Any?>>): String = buildString {
        append(PREFIX)
        append('[')
        append(sanitizeIdentifier(event, uppercase = true))
        append(']')
        fields.forEach { (name, value) ->
            append(' ')
            append(sanitizeIdentifier(name, uppercase = false))
            append('=')
            append(formatValue(value))
        }
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is Enum<*> -> value.name
        else -> "\"${escapeText(value.toString())}\""
    }

    private fun sanitizeIdentifier(value: String, uppercase: Boolean): String {
        val normalized = buildString(value.length) {
            value.forEach { character ->
                append(if (character.isLetterOrDigit() || character == '_' || character == '-') {
                    character
                } else {
                    '_'
                })
            }
        }.ifBlank { "unknown" }
        return if (uppercase) normalized.uppercase() else normalized.lowercase()
    }

    private fun escapeText(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (!character.isISOControl()) append(character)
            }
        }
    }

    private companion object {
        const val PREFIX = "[SpearKill]"
    }
}
