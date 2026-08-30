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
@file:JvmName("ProminentBlockColorResolverKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.util.ARGB
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class ProminentColorAccumulator {

    private val buckets = arrayOfNulls<ColorBucket>(COLOR_BUCKET_COUNT)

    fun add(argb: Int, weight: Int = 1) {
        if (weight <= 0) return

        val alpha = ARGB.alpha(argb)
        if (alpha < MIN_VISIBLE_ALPHA) return

        val red = ARGB.red(argb)
        val green = ARGB.green(argb)
        val blue = ARGB.blue(argb)
        val bucketIndex = colorBucketIndex(red, green, blue)
        val bucket = buckets[bucketIndex] ?: ColorBucket().also { buckets[bucketIndex] = it }
        bucket.add(red, green, blue, weight.toLong() * alpha)
    }

    fun prominentColor(): Color4b? = buckets.asSequence()
        .filterNotNull()
        .maxWithOrNull(compareBy<ColorBucket> { it.weight }
            .thenBy { it.chroma }
            .thenBy { it.brightness })
        ?.toColor()

    private fun colorBucketIndex(red: Int, green: Int, blue: Int): Int =
        ((red ushr COLOR_BUCKET_SHIFT) shl (COLOR_BUCKET_BITS * 2)) or
            ((green ushr COLOR_BUCKET_SHIFT) shl COLOR_BUCKET_BITS) or
            (blue ushr COLOR_BUCKET_SHIFT)

    private class ColorBucket {
        var weight = 0L
            private set
        var chroma = 0L
            private set
        var brightness = 0L
            private set
        private var red = 0L
        private var green = 0L
        private var blue = 0L

        fun add(red: Int, green: Int, blue: Int, weight: Long) {
            this.weight += weight
            this.red += red * weight
            this.green += green * weight
            this.blue += blue * weight
            this.chroma += (maxOf(red, green, blue) - minOf(red, green, blue)) * weight
            this.brightness += maxOf(red, green, blue) * weight
        }

        fun toColor() = Color4b(
            averaged(red),
            averaged(green),
            averaged(blue),
        )

        private fun averaged(channel: Long) = ((channel + weight / 2) / weight).toInt()
    }

    private companion object {
        const val MIN_VISIBLE_ALPHA = 16
        const val COLOR_BUCKET_BITS = 3
        const val COLOR_BUCKET_SHIFT = 8 - COLOR_BUCKET_BITS
        const val COLOR_BUCKET_COUNT = 1 shl (COLOR_BUCKET_BITS * 3)
    }
}
