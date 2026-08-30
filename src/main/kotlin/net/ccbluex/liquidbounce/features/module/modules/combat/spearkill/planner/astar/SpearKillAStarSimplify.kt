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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


import net.minecraft.world.phys.Vec3

/**
 * Any-angle string-pull: from the current point, keep the farthest later waypoint whose swept
 * player box is clear. Unlike collinear simplify, shortcuts need not share a direction and are
 * not capped by StepLimit — packet expansion splits long edges afterward.
 */
internal fun simplifySpearKillAStarWaypointsWithLineOfSight(
    origin: Vec3,
    waypoints: List<Vec3>,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3> {
    if (!origin.isFiniteSpearKillSimplifyPoint() || waypoints.isEmpty()) {
        return waypoints
    }

    val simplified = ArrayList<Vec3>(waypoints.size)
    var current = origin
    var index = 0

    while (index < waypoints.size) {
        if (!waypoints[index].isFiniteSpearKillSimplifyPoint()) return waypoints

        var selectedIndex = index
        var candidateIndex = index
        while (candidateIndex < waypoints.size) {
            val candidate = waypoints[candidateIndex]
            if (!candidate.isFiniteSpearKillSimplifyPoint() ||
                !segmentValidator.isClear(current, candidate)
            ) {
                break
            }
            selectedIndex = candidateIndex
            candidateIndex++
        }

        current = waypoints[selectedIndex]
        simplified += current
        index = selectedIndex + 1
    }

    return simplified
}

/** Picks collinear or any-angle waypoint compaction based on the LineOfSightShortcuts toggle. */
internal fun compactSpearKillAStarWaypoints(
    origin: Vec3,
    waypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    lineOfSightShortcuts: Boolean,
): List<Vec3> = if (lineOfSightShortcuts) {
    simplifySpearKillAStarWaypointsWithLineOfSight(origin, waypoints, segmentValidator)
} else {
    simplifySpearKillAStarWaypoints(origin, waypoints, maxSpeed, segmentValidator)
}

private fun Vec3.isFiniteSpearKillSimplifyPoint(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
