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

import net.minecraft.core.BlockPos
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

internal data class SimulationGravity(val amount: Double, val isFalling: Boolean)

internal fun SimulatedPlayer.simulateTravel(movementInput: Vec3) {
    applySwimmingLift()
    val beforeTravelVelocityY = deltaMovement.y
    val gravity = resolveGravity()

    when {
        isInWater() && player.isAffectedByFluids -> travelInWater(movementInput, gravity)
        isInLava() && player.isAffectedByFluids -> travelInLava(movementInput, gravity)
        fallFlying -> travelWhileGliding(gravity.amount)
        else -> travelOnGround(movementInput, gravity.amount)
    }

    if (player.abilities.flying && !player.isPassenger) {
        deltaMovement = Vec3(deltaMovement.x, beforeTravelVelocityY * 0.6, deltaMovement.z)
        land()
    }
}

private fun SimulatedPlayer.applySwimmingLift() {
    if (!isSwimming || player.isPassenger) {
        return
    }

    val viewY = getViewVector().y
    val fluidAboveIsEmpty = player.level()
        .getBlockState(BlockPos.containing(pos.x, pos.y + 0.9, pos.z))
        .fluidState.isEmpty
    if (viewY <= 0.0 || input.keyPresses.jump || !fluidAboveIsEmpty) {
        val swimLift = if (viewY < -0.2) 0.085 else 0.06
        deltaMovement = deltaMovement.add(0.0, (viewY - deltaMovement.y) * swimLift, 0.0)
    }
}

private fun SimulatedPlayer.resolveGravity(): SimulationGravity {
    val isFalling = deltaMovement.y <= 0.0
    if (isFalling && hasStatusEffect(MobEffects.SLOW_FALLING)) {
        land()
        return SimulationGravity(0.01, true)
    }
    return SimulationGravity(0.08, isFalling)
}
