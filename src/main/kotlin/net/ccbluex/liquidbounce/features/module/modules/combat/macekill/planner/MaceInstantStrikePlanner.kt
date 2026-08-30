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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

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

internal data class MacePostAttackFallResetRequest(
    val endpoint: Vec3,
    val endpointBoundingBox: AABB,
)

internal data class MacePostAttackFallResetPlan(
    val rise: Double,
    val packets: List<MaceInstantStrikePacket.Position>,
)

internal sealed interface MacePostAttackFallResetPlanResult {
    data class Ready(val plan: MacePostAttackFallResetPlan) : MacePostAttackFallResetPlanResult

    data object NoUsableRise : MacePostAttackFallResetPlanResult
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

/**
 * Clears a possibly unconsumed smash fall distance only after the attack packet was sent.
 *
 * Minecraft 26.2 resets server fall distance after accepted upward movement. The following tiny
 * grounded descent can therefore restore the exact endpoint without turning a rejected hit into
 * fall damage for the attacker.
 */
internal object MacePostAttackFallResetPlanner {

    fun plan(
        request: MacePostAttackFallResetRequest,
        isCollisionFree: (AABB) -> Boolean,
    ): MacePostAttackFallResetPlanResult {
        if (!request.hasFiniteCoordinates()) return MacePostAttackFallResetPlanResult.NoUsableRise
        val rise = POST_ATTACK_RESET_RISE_CANDIDATES.firstOrNull { candidate ->
            isCollisionFree(request.endpointBoundingBox.move(0.0, candidate, 0.0))
        } ?: return MacePostAttackFallResetPlanResult.NoUsableRise

        return MacePostAttackFallResetPlanResult.Ready(
            MacePostAttackFallResetPlan(
                rise = rise,
                packets = listOf(
                    MaceInstantStrikePacket.Position(
                        request.endpoint.add(0.0, rise, 0.0),
                        onGround = false,
                    ),
                    MaceInstantStrikePacket.Position(request.endpoint, onGround = true),
                ),
            ),
        )
    }

    private fun MacePostAttackFallResetRequest.hasFiniteCoordinates(): Boolean =
        endpoint.x.isFinite() && endpoint.y.isFinite() && endpoint.z.isFinite() &&
            endpointBoundingBox.minX.isFinite() && endpointBoundingBox.minY.isFinite() &&
            endpointBoundingBox.minZ.isFinite() && endpointBoundingBox.maxX.isFinite() &&
            endpointBoundingBox.maxY.isFinite() && endpointBoundingBox.maxZ.isFinite()

    private val POST_ATTACK_RESET_RISE_CANDIDATES = doubleArrayOf(
        1.0 / 16.0,
        1.0 / 32.0,
        1.0 / 64.0,
        1.0 / 128.0,
        1.0 / 256.0,
        1.0 / 512.0,
        1.0 / 1024.0,
    )

}
