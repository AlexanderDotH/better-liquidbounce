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

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeStatus
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.shouldRewriteInteractableAmbientMovement
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovementConfirmation
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionEffect
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun MinecraftReachInteractableRuntime.processOutgoingPacket(event: PacketEvent) {
    if (event.origin != TransferOrigin.OUTGOING) return
    val packet = event.packet
    when {
        packet === state.immediatePacket -> confirmImmediatePacket(packet, event.isCancelled)
        state.interactionCaptureActive && packet.isContainerInteractionPacket() -> {
            state.interactionDispositions += packet.finalDisposition(
                event.isCancelled,
                TransferOrigin.OUTGOING,
                packetRateLimitDispositionPort,
            )
        }
        packet is ServerboundMovePlayerPacket && packet === state.pendingTransportPacket -> {
            confirmPendingTransport(packet, event.isCancelled)
        }
        packet is ServerboundMovePlayerPacket && shouldRewriteAmbientMovement() -> {
            session.serverAnchorPosition?.let { packet.rewritePosition(it) }
            packet.finalDisposition(
                event.isCancelled,
                TransferOrigin.OUTGOING,
                packetRateLimitDispositionPort,
            )
        }
    }
}

private fun MinecraftReachInteractableRuntime.shouldRewriteAmbientMovement() =
    shouldRewriteInteractableAmbientMovement(
        movementLeaseRequired = session.movementLeaseRequired,
        correctionInProgress = state.correctionContext != null,
    )

internal fun MinecraftReachInteractableRuntime.dispatchNextMovement(tick: Int) {
    if (tick < state.nextMovementTick || state.pendingTransportPacket != null) return
    if (!session.state.acceptsMovement()) return
    val burst = MovementBurst()
    while (session.state.acceptsMovement()) {
        val next = session.nextMovement() ?: return
        if (!burst.accept(next.payload.transportBurstId)) return
        if (!dispatchOneMovement(tick)) return
        if (!burst.hasTransportId) return
    }
}

private class MovementBurst {
    private var transportId: Int? = null

    val hasTransportId: Boolean
        get() = transportId != null

    fun accept(nextTransportId: Int?): Boolean {
        if (transportId != null && nextTransportId != transportId) return false
        if (transportId == null) transportId = nextTransportId
        return true
    }
}

private fun MinecraftReachInteractableRuntime.dispatchOneMovement(tick: Int): Boolean {
    val identity = Any()
    val movement = session.prepareMovement(identity) ?: return false
    val confirmedOrigin = session.confirmedPosition ?: return false
    if (!movement.payload.isSafeToSend(this, confirmedOrigin, movement.confirmedPosition)) {
        status = InteractableRuntimeStatus.Failure("ROUTE_BLOCKED")
        session.confirmMovement(identity, InteractablePacketDisposition.DROPPED, tick)
        controller.abort(InteractableSessionCause.ROUTE_BLOCKED, tick)
        return false
    }
    val packet = movement.payload.toPacket()
    state.pendingTransportPacket = packet
    state.pendingSessionIdentity = identity
    state.pendingInstruction = movement.payload
    val connection = mc.connection ?: return rejectDisconnectedMovement(identity, tick)
    state.lastTransportConfirmation = null
    connection.send(packet)
    if (state.pendingTransportPacket === packet) return rejectUndeliveredMovement(identity, tick)
    return state.lastTransportConfirmation?.committed == true
}

private fun MinecraftReachInteractableRuntime.rejectDisconnectedMovement(identity: Any, tick: Int): Boolean {
    clearPendingPacket()
    session.confirmMovement(identity, InteractablePacketDisposition.DROPPED, tick)
    hardReset(InteractableSessionCause.DISCONNECT)
    return false
}

private fun MinecraftReachInteractableRuntime.rejectUndeliveredMovement(identity: Any, tick: Int): Boolean {
    clearPendingPacket()
    executeEffects(
        session.rejectMovement(
            identity,
            InteractablePacketDisposition.DROPPED,
            InteractableSessionCause.ROUTE_BLOCKED,
            tick,
        ),
    )
    return false
}

private fun MinecraftReachInteractableRuntime.confirmPendingTransport(
    packet: ServerboundMovePlayerPacket,
    cancelled: Boolean,
) {
    state.pendingInstruction?.applyTo(packet)
    val identity = state.pendingSessionIdentity ?: return
    val disposition = packet.finalDisposition(
        cancelled,
        TransferOrigin.OUTGOING,
        packetRateLimitDispositionPort,
    )
    val confirmation = confirmMovementDisposition(identity, disposition)
    state.lastTransportConfirmation = confirmation
    clearPendingPacket()
    if (confirmation.committed) {
        state.nextMovementTick = currentTick() + (state.activeSettings?.routing?.stepDelayTicks ?: 0)
    }
    dispatchConfirmationEffects(confirmation)
    controller.reconcileOwnership()
    clearTransientState()
}

private fun MinecraftReachInteractableRuntime.confirmMovementDisposition(
    identity: Any,
    disposition: InteractablePacketDisposition,
): InteractableMovementConfirmation {
    if (disposition == InteractablePacketDisposition.DELIVERED) {
        return session.confirmMovement(identity, disposition, currentTick())
    }
    executeEffects(
        session.rejectMovement(
            identity,
            disposition,
            InteractableSessionCause.ROUTE_BLOCKED,
            currentTick(),
        ),
    )
    return InteractableMovementConfirmation(matchedPacket = true, committed = false)
}

private fun MinecraftReachInteractableRuntime.dispatchConfirmationEffects(
    confirmation: InteractableMovementConfirmation,
) {
    confirmation.effects.forEach { effect ->
        if (effect is InteractableSessionEffect.OpenAttempt) {
            state.deferredOpenAttempts += effect
        } else {
            executeEffect(effect)
        }
    }
}
