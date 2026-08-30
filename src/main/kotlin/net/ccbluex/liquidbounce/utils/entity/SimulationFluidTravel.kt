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

import net.minecraft.tags.FluidTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

private data class WaterMovement(val drag: Float, val acceleration: Float)

internal fun SimulatedPlayer.travelInWater(movementInput: Vec3, gravity: SimulationGravity) {
    val initialY = pos.y
    val movement = waterMovement()
    updateVelocity(movement.acceleration, movementInput)
    moveSimulated(deltaMovement)

    val climbedMovement = if (horizontalCollision && isClimbing()) {
        Vec3(deltaMovement.x, 0.2, deltaMovement.z)
    } else {
        deltaMovement
    }
    deltaMovement = climbedMovement.multiply(movement.drag.toDouble(), 0.8, movement.drag.toDouble())
    deltaMovement = player.getFluidFallingAdjustedMovement(gravity.amount, gravity.isFalling, deltaMovement)
    applyFluidCollisionBoost(initialY, deltaMovement)
}

private fun SimulatedPlayer.waterMovement(): WaterMovement {
    var drag = if (isSprinting) 0.9f else 0.8f
    var acceleration = 0.02f
    var efficiency = getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY).toFloat()
    if (!onGround) {
        efficiency *= 0.5f
    }
    if (efficiency > 0.0f) {
        drag += (0.54600006f - drag) * efficiency / 3.0f
        acceleration += (getSpeed() - acceleration) * efficiency / 3.0f
    }
    if (hasStatusEffect(MobEffects.DOLPHINS_GRACE)) {
        drag = 0.96f
    }
    return WaterMovement(drag, acceleration)
}

internal fun SimulatedPlayer.travelInLava(movementInput: Vec3, gravity: SimulationGravity) {
    val initialY = pos.y
    updateVelocity(0.02f, movementInput)
    moveSimulated(deltaMovement)
    deltaMovement = if (getFluidHeight(FluidTags.LAVA) <= getFluidJumpThreshold()) {
        val slowed = deltaMovement.multiply(0.5, 0.8, 0.5)
        player.getFluidFallingAdjustedMovement(gravity.amount, gravity.isFalling, slowed)
    } else {
        deltaMovement.scale(0.5)
    }
    if (!player.isNoGravity) {
        deltaMovement = deltaMovement.add(0.0, -gravity.amount / 4.0, 0.0)
    }
    applyFluidCollisionBoost(initialY, deltaMovement)
}

private fun SimulatedPlayer.applyFluidCollisionBoost(initialY: Double, movement: Vec3) {
    if (horizontalCollision && doesNotCollide(
            movement.x,
            movement.y + 0.6 - pos.y + initialY,
            movement.z,
        )
    ) {
        deltaMovement = Vec3(movement.x, 0.3, movement.z)
    }
}
