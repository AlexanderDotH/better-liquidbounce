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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.common.PlayerSimulationHooks
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

private const val MAX_WALKING_SPEED = 0.121

internal fun createClientSimulationInput(
    directionalInput: DirectionalInput,
    jump: Boolean,
    sprinting: Boolean,
    sneaking: Boolean,
): SimulatedPlayer.SimulatedPlayerInput {
    val input = SimulatedPlayer.SimulatedPlayerInput(directionalInput, jump, sprinting, sneaking)
    if (PlayerSimulationHooks.isSafeWalkEnabled()) {
        input.forceSafeWalk = true
    }
    return input
}

internal fun guessSimulationInput(entity: Player, velocity: Vec3):
    SimulatedPlayer.SimulatedPlayerInput {
    val horizontalVelocity = velocity.horizontalDistanceSqr()
    val sprinting = horizontalVelocity >= MAX_WALKING_SPEED * MAX_WALKING_SPEED
    val directionalInput = if (horizontalVelocity > 0.05 * 0.05) {
        val relativeAngle = getDegreesRelativeToView(velocity, yaw = entity.yRot)
        getDirectionalInputForDegrees(DirectionalInput.NONE, Mth.wrapDegrees(relativeAngle))
    } else {
        DirectionalInput.NONE
    }
    return SimulatedPlayer.SimulatedPlayerInput(
        directionalInput,
        jumping = !entity.onGround(),
        sprinting = sprinting,
        sneaking = entity.isShiftKeyDown,
    )
}
