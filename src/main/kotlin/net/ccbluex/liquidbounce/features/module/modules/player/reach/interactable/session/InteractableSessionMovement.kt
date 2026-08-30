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

internal fun <T : Any, P : Any> InteractableSession<T, P>.movementCommitted(tick: Int): List<InteractableSessionEffect> = when (val current = state) {
    is InteractableSessionState.Outbound -> commitOutbound(current, tick)
    is InteractableSessionState.Returning -> commitReturn(current)
    is InteractableSessionState.Recovering -> commitRecovery(current)
    else -> error("Interactable movement committed outside a travel state")
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.commitOutbound(
    current: InteractableSessionState.Outbound,
    tick: Int,
): List<InteractableSessionEffect> {
    confirmedOutboundSteps++
    if (confirmedOutboundSteps < current.totalSteps) {
        state = current.copy(confirmedSteps = confirmedOutboundSteps)
        return emptyList()
    }

    val verificationTicks = capturedSettings().endpointVerifyTicks
    state = InteractableSessionState.Opening(
        attemptsSent = if (verificationTicks == 0) 1 else 0,
        attemptStartedTick = tick,
    )
    return if (verificationTicks == 0) listOf(InteractableSessionEffect.OpenAttempt(1)) else emptyList()
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.commitReturn(current: InteractableSessionState.Returning): List<InteractableSessionEffect> {
    val committed = current.confirmedMovements + 1
    if (movementQueue.isNotEmpty()) {
        state = current.copy(confirmedMovements = committed)
        return emptyList()
    }
    return releaseAtPosition(InteractableSessionCause.COMPLETED, requireNotNull(origin))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.commitRecovery(current: InteractableSessionState.Recovering): List<InteractableSessionEffect> {
    val committed = current.confirmedMovements + 1
    if (movementQueue.isNotEmpty()) {
        state = current.copy(confirmedMovements = committed)
        return emptyList()
    }
    return releaseAtPosition(InteractableSessionCause.COMPLETED, requireNotNull(origin))
}
