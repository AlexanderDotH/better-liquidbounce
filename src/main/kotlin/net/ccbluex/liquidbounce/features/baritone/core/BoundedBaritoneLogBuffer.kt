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

package net.ccbluex.liquidbounce.features.baritone.core

import java.util.concurrent.atomic.AtomicReference

/** Thread-safe, revision-aware log buffer used by snapshots and websocket event publishers. */
class BoundedBaritoneLogBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val state = AtomicReference(BufferState())

    init {
        require(capacity > 0) { "Log buffer capacity must be positive" }
    }

    fun append(entry: BaritoneLogEntry): Boolean {
        while (true) {
            val current = state.get()
            if (entry.revision <= current.latestRevision) {
                return false
            }

            val updated = current.append(entry, capacity)
            if (state.compareAndSet(current, updated)) {
                return true
            }
        }
    }

    fun entries(): List<BaritoneLogEntry> = state.get().entries

    fun latestRevision(): BaritoneRevision = state.get().latestRevision

    fun clear() {
        while (true) {
            val current = state.get()
            val cleared = BufferState(latestRevision = current.latestRevision)
            if (state.compareAndSet(current, cleared)) {
                return
            }
        }
    }

    private data class BufferState(
        val entries: List<BaritoneLogEntry> = emptyList(),
        val latestRevision: BaritoneRevision = BaritoneRevision.ZERO,
    ) {
        fun append(entry: BaritoneLogEntry, capacity: Int): BufferState {
            val retained = ArrayList<BaritoneLogEntry>(capacity)
            val retainedOldEntries = (capacity - 1).coerceAtLeast(0)
            val firstRetainedIndex = (entries.size - retainedOldEntries).coerceAtLeast(0)
            retained.addAll(entries.subList(firstRetainedIndex, entries.size))
            retained.add(entry)
            return BufferState(immutableListCopy(retained), entry.revision)
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
