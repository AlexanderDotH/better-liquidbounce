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

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.additions.forceSneak
import net.ccbluex.liquidbounce.additions.suppressJump
import net.ccbluex.liquidbounce.additions.suppressSneak
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleMiddleClickAction
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDistanceTarget
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipClipResult
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFoliaMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipInputController
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipInputSuppression
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipLandingIndicator
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipLandingIndicatorState
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipMovementMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPacketDeliveryTracker
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPosition
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipSmartTarget
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTargetMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipVanillaMode
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.acceptsVClipControlInput
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.isVClipRidingJumpAction
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.migrateLegacyVClipBedrockSafety
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.runtime.VClipMovementTransport
import net.ccbluex.liquidbounce.render.drawBlockSelection
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Moves vertically with Space and Shift, optionally gated by MiddleClickAction's held modifier.
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

    private val doNotClipAroundBedrock by boolean("DoNotClipAroundBedrock", true)
    private val repeatDelay by int("RepeatDelay", 5, 1..20, "ticks")
    private val inputController = VClipInputController()
    private val packetDeliveryTracker = VClipPacketDeliveryTracker()
    private var landingIndicators = emptyList<VClipLandingIndicatorState>()

    init { VClipMovementTransport.bind(::sendMovementPacket) }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyVClipBedrockSafety(jsonObject)
    }

    override fun onEnabled() {
        inputController.reset()
        ModuleMiddleClickAction.resetSmartVClipLock()
        packetDeliveryTracker.clear()
        landingIndicators = emptyList()
    }

    override fun onDisabled() {
        inputController.reset()
        ModuleMiddleClickAction.resetSmartVClipLock()
        packetDeliveryTracker.clear()
        landingIndicators = emptyList()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        updateLandingIndicators()

        if (ModuleMiddleClickAction.isSmartVClipLockActive()) {
            inputController.reset()
            val acceptsInput = acceptsControlInput()
            val direction = ModuleMiddleClickAction.resolveSmartVClipDirection(
                jumpPressed = acceptsInput && mc.options.keyJump.isDown,
                shiftPressed = acceptsInput && mc.options.keyShift.isDown,
                repeatDelayTicks = repeatDelay,
            ) ?: return@handler

            clip(direction)
            return@handler
        }

        val direction = inputController.resolve(
            spacePressed = acceptsControlInput() && mc.options.keyJump.isDown,
            shiftPressed = acceptsControlInput() && mc.options.keyShift.isDown,
            repeatDelayTicks = repeatDelay,
        ) ?: return@handler

        clip(direction)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val indicators = landingIndicators
        if (indicators.isEmpty()) return@handler

        event.renderEnvironment {
            indicators.forEach { indicator ->
                drawBlockSelection(indicator.renderPosition, indicator.color)
            }
        }
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = READ_FINAL_STATE) { event ->
        val suppression = inputSuppression()
        if (suppression.jump) event.jump = false
        if (suppression.sneak) event.sneak = false
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent>(priority = READ_FINAL_STATE) { event ->
        if (inputSuppression().jump) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = Short.MIN_VALUE) { event ->
        if (event.origin != TransferOrigin.OUTGOING) {
            return@handler
        }

        packetDeliveryTracker.finalizeProtectedMovement(event)
        when (val packet = event.packet) {
            is ServerboundPlayerInputPacket -> {
                val suppression = inputSuppression()
                if (suppression.jump) packet.suppressJump = true
                if (suppression.sneak) {
                    packet.forceSneak = false
                    packet.suppressSneak = true
                }
            }
            is ServerboundPlayerCommandPacket -> if (
                packet.action.isVClipRidingJumpAction &&
                inputSuppression().jump
            ) {
                event.cancelEvent()
            }
        }
    }

    internal fun sendMovementPacket(packet: ServerboundMovePlayerPacket): Boolean {
        packetDeliveryTracker.protect(packet)
        try {
            network.send(packet)
            return packetDeliveryTracker.takeDelivery(packet) != null
        } finally {
            packetDeliveryTracker.discard(packet)
        }
    }

    private fun clip(direction: VClipDirection) {
        val entity = player.vehicle ?: player
        val origin = entity.position().let { VClipPosition(it.x, it.y, it.z) }
        val target = targets.activeMode.resolve(entity, direction, doNotClipAroundBedrock)
        if (target == null) {
            notification("VClip", message("noPositionFound"), NotificationEvent.Severity.ERROR)
            return
        }

        val result = modes.activeMode.clip(
            entity = entity,
            origin = origin,
            target = target,
            fallSafety = VClipFallSafetyContext(
                initialFallDistance = maxOf(entity.fallDistance, player.fallDistance),
                safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
            ),
        )
        if (result == VClipClipResult.FALL_PROTECTION_UNAVAILABLE) {
            notification("VClip", message("fallProtectionUnavailable"), NotificationEvent.Severity.ERROR)
        }
    }

    private fun updateLandingIndicators() {
        val entity = player.vehicle ?: player
        val position = entity.position()
        val origin = VClipPosition(position.x, position.y, position.z)

        landingIndicators = VClipDirection.entries.mapNotNull { direction ->
            val target = targets.activeMode.resolve(entity, direction, doNotClipAroundBedrock)
            VClipLandingIndicator.resolve(origin, target, direction)
        }
    }

    private fun acceptsControlInput() = acceptsVClipControlInput(
        screenOpen = mc.gui.screen() != null,
        remoteMovementOwned = RemoteMovementOwnership.active,
    )

    private fun inputSuppression(): VClipInputSuppression {
        val smartLockActive = ModuleMiddleClickAction.isSmartVClipLockActive()
        val modifierHeld = ModuleMiddleClickAction.isSmartVClipModifierHeld()

        return VClipInputSuppression.resolve(smartLockActive, modifierHeld)
    }

}
