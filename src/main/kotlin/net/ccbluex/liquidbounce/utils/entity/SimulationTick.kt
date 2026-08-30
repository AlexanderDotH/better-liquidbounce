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
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

internal fun SimulatedPlayer.simulateTick() {
    clipLedged = false
    if (pos.y <= -70) {
        return
    }

    input.update()
    refreshFluidState()
    updateJumpState()
    normalizeTinyVelocity()
    handleJumpInput()

    if (hasStatusEffect(MobEffects.SLOW_FALLING) || hasStatusEffect(MobEffects.LEVITATION)) {
        land()
    }
    simulateTravel(Vec3(input.sidewaysMovement * 0.98, 0.0, input.forwardMovement * 0.98))
    simulatedTicks++
}

private fun SimulatedPlayer.updateJumpState() {
    if (jumpTriggerTime > 0) {
        jumpTriggerTime--
    }
    jumping = input.keyPresses.jump
}

private fun SimulatedPlayer.normalizeTinyVelocity() {
    val movement = deltaMovement
    deltaMovement = Vec3(
        if (abs(movement.x) < 0.003) 0.0 else movement.x,
        if (abs(movement.y) < 0.003) 0.0 else movement.y,
        if (abs(movement.z) < 0.003) 0.0 else movement.z,
    )
    if (onGround) {
        fallFlying = false
    }
}

private fun SimulatedPlayer.handleJumpInput() {
    if (!jumping) {
        return
    }

    val fluidHeight = if (isInLava()) getFluidHeight(FluidTags.LAVA) else getFluidHeight(FluidTags.WATER)
    val inWater = isInWater() && fluidHeight > 0.0
    val swimHeight = getFluidJumpThreshold()
    when {
        inWater && (!onGround || fluidHeight > swimHeight) -> swimUpward(FluidTags.WATER)
        isInLava() && (!onGround || fluidHeight > swimHeight) -> swimUpward(FluidTags.LAVA)
        (onGround || inWater && fluidHeight <= swimHeight) && jumpTriggerTime == 0 -> {
            performGroundJump()
            jumpTriggerTime = 10
        }
    }
}
