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

/**
 * Validates one direct Packet edge with the same swept player-hitbox raycast used by A*.
 *
 * Direct routes intentionally reject any obstacle intersected by the actual body corridor. This
 * avoids accepting a packet merely because an old movement simulation could step over or clip
 * around a shape before the server evaluates the kinetic route.
 */
internal fun createSpearKillDirectPacketSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasHitboxRaycastCollision: (AABB, Vec3) -> Boolean,
): SpearKillAStarSegmentValidator {
    val results = HashMap<SpearKillDirectPacketSegment, Boolean>()
    return SpearKillAStarSegmentValidator { from, to ->
        if (!from.hasFiniteSpearKillDirectPacketCoordinates() ||
            !to.hasFiniteSpearKillDirectPacketCoordinates()
        ) {
            false
        } else {
            val segment = SpearKillDirectPacketSegment(from, to)
            results[segment] ?: (!hasHitboxRaycastCollision(
                playerBoundingBox.move(from.subtract(origin)),
                to.subtract(from),
            )).also { results[segment] = it }
        }
    }
}

/**
 * Final server-faithful preflight for a selected Packet edge.
 *
 * Search continues to use the cheaper swept-hitbox validator. Only the bounded route that may be
 * emitted pays for vanilla collision resolution and the moved-wrongly residual check.
 */
internal fun createSpearKillServerPacketSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasDestinationCollision: (AABB) -> Boolean,
    resolveMovement: (AABB, Vec3) -> Vec3,
): SpearKillAStarSegmentValidator {
    val results = HashMap<SpearKillDirectPacketSegment, Boolean>()
    return SpearKillAStarSegmentValidator { from, to ->
        if (!from.hasFiniteSpearKillDirectPacketCoordinates() ||
            !to.hasFiniteSpearKillDirectPacketCoordinates()
        ) {
            false
        } else {
            val segment = SpearKillDirectPacketSegment(from, to)
            results[segment] ?: isSpearKillServerPacketSegmentAccepted(
                origin = origin,
                playerBoundingBox = playerBoundingBox,
                from = from,
                to = to,
                hasDestinationCollision = hasDestinationCollision,
                resolveMovement = resolveMovement,
            ).also { results[segment] = it }
        }
    }
}

/** Mirrors the horizontal residual threshold used by the server's moved-wrongly check. */
internal fun isSpearKillServerPacketMovementAccepted(
    requestedMovement: Vec3,
    resolvedMovement: Vec3,
): Boolean {
    if (!requestedMovement.hasFiniteSpearKillDirectPacketCoordinates() ||
        !resolvedMovement.hasFiniteSpearKillDirectPacketCoordinates()
    ) {
        return false
    }

    val residualX = requestedMovement.x - resolvedMovement.x
    val residualZ = requestedMovement.z - resolvedMovement.z
    return residualX * residualX + residualZ * residualZ <= SPEAR_KILL_SERVER_MOVED_WRONGLY_THRESHOLD_SQUARED
}

private fun isSpearKillServerPacketSegmentAccepted(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    hasDestinationCollision: (AABB) -> Boolean,
    resolveMovement: (AABB, Vec3) -> Vec3,
): Boolean {
    val destinationBox = playerBoundingBox
        .move(to.subtract(origin))
        .deflate(SPEAR_KILL_SERVER_DESTINATION_EPSILON)
    if (hasDestinationCollision(destinationBox)) return false

    val requestedMovement = to.subtract(from)
    if (requestedMovement.lengthSqr() <= SPEAR_KILL_DIRECT_PACKET_MOVEMENT_EPSILON_SQUARED) return true

    val rawMovementBox = playerBoundingBox.move(from.subtract(origin))
    val movementBox = AABB(
        rawMovementBox.minX,
        rawMovementBox.minY + SPEAR_KILL_SERVER_DESTINATION_EPSILON,
        rawMovementBox.minZ,
        rawMovementBox.maxX,
        rawMovementBox.maxY,
        rawMovementBox.maxZ,
    )
    return isSpearKillServerPacketMovementAccepted(requestedMovement, resolveMovement(movementBox, requestedMovement))
}

private fun Vec3.hasFiniteSpearKillDirectPacketCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

private data class SpearKillDirectPacketSegment(val from: Vec3, val to: Vec3)

private const val SPEAR_KILL_SERVER_MOVED_WRONGLY_THRESHOLD_SQUARED = 0.0625
private const val SPEAR_KILL_SERVER_DESTINATION_EPSILON = 1.0E-5
private const val SPEAR_KILL_DIRECT_PACKET_MOVEMENT_EPSILON_SQUARED = 1.0E-12
