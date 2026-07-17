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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object PlayerModelFakeJesusState {

    private const val ZERO_OFFSET_EPSILON = 0.005
    private val sampleOffsets = arrayOf(
        0.0 to 0.0,
        0.28 to 0.28,
        0.28 to -0.28,
        -0.28 to 0.28,
        -0.28 to -0.28,
    )

    private var targetEntityId: Int? = null
    private var active = false
    private var displayOffset = 0.0
    private var targetOffset = 0.0
    private var standingPose = false
    private var lastFrameTime = 0L

    fun tick(
        target: LivingEntity,
        visualPos: Vec3,
        surfaceOffset: Float,
        bobAmount: Float,
        activationRange: Float,
        smoothDuration: Int,
        spoofStandingPose: Boolean,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        val desiredOffset = calculateDesiredOffset(target, visualPos, surfaceOffset, bobAmount, activationRange)
        active = desiredOffset != null
        targetOffset = desiredOffset ?: 0.0
        standingPose = spoofStandingPose && (active || abs(displayOffset) > ZERO_OFFSET_EPSILON)
        updateDisplayOffset(smoothDuration)
    }

    fun getTransform(entity: LivingEntity, partialTicks: Float, basePosition: Vec3): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId || abs(displayOffset) <= ZERO_OFFSET_EPSILON) {
            return null
        }

        return PlayerModelVisualTransform(
            position = basePosition.add(0.0, displayOffset, 0.0),
            bodyYaw = entity.interpolateBodyYaw(partialTicks),
            headYaw = entity.interpolateHeadYaw(partialTicks),
            pitch = entity.interpolatePitch(partialTicks),
        )
    }

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (entity.id != targetEntityId || !standingPose) {
            return null
        }

        return PlayerModelActionState(groundPose = true)
    }

    fun reset() {
        targetEntityId = null
        active = false
        displayOffset = 0.0
        targetOffset = 0.0
        standingPose = false
        lastFrameTime = 0L
    }

    private fun calculateDesiredOffset(
        target: LivingEntity,
        visualPos: Vec3,
        surfaceOffset: Float,
        bobAmount: Float,
        activationRange: Float,
    ): Double? {
        val level = mc.level ?: return null
        val surfaceY = findWaterSurface(level, visualPos) ?: return null
        val bob = sin((target.tickCount + target.id) * 0.35 * PI).coerceIn(-1.0, 1.0) * bobAmount
        val offset = surfaceY + surfaceOffset + bob - visualPos.y

        if (abs(offset) > activationRange.toDouble()) {
            return null
        }

        return offset
    }

    private fun findWaterSurface(level: BlockGetter, visualPos: Vec3): Double? {
        val sampleYs = doubleArrayOf(visualPos.y - 0.04, visualPos.y - 0.24, visualPos.y + 0.02)
        var bestSurface: Double? = null

        for (sampleY in sampleYs) {
            for ((xOffset, zOffset) in sampleOffsets) {
                val pos = BlockPos.containing(visualPos.x + xOffset, sampleY, visualPos.z + zOffset)
                val fluidState = level.getFluidState(pos)
                if (!fluidState.`is`(FluidTags.WATER)) {
                    continue
                }

                val surface = pos.y.toDouble() + fluidState.getHeight(level, pos).toDouble()
                bestSurface = maxOf(bestSurface ?: surface, surface)
            }
        }

        return bestSurface
    }

    private fun updateDisplayOffset(smoothDuration: Int) {
        val now = System.currentTimeMillis()
        val previous = lastFrameTime
        lastFrameTime = now
        if (previous == 0L || smoothDuration <= 0) {
            displayOffset = targetOffset
            return
        }

        val frameDelta = (now - previous).coerceIn(1L, 100L).toDouble()
        val t = (frameDelta / smoothDuration.toDouble()).coerceIn(0.0, 1.0)
        displayOffset = Mth.lerp(t, displayOffset, targetOffset)
    }
}
