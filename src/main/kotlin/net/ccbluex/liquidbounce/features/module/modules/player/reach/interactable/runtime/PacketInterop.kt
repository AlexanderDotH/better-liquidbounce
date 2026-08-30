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
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableCorrectionDecision
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSession
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionEffect
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.phys.Vec3

internal class MinecraftInteractableSessionPort(
    private val session: InteractableSession<InteractableRuntimeTarget, InteractablePacketInstruction>,
    private val effectSink: (List<InteractableSessionEffect>) -> Unit,
) : ControllerSessionPort<InteractableRuntimeTarget, InteractableSessionRoute<InteractablePacketInstruction>> {
    override val active: Boolean
        get() = session.movementLeaseRequired

    override fun beginPlanning(
        target: InteractableRuntimeTarget,
        origin: Vec3,
        settings: InteractableSessionSettings,
        tick: Int,
    ) = session.beginPlanning(target, origin, settings, tick)

    override fun acceptRoute(route: InteractableSessionRoute<InteractablePacketInstruction>, tick: Int) {
        check(session.acceptRoute(route, tick)) { "Interactable session rejected its planned route" }
    }

    override fun tick(tick: Int) = effectSink(session.tick(tick))

    override fun abort(cause: InteractableSessionCause, tick: Int) = effectSink(session.abort(cause, tick))

    override fun hardReset(cause: InteractableSessionCause) = effectSink(session.hardReset(cause))
}

internal object MinecraftMovementOwnership : ControllerMovementOwnership {
    override fun tryAcquire(owner: String): ControllerMovementLease? =
        RemoteMovementOwnership.tryAcquire(owner)?.let(::MinecraftMovementLease)
}

private class MinecraftMovementLease(
    private val delegate: RemoteMovementOwnership.Lease,
) : ControllerMovementLease {
    override val active: Boolean
        get() = delegate.active

    override fun close() = delegate.close()
}

internal fun InteractablePacketInstruction.toPacket(
    rotation: Rotation? = null,
): ServerboundMovePlayerPacket {
    val player = requireNotNull(mc.player)
    return when (this) {
        is InteractablePacketInstruction.Status -> ServerboundMovePlayerPacket.StatusOnly(
            onGround,
            player.horizontalCollision,
        )
        is InteractablePacketInstruction.Position -> positionPacket(
            rotation,
            player.yRot,
            player.xRot,
            player.horizontalCollision,
        )
    }
}

private fun InteractablePacketInstruction.Position.positionPacket(
    rotation: Rotation?,
    playerYaw: Float,
    playerPitch: Float,
    horizontalCollision: Boolean,
): ServerboundMovePlayerPacket {
    val resolvedRotation = rotation ?: Rotation(playerYaw, playerPitch)
    if (!fullPacket) {
        return ServerboundMovePlayerPacket.Pos(
            position.x, position.y, position.z, onGround, horizontalCollision,
        )
    }
    return ServerboundMovePlayerPacket.PosRot(
        position.x,
        position.y,
        position.z,
        resolvedRotation.yRot,
        resolvedRotation.xRot,
        onGround,
        horizontalCollision,
    )
}

internal fun InteractablePacketInstruction.applyTo(packet: ServerboundMovePlayerPacket) {
    packet.onGround = onGround
    if (this !is InteractablePacketInstruction.Position) return
    packet.rewritePosition(position)
    if (!fullPacket) return
    packet.yRot = mc.player?.yRot ?: packet.yRot
    packet.xRot = mc.player?.xRot ?: packet.xRot
    packet.hasRot = true
}

internal fun ServerboundMovePlayerPacket.rewritePosition(position: Vec3) {
    x = position.x
    y = position.y
    z = position.z
    hasPos = true
}

internal fun Packet<*>.finalDisposition(
    cancelled: Boolean,
    origin: TransferOrigin,
    rateLimitDispositionPort: PacketRateLimitDispositionPort,
): InteractablePacketDisposition {
    if (BlinkManager.takeQueued(this, origin)) return InteractablePacketDisposition.QUEUED
    if (origin == TransferOrigin.INCOMING) {
        return if (cancelled) InteractablePacketDisposition.CANCELLED else InteractablePacketDisposition.DELIVERED
    }
    return when (rateLimitDispositionPort.takeDisposition(this)) {
        PacketRateLimitDisposition.QUEUED -> InteractablePacketDisposition.QUEUED
        PacketRateLimitDisposition.DROPPED -> InteractablePacketDisposition.DROPPED
        null -> if (cancelled) InteractablePacketDisposition.CANCELLED else InteractablePacketDisposition.DELIVERED
    }
}

internal fun Packet<*>.isContainerInteractionPacket(): Boolean =
    this is ServerboundUseItemOnPacket || this is ServerboundInteractPacket

internal fun InteractableSessionState.acceptsMovement(): Boolean =
    this is InteractableSessionState.Outbound ||
        this is InteractableSessionState.Returning ||
        this is InteractableSessionState.Recovering

internal fun InteractableCorrectionDecision.effects(): List<InteractableSessionEffect> = when (this) {
    InteractableCorrectionDecision.Ignored -> emptyList()
    is InteractableCorrectionDecision.Recovering -> effects
    is InteractableCorrectionDecision.Completed -> effects
    is InteractableCorrectionDecision.AcceptLocally -> effects
}
