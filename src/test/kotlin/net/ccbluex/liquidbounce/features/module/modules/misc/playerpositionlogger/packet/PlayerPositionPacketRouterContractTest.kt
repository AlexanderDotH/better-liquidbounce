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

package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.packet

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PlayerPositionPacketRouterContractTest {

    @Test
    fun `router retains every observed packet type`() {
        val source = Files.list(PACKET_ROOT).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
        val packetTypes = listOf(
            "ServerboundMovePlayerPacket",
            "ServerboundMoveVehiclePacket",
            "ServerboundAcceptTeleportationPacket",
            "ClientboundPlayerPositionPacket",
            "ClientboundPlayerRotationPacket",
            "ClientboundExplodePacket",
            "ClientboundMoveEntityPacket",
            "ClientboundTeleportEntityPacket",
            "ClientboundEntityPositionSyncPacket",
            "ClientboundSetEntityMotionPacket",
            "ClientboundRotateHeadPacket",
            "ClientboundAddEntityPacket",
            "ClientboundSetPassengersPacket",
            "ClientboundRemoveEntitiesPacket",
            "ClientboundRespawnPacket",
        )

        packetTypes.forEach { packetType ->
            assertTrue(source.contains("is $packetType"), "$packetType must remain routed")
        }
    }

    private companion object {
        val PACKET_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/misc/" +
                "playerpositionlogger/packet",
        )
    }
}
