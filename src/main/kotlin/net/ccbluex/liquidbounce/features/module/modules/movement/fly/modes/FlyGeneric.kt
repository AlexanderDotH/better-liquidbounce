/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.additions.rawInput
import net.ccbluex.liquidbounce.additions.suppressSneak
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationInput
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.phys.Vec3

/**
 * Runtime shared by ordinary Vanilla Fly and packet-sequenced Fly. Subclasses may change how a
 * collision-resolved movement is transmitted, but local movement and Vanilla safety behavior stay identical.
 */
internal abstract class VanillaFlyMode(
    name: String,
    speedRange: ClosedFloatingPointRange<Float>,
) : Mode(name), FlyAutomationProfile {

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
        reliableSpeed = true,
    )

    override fun automationReadiness() = FlyAutomationReadiness.Ready

    private val glide by float("Glide", 0.0f, -1f..1f)

    private val bypassVanillaCheck by boolean("BypassVanillaCheck", true)
    private val bypassMode by enumChoice("BypassMode", VanillaFlyCheckBypassMode.PACKET)
    private val noFall by boolean("NoFall", false)
    private val noFallRuntime = VanillaFlyNoFallRuntime { noFall }

    private val baseSpeed = VanillaFlyBaseSpeed(speedRange)
    private val sprintSpeed = VanillaFlySprintSpeed(this, speedRange)

    init {
        tree(baseSpeed)
        tree(sprintSpeed)
    }


    private val useSprintSpeed
        get() = FlyAutomationInput.sprint(mc.options.keySprint.isDown) && sprintSpeed.enabled

    private val horizontalSpeed
        get() = if (useSprintSpeed) sprintSpeed.horizontalSpeed else baseSpeed.horizontalSpeed

    private val verticalSpeed
        get() = if (useSprintSpeed) sprintSpeed.verticalSpeed else baseSpeed.verticalSpeed

    protected val requestedVerticalMotion
        get() = when {
            FlyAutomationInput.jump(mc.options.keyJump.isDown) &&
                !FlyAutomationInput.sneak(mc.options.keyShift.isDown) -> verticalSpeed.toDouble()
            FlyAutomationInput.sneak(mc.options.keyShift.isDown) &&
                !FlyAutomationInput.jump(mc.options.keyJump.isDown) -> -verticalSpeed.toDouble()
            else -> glide.toDouble()
        }

    override fun disable() {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
        super.disable()
    }

    protected open val movementSuspended: Boolean
        get() = false

    protected open fun onVanillaFlyRuntimeReset() = Unit

    protected open fun onVanillaFlyMovementSuspended() = Unit

    protected val existingDeliveredMovementPacketCount: Int
        get() = noFallRuntime.deliveredMovementPacketsThisTick

    protected fun isTrackedNoFallGroundPacket(packet: ServerboundMovePlayerPacket) =
        noFallRuntime.isTrackedGroundPacket(packet)

    protected fun forecastNoFallPacketCount(target: Vec3) = noFallRuntime.forecastPacketCount(target)

    protected fun forecastPostBypassPacketCount(): Int = if (
        shouldSendVanillaFlyPacketBypass(
            eventState = EventState.POST,
            enabled = bypassVanillaCheck,
            tickCount = player.tickCount,
            configuredMode = bypassMode,
            isFallFlying = player.isFallFlying,
            movementSuspended = movementSuspended,
        )
    ) {
        1
    } else {
        0
    }

    protected fun clearVanillaFlyRuntime() = noFallRuntime.clear()

    @Suppress("unused")
    private val tickHandler = tickHandler {
        noFallRuntime.beginTick()
        if (movementSuspended) {
            player.deltaMovement = Vec3.ZERO
            noFallRuntime.clear()
            onVanillaFlyMovementSuspended()
            return@tickHandler
        }

        val physicalInput = DirectionalInput(player.input)
        val resolvedInput = FlyAutomationInput.directional(physicalInput)
        val physicalYaw = player.getMovementDirectionOfInput(physicalInput)
        player.deltaMovement = player.deltaMovement.withStrafe(
            speed = horizontalSpeed.toDouble(),
            input = resolvedInput,
            yaw = FlyAutomationInput.desiredYaw(physicalYaw, physicalInput),
        )
        player.deltaMovement.y = requestedVerticalMotion

        if (
            shouldRunVanillaFlyCheckBypass(bypassVanillaCheck, player.tickCount) &&
            resolveVanillaFlyCheckBypassMode(bypassMode, player.isFallFlying) == VanillaFlyCheckBypassMode.MOTION
        ) {
            player.deltaMovement.y = -VANILLA_CHECK_BYPASS_Y_OFFSET
        }

        noFallRuntime.run()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
    }

    @Suppress("unused")
    private val noFallSafetyPacketHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) {
        noFallRuntime.handleSafetyPacket(it)
    }

    @Suppress("unused")
    private val noFallSegmentationPacketHandler = handler<PacketEvent>(
        priority = (READ_FINAL_STATE + 1).toShort(),
    ) {
        noFallRuntime.handleSegmentationPacket(it)
    }

    @Suppress("unused")
    private val noFallFinalPacketHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) {
        noFallRuntime.handleFinalPacket(it)
    }

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (!shouldSendVanillaFlyPacketBypass(
                eventState = event.state,
                enabled = bypassVanillaCheck,
                tickCount = player.tickCount,
                configuredMode = bypassMode,
                isFallFlying = player.isFallFlying,
                movementSuspended = movementSuspended,
            )
        ) {
            return@handler
        }

        network.send(MovePacketType.POSITION_AND_ON_GROUND.generatePacket().apply {
            applyVanillaFlyCheckBypass(this, player.y)
        })
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = SAFETY_FEATURE) { event ->
        if (movementSuspended) {
            return@handler
        }
        applyVanillaFlyElytraVerticalMotion(
            event = event,
            isFallFlying = player.isFallFlying,
            requestedVerticalMotion = requestedVerticalMotion,
        ) { player.deltaMovement.y = it }
    }

    @Suppress("unused")
    private val inputPacketHandler = handler<PacketEvent> { event ->
        if (movementSuspended || event.origin != TransferOrigin.OUTGOING) return@handler

        val packet = event.packet as? ServerboundPlayerInputPacket ?: return@handler
        if (shouldSuppressVanillaFlyServerSneak(packet.rawInput)) {
            packet.suppressSneak = true
        }
    }

}

internal object FlyVanilla : VanillaFlyMode("Vanilla", 0.1f..10f)
