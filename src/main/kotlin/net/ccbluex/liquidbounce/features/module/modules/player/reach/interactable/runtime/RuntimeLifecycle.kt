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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.runtime

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableControllerMessage
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeStatus
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.utils.client.mc

internal fun MinecraftReachInteractableRuntime.validateActiveTarget(tick: Int): Boolean {
    if (!session.state.requiresTargetValidation()) return true
    val allowInteractionStateChange = session.state is InteractableSessionState.Holding ||
        session.state is InteractableSessionState.Opening && state.openLifecycle.awaitingConfirmation
    if (controller.validateTarget(allowInteractionStateChange)) return true
    status = InteractableRuntimeStatus.Failure("TARGET_CHANGED")
    controller.abort(InteractableSessionCause.TARGET_CHANGED, tick)
    controller.reconcileOwnership()
    return false
}

internal fun MinecraftReachInteractableRuntime.clearTransientState() {
    if (controller.active) return
    state.activeSettings = null
    state.acceptsContainerPackets = false
    state.nextMovementTick = 0
    clearPendingPacket()
    state.immediatePacket = null
    state.immediateDisposition = null
    state.correctionContext = null
    state.deferredOpenAttempts.clear()
    state.openLifecycle.clear()
    state.lastTransportConfirmation = null
    state.interactionCaptureActive = false
    state.interactionDispositions.clear()
}

internal fun MinecraftReachInteractableRuntime.clearPendingPacket() {
    state.pendingTransportPacket = null
    state.pendingSessionIdentity = null
    state.pendingInstruction = null
}

internal fun MinecraftReachInteractableRuntime.currentTick(): Int = mc.player?.tickCount ?: 0

internal fun InteractableSessionState.requiresTargetValidation(): Boolean =
    this is InteractableSessionState.Outbound ||
        this is InteractableSessionState.Opening ||
        this is InteractableSessionState.Holding

internal fun InteractableControllerMessage.statusReason(): String = when (this) {
    InteractableControllerMessage.MovementBusy -> "REMOTE_MOVEMENT_BUSY"
    is InteractableControllerMessage.TargetRejected -> reason
    is InteractableControllerMessage.RouteFailed -> reason
}
