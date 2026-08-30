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
package net.ccbluex.liquidbounce.utils.aiming

import net.ccbluex.liquidbounce.common.runtime.RunningOwner
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.aiming.utils.setRotation
import net.ccbluex.liquidbounce.utils.aiming.utils.withFixedYaw
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.lastRotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.InventoryRuntimeHooks
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.protocol.Packet

object RotationManager {
    private val requests = RotationRequestCoordinator()
    private val state = RotationState()
    private val packetTracker = RotationPacketTracker(state)
    private val rotationTarget: RotationTarget? get() = requests.activeTarget

    val activeRotationTarget: RotationTarget? get() = rotationTarget ?: previousRotationTarget
    internal var previousRotationTarget: RotationTarget?
        get() = state.previousRotationTarget
        private set(value) { state.previousRotationTarget = value }
    var currentRotation: Rotation?
        get() = state.currentRotation
        private set(value) { state.updateCurrent(value) { mc.player?.rotation ?: Rotation.ZERO } }
    var playerRotation: Rotation?
        get() = state.playerRotation
        private set(value) { state.playerRotation = value }
    var previousRotation: Rotation?
        get() = state.previousRotation
        private set(value) { state.previousRotation = value }
    val serverRotation: Rotation
        get() = if (RotationEnvironmentBridge.isFakeLagging() || RotationEnvironmentBridge.isFreezing()) {
            state.theoreticalServerRotation
        } else actualServerRotation
    val movementYaw: Float
        get() = resolveMovementYaw(
            player.yRot,
            currentRotation?.yaw ?: Float.NaN,
            activeRotationTarget?.movementCorrection,
        )
    var actualServerRotation: Rotation
        get() = state.actualServerRotation
        private set(value) { state.actualServerRotation = value }

    internal fun reset() {
        requests.clear()
        state.reset()
    }

    @Suppress("LongParameterList")
    fun setRotationTarget(
        rotation: Rotation,
        considerInventory: Boolean = true,
        valueGroup: RotationTargetFactory,
        priority: Priority,
        provider: RunningOwner,
        whenReached: RestrictedSingleUseAction? = null,
    ) = setRotationTarget(
        valueGroup.toRotationTarget(rotation, considerInventory = considerInventory, whenReached = whenReached),
        priority,
        provider,
    )

    fun setRotationTarget(plan: RotationTarget, priority: Priority, provider: RunningOwner) {
        if (allowedToUpdate()) requests.request(plan, priority, provider)
    }

    fun isRotatingAllowed(rotationTarget: RotationTarget): Boolean {
        if (!allowedToUpdate()) return false
        if (!rotationTarget.considerInventory) return true
        return !InventoryRuntimeHooks.isInventoryOpen && mc.gui.screen() !is ContainerScreen
    }

    fun update() {
        val playerRotation = player.rotation.also { this.playerRotation = it }
        val activeTarget = activeRotationTarget ?: return
        updateManagedRotation(activeTarget, rotationTarget, playerRotation)
        requests.tick()
    }

    private fun updateManagedRotation(
        activeTarget: RotationTarget,
        requestedTarget: RotationTarget?,
        playerRotation: Rotation,
    ) {
        if (!isRotatingAllowed(activeTarget)) return
        val rotation = activeTarget.towards(currentRotation ?: playerRotation, requestedTarget == null).normalize()
        if (shouldFinishReset(activeTarget, requestedTarget, rotation, playerRotation)) {
            finishReset()
            return
        }
        currentRotation = rotation
        previousRotationTarget = activeTarget
        requestedTarget?.whenReached?.invoke()
    }

    private fun shouldFinishReset(
        target: RotationTarget,
        requestedTarget: RotationTarget?,
        rotation: Rotation,
        playerRotation: Rotation,
    ) = requestedTarget == null && (
        target.movementCorrection == MovementCorrection.CHANGE_LOOK ||
            target.processors.isEmpty() ||
            rotation.rotationDeltaLengthTo(playerRotation) <= target.resetThreshold
        )

    private fun finishReset() {
        currentRotation?.let { rotation ->
            player.yRot = player.withFixedYaw(rotation)
            player.yBob = player.yRot
            player.yBobO = player.yRot
        }
        currentRotation = null
        previousRotationTarget = null
    }

    fun applyChangeLookRotation(partialTicks: Float) {
        val target = activeRotationTarget ?: return
        if (!isRotatingAllowed(target) || target.movementCorrection != MovementCorrection.CHANGE_LOOK) return
        val playerRotation = playerRotation ?: return
        val currentRotation = currentRotation ?: return
        player.setRotation(playerRotation.interpolateTo(currentRotation, partialTicks))
    }

    internal fun adjustMouseRotation(deltaX: Double, deltaY: Double) {
        val target = activeRotationTarget ?: return
        if (!isRotatingAllowed(target) || target.movementCorrection != MovementCorrection.CHANGE_LOOK) return
        playerRotation = playerRotation?.let { RotationUtil.applyMouseTurnDelta(it, deltaX, deltaY) }
        currentRotation = currentRotation?.let { RotationUtil.applyMouseTurnDelta(it, deltaX, deltaY) }
    }

    internal fun velocityRotationYaw(): Float? {
        if (activeRotationTarget?.movementCorrection == MovementCorrection.OFF) return null
        return currentRotation?.yaw
    }

    private fun allowedToUpdate() = !RotationEnvironmentBridge.shouldPauseRotation()

    fun rotationMatchesPreviousRotation(): Boolean {
        val player = mc.player ?: return false
        return currentRotation?.let { it == previousRotation } ?: (player.rotation == player.lastRotation)
    }

    internal fun trackPacket(packet: Packet<*>, incoming: Boolean, cancelled: Boolean) =
        packetTracker.track(packet, incoming, cancelled)

}

internal fun resolveMovementYaw(
    playerYaw: Float,
    managedYaw: Float,
    movementCorrection: MovementCorrection?,
): Float = if (managedYaw.isFinite() && movementCorrection != null && movementCorrection != MovementCorrection.OFF) {
    managedYaw
} else playerYaw
