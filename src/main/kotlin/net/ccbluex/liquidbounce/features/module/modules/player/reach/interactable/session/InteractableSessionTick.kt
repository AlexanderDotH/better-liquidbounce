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

internal fun <T : Any, P : Any> InteractableSession<T, P>.tickOpening(
    current: InteractableSessionState.Opening,
    tick: Int,
): List<InteractableSessionEffect> {
    val settings = capturedSettings()
    if (current.attemptsSent == 0) return tickEndpointVerification(current, tick, settings)
    if (!hasElapsed(current.attemptStartedTick, tick, settings.openTimeoutTicks)) return emptyList()
    return if (current.attemptsSent <= settings.openRetries) {
        nextOpenAttempt(current, tick)
    } else {
        beginRecovery(InteractableSessionCause.OPEN_TIMEOUT, tick)
    }
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.tickEndpointVerification(
    current: InteractableSessionState.Opening,
    tick: Int,
    settings: InteractableSessionSettings,
): List<InteractableSessionEffect> {
    if (!hasElapsed(current.attemptStartedTick, tick, settings.endpointVerifyTicks)) return emptyList()
    state = current.copy(attemptsSent = 1, attemptStartedTick = tick)
    return listOf(InteractableSessionEffect.OpenAttempt(1))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.nextOpenAttempt(
    current: InteractableSessionState.Opening,
    tick: Int,
): List<InteractableSessionEffect> {
    val nextAttempt = current.attemptsSent + 1
    state = current.copy(attemptsSent = nextAttempt, attemptStartedTick = tick)
    return listOf(InteractableSessionEffect.OpenAttempt(nextAttempt))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.tickHolding(
    current: InteractableSessionState.Holding,
    tick: Int,
): List<InteractableSessionEffect> {
    val timeout = capturedSettings().holdTimeoutTicks
    if (timeout == 0 || !hasElapsed(current.startedTick, tick, timeout)) return emptyList()
    return beginReturn(InteractableSessionCause.HOLD_TIMEOUT, tick, closeOwnedContainer = true)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.tickReturning(
    current: InteractableSessionState.Returning,
    tick: Int,
): List<InteractableSessionEffect> {
    if (!hasElapsed(current.startedTick, tick, capturedSettings().routeTimeoutTicks)) return emptyList()
    return promoteReturnToRecovery(InteractableSessionCause.ROUTE_TIMEOUT, tick)
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.tickRecovering(
    current: InteractableSessionState.Recovering,
    tick: Int,
): List<InteractableSessionEffect> {
    if (current.timeoutReported || !hasElapsed(
            current.startedTick,
            tick,
            capturedSettings().routeTimeoutTicks,
        )) {
        return emptyList()
    }
    val position = requireNotNull(confirmedPosition)
    clearActiveSession(position)
    return listOf(
        InteractableSessionEffect.AcceptCorrectionLocally(position),
        InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
    )
}
