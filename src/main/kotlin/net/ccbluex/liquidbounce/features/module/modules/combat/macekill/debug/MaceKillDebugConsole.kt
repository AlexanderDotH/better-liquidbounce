/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

/** Debug-gated, lazily formatted MaceKill lifecycle diagnostics. */
internal class MaceKillDebugConsole(
    private val enabled: () -> Boolean,
    private val sink: (String) -> Unit,
) {
    private val transitionFingerprints = mutableMapOf<String, Any?>()

    fun log(event: String, fields: () -> List<Pair<String, Any?>>) {
        if (!enabled()) return
        sink(format(event, fields()))
    }

    fun logChanged(
        channel: String,
        event: String,
        fingerprint: () -> Any?,
        fields: () -> List<Pair<String, Any?>>,
    ) {
        if (!enabled()) {
            clearTransitions()
            return
        }
        val next = fingerprint()
        if (transitionFingerprints.containsKey(channel) && transitionFingerprints[channel] == next) return
        transitionFingerprints[channel] = next
        sink(format(event, fields()))
    }

    fun clearTransitions() {
        transitionFingerprints.clear()
    }

    fun clearTransition(channel: String) {
        transitionFingerprints.remove(channel)
    }

    private fun format(event: String, fields: List<Pair<String, Any?>>): String = buildString {
        append("[MaceKill][")
        append(event.sanitize(uppercase = true))
        append(']')
        fields.forEach { (name, value) ->
            append(' ')
            append(name.sanitize(uppercase = false))
            append('=')
            append(value.formatValue())
        }
    }

    private fun String.sanitize(uppercase: Boolean): String {
        val safe = map { character ->
            if (character.isLetterOrDigit() || character == '_' || character == '-') character else '_'
        }.joinToString("").ifBlank { "unknown" }
        return if (uppercase) safe.uppercase() else safe.lowercase()
    }

    private fun Any?.formatValue(): String = when (this) {
        null -> "null"
        is Number, is Boolean -> toString()
        is Enum<*> -> name
        else -> "\"${toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}
