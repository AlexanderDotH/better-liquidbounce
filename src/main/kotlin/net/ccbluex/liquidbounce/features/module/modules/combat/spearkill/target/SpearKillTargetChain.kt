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

@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


import net.minecraft.world.phys.Vec3

internal data class SpearKillTargetChainSelection<T, R>(
    val target: T,
    val route: R,
)

/** Tries eligible post-kill candidates nearest-first and stops at the first reachable route. */
internal inline fun <T, R> selectNearestReachableSpearKillChainTarget(
    candidates: Iterable<T>,
    crossinline distanceSquared: (T) -> Double,
    createRoute: (T) -> R?,
): SpearKillTargetChainSelection<T, R>? {
    val orderedCandidates = candidates.mapNotNull { candidate ->
        val distance = distanceSquared(candidate)
        candidate.takeIf { distance.isFinite() && distance >= 0.0 }?.let { it to distance }
    }.sortedBy { it.second }

    for ((candidate) in orderedCandidates) {
        val route = createRoute(candidate) ?: continue
        return SpearKillTargetChainSelection(candidate, route)
    }
    return null
}

/** Inserts a new attack round trip ahead of an already validated return tail. */
internal fun buildSpearKillChainedAttackMovements(
    outboundMovements: List<Vec3>,
    existingReturnMovements: List<Vec3>,
): List<Vec3> = buildList(outboundMovements.size * 2 + existingReturnMovements.size) {
    addAll(outboundMovements)
    outboundMovements.asReversed().forEach { add(it.scale(-1.0)) }
    addAll(existingReturnMovements)
}
