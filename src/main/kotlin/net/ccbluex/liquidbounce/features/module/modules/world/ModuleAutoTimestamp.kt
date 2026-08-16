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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.autotimestamp.AutoTimestampLines
import net.ccbluex.liquidbounce.injection.mixins.minecraft.gui.MixinAbstractSignEditScreenAccessor
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import java.time.LocalDate

/**
 * Adds a dated player signature to the two empty closing lines of an edited sign.
 */
object ModuleAutoTimestamp : ClientModule("AutoTimestamp", ModuleCategories.WORLD) {

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent> { event ->
        val screen = event.screen as? AbstractSignEditScreen ?: return@handler
        val accessor = screen as MixinAbstractSignEditScreenAccessor
        val maximumLineWidth = accessor.sign.maxTextLineWidth
        val playerName = player.gameProfile.name ?: mc.user.name

        AutoTimestampLines.append(
            lines = accessor.messages,
            date = LocalDate.now(),
            playerName = playerName,
            fitLine = { mc.font.plainSubstrByWidth(it, maximumLineWidth) },
        )
    }
}
