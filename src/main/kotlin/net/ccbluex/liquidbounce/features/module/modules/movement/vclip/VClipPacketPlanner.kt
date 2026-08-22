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

internal object VClipPacketPlanner {

    private const val PAPER_DISTANCE_PER_PACKET = 10.0
    private const val MAX_FOLIA_MOVEMENT_PACKETS = 5

    fun vanilla(
        origin: VClipPosition,
        target: VClipPosition,
        paperBypass: Boolean,
        forceTargetPacket: Boolean = false,
        fullPacket: Boolean,
        onGround: Boolean,
    ): List<VClipPlayerPacketStep> {
        if (!paperBypass && !forceTargetPacket) {
            return emptyList()
        }

        val shape = positionalShape(fullPacket)
        val stationaryPackets = if (paperBypass) vanillaStationaryPackets(origin, target) else 0
        return buildList(stationaryPackets + 1) {
            repeat(stationaryPackets) {
                add(VClipPlayerPacketStep(shape, origin, onGround))
            }
            add(VClipPlayerPacketStep(shape, target, onGround))
        }
    }

    fun folia(
        target: VClipPosition,
        movementPackets: Int,
        fullPacket: Boolean,
        onGround: Boolean,
    ): List<VClipPlayerPacketStep> {
        require(movementPackets in 1..MAX_FOLIA_MOVEMENT_PACKETS) {
            "Folia movement packets must stay within the researched 1..5 window"
        }

        return buildList(movementPackets) {
            repeat(movementPackets - 1) {
                add(VClipPlayerPacketStep(VClipPlayerPacketShape.STATUS_ONLY, null, onGround))
            }
            add(VClipPlayerPacketStep(positionalShape(fullPacket), target, onGround))
        }
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
}
