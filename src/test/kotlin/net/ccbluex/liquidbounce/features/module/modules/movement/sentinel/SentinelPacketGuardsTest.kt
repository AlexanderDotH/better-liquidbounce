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

package net.ccbluex.liquidbounce.features.module.modules.movement.sentinel

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentinelPacketGuardsTest {

    @Test
    fun `sentinel lag only targets outgoing movement packets`() {
        val movementPacket = ServerboundMovePlayerPacket.Pos(0.0, 64.0, 0.0, true, false)

        assertTrue(isSentinelOutgoingMovementPacket(TransferOrigin.OUTGOING, movementPacket))
        assertFalse(isSentinelOutgoingMovementPacket(TransferOrigin.INCOMING, movementPacket))
        assertFalse(isSentinelOutgoingMovementPacket(TransferOrigin.OUTGOING, null))
    }

}
