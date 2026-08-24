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

package net.ccbluex.liquidbounce.render.engine

import kotlin.math.ceil
import kotlin.math.exp

data class GaussianPair(val offset: Float, val weight: Float)

data class GaussianKernel(val centerWeight: Float, val pairs: List<GaussianPair>) {

    companion object {
        const val MIN_SCREEN_RADIUS = 4f
        const val MAX_SCREEN_RADIUS = 24f
        const val MIN_SOFTNESS = 0.5f
        const val MAX_SOFTNESS = 1.5f
        const val PAIR_COUNT = 6

        fun forScreenRadius(radius: Float, softness: Float = 1f): GaussianKernel {
            val halfRadius = radius.coerceIn(MIN_SCREEN_RADIUS, MAX_SCREEN_RADIUS) * 0.5f
            val sampleRadius = ceil(halfRadius).toInt().coerceIn(1, PAIR_COUNT * 2)
            val sigma = (halfRadius / 3f * softness.coerceIn(MIN_SOFTNESS, MAX_SOFTNESS)).coerceAtLeast(0.5f)
            val discrete = FloatArray(PAIR_COUNT * 2 + 1) { index ->
                if (index > sampleRadius) 0f else exp((-index * index / (2f * sigma * sigma)).toDouble()).toFloat()
            }
            val normalization = discrete[0] + 2f * discrete.drop(1).sum()
            discrete.indices.forEach { discrete[it] /= normalization }

            val pairs = List(PAIR_COUNT) { pairIndex ->
                val firstIndex = pairIndex * 2 + 1
                val secondIndex = firstIndex + 1
                val firstWeight = discrete[firstIndex]
                val secondWeight = discrete[secondIndex]
                val combinedWeight = firstWeight + secondWeight
                val offset = if (combinedWeight > 0f) {
                    (firstIndex * firstWeight + secondIndex * secondWeight) / combinedWeight
                } else {
                    firstIndex + 0.5f
                }

                GaussianPair(offset, combinedWeight)
            }

            return GaussianKernel(discrete[0], pairs)
        }
    }
}
