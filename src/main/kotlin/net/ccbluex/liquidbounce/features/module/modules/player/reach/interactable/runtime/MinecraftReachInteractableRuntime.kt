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
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSession
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.input.InputTracker.wasPressedRecently
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player

internal class MinecraftReachInteractableRuntime(
    internal val packetRateLimitDispositionPort: PacketRateLimitDispositionPort,
    targetPort: ControllerTargetPort<InteractableRuntimeTarget>,
    routePort: ControllerRoutePort<
        InteractableRuntimeTarget,
        InteractableSessionRoute<InteractablePacketInstruction>,
        InteractableRenderSnapshot,
    >,
    internal val interactionPort: InteractableInteractionPort,
) {
    internal val session = InteractableSession<InteractableRuntimeTarget, InteractablePacketInstruction>()
    private val sessionPort = MinecraftInteractableSessionPort(session) { effects -> executeEffects(effects) }
    internal val controller = ReachInteractableController(
        MinecraftMovementOwnership,
        targetPort,
        routePort,
        sessionPort,
    )
    internal val state = RuntimeState()

    var status: InteractableRuntimeStatus? = null
        internal set

    val active: Boolean
        get() = controller.active

    val renderSnapshot: InteractableRenderSnapshot?
        get() = controller.renderSnapshot

    fun claimUse(settings: InteractableSettingsSnapshot): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        if (gameMode.isDestroying || player.isHandsBusy) return false
        if (!mc.options.keyUse.wasPressedRecently(FRESH_USE_WINDOW_MS)) return false
        val claimed = controller.claim(
            normalInteractionAvailable = normalInteractionAvailable(),
            origin = player.position(),
            settings = settings,
            tick = player.tickCount,
        )
        if (!claimed) {
            controller.lastMessage?.let { status = InteractableRuntimeStatus.Failure(it.statusReason()) }
            return false
        }
        state.acceptsContainerPackets = true
        state.activeSettings = settings
        status = InteractableRuntimeStatus.State(session.state)
        return true
    }

    fun tick() {
        val player = mc.player ?: run {
            if (active) hardReset(InteractableSessionCause.WORLD_CHANGE)
            return
        }
        if (mc.connection == null) {
            hardReset(InteractableSessionCause.DISCONNECT)
            return
        }
        val tick = player.tickCount
        evaluateOpenLifecycle(tick)
        if (session.state !is InteractableSessionState.Opening || !state.openLifecycle.awaitingConfirmation) {
            controller.tick(tick)
        }
        controller.lastMessage?.let { status = InteractableRuntimeStatus.Failure(it.statusReason()) }
        if (!controller.active) {
            clearTransientState()
            return
        }
        if (!validateActiveTarget(tick)) return
        drainDeferredOpenAttempts()
        dispatchNextMovement(tick)
        status = status.takeIf { it !is InteractableRuntimeStatus.State }
            ?: InteractableRuntimeStatus.State(session.state)
    }

    fun rewriteOrConfirmOutgoing(event: PacketEvent) = processOutgoingPacket(event)

    fun captureContainerPacket(event: PacketEvent) = observeContainerPacket(event)

    fun onScreen(screen: Screen?) = reconcileScreen(screen)

    fun abort(cause: InteractableSessionCause) = abortRuntime(cause)

    fun hardReset(cause: InteractableSessionCause) = resetRuntime(cause)

    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) =
        prepareCorrection(packet, player)

    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) =
        finishCorrection(packet, player)

    fun suppressLocalMovement(): Boolean = session.movementLeaseRequired
}

private const val FRESH_USE_WINDOW_MS = 150L
