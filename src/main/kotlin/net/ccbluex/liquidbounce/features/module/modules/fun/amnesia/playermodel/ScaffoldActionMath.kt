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

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldYawMode
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

internal object ScaffoldActionMath {

    const val ACTION_WINDOW_MS = 220L
    private const val MIN_HORIZONTAL_MOVEMENT_SQ = 1.0E-4

    fun yaw(movement: Vec3, fallbackYaw: Float, mode: ScaffoldYawMode): Float {
        val horizontal = Vec3(movement.x, 0.0, movement.z)
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return fallbackYaw
        }

        var yaw = Math.toDegrees(atan2(horizontal.z, horizontal.x)).toFloat() - 90f
        if (mode == ScaffoldYawMode.REVERSE) {
            yaw += 180f
        }
        if (mode == ScaffoldYawMode.SNAP_45) {
            yaw = (yaw / 45f).roundToInt() * 45f
        }
        return Mth.wrapDegrees(yaw)
    }

    fun swingProgress(elapsedMs: Long, windowMs: Long = ACTION_WINDOW_MS): Float {
        val progress = (elapsedMs.coerceAtLeast(0L).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        return sin(progress * PI).toFloat().coerceIn(0f, 1f)
    }
}
