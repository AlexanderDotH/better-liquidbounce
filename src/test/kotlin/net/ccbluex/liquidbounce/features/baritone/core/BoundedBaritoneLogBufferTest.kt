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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundedBaritoneLogBufferTest {

    @Test
    fun `buffer retains only the newest entries up to capacity`() {
        val buffer = BoundedBaritoneLogBuffer(capacity = 3)

        (1L..5L).forEach { revision ->
            assertTrue(buffer.append(entry(revision)))
        }

        assertEquals(listOf(3L, 4L, 5L), buffer.entries().map { it.revision.value })
        assertEquals(BaritoneRevision(5), buffer.latestRevision())
    }

    @Test
    fun `stale and duplicate revisions cannot replace newer log state`() {
        val buffer = BoundedBaritoneLogBuffer(capacity = 3)
        assertTrue(buffer.append(entry(5)))

        assertFalse(buffer.append(entry(5, "duplicate")))
        assertFalse(buffer.append(entry(4, "stale")))

        assertEquals(listOf("entry-5"), buffer.entries().map(BaritoneLogEntry::message))
    }

    @Test
    fun `clearing entries preserves the monotonic revision watermark`() {
        val buffer = BoundedBaritoneLogBuffer(capacity = 2)
        buffer.append(entry(8))

        buffer.clear()

        assertEquals(emptyList(), buffer.entries())
        assertFalse(buffer.append(entry(7)))
        assertTrue(buffer.append(entry(9)))
    }

    @Test
    fun `entry snapshots cannot be mutated by callers`() {
        val buffer = BoundedBaritoneLogBuffer(capacity = 2)
        buffer.append(entry(1))

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (buffer.entries() as MutableList<BaritoneLogEntry>).clear()
        }
        assertEquals(1, buffer.entries().size)
    }

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { BoundedBaritoneLogBuffer(0) }
    }

    private fun entry(revision: Long, message: String = "entry-$revision") = BaritoneLogEntry(
        revision = BaritoneRevision(revision),
        level = BaritoneLogLevel.INFO,
        message = message,
        timestamp = revision,
    )
}
