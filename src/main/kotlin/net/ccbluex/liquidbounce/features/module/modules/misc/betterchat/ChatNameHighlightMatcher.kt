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
package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.ccbluex.liquidbounce.utils.math.levenshtein
import java.util.Locale
import kotlin.math.abs

object ChatNameHighlightMatcher {

    private const val MAX_DISTANCE = 1
    private val usernameToken = Regex("[A-Za-z0-9_]+")

    @JvmStatic
    fun containsMention(message: String, playerName: String): Boolean {
        if (playerName.isBlank()) {
            return false
        }

        val normalizedPlayerName = playerName.lowercase(Locale.ROOT)
        return usernameToken.findAll(message).any { match ->
            val candidate = match.value.lowercase(Locale.ROOT)
            abs(candidate.length - normalizedPlayerName.length) <= MAX_DISTANCE &&
                levenshtein(candidate, normalizedPlayerName) <= MAX_DISTANCE
        }
    }
}
