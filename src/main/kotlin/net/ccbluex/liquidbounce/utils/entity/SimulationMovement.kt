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

import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.max

internal fun SimulatedPlayer.applyMovementInput(movementInput: Vec3, slipperiness: Float): Vec3 {
    updateVelocity(getFrictionInfluencedSpeed(slipperiness), movementInput)
    deltaMovement = handleOnClimbable(deltaMovement)
    deltaMovement = applyWebSpeed(deltaMovement)
    moveSimulated(deltaMovement)

    val powderSnow = pos.toBlockPos().state?.`is`(Blocks.POWDER_SNOW) == true &&
        PowderSnowBlock.canEntityWalkOnPowderSnow(player)
    return if ((horizontalCollision || jumping) && (isClimbing() || powderSnow)) {
        Vec3(deltaMovement.x, 0.2, deltaMovement.z)
    } else {
        deltaMovement
    }
}

internal fun SimulatedPlayer.updateVelocity(speed: Float, movementInput: Vec3) {
    deltaMovement += Entity.getInputVector(movementInput, speed, yRot)
}

internal fun SimulatedPlayer.getSpeed(): Float = 0.10000000149011612.toFloat()

private fun SimulatedPlayer.getFrictionInfluencedSpeed(slipperiness: Float): Float = if (onGround) {
    getSpeed() * (0.21600002f / (slipperiness * slipperiness * slipperiness))
} else if (input.sprinting) {
    (0.02f + 0.005999999865889549).toFloat()
} else {
    0.02f
}

internal fun SimulatedPlayer.performGroundJump() {
    val jumpPower = getJumpPower().toDouble()
    deltaMovement = Vec3(deltaMovement.x, max(jumpPower, deltaMovement.y), deltaMovement.z)
    if (isSprinting) {
        val yawRadians = yRot.toRadians()
        deltaMovement += Vec3(
            (-yawRadians.fastSin() * 0.2f).toDouble(),
            0.0,
            (yawRadians.fastCos() * 0.2f).toDouble(),
        )
    }
}

private fun SimulatedPlayer.handleOnClimbable(motion: Vec3): Vec3 {
    if (!isClimbing()) {
        return motion
    }
    land()
    val clampedY = climbingVerticalMovement(motion.y)
    return Vec3(
        Mth.clamp(motion.x, -0.15000000596046448, 0.15000000596046448),
        clampedY,
        Mth.clamp(motion.z, -0.15000000596046448, 0.15000000596046448),
    )
}

private fun SimulatedPlayer.climbingVerticalMovement(vertical: Double): Double {
    val clamped = max(vertical, -0.15000000596046448)
    val onScaffolding = pos.toBlockPos().state!!.`is`(Blocks.SCAFFOLDING)
    return if (clamped < 0.0 && !onScaffolding && player.isSuppressingSlidingDownLadder) 0.0 else clamped
}

private fun SimulatedPlayer.applyWebSpeed(motion: Vec3): Vec3 {
    if (level.getBlockState(pos.toBlockPos()).block != Blocks.COBWEB) {
        return motion
    }
    val multiplier = if (hasStatusEffect(MobEffects.WEAVING)) Vec3(0.5, 0.25, 0.5) else Vec3(0.25, 0.05, 0.25)
    return motion.multiply(multiplier.x, multiplier.y, multiplier.z)
}

internal fun SimulatedPlayer.isClimbing(): Boolean {
    val blockPos = pos.toBlockPos()
    val blockState = blockPos.state!!
    return blockState.`is`(BlockTags.CLIMBABLE) ||
        blockState.block is TrapDoorBlock && trapdoorUsableAsLadder(blockPos, blockState)
}

private fun SimulatedPlayer.trapdoorUsableAsLadder(pos: BlockPos, state: BlockState): Boolean {
    if (!state.getValue(TrapDoorBlock.OPEN)) {
        return false
    }
    val below = player.level().getBlockState(pos.below())
    return below.`is`(Blocks.LADDER) && below.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING)
}
