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
 * Creates a cached swept-hitbox validator for virtual movement relative to [origin].
 *
 * The collision callback performs one raycast for the entire movement vector after expanding each
 * server collision box by the player's half extents. This follows the actual player hitbox rather
 * than sampling a simulated path or querying a broad diagonal rectangle.
 */
internal fun createSpearKillAStarSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasHitboxRaycastCollision: (AABB, Vec3) -> Boolean,
): SpearKillAStarSegmentValidator {
    val results = HashMap<SpearKillAStarSegment, Boolean>()
    return SpearKillAStarSegmentValidator { from, to ->
        if (!from.isFiniteSpearKillCollisionPoint() || !to.isFiniteSpearKillCollisionPoint()) {
            false
        } else {
            val segment = SpearKillAStarSegment(from, to)
            results[segment] ?: (!hasHitboxRaycastCollision(
                playerBoundingBox.move(from.subtract(origin)),
                to.subtract(from),
            )).also { results[segment] = it }
        }
    }
}

private data class SpearKillAStarSegment(val from: Vec3, val to: Vec3)

private fun Vec3.isFiniteSpearKillCollisionPoint(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
