/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import kotlin.math.abs
import kotlin.math.floor

internal data class VClipPosition(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "VClip position must be finite" }
    }
}

internal enum class VClipPlayerPacketShape {
    STATUS_ONLY,
    POSITION,
    FULL,
}

internal data class VClipPlayerPacketStep(
    val shape: VClipPlayerPacketShape,
    val position: VClipPosition?,
    val onGround: Boolean,
)

internal sealed interface VClipPacketPlanResult {

    sealed interface Ready : VClipPacketPlanResult {
        val steps: List<VClipPlayerPacketStep>
    }

    data class GroundedSegmentation(
        override val steps: List<VClipPlayerPacketStep>,
    ) : Ready

    data class PacketJumpFallback(
        override val steps: List<VClipPlayerPacketStep>,
    ) : Ready

    data object Unavailable : VClipPacketPlanResult
}

internal object VClipPacketPlanner {

    private const val PAPER_DISTANCE_PER_PACKET = 10.0
    private const val PACKET_JUMP_Y_OFFSET = 1.0E-9
    private const val MAX_FOLIA_MOVEMENT_PACKETS = 5
    private const val VCLIP_ON_GROUND = true

    fun vanilla(
        origin: VClipPosition,
        target: VClipPosition,
        paperBypass: Boolean,
        fullPacket: Boolean,
        initialFallDistance: Double,
        safeFallDistance: Double,
    ): VClipPacketPlanResult {
        val checkpoints = groundedCheckpoints(
            origin,
            target,
            initialFallDistance,
            safeFallDistance,
        ) ?: return VClipPacketPlanResult.Unavailable
        val shape = positionalShape(fullPacket)
        val stationaryPackets = if (paperBypass) vanillaStationaryPackets(origin, target) else 0
        val steps = buildList(stationaryPackets + checkpoints.size) {
            repeat(stationaryPackets) {
                add(VClipPlayerPacketStep(shape, origin, VCLIP_ON_GROUND))
            }
            checkpoints.forEach { checkpoint ->
                add(VClipPlayerPacketStep(shape, checkpoint, VCLIP_ON_GROUND))
            }
        }
        return VClipPacketPlanResult.GroundedSegmentation(steps)
    }

    fun folia(
        origin: VClipPosition,
        target: VClipPosition,
        movementPackets: Int,
        fullPacket: Boolean,
        initialFallDistance: Double,
        safeFallDistance: Double,
    ): VClipPacketPlanResult {
        requireFoliaPacketCount(movementPackets)

        val checkpoints = groundedCheckpoints(origin, target, initialFallDistance, safeFallDistance)
        if (checkpoints == null || checkpoints.size > movementPackets) {
            return packetJumpFallback(target, movementPackets, fullPacket)
        }

        val shape = positionalShape(fullPacket)
        val steps = buildList(movementPackets) {
            repeat(movementPackets - checkpoints.size) {
                add(VClipPlayerPacketStep(VClipPlayerPacketShape.STATUS_ONLY, null, VCLIP_ON_GROUND))
            }
            checkpoints.forEach { checkpoint ->
                add(VClipPlayerPacketStep(shape, checkpoint, VCLIP_ON_GROUND))
            }
        }
        return VClipPacketPlanResult.GroundedSegmentation(steps)
    }

    private fun packetJumpFallback(
        target: VClipPosition,
        movementPackets: Int,
        fullPacket: Boolean,
    ): VClipPacketPlanResult {
        if (movementPackets < 2) {
            return VClipPacketPlanResult.Unavailable
        }

        val shape = positionalShape(fullPacket)
        val steps = buildList(movementPackets) {
            repeat(movementPackets - 2) {
                add(VClipPlayerPacketStep(VClipPlayerPacketShape.STATUS_ONLY, null, onGround = false))
            }
            add(VClipPlayerPacketStep(shape, target, onGround = false))
            add(
                VClipPlayerPacketStep(
                    shape,
                    target.copy(y = target.y + PACKET_JUMP_Y_OFFSET),
                    onGround = false,
                ),
            )
        }
        return VClipPacketPlanResult.PacketJumpFallback(steps)
    }

    private fun groundedCheckpoints(
        origin: VClipPosition,
        target: VClipPosition,
        initialFallDistance: Double,
        safeFallDistance: Double,
    ) = when (
        val plan = VClipFallSafetyPlanner.plan(origin, target, initialFallDistance, safeFallDistance)
    ) {
        is VClipFallSafetyPlan.GroundedSegmentation -> plan.checkpoints
        VClipFallSafetyPlan.Unsafe -> null
    }

    private fun positionalShape(fullPacket: Boolean) = if (fullPacket) {
        VClipPlayerPacketShape.FULL
    } else {
        VClipPlayerPacketShape.POSITION
    }

    private fun vanillaStationaryPackets(origin: VClipPosition, target: VClipPosition): Int {
        val manhattanDistance = abs(target.x - origin.x) +
            abs(target.y - origin.y) +
            abs(target.z - origin.z)
        return (floor(manhattanDistance / PAPER_DISTANCE_PER_PACKET) - 1).toInt().coerceAtLeast(0)
    }

    private fun requireFoliaPacketCount(movementPackets: Int) {
        require(movementPackets in 1..MAX_FOLIA_MOVEMENT_PACKETS) {
            "Folia movement packets must stay within the researched 1..5 window"
        }
    }
}
