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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.GLFW_REPEAT
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeDropPressTrackerTest {

    private val tracker = SafeDropPressTracker()

    @Test
    fun `one physical press can be consumed exactly once`() {
        tracker.record(GLFW_PRESS)

        assertTrue(tracker.consumeFreshPress())
        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `two physical presses queued before a tick remain independently consumable`() {
        tracker.record(GLFW_PRESS)
        tracker.record(GLFW_PRESS)

        assertTrue(tracker.consumeFreshPress())
        assertTrue(tracker.consumeFreshPress())
        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `repeat and release events never enqueue a fresh press`() {
        tracker.record(GLFW_REPEAT)
        tracker.record(GLFW_RELEASE)

        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `holding a key contributes only its initial press`() {
        tracker.record(GLFW_PRESS)
        repeat(5) {
            tracker.record(GLFW_REPEAT)
        }

        assertTrue(tracker.consumeFreshPress())
        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `synchronous screen repeat discards an older unconsumed physical press`() {
        tracker.recordImmediate(GLFW_PRESS)

        tracker.recordImmediate(GLFW_REPEAT)

        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `new synchronous screen press replaces rather than queues stale input`() {
        tracker.recordImmediate(GLFW_PRESS)

        tracker.recordImmediate(GLFW_PRESS)

        assertTrue(tracker.consumeFreshPress())
        assertFalse(tracker.consumeFreshPress())
    }

    @Test
    fun `clear discards every queued press`() {
        tracker.record(GLFW_PRESS)
        tracker.record(GLFW_PRESS)

        tracker.clear()

        assertFalse(tracker.consumeFreshPress())
    }
}
