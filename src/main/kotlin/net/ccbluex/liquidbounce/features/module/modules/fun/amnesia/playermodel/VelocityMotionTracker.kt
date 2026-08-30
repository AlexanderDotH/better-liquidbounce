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

import net.minecraft.world.phys.Vec3

internal class VelocityMotionTracker {

    private var recentVelocityPerMs = Vec3.ZERO
    private var lastSamplePosition: Vec3? = null
    private var lastSampleTime = 0L
    private var lastFrameTime = 0L

    fun frameDelta(now: Long): Long {
        val previous = lastFrameTime
        lastFrameTime = now
        return if (previous == 0L) 1L else (now - previous).coerceIn(1L, 100L)
    }

    fun sample(realPosition: Vec3, now: Long) {
        val previousPosition = lastSamplePosition
        val previousTime = lastSampleTime
        if (previousPosition != null && previousTime > 0L) {
            val delta = realPosition.subtract(previousPosition)
            if (delta.lengthSqr() <= MAX_SAMPLE_DELTA_SQ) {
                val elapsed = (now - previousTime).coerceAtLeast(1L)
                recentVelocityPerMs = delta.scale(1.0 / elapsed.toDouble())
            }
        }
        seed(realPosition, now)
    }

    fun retainedVelocity(factor: Float): Vec3 = recentVelocityPerMs.scale(factor.toDouble())

    fun seed(realPosition: Vec3, now: Long = System.currentTimeMillis()) {
        lastSamplePosition = realPosition
        lastSampleTime = now
    }

    fun reset() {
        recentVelocityPerMs = Vec3.ZERO
        lastSamplePosition = null
        lastSampleTime = 0L
        lastFrameTime = 0L
    }

    private companion object {
        const val MAX_SAMPLE_DELTA_SQ = 16.0
    }
}
