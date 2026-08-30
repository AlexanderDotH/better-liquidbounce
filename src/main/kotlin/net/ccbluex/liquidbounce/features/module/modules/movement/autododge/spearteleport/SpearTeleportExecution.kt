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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

internal fun buildSpearTeleportPath(
    from: Vec3,
    to: Vec3,
    stepDistance: Double,
    maxPackets: Int,
): List<Vec3>? {
    require(stepDistance > 0.0 && stepDistance.isFinite()) { "Step distance must be finite and positive" }
    require(maxPackets > 0) { "Maximum packet count must be positive" }
    val packetCount = ceil(from.distanceTo(to) / stepDistance).toInt().coerceAtLeast(1)
    if (packetCount > maxPackets) return null
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
    if (!isStillSafe()) return false
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
    requiresLandingSupport: Boolean = true,
): Boolean = loaded && withinWorldBorder && destinationCollisionFree && routeCollisionFree &&
    (!requiresLandingSupport || supported && !overVoid)

internal const val SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE = 0.25

internal fun buildSpearTeleportCollisionSamples(from: Vec3, to: Vec3): List<Vec3> {
    val sampleCount = ceil(from.distanceTo(to) / SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE).toInt().coerceAtLeast(1)
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
