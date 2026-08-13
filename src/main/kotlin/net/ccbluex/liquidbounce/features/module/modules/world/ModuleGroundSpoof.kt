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

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories

/**
 * Reports the player as grounded while breaking blocks in midair.
 */
object ModuleGroundSpoof : ClientModule("GroundSpoof", ModuleCategories.WORLD) {

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        applyGroundSpoof(event, player.onGround(), interaction.isDestroying)
    }
}

internal fun applyGroundSpoof(
    event: PlayerNetworkMovementTickEvent,
    playerOnGround: Boolean,
    breakingBlock: Boolean,
) {
    if (event.state != EventState.PRE || playerOnGround || !breakingBlock) {
        return
    }

    event.ground = true
}
