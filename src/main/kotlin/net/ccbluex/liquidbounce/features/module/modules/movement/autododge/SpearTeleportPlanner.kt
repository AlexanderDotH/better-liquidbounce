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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.hypot

data class SpearTeleportPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "A spear teleport point must be finite" }
    }

    fun distanceTo(other: SpearTeleportPoint): Double {
        val deltaX = x - other.x
        val deltaY = y - other.y
        val deltaZ = z - other.z
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    }

    fun horizontalDistanceTo(other: SpearTeleportPoint): Double = hypot(x - other.x, z - other.z)

    fun offset(x: Double, y: Double, z: Double) = SpearTeleportPoint(this.x + x, this.y + y, this.z + z)

    fun toVec3() = Vec3(x, y, z)
}

data class SpearTeleportDirection(
    val x: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && z.isFinite()) { "A spear teleport direction must be finite" }
    }

    fun normalizedOrNull(): SpearTeleportDirection? {
        val length = hypot(x, z)
        return takeIf { length > MINIMUM_DIRECTION_LENGTH }?.let { SpearTeleportDirection(x / length, z / length) }
    }

    companion object {
        private const val MINIMUM_DIRECTION_LENGTH = 1.0E-6

        fun from(attacker: SpearTeleportPoint, player: SpearTeleportPoint) = SpearTeleportDirection(
            x = player.x - attacker.x,
            z = player.z - attacker.z,
        )
    }
}

data class SpearTeleportRequest(
    val playerPosition: SpearTeleportPoint,
    val attackerPosition: SpearTeleportPoint,
    val attackerLook: SpearTeleportDirection,
    val behindDistance: Double,
    val lateralDistance: Double,
    val maxDistance: Double,
    val searchRadius: Int,
) {
    init {
        require(behindDistance.isFinite() && behindDistance > 0.0) { "Behind distance must be positive" }
        require(lateralDistance.isFinite() && lateralDistance > 0.0) { "Lateral distance must be positive" }
        require(maxDistance.isFinite() && maxDistance > 0.0) { "Maximum distance must be positive" }
        require(searchRadius >= 0) { "Search radius must not be negative" }
    }
}

data class SpearTeleportPlan(
    val destination: SpearTeleportPoint,
    val travelDistance: Double,
)

/** Prefers the attacker flank, then falls back to a bounded lateral escape around the defender. */
class SpearTeleportPlanner {

    fun plan(
        request: SpearTeleportRequest,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? {
        val direction = request.attackerLook.normalizedOrNull()
            ?: SpearTeleportDirection.from(request.attackerPosition, request.playerPosition).normalizedOrNull()
            ?: return null
        val ideal = request.attackerPosition.offset(
            x = -direction.x * request.behindDistance,
            y = request.playerPosition.y - request.attackerPosition.y,
            z = -direction.z * request.behindDistance,
        )
        val perpendicular = SpearTeleportDirection(-direction.z, direction.x)
        val anchors = listOf(
            SearchAnchor(ideal) { candidate ->
                candidate.isBehind(request.attackerPosition, direction)
            },
            request.lateralAnchor(perpendicular, 1.0),
            request.lateralAnchor(perpendicular, -1.0),
        )

        return anchors.asSequence()
            .flatMap { anchor ->
                searchOffsets(request.searchRadius).asSequence().flatMap { (offsetX, offsetZ) ->
                    VERTICAL_OFFSETS.asSequence().map { offsetY ->
                        anchor to anchor.point.offset(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble())
                    }
                }
            }
            .filter { (anchor, candidate) -> anchor.accepts(candidate) }
            .map { (_, candidate) -> candidate }
            .filter { candidate ->
                candidate.horizontalDistanceTo(request.attackerPosition) >= MINIMUM_ATTACKER_DISTANCE
            }
            .map { candidate -> candidate to candidate.distanceTo(request.playerPosition) }
            .filter { (_, distance) -> distance in MINIMUM_TRAVEL_DISTANCE..request.maxDistance }
            .firstOrNull { (candidate, _) -> isSafe(candidate) }
            ?.let { (destination, distance) -> SpearTeleportPlan(destination, distance) }
    }

    private fun SpearTeleportRequest.lateralAnchor(
        perpendicular: SpearTeleportDirection,
        side: Double,
    ): SearchAnchor {
        val point = playerPosition.offset(
            x = perpendicular.x * lateralDistance * side,
            y = 0.0,
            z = perpendicular.z * lateralDistance * side,
        )
        return SearchAnchor(point) { candidate ->
            val lateralProjection = (candidate.x - playerPosition.x) * perpendicular.x * side +
                (candidate.z - playerPosition.z) * perpendicular.z * side
            lateralProjection >= MINIMUM_LATERAL_PROJECTION
        }
    }

    private fun SpearTeleportPoint.isBehind(
        attacker: SpearTeleportPoint,
        direction: SpearTeleportDirection,
    ): Boolean {
        val projection = (x - attacker.x) * direction.x + (z - attacker.z) * direction.z
        return projection <= MINIMUM_BEHIND_PROJECTION
    }

    private fun searchOffsets(radius: Int): List<Pair<Int, Int>> = (-radius..radius).flatMap { x ->
        (-radius..radius).map { z -> x to z }
    }.sortedWith(compareBy<Pair<Int, Int>> { (x, z) -> x * x + z * z }.thenBy { it.first }.thenBy { it.second })

    private companion object {
        val VERTICAL_OFFSETS = intArrayOf(0, -1, 1, -2, 2)
        const val MINIMUM_ATTACKER_DISTANCE = 0.75
        const val MINIMUM_TRAVEL_DISTANCE = 1.0
        const val MINIMUM_BEHIND_PROJECTION = -0.25
        const val MINIMUM_LATERAL_PROJECTION = 0.75
    }

    private data class SearchAnchor(
        val point: SpearTeleportPoint,
        val accepts: (SpearTeleportPoint) -> Boolean,
    )
}

internal fun buildSpearTeleportPath(
    from: Vec3,
    to: Vec3,
    stepDistance: Double,
    maxPackets: Int,
): List<Vec3>? {
    require(stepDistance > 0.0 && stepDistance.isFinite()) { "Step distance must be finite and positive" }
    require(maxPackets > 0) { "Maximum packet count must be positive" }

    val distance = from.distanceTo(to)
    val packetCount = ceil(distance / stepDistance).toInt().coerceAtLeast(1)
    if (packetCount > maxPackets) {
        return null
    }

    return List(packetCount) { index -> from.lerp(to, (index + 1.0) / packetCount) }
}

internal fun createSpearTeleportPacket(
    position: Vec3,
    onGround: Boolean,
    horizontalCollision: Boolean,
) = ServerboundMovePlayerPacket.Pos(
    position.x,
    position.y,
    position.z,
    onGround,
    horizontalCollision,
)

internal fun executeSpearTeleport(
    from: Vec3,
    plan: SpearTeleportPlan,
    stepDistance: Double,
    maxPackets: Int,
    onGround: Boolean,
    horizontalCollision: Boolean,
    isStillSafe: () -> Boolean,
    sendPacket: (ServerboundMovePlayerPacket) -> Unit,
    moveLocalPlayer: (Vec3) -> Unit,
): Boolean {
    val destination = plan.destination.toVec3()
    val path = buildSpearTeleportPath(from, destination, stepDistance, maxPackets) ?: return false
    if (!isStillSafe()) {
        return false
    }

    path.asSequence()
        .map { createSpearTeleportPacket(it, onGround, horizontalCollision) }
        .forEach(sendPacket)
    moveLocalPlayer(destination)
    return true
}

internal fun isSpearTeleportCandidateSafe(
    destinationCollisionFree: Boolean,
    supported: Boolean,
    overVoid: Boolean,
    routeCollisionFree: Boolean,
    loaded: Boolean = true,
    withinWorldBorder: Boolean = true,
): Boolean = loaded && withinWorldBorder && destinationCollisionFree && supported && !overVoid && routeCollisionFree

internal const val SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE = 0.25

/** Samples the entire swept route independently of the larger network packet spacing. */
internal fun buildSpearTeleportCollisionSamples(
    from: Vec3,
    to: Vec3,
): List<Vec3> {
    val distance = from.distanceTo(to)
    val sampleCount = ceil(distance / SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE).toInt().coerceAtLeast(1)
    return List(sampleCount) { index -> from.lerp(to, (index + 1.0) / sampleCount) }
}

internal class SpearTeleportCooldown {
    private var lastSuccessTick: Long? = null

    fun isReady(tick: Long, cooldownTicks: Int): Boolean {
        require(cooldownTicks >= 0) { "Cooldown must not be negative" }
        val lastTick = lastSuccessTick ?: return true
        return tick - lastTick >= cooldownTicks
    }

    fun recordSuccess(tick: Long) {
        lastSuccessTick = tick
    }

    fun reset() {
        lastSuccessTick = null
    }
}
