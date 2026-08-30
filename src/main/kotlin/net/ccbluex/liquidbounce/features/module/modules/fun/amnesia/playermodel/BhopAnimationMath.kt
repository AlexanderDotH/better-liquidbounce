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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.BhopStyle
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

internal object BhopAnimationMath {

    private const val MIN_HORIZONTAL_MOVEMENT_SQ = 1.0E-5

    fun movementVector(previous: Vec3?, current: Vec3, fallback: Vec3, frameDeltaMs: Long): BhopMovementVector {
        val rawMovement = previous?.let(current::subtract) ?: fallback
        val movement = rawMovement.takeIf { it.horizontalDistanceSqr() > MIN_HORIZONTAL_MOVEMENT_SQ } ?: fallback
        val speedPerTick = if (previous == null) {
            movement.horizontalDistance()
        } else {
            movement.horizontalDistance() * (50.0 / frameDeltaMs.toDouble())
        }
        return BhopMovementVector(movement, speedPerTick)
    }

    fun movementYaw(movement: Vec3, fallbackYaw: Float): Float {
        if (movement.horizontalDistanceSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return fallbackYaw
        }
        return Mth.wrapDegrees(Math.toDegrees(atan2(movement.z, movement.x)).toFloat() - 90f)
    }

    fun offset(
        phaseMs: Long,
        style: BhopStyle,
        hopHeight: Float,
        hopInterval: Int,
        strafeAmount: Float,
        displayStrength: Double,
        directionX: Double,
        directionZ: Double,
    ): Vec3 {
        val progress = phaseMs.toDouble() / styleInterval(style, hopInterval).toDouble()
        val vertical = sin(progress * PI).coerceAtLeast(0.0) * styleHeight(style, hopHeight) * displayStrength
        val side = sin(progress * PI * 2.0) * styleStrafe(style, strafeAmount) * displayStrength
        return Vec3(-directionZ * side, vertical, directionX * side)
    }

    fun styleHeight(style: BhopStyle, hopHeight: Float): Double = when (style) {
        BhopStyle.NORMAL -> hopHeight.toDouble()
        BhopStyle.LOW_HOP -> hopHeight.toDouble() * 0.55
        BhopStyle.STRAFE -> hopHeight.toDouble() * 0.85
    }.coerceAtLeast(0.0)

    fun styleInterval(style: BhopStyle, hopInterval: Int): Long = when (style) {
        BhopStyle.NORMAL -> hopInterval
        BhopStyle.LOW_HOP -> (hopInterval * 0.75f).toInt()
        BhopStyle.STRAFE -> (hopInterval * 0.85f).toInt()
    }.coerceAtLeast(1).toLong()

    fun styleStrafe(style: BhopStyle, strafeAmount: Float): Double = when (style) {
        BhopStyle.NORMAL -> strafeAmount.toDouble() * 0.4
        BhopStyle.LOW_HOP -> strafeAmount.toDouble() * 0.25
        BhopStyle.STRAFE -> strafeAmount.toDouble()
    }.coerceAtLeast(0.0)

    fun updateStrength(
        displayStrength: Double,
        targetStrength: Double,
        frameDeltaMs: Long,
        smoothStopDuration: Int,
    ): Double {
        if (targetStrength >= displayStrength || smoothStopDuration <= 0) {
            return targetStrength
        }
        val factor = (frameDeltaMs.toDouble() / smoothStopDuration.toDouble()).coerceIn(0.0, 1.0)
        return Mth.lerp(factor, displayStrength, targetStrength)
    }
}

internal data class BhopMovementVector(
    val vector: Vec3,
    val horizontalSpeedPerTick: Double,
)
