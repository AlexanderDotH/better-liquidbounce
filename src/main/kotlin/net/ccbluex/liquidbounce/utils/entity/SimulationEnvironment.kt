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

import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SimulatedPlayer.land() {
    fallDistance = 0.0
}

internal fun SimulatedPlayer.hasStatusEffect(effect: Holder<MobEffect>): Boolean {
    val instance = player.getEffect(effect) ?: return false
    return instance.duration >= simulatedTicks
}

internal fun SimulatedPlayer.getStatusEffect(effect: Holder<MobEffect>): MobEffectInstance? {
    val instance = player.getEffect(effect) ?: return null
    return instance.takeIf { it.duration >= simulatedTicks }
}

internal fun SimulatedPlayer.getViewVector(): Vec3 = calculateViewVector(xRot, yRot)

private fun calculateViewVector(xRot: Float, yRot: Float): Vec3 {
    val realXRot = xRot * (Math.PI.toFloat() / 180f)
    val realYRot = -yRot * (Math.PI.toFloat() / 180f)
    val yCos = Mth.cos(realYRot.toDouble())
    val ySin = Mth.sin(realYRot.toDouble())
    val xCos = Mth.cos(realXRot.toDouble())
    val xSin = Mth.sin(realXRot.toDouble())
    return Vec3((ySin * xCos).toDouble(), (-xSin).toDouble(), (yCos * xCos).toDouble())
}

internal fun SimulatedPlayer.getBlockPosBelowThatAffectsMovement(): BlockPos =
    BlockPos.containing(pos.x, boundingBox.minY - 0.5000001, pos.z)

internal fun SimulatedPlayer.doesNotCollide(offsetX: Double, offsetY: Double, offsetZ: Double): Boolean =
    doesNotCollide(boundingBox.move(offsetX, offsetY, offsetZ))

private fun SimulatedPlayer.doesNotCollide(box: AABB): Boolean =
    player.level().noCollision(player, box) && !player.level().containsAnyLiquid(box)

internal fun SimulatedPlayer.getJumpPower(): Float =
    getAttributeValue(Attributes.JUMP_STRENGTH).toFloat() * getJumpVelocityMultiplier() + getJumpBoostPower()

private fun SimulatedPlayer.getJumpBoostPower(): Float {
    val jumpBoost = getStatusEffect(MobEffects.JUMP_BOOST) ?: return 0f
    return 0.1f * (jumpBoost.amplifier.toFloat() + 1f)
}

private fun SimulatedPlayer.getJumpVelocityMultiplier(): Float {
    val currentFactor = pos.toBlockPos().getBlock()?.jumpFactor ?: 0f
    val belowFactor = getBlockPosBelowThatAffectsMovement().getBlock()?.jumpFactor ?: 0f
    return if (currentFactor.toDouble() == 1.0) belowFactor else currentFactor
}
