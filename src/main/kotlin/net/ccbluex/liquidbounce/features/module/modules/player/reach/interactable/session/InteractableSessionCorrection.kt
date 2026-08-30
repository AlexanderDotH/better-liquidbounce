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

internal fun <T : Any, P : Any> InteractableSession<T, P>.decideCorrection(
    authoritativePosition: Vec3,
    validatedRecovery: List<InteractableMovement<P>>?,
    tick: Int,
): InteractableCorrectionDecision {
    if (state === InteractableSessionState.Idle) return InteractableCorrectionDecision.Ignored
    require(authoritativePosition.hasFiniteCoordinates()) { "Authoritative correction must be finite" }
    val sessionOrigin = requireNotNull(origin)
    if (authoritativePosition.matches(sessionOrigin)) {
        val effects = closeOwnedContainerEffect() + releaseAtPosition(
            InteractableSessionCause.COMPLETED,
            sessionOrigin,
        )
        return InteractableCorrectionDecision.Completed(effects)
    }

    val recovery = knownRecoveryFrom(authoritativePosition) ?: validatedRecovery?.also {
        requireValidRecovery(authoritativePosition, it)
    }
    if (recovery != null) {
        val effects = installRecovery(
            cause = InteractableSessionCause.CORRECTION,
            fromPosition = authoritativePosition,
            recovery = recovery,
            tick = tick,
            closeOwnedContainer = true,
        )
        return InteractableCorrectionDecision.Recovering(effects)
    }

    val effects = closeOwnedContainerEffect() + listOf(
        InteractableSessionEffect.AcceptCorrectionLocally(authoritativePosition),
        InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
    )
    clearActiveSession(authoritativePosition)
    return InteractableCorrectionDecision.AcceptLocally(effects)
}
