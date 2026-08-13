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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleGroundSpoofTest {

    @Test
    fun `airborne block breaking reports the player on ground`() {
        val event = movementEvent(EventState.PRE, ground = false)

        applyGroundSpoof(event, playerOnGround = false, breakingBlock = true)

        assertTrue(event.ground)
    }

    @Test
    fun `airborne movement remains airborne when no block is being broken`() {
        val event = movementEvent(EventState.PRE, ground = false)

        applyGroundSpoof(event, playerOnGround = false, breakingBlock = false)

        assertFalse(event.ground)
    }

    @Test
    fun `grounded block breaking keeps the original grounded state`() {
        val event = movementEvent(EventState.PRE, ground = true)

        applyGroundSpoof(event, playerOnGround = true, breakingBlock = true)

        assertTrue(event.ground)
    }

    @Test
    fun `post movement event is not rewritten`() {
        val event = movementEvent(EventState.POST, ground = false)

        applyGroundSpoof(event, playerOnGround = false, breakingBlock = true)

        assertFalse(event.ground)
    }

    private fun movementEvent(state: EventState, ground: Boolean) =
        PlayerNetworkMovementTickEvent(state, 0.0, 64.0, 0.0, ground)
}
