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

internal val HARD_RESET_CAUSES = setOf(
    InteractableSessionCause.WORLD_CHANGE,
    InteractableSessionCause.DISCONNECT,
    InteractableSessionCause.DEATH,
)

internal fun <T : Any, P : Any> InteractableSession<T, P>.releaseWithoutMovement(cause: InteractableSessionCause): List<InteractableSessionEffect> {
    val position = confirmedPosition
    clearActiveSession(position)
    return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.releaseAtPosition(
    cause: InteractableSessionCause,
    position: Vec3,
): List<InteractableSessionEffect> {
    clearActiveSession(position)
    return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.closeOwnedContainerEffect(): List<InteractableSessionEffect> {
    val containerId = ownedContainer ?: return emptyList()
    ownedContainer = null
    return listOf(InteractableSessionEffect.CloseOwnedContainer(containerId))
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.clearActiveSession(preservedPosition: Vec3?) {
    movementQueue.clear()
    pendingMovement = null
    route = null
    recoveryCheckpoints = emptyList()
    confirmedOutboundSteps = 0
    ownedContainer = null
    target = null
    origin = null
    settings = null
    confirmedPosition = preservedPosition
    state = InteractableSessionState.Idle
}

internal fun <T : Any, P : Any> InteractableSession<T, P>.capturedSettings() = requireNotNull(settings) { "Interactable settings were not captured" }

internal fun <T : Any, P : Any> InteractableSession<T, P>.capturedRoute() = requireNotNull(route) { "Interactable route was not captured" }

internal fun <T : Any, P : Any> InteractableSession<T, P>.capturedRouteOrNull() = route

internal fun <T : Any, P : Any> InteractableSession<T, P>.hasElapsed(
    startTick: Int,
    currentTick: Int,
    durationTicks: Int,
): Boolean =
    currentTick.toLong() - startTick.toLong() >= durationTicks.toLong()

internal fun InteractableSessionState.acceptsMovement(): Boolean =
    this is InteractableSessionState.Outbound ||
        this is InteractableSessionState.Returning ||
        this is InteractableSessionState.Recovering
