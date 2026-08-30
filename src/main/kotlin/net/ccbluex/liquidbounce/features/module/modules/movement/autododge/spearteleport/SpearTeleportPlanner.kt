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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport

class SpearTeleportPlanner {
    fun plan(request: SpearTeleportRequest, isSafe: (SpearTeleportPoint) -> Boolean): SpearTeleportPlan? {
        val direction = resolveDirection(request) ?: return null
        val ideal = request.attackerPosition.offset(
            -direction.x * request.behindDistance,
            request.playerPosition.y - request.attackerPosition.y,
            -direction.z * request.behindDistance,
        )
        val perpendicular = SpearTeleportDirection(-direction.z, direction.x)
        val lateralAnchors = listOf(request.preferredLateralSide, request.preferredLateralSide.opposite)
            .map { side -> request.lateralAnchor(perpendicular, side.multiplier) }
        val anchors = if (request.preferLocalEscape) {
            lateralAnchors
        } else {
            listOf(SearchAnchor(ideal) { it.isBehind(request.attackerPosition, direction) }) + lateralAnchors
        }
        return candidates(anchors, request.searchRadius)
            .filter { it.horizontalDistanceTo(request.attackerPosition) >= MINIMUM_ATTACKER_DISTANCE }
            .map { candidate -> candidate to candidate.distanceTo(request.playerPosition) }
            .filter { (_, distance) -> distance in MINIMUM_TRAVEL_DISTANCE..request.maxDistance }
            .firstOrNull { (candidate, _) -> isSafe(candidate) }
            ?.let { (destination, distance) -> SpearTeleportPlan(destination, distance) }
    }

    private fun resolveDirection(request: SpearTeleportRequest): SpearTeleportDirection? =
        if (request.preferLocalEscape) {
            DEFAULT_LOCAL_ESCAPE_DIRECTION
        } else {
            request.attackerLook.normalizedOrNull()
                ?: SpearTeleportDirection.from(request.attackerPosition, request.playerPosition).normalizedOrNull()
        }

    private fun candidates(anchors: List<SearchAnchor>, radius: Int) = anchors.asSequence().flatMap { anchor ->
        searchOffsets(radius).asSequence().flatMap { (offsetX, offsetZ) ->
            VERTICAL_OFFSETS.asSequence().map { offsetY ->
                anchor to anchor.point.offset(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble())
            }
        }
    }.filter { (anchor, candidate) -> anchor.accepts(candidate) }.map { it.second }

    private fun SpearTeleportRequest.lateralAnchor(
        perpendicular: SpearTeleportDirection,
        side: Double,
    ): SearchAnchor {
        val point = playerPosition.offset(
            perpendicular.x * lateralDistance * side,
            0.0,
            perpendicular.z * lateralDistance * side,
        )
        return SearchAnchor(point) { candidate ->
            val projection = (candidate.x - playerPosition.x) * perpendicular.x * side +
                (candidate.z - playerPosition.z) * perpendicular.z * side
            projection >= MINIMUM_LATERAL_PROJECTION
        }
    }

    private fun SpearTeleportPoint.isBehind(
        attacker: SpearTeleportPoint,
        direction: SpearTeleportDirection,
    ): Boolean = (x - attacker.x) * direction.x + (z - attacker.z) * direction.z <= MINIMUM_BEHIND_PROJECTION

    private fun searchOffsets(radius: Int): List<Pair<Int, Int>> = (-radius..radius).flatMap { x ->
        (-radius..radius).map { z -> x to z }
    }.sortedWith(compareBy<Pair<Int, Int>> { (x, z) -> x * x + z * z }.thenBy { it.first }.thenBy { it.second })

    private data class SearchAnchor(
        val point: SpearTeleportPoint,
        val accepts: (SpearTeleportPoint) -> Boolean,
    )

    private companion object {
        val VERTICAL_OFFSETS = intArrayOf(0, -1, 1, -2, 2)
        const val MINIMUM_ATTACKER_DISTANCE = 0.75
        const val MINIMUM_TRAVEL_DISTANCE = 1.0
        const val MINIMUM_BEHIND_PROJECTION = -0.25
        const val MINIMUM_LATERAL_PROJECTION = 0.75
        val DEFAULT_LOCAL_ESCAPE_DIRECTION = SpearTeleportDirection(1.0, 0.0)
    }
}
