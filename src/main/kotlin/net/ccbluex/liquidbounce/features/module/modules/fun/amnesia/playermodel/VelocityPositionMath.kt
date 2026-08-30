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

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

internal object VelocityPositionMath {

    fun capDesync(predicted: Vec3, realPosition: Vec3, maxDesync: Float): Vec3 {
        val offset = realPosition.subtract(predicted)
        val maxDistance = maxDesync.toDouble()
        if (offset.lengthSqr() <= maxDistance * maxDistance) {
            return predicted
        }
        return realPosition.subtract(offset.normalize().scale(maxDistance))
    }

    fun tinyRecoil(visualStart: Vec3, realPosition: Vec3, maximumDistance: Float): Vec3 {
        if (maximumDistance <= 0f) {
            return Vec3.ZERO
        }
        val hitOffset = realPosition.subtract(visualStart)
        if (hitOffset.lengthSqr() <= 1.0E-6) {
            return Vec3.ZERO
        }
        val recoilDistance = hitOffset.length().coerceAtMost(maximumDistance.toDouble())
        return hitOffset.normalize().scale(recoilDistance)
    }

    fun lerp(from: Vec3, to: Vec3, factor: Float): Vec3 {
        val fraction = factor.toDouble()
        return Vec3(
            Mth.lerp(fraction, from.x, to.x),
            Mth.lerp(fraction, from.y, to.y),
            Mth.lerp(fraction, from.z, to.z),
        )
    }
}
