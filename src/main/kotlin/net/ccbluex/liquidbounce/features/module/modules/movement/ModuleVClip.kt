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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.additions.forceSneak
import net.ccbluex.liquidbounce.additions.suppressJump
import net.ccbluex.liquidbounce.additions.suppressSneak
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDistanceTarget
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallProtection
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFoliaMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipInputController
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipMovementMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPosition
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipSmartTarget
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTargetMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipVanillaMode
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket

/**
 * Moves vertically with Space and Shift without forwarding jump or sneak input to the server.
 */
object ModuleVClip : ClientModule(
    "VClip",
    ModuleCategories.MOVEMENT,
    disableOnQuit = true,
) {

    internal val modes = choices<VClipMovementMode>(
        "Mode",
        VClipVanillaMode,
        arrayOf(VClipVanillaMode, VClipFoliaMode),
    ).apply { tagBy(this) }

    internal val targets = choices<VClipTargetMode>(
        "Target",
        VClipSmartTarget,
        arrayOf(VClipDistanceTarget, VClipSmartTarget),
    )

    private val repeatDelay by int("RepeatDelay", 5, 1..20, "ticks")
    private val inputController = VClipInputController()
    private val fallProtectionTracker = GroundPacketDeliveryTracker()

    override fun onEnabled() {
        inputController.reset()
        fallProtectionTracker.clear()
    }

    override fun onDisabled() {
        inputController.reset()
        fallProtectionTracker.clear()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val direction = inputController.resolve(
            spacePressed = acceptsControlInput() && mc.options.keyJump.isDown,
            shiftPressed = acceptsControlInput() && mc.options.keyShift.isDown,
            repeatDelayTicks = repeatDelay,
        ) ?: return@handler

        clip(direction)
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = READ_FINAL_STATE) { event ->
        event.jump = false
        event.sneak = false
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent>(priority = READ_FINAL_STATE) { event ->
        event.cancelEvent()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = Short.MIN_VALUE) { event ->
        if (event.origin != TransferOrigin.OUTGOING) {
            return@handler
        }

        finalizeProtectedMovement(event)
        when (val packet = event.packet) {
            is ServerboundPlayerInputPacket -> {
                packet.forceSneak = false
                packet.suppressJump = true
                packet.suppressSneak = true
            }
            is ServerboundPlayerCommandPacket -> if (packet.action.isRidingJumpAction) {
                event.cancelEvent()
            }
        }
    }

    internal fun sendMovementPacket(packet: ServerboundMovePlayerPacket, fallProtection: VClipFallProtection) {
        if (!fallProtection.resetLocalFallDistance) {
            network.send(packet)
            return
        }

        fallProtectionTracker.protect(packet)
        try {
            network.send(packet)
        } finally {
            fallProtectionTracker.discard(packet)
        }
    }

    private fun clip(direction: VClipDirection) {
        val entity = player.vehicle ?: player
        val origin = entity.position().let { VClipPosition(it.x, it.y, it.z) }
        val target = targets.activeMode.resolve(entity, direction)
        if (target == null) {
            notification("VClip", message("noPositionFound"), NotificationEvent.Severity.ERROR)
            return
        }

        modes.activeMode.clip(entity, origin, target)
    }

    private fun acceptsControlInput() = mc.gui.screen() == null

    private fun finalizeProtectedMovement(event: PacketEvent) {
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        if (!fallProtectionTracker.reassertGround(packet)) {
            return
        }

        if (fallProtectionTracker.confirmFinalState(packet, event.isCancelled)) {
            player.resetFallDistance()
        }
    }

    private val ServerboundPlayerCommandPacket.Action.isRidingJumpAction: Boolean
        get() = this == ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP ||
            this == ServerboundPlayerCommandPacket.Action.STOP_RIDING_JUMP
}
