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
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.PacketRateLimitDispositionPort
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableContainerCloseCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableOpenLifecycleAction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket

internal fun MinecraftReachInteractableRuntime.observeContainerPacket(event: PacketEvent) {
    if (!state.acceptsContainerPackets) return
    val fact = event.toContainerPacketFact(packetRateLimitDispositionPort) ?: return
    mc.execute { applyContainerFact(fact) }
}

private fun PacketEvent.toContainerPacketFact(
    rateLimitDispositionPort: PacketRateLimitDispositionPort,
): ContainerPacketFact? {
    return when {
        origin == TransferOrigin.INCOMING && packet is ClientboundOpenScreenPacket ->
            ContainerPacketFact.Open(
                packet.containerId,
                packet.finalDisposition(isCancelled, origin, rateLimitDispositionPort),
            )
        origin == TransferOrigin.INCOMING && packet is ClientboundContainerClosePacket ->
            ContainerPacketFact.Close(
                packet.containerId,
                InteractableContainerCloseCause.SERVER,
                packet.finalDisposition(isCancelled, origin, rateLimitDispositionPort),
            )
        origin == TransferOrigin.OUTGOING && packet is ServerboundContainerClosePacket ->
            ContainerPacketFact.Close(
                packet.containerId,
                InteractableContainerCloseCause.USER,
                packet.finalDisposition(isCancelled, origin, rateLimitDispositionPort),
            )
        else -> null
    }
}

internal fun MinecraftReachInteractableRuntime.reconcileScreen(screen: Screen?) {
    val container = screen as? AbstractContainerScreen<*>
    val owned = session.ownedContainerId
    if (owned == null) {
        reconcileUnownedScreen(container)
        return
    }
    if (container != null && container.menu.containerId == owned) return
    if (container != null || mc.player?.containerMenu?.containerId != owned) {
        executeEffects(session.containerClosed(owned, InteractableContainerCloseCause.SERVER, currentTick()))
        controller.reconcileOwnership()
    }
}

private fun MinecraftReachInteractableRuntime.reconcileUnownedScreen(
    container: AbstractContainerScreen<*>?,
) {
    if (container == null || !session.movementLeaseRequired ||
        session.state is InteractableSessionState.Opening
    ) {
        return
    }
    controller.abort(InteractableSessionCause.CONFLICTING_SCREEN, currentTick())
    controller.reconcileOwnership()
}

private fun MinecraftReachInteractableRuntime.applyContainerFact(fact: ContainerPacketFact) {
    when (fact) {
        is ContainerPacketFact.Open -> observeOpen(fact)
        is ContainerPacketFact.Close -> observeClose(fact)
    }
    controller.reconcileOwnership()
}

private fun MinecraftReachInteractableRuntime.observeOpen(fact: ContainerPacketFact.Open) {
    applyOpenLifecycleAction(
        state.openLifecycle.observe(
            fact.containerId,
            currentTick(),
            fact.disposition,
            opening = session.state is InteractableSessionState.Opening,
        ),
    )
}

private fun MinecraftReachInteractableRuntime.observeClose(fact: ContainerPacketFact.Close) {
    if (fact.cause == InteractableContainerCloseCause.USER &&
        fact.disposition != InteractablePacketDisposition.DELIVERED
    ) {
        forceCloseServerContainer(fact.containerId)
    }
    executeEffects(session.containerClosed(fact.containerId, fact.cause, currentTick()))
    if (fact.cause == InteractableContainerCloseCause.SERVER &&
        fact.disposition != InteractablePacketDisposition.DELIVERED
    ) {
        closeOwnedContainer(fact.containerId)
    }
}

internal fun MinecraftReachInteractableRuntime.evaluateOpenLifecycle(tick: Int) {
    val screenContainerId = (mc.gui.screen() as? AbstractContainerScreen<*>)?.menu?.containerId
    val playerMenuId = mc.player?.containerMenu?.containerId
    applyOpenLifecycleAction(state.openLifecycle.evaluate(tick, screenContainerId, playerMenuId))
}

private fun MinecraftReachInteractableRuntime.applyOpenLifecycleAction(
    action: InteractableOpenLifecycleAction?,
) {
    when (action) {
        null -> Unit
        is InteractableOpenLifecycleAction.Confirm -> confirmOpenedContainer(action.containerId)
        is InteractableOpenLifecycleAction.CloseUnexpected -> abortUnexpectedContainer(action.containerId)
        is InteractableOpenLifecycleAction.CloseAndAbort -> abortUnexpectedContainer(action.containerId)
    }
    controller.reconcileOwnership()
}

private fun MinecraftReachInteractableRuntime.confirmOpenedContainer(containerId: Int) {
    if (session.claimOpenedContainer(containerId, currentTick())) {
        status = InteractableRuntimeStatus.State(session.state)
        return
    }
    forceCloseServerContainer(containerId)
}

private fun MinecraftReachInteractableRuntime.abortUnexpectedContainer(containerId: Int) {
    forceCloseServerContainer(containerId)
    controller.abort(InteractableSessionCause.CONFLICTING_SCREEN, currentTick())
}

private fun MinecraftReachInteractableRuntime.forceCloseServerContainer(containerId: Int) {
    sendPacketSilently(ServerboundContainerClosePacket(containerId), bypassSilentPacketEvent = true)
    closeOwnedContainer(containerId)
}

internal fun MinecraftReachInteractableRuntime.closeOwnedContainer(containerId: Int) {
    val player = mc.player ?: return
    if (player.containerMenu.containerId == containerId) player.closeContainer()
}

internal fun MinecraftReachInteractableRuntime.abortRuntime(cause: InteractableSessionCause) {
    state.deferredOpenAttempts.clear()
    controller.abort(cause, currentTick())
    controller.reconcileOwnership()
    clearTransientState()
}

internal fun MinecraftReachInteractableRuntime.resetRuntime(cause: InteractableSessionCause) {
    state.deferredOpenAttempts.clear()
    controller.hardReset(cause)
    clearTransientState()
}
