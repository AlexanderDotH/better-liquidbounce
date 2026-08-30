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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.cubecraft

import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

internal fun cubeCraftPositionBehind(
    targetPosition: Vec3,
    targetYaw: Float,
    distance: Double,
): Vec3 {
    val yawRadians = Math.toRadians(targetYaw.toDouble())
    return targetPosition.add(sin(yawRadians) * distance, 0.0, -cos(yawRadians) * distance)
}

internal fun cubeCraftSearchOffsets(radius: Int): List<Pair<Int, Int>> {
    require(radius >= 0) { "radius must not be negative" }
    return (-radius..radius).flatMap { x ->
        (-radius..radius).map { z -> x to z }
    }.sortedBy { (x, z) -> x * x + z * z }
}

internal class CubeCraftTargetStrafeTracker {
    private enum class State {
        WAITING_DAMAGE,
        READY,
        TELEPORTING,
        FALLBACK,
        TELEPORTED,
    }

    private var state = State.WAITING_DAMAGE
    private var targetId: Int? = null

    var lockedDestination: Vec3? = null
        private set

    val useInputFallback get() = state != State.TELEPORTING && state != State.TELEPORTED
    val teleported get() = state == State.TELEPORTED

    fun tracksTarget(targetId: Int) = this.targetId == targetId

    fun hasLockFor(targetId: Int) = tracksTarget(targetId) && lockedDestination != null

    fun lock(targetId: Int, destination: Vec3) {
        if (hasLockFor(targetId)) return
        this.targetId = targetId
        lockedDestination = destination
        state = State.WAITING_DAMAGE
    }

    fun invalidateLock() {
        lockedDestination = null
        state = State.WAITING_DAMAGE
    }

    fun confirmDamage() {
        if (lockedDestination == null || state == State.TELEPORTING || state == State.TELEPORTED) return
        state = State.READY
    }

    fun takeTeleportRequest(): Vec3? {
        if (state != State.READY) return null
        state = State.TELEPORTING
        return lockedDestination
    }

    fun completeTeleport(success: Boolean) {
        if (state != State.TELEPORTING) return
        state = if (success) State.TELEPORTED else State.FALLBACK
    }

    fun updatePosition(position: Vec3, arrivalDistance: Double) {
        require(arrivalDistance >= 0.0) { "arrivalDistance must not be negative" }
        if (state != State.FALLBACK) return
        val destination = lockedDestination ?: return
        if (position.distanceToSqr(destination) <= arrivalDistance * arrivalDistance) {
            state = State.TELEPORTED
        }
    }

    fun reset() {
        state = State.WAITING_DAMAGE
        targetId = null
        lockedDestination = null
    }
}
