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

package net.ccbluex.liquidbounce.features.module.modules.render.freecam

import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.inventorymove.ModuleInventoryMove.shouldHandleInputs
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

internal inline fun suppressFreeCamPlayerMovement(event: PlayerMoveEvent, setVelocity: (Vec3) -> Unit) {
    event.movement = Vec3.ZERO
    setVelocity(Vec3.ZERO)
}

internal data class FreeCamMovementSpeed(
    val horizontalSpeed: Double,
    val verticalSpeed: Double,
)

internal fun resolveFreeCamMovementSpeed(
    baseSpeed: FreeCamMovementSpeed,
    sprintSpeed: FreeCamMovementSpeed,
    sprintSpeedEnabled: Boolean,
    sprintBindingPressed: Boolean,
) = if (sprintSpeedEnabled && sprintBindingPressed) sprintSpeed else baseSpeed

internal data class FreeCamMovement(
    val directionalInput: DirectionalInput,
    val velocity: Vec3,
)

internal object FreeCamMovementResolver : MinecraftShortcuts {

    fun resolve(
        rotation: Rotation,
        baseSpeed: FreeCamMovementSpeed,
        sprintSpeed: FreeCamMovementSpeed,
        sprintSpeedEnabled: Boolean,
    ): FreeCamMovement {
        val input = DirectionalInput(mc.options)
        val verticalInput = verticalInput()
        val speed = resolveFreeCamMovementSpeed(
            baseSpeed,
            sprintSpeed,
            sprintSpeedEnabled,
            shouldHandleInputs(mc.options.keySprint) && mc.options.keySprint.isPressedOnAny,
        )
        val velocity = Vec3.ZERO.withStrafe(
            speed = speed.horizontalSpeed,
            input = input,
            yaw = getMovementDirectionOfInput(rotation.yaw, input),
        ).with(Direction.Axis.Y, verticalInput * speed.verticalSpeed)
        return FreeCamMovement(input, velocity)
    }

    private fun verticalInput(): Double {
        var input = 0.0
        if (shouldHandleInputs(mc.options.keyJump) && mc.options.keyJump.isPressedOnAny) input += 1.0
        if (shouldHandleInputs(mc.options.keyShift) && mc.options.keyShift.isPressedOnAny) input -= 1.0
        return input
    }
}
