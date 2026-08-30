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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.buildLinearTeleportPath
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

internal enum class ReachHitMode(
    override val tag: String,
    override val tagAliases: List<String> = emptyList(),
) : Tagged {
    PACKET("Packet", listOf("Direct", "SinglePacket")),
    A_STAR("AStar"),
    ADAPTIVE("Adaptive"),
    MOTION("Motion"),
    PULSE("Pulse"),
    SENTINEL("Sentinel", listOf("Cubecraft", "Cube Craft"));

    val usesPacketTravel: Boolean
        get() = this == PACKET || this == A_STAR || this == ADAPTIVE || this == PULSE
}

internal class ReachHitExecutionMode {
    var current: ReachHitMode? = null
        private set

    fun capture(configuredMode: ReachHitMode): ReachHitMode {
        check(current == null) { "A Reach Hit execution mode is already captured" }
        current = configuredMode
        return configuredMode
    }

    fun clear() {
        current = null
    }
}

/** Prevents automatic failed routes from being recomputed on every KillAura click. */
internal class ReachHitAutomaticRetryGate(private val retryDelayTicks: Int) {
    private var failedTargetId: Int? = null
    private var retryAtTick = 0

    init {
        require(retryDelayTicks > 0) { "retryDelayTicks must be positive" }
    }

    fun canAttempt(targetId: Int, currentTick: Int): Boolean =
        failedTargetId != targetId || currentTick >= retryAtTick

    fun recordFailure(targetId: Int, currentTick: Int) {
        failedTargetId = targetId
        retryAtTick = currentTick + retryDelayTicks
    }

    fun recordSuccess() = clear()

    fun clear() {
        failedTargetId = null
        retryAtTick = 0
    }
}

internal fun buildReachHitTravelPath(
    mode: ReachHitMode,
    from: Vec3,
    to: Vec3,
    stepSize: Double,
): List<Vec3> = when (mode) {
    ReachHitMode.PACKET, ReachHitMode.ADAPTIVE, ReachHitMode.PULSE ->
        buildLinearTeleportPath(from, to, stepSize)
    ReachHitMode.A_STAR, ReachHitMode.MOTION, ReachHitMode.SENTINEL -> emptyList()
}

internal fun calculateReachHitAdaptiveStepSizes(
    initialStep: Double,
    minimumStep: Double,
    retries: Int,
): List<Double> {
    require(initialStep > 0.0) { "initialStep must be positive" }
    require(minimumStep > 0.0) { "minimumStep must be positive" }
    require(retries >= 0) { "retries must not be negative" }

    val effectiveMinimum = minimumStep.coerceAtMost(initialStep)
    var step = initialStep
    return List(retries + 1) {
        val current = step
        step = (step / 2.0).coerceAtLeast(effectiveMinimum)
        current
    }
}

internal suspend fun executeAdaptiveReachHit(
    stepSizes: List<Double>,
    attempt: suspend (Double) -> Boolean,
    onAccepted: suspend (Double) -> Boolean,
    onExhausted: suspend () -> Unit,
): Boolean {
    require(stepSizes.isNotEmpty()) { "stepSizes must not be empty" }

    for (step in stepSizes) {
        if (attempt(step)) {
            return onAccepted(step)
        }
    }

    onExhausted()
    return false
}

internal fun buildReachHitAStarReturnPath(origin: Vec3, outward: List<Vec3>): List<Vec3> =
    outward.dropLast(1).asReversed() + origin

internal fun isReachHitAttackReady(usesAttackCooldown: Boolean, attackStrength: Float): Boolean =
    !usesAttackCooldown || attackStrength > REACH_HIT_MIN_ATTACK_STRENGTH

internal fun shouldRenderReachHitTracer(tracersEnabled: Boolean, hasTarget: Boolean): Boolean =
    tracersEnabled && hasTarget

internal fun isWithinReachHitTargetRange(
    distanceSquared: Double,
    minRange: Float,
    maxRange: Float,
): Boolean = distanceSquared > minRange.sq() && distanceSquared <= maxRange.sq()

internal fun calculateReachHitDestination(
    origin: Vec3,
    targetPosition: Vec3,
    playerWidth: Double,
    targetWidth: Double,
): Vec3 {
    require(playerWidth >= 0.0) { "Player width must not be negative" }
    require(targetWidth >= 0.0) { "Target width must not be negative" }

    val towardOrigin = Vec3(origin.x - targetPosition.x, 0.0, origin.z - targetPosition.z)
    val direction = if (towardOrigin.lengthSqr() > REACH_HIT_DIRECTION_EPSILON) {
        towardOrigin.normalize()
    } else {
        Vec3(1.0, 0.0, 0.0)
    }
    val collisionClearance = (playerWidth + targetWidth) / 2.0 + REACH_HIT_COLLISION_PADDING
    val axisProjection = max(abs(direction.x), abs(direction.z))
    val clearance = collisionClearance / axisProjection

    return targetPosition.add(direction.scale(clearance))
}

internal suspend fun executeRoundTripReachHit(
    origin: Vec3,
    destination: Vec3,
    stayTicks: Int,
    teleport: suspend (Vec3) -> Boolean,
    shouldRecover: () -> Boolean,
    synchronizeRotation: () -> Unit,
    attack: () -> Boolean,
    wait: suspend (Int) -> Unit,
): ReachHitRoundTripOutcome {
    require(stayTicks >= 0) { "stayTicks must not be negative" }

    if (!teleport(destination)) {
        val recovered = shouldRecover() && withContext(NonCancellable) { teleport(origin) }
        return ReachHitRoundTripOutcome(attacked = false, returned = recovered)
    }

    var attacked = false
    var returned = false
    try {
        synchronizeRotation()
        attacked = attack()
        if (attacked && stayTicks > 0) {
            wait(stayTicks)
        }
    } finally {
        returned = withContext(NonCancellable) { teleport(origin) }
    }

    return ReachHitRoundTripOutcome(attacked = attacked, returned = returned)
}

internal data class ReachHitRoundTripOutcome(val attacked: Boolean, val returned: Boolean) {
    companion object {
        val NOT_STARTED = ReachHitRoundTripOutcome(attacked = false, returned = false)
    }
}

internal const val REACH_HIT_MOVEMENT_OWNER = "ReachHit"
internal const val REACH_HIT_AUTOMATIC_RETRY_DELAY_TICKS = 10
internal const val REACH_HIT_HOME_DISTANCE_SQUARED = 4.0

private const val REACH_HIT_MIN_ATTACK_STRENGTH = 0.9f
private const val REACH_HIT_COLLISION_PADDING = 0.1
private const val REACH_HIT_DIRECTION_EPSILON = 1.0E-9
