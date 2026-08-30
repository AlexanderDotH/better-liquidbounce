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
package net.ccbluex.liquidbounce.common.interop

import net.minecraft.world.scores.PlayerScoreEntry
import java.util.Comparator

object ScoreboardEntryOrder {

    fun interface Provider {
        fun comparator(): Comparator<PlayerScoreEntry>
    }

    @Volatile
    private var provider: Provider? = null

    @JvmStatic
    fun install(provider: Provider) {
        this.provider = provider
    }

    fun comparator(): Comparator<PlayerScoreEntry> = checkNotNull(provider) {
        "HUD scoreboard entry order has not been installed"
    }.comparator()

    internal fun resetForTests() {
        provider = null
    }
}
