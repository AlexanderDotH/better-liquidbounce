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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlyVanillaTest {

    @Test
    fun `vanilla check bypass runs every forty ticks only when enabled`() {
        assertFalse(shouldRunVanillaFlyCheckBypass(enabled = false, tickCount = 40))
        assertFalse(shouldRunVanillaFlyCheckBypass(enabled = true, tickCount = 39))
        assertTrue(shouldRunVanillaFlyCheckBypass(enabled = true, tickCount = 40))
    }

    @Test
    fun `vanilla check bypass packet dips only the previous player Y`() {
        val packet = ServerboundMovePlayerPacket.Pos(12.5, 90.0, -4.25, true, true)

        applyVanillaFlyCheckBypass(packet, previousY = 64.0)

        assertEquals(12.5, packet.x)
        assertEquals(63.96, packet.y, 1e-9)
        assertEquals(-4.25, packet.z)
        assertTrue(packet.isOnGround)
        assertTrue(packet.horizontalCollision())
    }
}
