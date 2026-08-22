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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil

data class MaceInstantStrikeRequest(
    val physicalPosition: Vec3,
    val physicalBoundingBox: AABB,
    val virtualEndpoint: Vec3,
    val maximumFallHeight: Int,
    val endpointOnGround: Boolean,
)

sealed interface MaceInstantStrikePacket {
    val onGround: Boolean

    data class StatusOnly(override val onGround: Boolean) : MaceInstantStrikePacket

    data class Position(
        val position: Vec3,
        override val onGround: Boolean,
    ) : MaceInstantStrikePacket
}

data class MaceInstantStrikePlan(
    val fallHeight: Int,
    val packets: List<MaceInstantStrikePacket>,
)

sealed interface MaceInstantStrikePlanResult {
    data class Ready(val plan: MaceInstantStrikePlan) : MaceInstantStrikePlanResult

    data object NoUsableHeight : MaceInstantStrikePlanResult
}

/**
 * Builds the movement spoof that must be delivered immediately before the accepted mace attack.
 *
 * Collision checks are supplied by the caller so planning stays deterministic and can operate at a
 * server-side virtual endpoint without moving the local player.
 */
object MaceInstantStrikePlanner {

    fun plan(
        request: MaceInstantStrikeRequest,
        isCollisionFree: (AABB) -> Boolean,
    ): MaceInstantStrikePlanResult {
        val endpointBoundingBox = request.physicalBoundingBox.atEndpoint(request)
        val fallHeight = (request.maximumFallHeight downTo 1).firstOrNull { height ->
            isCollisionFree(endpointBoundingBox.move(0.0, height.toDouble(), 0.0))
        } ?: return MaceInstantStrikePlanResult.NoUsableHeight

        return MaceInstantStrikePlanResult.Ready(
            MaceInstantStrikePlan(
                fallHeight = fallHeight,
                packets = buildPackets(request, fallHeight),
            ),
        )
    }

    private fun AABB.atEndpoint(request: MaceInstantStrikeRequest): AABB {
        val offset = request.virtualEndpoint.subtract(request.physicalPosition)
        return move(offset.x, offset.y, offset.z)
    }

    private fun buildPackets(
        request: MaceInstantStrikeRequest,
        fallHeight: Int,
    ): List<MaceInstantStrikePacket> = buildList {
        val longHeight = fallHeight > LONG_HEIGHT_THRESHOLD
        val primingPackets = if (longHeight) {
            ceil(abs(fallHeight / HEIGHT_PER_PRIMING_PACKET)).toInt()
        } else {
            SHORT_HEIGHT_PRIMING_PACKETS
        }
        val primingOnGround = !longHeight && request.endpointOnGround

        repeat(primingPackets) {
            add(MaceInstantStrikePacket.StatusOnly(primingOnGround))
        }
        add(MaceInstantStrikePacket.Position(
            request.virtualEndpoint.add(0.0, fallHeight.toDouble(), 0.0),
            onGround = false,
        ))
        add(MaceInstantStrikePacket.Position(request.virtualEndpoint, onGround = false))
    }

    private const val LONG_HEIGHT_THRESHOLD = 10
    private const val HEIGHT_PER_PRIMING_PACKET = 10.0
    private const val SHORT_HEIGHT_PRIMING_PACKETS = 2

}
