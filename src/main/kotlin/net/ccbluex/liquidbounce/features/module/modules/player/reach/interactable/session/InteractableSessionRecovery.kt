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

internal fun <T : Any, P : Any> InteractableSession<T, P>.beginReturn(
    cause: InteractableSessionCause,
    tick: Int,
    closeOwnedContainer: Boolean,
): List<InteractableSessionEffect> {
    val exactReturn = capturedRoute().exactReturnForPrefix(confirmedOutboundSteps)
    return installTravel(
        cause = cause,
        recovery = false,
        movements = exactReturn,
        tick = tick,
        closeOwnedContainer = closeOwnedContainer,
    )
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.beginRecovery(
    cause: InteractableSessionCause,
    tick: Int,
): List<InteractableSessionEffect> {
    val exactReturn = capturedRouteOrNull()?.exactReturnForPrefix(confirmedOutboundSteps).orEmpty()
    return installTravel(
        cause = cause,
        recovery = true,
        movements = exactReturn,
        tick = tick,
        closeOwnedContainer = true,
    )
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.promoteReturnToRecovery(
    cause: InteractableSessionCause,
    tick: Int,
): List<InteractableSessionEffect> {
    pendingMovement = null
    val remaining = movementQueue.map(InteractableQueuedMovement<P>::movement)
    return installRecovery(
        cause = cause,
        fromPosition = requireNotNull(confirmedPosition),
        recovery = remaining,
        tick = tick,
        closeOwnedContainer = true,
    )
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.installTravel(
    cause: InteractableSessionCause,
    recovery: Boolean,
    movements: List<InteractableMovement<P>>,
    tick: Int,
    closeOwnedContainer: Boolean,
): List<InteractableSessionEffect> {
    val fromPosition = requireNotNull(confirmedPosition)
    if (movements.isEmpty()) {
        val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
        return closeEffects + releaseAtPosition(cause, requireNotNull(origin))
    }
    return if (recovery) {
        installRecovery(cause, fromPosition, movements, tick, closeOwnedContainer)
    } else {
        installReturn(cause, fromPosition, movements, tick, closeOwnedContainer)
    }
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.installReturn(
    cause: InteractableSessionCause,
    fromPosition: Vec3,
    movements: List<InteractableMovement<P>>,
    tick: Int,
    closeOwnedContainer: Boolean,
): List<InteractableSessionEffect> {
    installMovementPlan(fromPosition, movements)
    state = InteractableSessionState.Returning(cause, tick, 0, movements.size)
    val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
    return closeEffects + InteractableSessionEffect.ReturnStarted(cause, fromPosition)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.installRecovery(
    cause: InteractableSessionCause,
    fromPosition: Vec3,
    recovery: List<InteractableMovement<P>>,
    tick: Int,
    closeOwnedContainer: Boolean,
): List<InteractableSessionEffect> {
    requireValidRecovery(fromPosition, recovery)
    confirmedPosition = fromPosition
    installMovementPlan(fromPosition, recovery)
    state = InteractableSessionState.Recovering(cause, tick, 0, recovery.size)
    val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
    return closeEffects + InteractableSessionEffect.RecoveryStarted(cause, fromPosition)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.installMovementPlan(
    fromPosition: Vec3,
    movements: List<InteractableMovement<P>>,
) {
    pendingMovement = null
    movementQueue.clear()
    movements.forEach { movementQueue += InteractableQueuedMovement(it) }
    recoveryCheckpoints = buildInteractableRecoveryCheckpoints(fromPosition, movements)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.buildInteractableRecoveryCheckpoints(
    fromPosition: Vec3,
    movements: List<InteractableMovement<P>>,
): List<InteractableRecoveryCheckpoint<P>> = buildList(movements.size + 1) {
    add(InteractableRecoveryCheckpoint(fromPosition, movements))
    movements.forEachIndexed { index, movement ->
        add(InteractableRecoveryCheckpoint(movement.confirmedPosition, movements.drop(index + 1)))
    }
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.knownRecoveryFrom(authoritativePosition: Vec3): List<InteractableMovement<P>>? {
    recoveryCheckpoints.lastOrNull { it.position.matches(authoritativePosition) }
        ?.let { return it.remainingMovements }

    val route = capturedRouteOrNull() ?: return null
    val matchingPrefix = (0 until confirmedOutboundSteps).lastOrNull { index ->
        route.steps[index].outbound.confirmedPosition.matches(authoritativePosition)
    } ?: return null
    return route.exactReturnForPrefix(matchingPrefix + 1)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.requireValidRecovery(
    authoritativePosition: Vec3,
    recovery: List<InteractableMovement<P>>,
) {
    val sessionOrigin = requireNotNull(origin)
    require(recovery.isNotEmpty() || authoritativePosition.matches(sessionOrigin)) {
        "Recovery outside the origin must contain movement"
    }
    require(recovery.lastOrNull()?.confirmedPosition?.matches(sessionOrigin) == true || recovery.isEmpty()) {
        "Validated interactable recovery must end at the captured origin"
    }
}
