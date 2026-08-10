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

import net.ccbluex.liquidbounce.common.ShapeFlag
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Validates one virtual player movement segment before SpearKill emits it. */
internal fun interface SpearKillAStarSegmentValidator {
    fun isClear(from: Vec3, to: Vec3): Boolean
}

/**
 * Packet A* must see vanilla collision shapes. Client modules like BlockWalk / LiquidWalk /
 * AvoidHazards temporarily solidify walk-through blocks via [BlockShapeEvent]; those shapes are
 * invisible to the server and must not reject a legal packet route.
 */
internal inline fun <T> withVanillaSpearKillBlockShapes(block: () -> T): T {
    val previous = ShapeFlag.noShapeChange
    ShapeFlag.noShapeChange = true
    return try {
        block()
    } finally {
        ShapeFlag.noShapeChange = previous
    }
}

/** A clear straight run-up needs packet splitting, not a block search. */
internal fun resolveSpearKillAStarApproachRoute(
    origin: Vec3,
    plannerGoal: Vec3,
    segmentValidator: SpearKillAStarSegmentValidator,
    routeSearch: () -> List<Vec3>?,
): List<Vec3>? = if (segmentValidator.isClear(origin, plannerGoal)) {
    emptyList()
} else {
    routeSearch()
}

/**
 * Creates a cached collision validator for virtual movement relative to [origin].
 *
 * A broad swept box is checked first. Only colliding diagonal boxes are recursively narrowed,
 * avoiding both huge rectangular scans and false collisions far away from the actual corridor.
 * Surviving segments must then resolve to the exact requested delta under vanilla's collision
 * solver, including adaptive safety margins and axis-decomposed elevation checks.
 */
internal fun createSpearKillAStarSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasCollision: (AABB) -> Boolean,
    resolveMovement: (AABB, Vec3) -> Vec3,
): SpearKillAStarSegmentValidator {
    val results = HashMap<SpearKillAStarSegment, Boolean>()
    val collisionResolver = SpearKillServerCollisionResolver(resolveMovement)
    return SpearKillAStarSegmentValidator { from, to ->
        if (!from.isFiniteSpearKillCollisionPoint() || !to.isFiniteSpearKillCollisionPoint()) {
            false
        } else {
            val segment = SpearKillAStarSegment(from, to)
            results[segment] ?: (
                isSpearKillAStarSegmentClear(
                    origin = origin,
                    playerBoundingBox = playerBoundingBox,
                    from = from,
                    to = to,
                    hasCollision = hasCollision,
                ) && isSpearKillServerMovementExact(
                    origin = origin,
                    playerBoundingBox = playerBoundingBox,
                    from = from,
                    to = to,
                    collisionResolver = collisionResolver,
                )
            ).also { results[segment] = it }
        }
    }
}

/** Adaptive inflate used by the server-faithful packet collision check. */
internal fun spearKillAStarServerCollisionMargin(
    movement: Vec3,
): SpearKillAStarServerCollisionMargin {
    val horizontalDiagonal = abs(movement.x) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON &&
        abs(movement.z) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON
    val hasElevation = abs(movement.y) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON
    val horizontal = when {
        horizontalDiagonal && hasElevation -> SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_DIAGONAL_ELEVATION
        horizontalDiagonal || hasElevation -> SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_COMPLEX
        else -> SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_BASE
    }
    val vertical = if (horizontalDiagonal || hasElevation) {
        SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_VERTICAL
    } else {
        0.0
    }
    return SpearKillAStarServerCollisionMargin(horizontal = horizontal, vertical = vertical)
}

internal data class SpearKillAStarServerCollisionMargin(
    val horizontal: Double,
    val vertical: Double,
)

private data class SpearKillServerCollisionResolver(
    val resolveMovement: (AABB, Vec3) -> Vec3,
)

private data class SpearKillAStarSegment(val from: Vec3, val to: Vec3)

private fun isSpearKillAStarSegmentClear(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    hasCollision: (AABB) -> Boolean,
): Boolean {
    val movement = to.subtract(from)
    val xDistance = abs(movement.x)
    val yDistance = abs(movement.y)
    val zDistance = abs(movement.z)
    val largestAxisDistance = maxOf(xDistance, yDistance, zDistance)
    val smallestAxisDistance = minOf(xDistance, yDistance, zDistance)
    val middleAxisDistance = xDistance + yDistance + zDistance - largestAxisDistance - smallestAxisDistance
    val middle = from.add(movement.scale(0.5))

    // Avoid ever asking the world to scan a large diagonal rectangle. Thin or axis-aligned sweeps
    // stay as one cheap query; broad diagonals are split into narrow corridor pieces first.
    if (middleAxisDistance > SPEAR_KILL_A_STAR_COLLISION_MAX_BROAD_DIAGONAL) {
        return isSpearKillAStarSegmentClear(
            origin, playerBoundingBox, from, middle, hasCollision,
        ) && isSpearKillAStarSegmentClear(
            origin, playerBoundingBox, middle, to, hasCollision,
        )
    }

    val fromBox = playerBoundingBox.move(from.subtract(origin))
    val toBox = playerBoundingBox.move(to.subtract(origin))
    if (!hasCollision(fromBox.minmax(toBox))) return true
    if (largestAxisDistance <= SPEAR_KILL_A_STAR_COLLISION_MIN_SLICE_LENGTH) return false

    return isSpearKillAStarSegmentClear(
        origin, playerBoundingBox, from, middle, hasCollision,
    ) && isSpearKillAStarSegmentClear(
        origin, playerBoundingBox, middle, to, hasCollision,
    )
}

/**
 * Server-faithful clearance for one A* / packet edge.
 *
 * Long edges are sliced so sand lips cannot hide between endpoints. Elevation plus horizontal
 * motion also requires both Y-then-XZ and XZ-then-Y resolves to stay exact — the failure mode that
 * produces vanilla "moved wrongly" on stepped dunes.
 */
private fun isSpearKillServerMovementExact(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    collisionResolver: SpearKillServerCollisionResolver,
): Boolean {
    val requestedMovement = to.subtract(from)
    if (!requestedMovement.isFiniteSpearKillCollisionPoint()) {
        return false
    }
    if (requestedMovement.lengthSqr() <= SPEAR_KILL_A_STAR_SERVER_MOVEMENT_EPSILON_SQUARED) {
        return true
    }

    val directClear = isSpearKillServerMovementExactSliced(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        from = from,
        to = to,
        collisionResolver = collisionResolver,
    )
    val hasElevation = abs(requestedMovement.y) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON
    val hasHorizontal = abs(requestedMovement.x) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON ||
        abs(requestedMovement.z) > SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON
    if (!directClear || !hasElevation || !hasHorizontal) {
        return directClear
    }

    return isSpearKillServerAxisDecomposedClear(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        from = from,
        to = to,
        collisionResolver = collisionResolver,
    )
}

private fun isSpearKillServerAxisDecomposedClear(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    collisionResolver: SpearKillServerCollisionResolver,
): Boolean {
    val requestedMovement = to.subtract(from)
    val vertical = Vec3(0.0, requestedMovement.y, 0.0)
    val horizontal = Vec3(requestedMovement.x, 0.0, requestedMovement.z)
    val midVertical = from.add(vertical)
    val midHorizontal = from.add(horizontal)

    val verticalThenHorizontal =
        isSpearKillServerMovementExactSliced(
            origin, playerBoundingBox, from, midVertical, collisionResolver,
        ) &&
            isSpearKillServerMovementExactSliced(
                origin, playerBoundingBox, midVertical, to, collisionResolver,
            )
    val horizontalThenVertical =
        isSpearKillServerMovementExactSliced(
            origin, playerBoundingBox, from, midHorizontal, collisionResolver,
        ) &&
            isSpearKillServerMovementExactSliced(
                origin, playerBoundingBox, midHorizontal, to, collisionResolver,
            )
    return verticalThenHorizontal && horizontalThenVertical
}

private fun isSpearKillServerMovementExactSliced(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    collisionResolver: SpearKillServerCollisionResolver,
): Boolean {
    val requestedMovement = to.subtract(from)
    val length = requestedMovement.length()
    if (length <= SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON) return true

    val sliceCount = max(
        1,
        ceil(length / SPEAR_KILL_A_STAR_SERVER_RESOLVE_SLICE_LENGTH).toInt(),
    )
    var cursor = from
    for (slice in 1..sliceCount) {
        val target = if (slice == sliceCount) {
            to
        } else {
            from.add(requestedMovement.scale(slice.toDouble() / sliceCount))
        }
        if (!isSpearKillServerMovementExactSlice(
                origin = origin,
                playerBoundingBox = playerBoundingBox,
                from = cursor,
                to = target,
                collisionResolver = collisionResolver,
            )
        ) {
            return false
        }
        cursor = target
    }
    return true
}

private fun isSpearKillServerMovementExactSlice(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    collisionResolver: SpearKillServerCollisionResolver,
): Boolean {
    val requestedMovement = to.subtract(from)
    val margin = spearKillAStarServerCollisionMargin(requestedMovement)
    val serverBox = playerBoundingBox
        .move(from.subtract(origin))
        .inflate(margin.horizontal, margin.vertical, margin.horizontal)
    val resolvedMovement = collisionResolver.resolveMovement(serverBox, requestedMovement)
    return resolvedMovement.isFiniteSpearKillCollisionPoint() &&
        resolvedMovement.distanceToSqr(requestedMovement) <= SPEAR_KILL_A_STAR_SERVER_MOVEMENT_EPSILON_SQUARED
}

private fun Vec3.isFiniteSpearKillCollisionPoint(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_A_STAR_COLLISION_MAX_BROAD_DIAGONAL = 2.0
private const val SPEAR_KILL_A_STAR_COLLISION_MIN_SLICE_LENGTH = 0.5
private const val SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_BASE = 0.05
private const val SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_COMPLEX = 0.06
private const val SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_DIAGONAL_ELEVATION = 0.08
private const val SPEAR_KILL_A_STAR_SERVER_COLLISION_MARGIN_VERTICAL = 0.02
private const val SPEAR_KILL_A_STAR_SERVER_RESOLVE_SLICE_LENGTH = 1.0
private const val SPEAR_KILL_A_STAR_SERVER_AXIS_EPSILON = 1.0E-6
private const val SPEAR_KILL_A_STAR_SERVER_MOVEMENT_EPSILON_SQUARED = 1.0E-12
