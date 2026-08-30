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

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractablePacketInstruction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableResolvedInteraction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeStatus
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.interactWithVanillaHandOrder
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.interactionDeliveryConfirmed
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionEffect
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.Packet
import net.minecraft.world.phys.Vec3

internal fun MinecraftReachInteractableRuntime.drainDeferredOpenAttempts() {
    if (session.state !is InteractableSessionState.Opening) {
        state.deferredOpenAttempts.clear()
        return
    }
    while (state.deferredOpenAttempts.isNotEmpty() && session.state is InteractableSessionState.Opening) {
        executeEffect(state.deferredOpenAttempts.removeFirst())
    }
}

internal fun MinecraftReachInteractableRuntime.confirmImmediatePacket(
    packet: Packet<*>,
    cancelled: Boolean,
) {
    state.immediateDisposition = packet.finalDisposition(
        cancelled,
        TransferOrigin.OUTGOING,
        packetRateLimitDispositionPort,
    )
}

internal fun MinecraftReachInteractableRuntime.executeEffects(effects: List<InteractableSessionEffect>) {
    effects.forEach(::executeEffect)
    controller.reconcileOwnership()
    clearTransientState()
}

internal fun MinecraftReachInteractableRuntime.executeEffect(effect: InteractableSessionEffect) {
    when (effect) {
        is InteractableSessionEffect.OpenAttempt -> handleOpenAttempt(effect)
        is InteractableSessionEffect.CloseOwnedContainer -> closeOwnedContainer(effect.containerId)
        is InteractableSessionEffect.ReturnStarted -> status = InteractableRuntimeStatus.State(session.state)
        is InteractableSessionEffect.RecoveryStarted -> status = InteractableRuntimeStatus.Recovery(effect.cause)
        is InteractableSessionEffect.RecoveryStalled ->
            status = InteractableRuntimeStatus.RecoveryStalled(effect.cause)
        is InteractableSessionEffect.ReleaseMovementLease -> releaseMovementLease(effect.cause)
        is InteractableSessionEffect.AcceptCorrectionLocally ->
            acceptCorrectionLocally(effect.authoritativePosition)
    }
}

private fun MinecraftReachInteractableRuntime.handleOpenAttempt(
    effect: InteractableSessionEffect.OpenAttempt,
) {
    if (!openTarget() && session.state is InteractableSessionState.Opening) {
        status = InteractableRuntimeStatus.Failure("OPEN_ATTEMPT_" + effect.attempt + "_FAILED")
    }
}

private fun MinecraftReachInteractableRuntime.releaseMovementLease(cause: InteractableSessionCause) {
    if (status !is InteractableRuntimeStatus.Resynchronized) {
        status = if (cause in SILENT_RELEASE_CAUSES) {
            InteractableRuntimeStatus.State(session.state)
        } else {
            InteractableRuntimeStatus.Terminated(cause)
        }
    }
    clearTransientState()
}

private fun MinecraftReachInteractableRuntime.acceptCorrectionLocally(position: Vec3) {
    mc.player?.let { player ->
        player.setPos(position)
        player.deltaMovement = Vec3.ZERO
    }
    status = InteractableRuntimeStatus.Resynchronized(position)
    clearTransientState()
}

private fun MinecraftReachInteractableRuntime.openTarget(): Boolean {
    val target = session.target ?: return false
    if (!controller.validateTarget()) {
        status = InteractableRuntimeStatus.Failure("TARGET_CHANGED")
        executeEffects(session.abort(InteractableSessionCause.TARGET_CHANGED, currentTick()))
        return false
    }
    val anchor = session.serverAnchorPosition ?: return false
    val eyePosition = anchor.eyePosition()
    val interactionRange = state.activeSettings?.interactionRange ?: return false
    val interaction = interactionPort.resolve(target, eyePosition, interactionRange) ?: return false
    val rotation = Rotation.lookingAt(interaction.point, eyePosition)
    val rotationPacket = InteractablePacketInstruction.Position(
        anchor,
        fullPacket = true,
        onGround = true,
    ).toPacket(rotation)
    if (sendImmediate(rotationPacket) != InteractablePacketDisposition.DELIVERED) return false
    return interactFromAnchor(anchor, interaction)
}

private fun MinecraftReachInteractableRuntime.interactFromAnchor(
    anchor: Vec3,
    interaction: InteractableResolvedInteraction,
): Boolean {
    val player = mc.player ?: return false
    val localPosition = player.position()
    val localVelocity = player.deltaMovement
    state.interactionDispositions.clear()
    state.interactionCaptureActive = true
    return try {
        player.setPos(anchor)
        val handled = interactWithVanillaHandOrder { hand -> interaction.interact(hand) }
        interactionDeliveryConfirmed(handled, state.interactionDispositions)
    } finally {
        state.interactionCaptureActive = false
        player.setPos(localPosition)
        player.deltaMovement = localVelocity
    }
}

private fun MinecraftReachInteractableRuntime.sendImmediate(
    packet: Packet<*>,
): InteractablePacketDisposition {
    state.immediatePacket = packet
    state.immediateDisposition = null
    mc.connection?.send(packet)
    return (state.immediateDisposition ?: InteractablePacketDisposition.DROPPED).also {
        state.immediatePacket = null
        state.immediateDisposition = null
    }
}

private val SILENT_RELEASE_CAUSES = setOf(
    InteractableSessionCause.COMPLETED,
    InteractableSessionCause.DISABLE,
    InteractableSessionCause.WORLD_CHANGE,
    InteractableSessionCause.DISCONNECT,
    InteractableSessionCause.DEATH,
)
