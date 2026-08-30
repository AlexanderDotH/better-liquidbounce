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

internal fun SimulatedPlayer.travelOnGround(movementInput: Vec3, gravity: Double) {
    val blockPos = getBlockPosBelowThatAffectsMovement()
    val slipperiness = player.level().getBlockState(blockPos).block.friction
    val friction = if (onGround) slipperiness * 0.91f else 0.91f
    val movement = applyMovementInput(movementInput, slipperiness)
    val verticalMovement = resolveVerticalMovement(movement, blockPos, gravity)

    deltaMovement = if (player.shouldDiscardFriction()) {
        Vec3(movement.x, verticalMovement, movement.z)
    } else {
        Vec3(
            movement.x * friction.toDouble(),
            verticalMovement * 0.9800000190734863,
            movement.z * friction.toDouble(),
        )
    }
}

private fun SimulatedPlayer.resolveVerticalMovement(
    movement: Vec3,
    blockPos: BlockPos,
    gravity: Double,
): Double {
    val levitation = getStatusEffect(MobEffects.LEVITATION)
    if (levitation != null) {
        return movement.y + (0.05 * (levitation.amplifier + 1).toDouble() - movement.y) * 0.2
    }
    if (player.level().isClientSide && !player.level().hasChunkAt(blockPos.x, blockPos.z)) {
        return if (pos.y > player.level().minY.toDouble()) -0.1 else 0.0
    }
    return if (player.isNoGravity) movement.y else movement.y - gravity
}
