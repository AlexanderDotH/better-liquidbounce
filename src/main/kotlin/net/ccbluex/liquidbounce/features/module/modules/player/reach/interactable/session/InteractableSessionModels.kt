/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

import net.minecraft.world.phys.Vec3

internal data class InteractableSessionSettings(
    val openRetries: Int,
    val openTimeoutTicks: Int,
    val routeTimeoutTicks: Int,
    val holdTimeoutTicks: Int,
) {
    init {
        require(openRetries >= 0) { "Open retries must not be negative" }
        require(openTimeoutTicks > 0) { "Open timeout must be positive" }
        require(routeTimeoutTicks > 0) { "Route timeout must be positive" }
        require(holdTimeoutTicks >= 0) { "Hold timeout must not be negative" }
    }
}

internal sealed interface InteractableSessionState {
    data object Idle : InteractableSessionState
    data class Planning(val startedTick: Int) : InteractableSessionState
    data class Outbound(
        val startedTick: Int,
        val confirmedSteps: Int,
        val totalSteps: Int,
    ) : InteractableSessionState
    data class Opening(
        val attemptsSent: Int,
        val attemptStartedTick: Int,
    ) : InteractableSessionState
    data class Holding(
        val containerId: Int,
        val startedTick: Int,
    ) : InteractableSessionState
    data class Returning(
        val cause: InteractableSessionCause,
        val startedTick: Int,
        val confirmedMovements: Int,
        val totalMovements: Int,
    ) : InteractableSessionState
    data class Recovering(
        val cause: InteractableSessionCause,
        val startedTick: Int,
        val confirmedMovements: Int,
        val totalMovements: Int,
        val timeoutReported: Boolean = false,
    ) : InteractableSessionState
}

internal enum class InteractablePacketDisposition {
    DELIVERED,
    CANCELLED,
    QUEUED,
    DROPPED,
}

internal enum class InteractableSessionCause {
    COMPLETED,
    PLANNING_FAILED,
    PLANNING_TIMEOUT,
    ROUTE_TIMEOUT,
    OPEN_TIMEOUT,
    USER_CLOSE,
    SERVER_CLOSE,
    HOLD_TIMEOUT,
    DISABLE,
    TARGET_LOST,
    TARGET_CHANGED,
    ROUTE_BLOCKED,
    CONFLICTING_SCREEN,
    CORRECTION,
    WORLD_CHANGE,
    DISCONNECT,
    DEATH,
    RESYNC_REQUIRED,
}

internal enum class InteractableContainerCloseCause {
    USER,
    SERVER,
}

internal data class InteractableMovement<P : Any>(
    val payload: P,
    val confirmedPosition: Vec3,
) {
    init {
        require(confirmedPosition.hasFiniteCoordinates()) { "Confirmed movement position must be finite" }
    }
}

/** One outbound instruction and the transport-specific exact inverse back to its preceding checkpoint. */
internal class InteractableRouteStep<P : Any>(
    val outbound: InteractableMovement<P>,
    inverse: List<InteractableMovement<P>>,
) {
    val inverse: List<InteractableMovement<P>> = inverse.toList()
}

/**
 * An immutable route whose every confirmed outbound prefix has a prevalidated exact return.
 *
 * A VClip instruction may provide several inverse packets. Status-only transport primers may use
 * the same confirmed position as the preceding checkpoint and therefore have an empty inverse.
 */
internal class InteractableSessionRoute<P : Any>(
    val origin: Vec3,
    steps: List<InteractableRouteStep<P>>,
) {
    val steps: List<InteractableRouteStep<P>> = steps.toList()
    val endpoint: Vec3

    init {
        require(origin.hasFiniteCoordinates()) { "Interactable route origin must be finite" }
        require(this.steps.isNotEmpty()) { "Interactable route must contain outbound movement" }

        var precedingCheckpoint = origin
        this.steps.forEach { step ->
            val inverseEndpoint = step.inverse.lastOrNull()?.confirmedPosition
            require(inverseEndpoint?.matches(precedingCheckpoint) == true ||
                step.inverse.isEmpty() && step.outbound.confirmedPosition.matches(precedingCheckpoint)
            ) {
                "Every interactable route step must return exactly to its preceding checkpoint"
            }
            precedingCheckpoint = step.outbound.confirmedPosition
        }
        endpoint = precedingCheckpoint
    }

    fun exactReturnForPrefix(confirmedSteps: Int): List<InteractableMovement<P>> {
        require(confirmedSteps in 0..steps.size) { "Confirmed prefix is outside the route" }
        return steps.take(confirmedSteps).asReversed().flatMap(InteractableRouteStep<P>::inverse)
    }
}

internal sealed interface InteractableSessionEffect {
    data class OpenAttempt(val attempt: Int) : InteractableSessionEffect
    data class CloseOwnedContainer(val containerId: Int) : InteractableSessionEffect
    data class ReturnStarted(
        val cause: InteractableSessionCause,
        val fromPosition: Vec3,
    ) : InteractableSessionEffect
    data class RecoveryStarted(
        val cause: InteractableSessionCause,
        val fromPosition: Vec3,
    ) : InteractableSessionEffect
    data class RecoveryStalled(val cause: InteractableSessionCause) : InteractableSessionEffect
    data class ReleaseMovementLease(val cause: InteractableSessionCause) : InteractableSessionEffect
    data class AcceptCorrectionLocally(val authoritativePosition: Vec3) : InteractableSessionEffect
}

internal data class InteractableMovementConfirmation(
    val matchedPacket: Boolean,
    val committed: Boolean,
    val effects: List<InteractableSessionEffect> = emptyList(),
)

internal sealed interface InteractableCorrectionDecision {
    data object Ignored : InteractableCorrectionDecision
    data class Recovering(val effects: List<InteractableSessionEffect>) : InteractableCorrectionDecision
    data class Completed(val effects: List<InteractableSessionEffect>) : InteractableCorrectionDecision
    data class AcceptLocally(val effects: List<InteractableSessionEffect>) : InteractableCorrectionDecision
}

internal fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal fun Vec3.matches(other: Vec3): Boolean = distanceToSqr(other) < POSITION_EPSILON_SQUARED

private const val POSITION_EPSILON_SQUARED = 1.0E-8
