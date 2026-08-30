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

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractablePacketInstruction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableSettingsSnapshot
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableContainerCloseCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovementConfirmation
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableOpenLifecycle
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionEffect
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal class RuntimeState {
    var activeSettings: InteractableSettingsSnapshot? = null
    var nextMovementTick = 0
    var pendingTransportPacket: ServerboundMovePlayerPacket? = null
    var pendingSessionIdentity: Any? = null
    var pendingInstruction: InteractablePacketInstruction? = null
    var immediatePacket: Packet<*>? = null
    var immediateDisposition: InteractablePacketDisposition? = null
    var correctionContext: CorrectionContext? = null
    val deferredOpenAttempts = ArrayDeque<InteractableSessionEffect.OpenAttempt>()
    val openLifecycle = InteractableOpenLifecycle(OPEN_SCREEN_CONFIRM_GRACE_TICKS)
    var lastTransportConfirmation: InteractableMovementConfirmation? = null
    var interactionCaptureActive = false
    val interactionDispositions = mutableListOf<InteractablePacketDisposition>()

    @Volatile
    var acceptsContainerPackets = false
}

internal data class CorrectionContext(
    val packet: ClientboundPlayerPositionPacket,
    val authoritative: Vec3,
    val visualOrigin: Vec3?,
    val visualVelocity: Vec3,
)

internal sealed interface ContainerPacketFact {
    data class Open(
        val containerId: Int,
        val disposition: InteractablePacketDisposition,
    ) : ContainerPacketFact

    data class Close(
        val containerId: Int,
        val cause: InteractableContainerCloseCause,
        val disposition: InteractablePacketDisposition,
    ) : ContainerPacketFact
}

private const val OPEN_SCREEN_CONFIRM_GRACE_TICKS = 2
