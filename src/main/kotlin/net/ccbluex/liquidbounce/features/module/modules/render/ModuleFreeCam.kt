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
package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PerspectiveEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.combat.contract.CombatRuntimeEnvironment
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.FreeCamCancelOn
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.FreeCamCancellation
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.FreeCamMovementResolver
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.FreeCamMovementSpeed
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.FreeCamNavigation
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.findFreeCamLookTarget
import net.ccbluex.liquidbounce.features.module.modules.render.freecam.suppressFreeCamPlayerMovement
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.CameraType
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * FreeCam module
 *
 * Allows you to move out of your body.
 */
object ModuleFreeCam : ClientModule("FreeCam", ModuleCategories.RENDER, disableOnQuit = true) {

    object BaseSpeed : ValueGroup("BaseSpeed") {
        val horizontalSpeed by float("Horizontal", 1f, 0.1f..10f)
        val verticalSpeed by float("Vertical", 1f, 0.1f..10f)
    }

    object SprintSpeed : ToggleableValueGroup(this, "SprintSpeed", true) {
        val horizontalSpeed by float("Horizontal", 2f, 0.1f..10f)
        val verticalSpeed by float("Vertical", 2f, 0.1f..10f)
    }

    /**
     * Allows to interact from the camera perspective. This is very useful to interact with blocks that
     * are behind the player or walls. Similar functionality to the GhostBlock module.
     */
    private object CameraInteract : ToggleableValueGroup(ModuleFreeCam, "AllowCameraInteract", true) {
        val lookAt by boolean("LookAt", true)
    }

    private val cancelOn by multiEnumChoice("CancelOn", enumSetOf<FreeCamCancelOn>())

    private object Navigation : FreeCamNavigation(ModuleFreeCam, ModuleFreeCam::getCameraLookingAt)

    private val midClickCameraTeleport by boolean("MidClickCameraTeleport", false)

    private val keepSneaking by boolean("KeepSneaking", false)

    private val rotations = tree(RotationsValueGroup(this))

    init {
        tree(BaseSpeed)
        tree(SprintSpeed)
        tree(CameraInteract)
        tree(Navigation)
        FreeCamCancellation.register(this, { cancelOn }) { enabled = false }
        CombatRuntimeEnvironment.bindFreeCam { enabled }
    }

    object PositionState {
        var available: Boolean = false
            set(value) {
                if (value) {
                    pos = player.eyePosition
                    lastPos = pos

                    rot = player.rotation
                    lastRot = rot
                } else {
                    pos = Vec3.ZERO
                    lastPos = Vec3.ZERO

                    rot = Rotation.ZERO
                    lastRot = rot
                }
                field = value
            }

        @JvmField var pos: Vec3 = Vec3.ZERO
        @JvmField var lastPos: Vec3 = Vec3.ZERO
        @JvmField var rot: Rotation = Rotation.ZERO
        @JvmField var lastRot: Rotation = Rotation.ZERO

        fun set(target: Vec3) {
            lastPos = pos
            pos = target
        }
        fun set(target: Rotation) {
            lastRot = rot
            rot = target
        }

        fun update(velocity: Vec3) = set(pos + velocity)
        fun rotation(xDelta: Double, yDelta: Double) = set(
            Rotation(
                rot.yRot + xDelta.toFloat(),
                (rot.xRot + yDelta.toFloat()).coerceIn(-90f..90f)
            )
        )
        fun interpolate(partialTicks: Float) = lastPos.lerp(pos, partialTicks.toDouble())
        fun interpolateRot(partialTicks: Float) = lastRot.interpolateTo(rot, partialTicks)
    }

    override fun onEnabled() {
        PositionState.available = true
        super.onEnabled()
    }

    override fun onDisabled() {
        PositionState.available = false
        super.onDisabled()
    }

    @Suppress("unused")
    private val mouseHandler = handler<MouseButtonEvent> { event ->
        if (midClickCameraTeleport && event.isMiddleClick) {
            val target = getCameraLookingAt() ?: return@handler

            // interpolate to prevent tp into block
            PositionState.set(PositionState.pos.lerp(target, 0.9))
        }
    }

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent>(priority = FIRST_PRIORITY) { event ->
        event.directionalInput = DirectionalInput.NONE
        event.jump = false
        event.sneak = false
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = FINAL_DECISION) { event ->
        suppressFreeCamPlayerMovement(event) { player.deltaMovement = it }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val movement = FreeCamMovementResolver.resolve(
            rotation = PositionState.rot,
            baseSpeed = FreeCamMovementSpeed(
                BaseSpeed.horizontalSpeed.toDouble(),
                BaseSpeed.verticalSpeed.toDouble(),
            ),
            sprintSpeed = FreeCamMovementSpeed(
                SprintSpeed.horizontalSpeed.toDouble(),
                SprintSpeed.verticalSpeed.toDouble(),
            ),
            sprintSpeedEnabled = SprintSpeed.enabled,
        )
        ModuleDebug.debugParameter(this, "DirectionalInput", movement.directionalInput)
        ModuleDebug.debugParameter(this, "Velocity", movement.velocity)
        PositionState.update(movement.velocity)

        ModuleDebug.debugParameter(this, "Position", PositionState.pos)
        ModuleDebug.debugParameter(this, "Rotation", PositionState.rot)
    }

    @Suppress("unused")
    private val forceSneakHandler = handler<MovementInputEvent>(priority = OBJECTION_AGAINST_EVERYTHING) { event ->
        if (keepSneaking) {
            event.sneak = true
        }
    }

    @Suppress("unused")
    private val perspectiveHandler = handler<PerspectiveEvent> { event ->
        event.perspective = CameraType.FIRST_PERSON
    }

    @Suppress("unused")
    private val rotationHandler = handler<RotationUpdateEvent> {
        val lookAt = if (Navigation.shouldBeGoing) {
            // Look at target position
            Navigation.getMovementRotation()
        } else if (CameraInteract.running && CameraInteract.lookAt) {
            // Aim at crosshair target
            val crosshairTarget = mc.hitResult ?: return@handler
            Rotation.lookingAt(crosshairTarget.location, player.eyePosition)
        } else {
            return@handler
        }

        RotationManager.setRotationTarget(rotations.toRotationTarget(lookAt),
            Priority.NOT_IMPORTANT, ModuleFreeCam)
    }

    @Suppress("unused")
    private val alwaysCancelOnHandler = handler<WorldChangeEvent> {
        // If not, will get stuck when world change
        enabled = false
    }

    fun applyCameraPosition(entity: Entity?, partialTicks: Float) {
        if (!running || entity != player || !PositionState.available) {
            return
        }

        val camera = mc.gameRenderer.mainCamera()

        return camera.setPosition(PositionState.interpolate(partialTicks))
    }

    @JvmStatic
    fun shouldCameraInteractActive() = running && CameraInteract.running
    fun shouldDisableCameraInteract() = running && !CameraInteract.running

    private fun getCameraLookingAt(): Vec3? {
        if (!PositionState.available) return null
        return findFreeCamLookTarget(PositionState.interpolate(1f), PositionState.rot)
    }

}
