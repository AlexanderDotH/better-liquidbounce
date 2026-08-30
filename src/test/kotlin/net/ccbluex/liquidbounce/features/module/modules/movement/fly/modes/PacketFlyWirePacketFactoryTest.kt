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
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PacketFlyWirePacketFactoryTest {

    @Test
    fun `position rotation priming preserves the complete player packet state`() {
        val packet = createPacketFlyAuxiliaryPacket(
            PacketFlyAuxiliaryPacketPlan.Priming(
                shape = PacketFlyPrimingPacketShape.PositionRotation,
                position = Vec3(3.0, 70.0, -5.0),
            ),
            PacketFlyPlayerPacketState(
                yaw = 87.5F,
                pitch = -31.25F,
                onGround = true,
                horizontalCollision = false,
            ),
        )

        assertIs<ServerboundMovePlayerPacket.PosRot>(packet)
        assertEquals(3.0, packet.x)
        assertEquals(70.0, packet.y)
        assertEquals(-5.0, packet.z)
        assertEquals(87.5F, packet.yRot)
        assertEquals(-31.25F, packet.xRot)
        assertTrue(packet.isOnGround)
        assertFalse(packet.horizontalCollision())
    }
}
