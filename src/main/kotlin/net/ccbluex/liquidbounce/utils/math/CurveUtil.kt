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

package net.ccbluex.liquidbounce.utils.math

import net.ccbluex.liquidbounce.common.Tagged
import org.joml.Vector2fc

/**
 * Chart.js spline interpolation
 */
object CurveUtil {

    enum class OnOutOfBounds(override val tag: String) : Tagged {
        CLAMP("Clamp"),
        EXTEND("Extend");

        internal fun resolveOutOfBoundsY(
            data: List<Vector2fc>,
            xPos: Float,
            isLeftSide: Boolean
        ): Float {
            return when (this) {
                CLAMP -> if (isLeftSide) data.first().y() else data.last().y()
                EXTEND -> CurveInterpolation.extrapolateLinear(data, xPos, isLeftSide)
            }
        }
    }

    /**
     * Find Y position at a given X using spline interpolation.
     *
     * @param data List of 2D points representing the curve
     * @param xPos X position to sample
     * @param tension Spline tension in range [0, 1] (out-of-range values are normalized)
     * @param onOutOfBounds Behavior for X values outside the curve domain, defaults to [OnOutOfBounds.CLAMP]
     */
    @JvmOverloads
    @JvmStatic
    fun transform(
        data: List<Vector2fc>,
        xPos: Float,
        tension: Float,
        onOutOfBounds: OnOutOfBounds = OnOutOfBounds.CLAMP,
    ): Float {
        require(data.isNotEmpty()) { "Curve data must not be empty" }

        if (data.size == 1) {
            return data[0].y()
        }

        val normalizedData = CurveDataNormalizer.sortAndDeduplicateByX(data)
        val normalizedTension = CurveDataNormalizer.normalizeTension(tension)

        return transformNormalized(normalizedData, xPos, normalizedTension, onOutOfBounds)
    }

    @JvmStatic
    internal fun transformNormalized(
        data: List<Vector2fc>,
        xPos: Float,
        tension: Float,
        onOutOfBounds: OnOutOfBounds,
    ): Float {
        return CurveInterpolation.transformNormalized(data, xPos, tension, onOutOfBounds)
    }
}
