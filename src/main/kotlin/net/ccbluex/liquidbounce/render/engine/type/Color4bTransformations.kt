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
package net.ccbluex.liquidbounce.render.engine.type

import java.lang.Math.fma

sealed interface Color4bTransformations {

    fun fade(fade: Float): Color4b {
        val color = asColor4b()
        return if (fade >= 1.0f) color else color.alpha((color.a * fade).toInt())
    }

    fun darker(): Color4b {
        val color = asColor4b()
        return Color4b(
            darkerChannel(color.r),
            darkerChannel(color.g),
            darkerChannel(color.b),
            color.a,
        )
    }

    /**
     * Interpolates this color with another color using the given percentage.
     */
    fun interpolateTo(other: Color4b, percentage: Double): Color4b =
        interpolateTo(other, percentage, percentage, percentage, percentage)

    /**
     * Interpolates this color with another color using separate factors for each component.
     */
    fun interpolateTo(
        other: Color4b,
        tR: Double,
        tG: Double,
        tB: Double,
        tA: Double,
    ): Color4b {
        val color = asColor4b()
        return Color4b(
            fma(tR, (other.r - color.r).toDouble(), color.r.toDouble()).toInt().coerceIn(0, 255),
            fma(tG, (other.g - color.g).toDouble(), color.g.toDouble()).toInt().coerceIn(0, 255),
            fma(tB, (other.b - color.b).toDouble(), color.b.toDouble()).toInt().coerceIn(0, 255),
            fma(tA, (other.a - color.a).toDouble(), color.a.toDouble()).toInt().coerceIn(0, 255),
        )
    }
}

private fun Color4bTransformations.asColor4b() = this as Color4b

private fun darkerChannel(value: Int) = (value * 0.7f).toInt().coerceAtLeast(0)
