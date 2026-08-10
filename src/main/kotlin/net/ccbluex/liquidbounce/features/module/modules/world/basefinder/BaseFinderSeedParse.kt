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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.minecraft.world.level.levelgen.WorldOptions

/** Parses a Minecraft world-seed string the same way vanilla world creation does. */
internal object BaseFinderSeedParse {

    fun parseOrNull(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parsed = WorldOptions.parseSeed(trimmed)
        return if (parsed.isPresent) parsed.asLong else null
    }

    fun isConfigured(raw: String): Boolean = parseOrNull(raw) != null
}
