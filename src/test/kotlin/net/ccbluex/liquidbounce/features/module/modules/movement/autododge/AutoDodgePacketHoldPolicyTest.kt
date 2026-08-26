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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDodgePacketHoldPolicyTest {

    @Test
    fun `active hold suppresses every outbound movement packet only`() {
        val movement = ServerboundMovePlayerPacket.StatusOnly(true, false)

        assertTrue(shouldSuppressAutoDodgePacketHoldMovement(
            holdActive = true,
            event = PacketEvent(TransferOrigin.OUTGOING, movement, original = true),
        ))
        assertFalse(shouldSuppressAutoDodgePacketHoldMovement(
            holdActive = false,
            event = PacketEvent(TransferOrigin.OUTGOING, movement, original = true),
        ))
        assertFalse(shouldSuppressAutoDodgePacketHoldMovement(
            holdActive = true,
            event = PacketEvent(TransferOrigin.INCOMING, movement, original = true),
        ))
        assertTrue(shouldSuppressAutoDodgePacketHoldMovement(
            holdActive = true,
            event = PacketEvent(TransferOrigin.OUTGOING, movement, original = false),
        ))
        assertFalse(shouldSuppressAutoDodgePacketHoldMovement(
            holdActive = true,
            event = PacketEvent(
                TransferOrigin.OUTGOING,
                ServerboundSwingPacket(InteractionHand.MAIN_HAND),
                original = true,
            ),
        ))
    }
}
