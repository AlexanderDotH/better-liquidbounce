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

import net.minecraft.core.BlockPos

internal class ScaffoldBlockStore {

    private data class FakeBlock(val createdAt: Long)

    private val activeBlocks = LinkedHashMap<BlockPos, FakeBlock>()

    fun positions(): Set<BlockPos> = activeBlocks.keys.toSet()

    fun add(candidates: List<BlockPos>, now: Long): Boolean {
        var placed = false
        candidates.forEach { candidate ->
            if (!activeBlocks.containsKey(candidate)) {
                activeBlocks[candidate] = FakeBlock(now)
                placed = true
            }
        }
        return placed
    }

    fun expire(lifetime: Int, now: Long = System.currentTimeMillis()) {
        val maxAge = lifetime.coerceAtLeast(1).toLong()
        activeBlocks.entries.removeIf { now - it.value.createdAt > maxAge }
    }

    fun trim(maxBlocks: Int) {
        val limit = maxBlocks.coerceAtLeast(1)
        while (activeBlocks.size > limit) {
            val oldest = activeBlocks.keys.firstOrNull() ?: return
            activeBlocks.remove(oldest)
        }
    }

    fun clear() = activeBlocks.clear()
}
