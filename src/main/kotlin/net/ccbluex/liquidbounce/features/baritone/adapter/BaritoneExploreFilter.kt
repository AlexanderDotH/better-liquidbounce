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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import kotlin.math.ceil

object BaritoneExploreFilter {
    fun chunks(origin: BaritoneHorizontalPosition, radius: Int): List<BaritoneHorizontalPosition> {
        require(radius in 1..MAX_RADIUS) { "Explore radius must be between 1 and $MAX_RADIUS blocks" }
        val centerX = origin.x shr CHUNK_SHIFT
        val centerZ = origin.z shr CHUNK_SHIFT
        val chunkRadius = ceil(radius / CHUNK_SIZE.toDouble()).toInt()
        val radiusSquared = radius.toLong() * radius
        return buildList {
            for (offsetX in -chunkRadius..chunkRadius) {
                for (offsetZ in -chunkRadius..chunkRadius) {
                    val blockX = offsetX.toLong() * CHUNK_SIZE
                    val blockZ = offsetZ.toLong() * CHUNK_SIZE
                    if (blockX * blockX + blockZ * blockZ <= radiusSquared) {
                        add(BaritoneHorizontalPosition(centerX + offsetX, centerZ + offsetZ))
                    }
                }
            }
        }
    }

    fun json(chunks: Collection<BaritoneHorizontalPosition>): String = chunks.joinToString(
        separator = ",",
        prefix = "[",
        postfix = "]",
    ) { "{\"x\":${it.x},\"z\":${it.z}}" }

    private const val CHUNK_SHIFT = 4
    private const val CHUNK_SIZE = 16
    private const val MAX_RADIUS = 4_096
}
