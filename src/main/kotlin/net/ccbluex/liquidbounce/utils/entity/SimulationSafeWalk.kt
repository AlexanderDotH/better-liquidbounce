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

import net.minecraft.world.phys.Vec3

private const val STEP_HEIGHT = 0.5
private const val BACK_OFF_STEP = 0.05

internal fun SimulatedPlayer.backOffFromEdge(movement: Vec3): Vec3 {
    if (movement.y > 0.0 || !isAboveGround()) {
        return movement
    }

    val backedOffX = backOffX(movement.x)
    val backedOffZ = backOffZ(movement.z)
    val backedOff = backOffDiagonal(backedOffX, backedOffZ)
    if (movement.x != backedOff.x || movement.z != backedOff.z) {
        clipLedged = true
    }
    return if (shouldClipAtLedge()) Vec3(backedOff.x, movement.y, backedOff.z) else movement
}

private fun SimulatedPlayer.backOffX(initial: Double): Double {
    var movement = initial
    while (movement != 0.0 && level.noCollision(player, boundingBox.move(movement, -STEP_HEIGHT, 0.0))) {
        movement = stepTowardZero(movement)
    }
    return movement
}

private fun SimulatedPlayer.backOffZ(initial: Double): Double {
    var movement = initial
    while (movement != 0.0 && level.noCollision(player, boundingBox.move(0.0, -STEP_HEIGHT, movement))) {
        movement = stepTowardZero(movement)
    }
    return movement
}

private fun SimulatedPlayer.backOffDiagonal(initialX: Double, initialZ: Double): Vec3 {
    var x = initialX
    var z = initialZ
    while (x != 0.0 && z != 0.0 && level.noCollision(player, boundingBox.move(x, -STEP_HEIGHT, z))) {
        x = stepTowardZero(x)
        z = stepTowardZero(z)
    }
    return Vec3(x, 0.0, z)
}

private fun stepTowardZero(value: Double): Double = when {
    value < BACK_OFF_STEP && value >= -BACK_OFF_STEP -> 0.0
    value > 0.0 -> value - BACK_OFF_STEP
    else -> value + BACK_OFF_STEP
}

private fun SimulatedPlayer.shouldClipAtLedge(): Boolean =
    !input.ignoreClippingAtLedge && (input.keyPresses.shift || input.forceSafeWalk)

private fun SimulatedPlayer.isAboveGround(): Boolean = onGround ||
    fallDistance < STEP_HEIGHT && !level.noCollision(
        player,
        boundingBox.move(0.0, fallDistance - STEP_HEIGHT, 0.0),
    )
