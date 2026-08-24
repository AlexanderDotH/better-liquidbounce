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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BaritoneLifecyclePolicyTest {

    @Test
    fun `cleanup boundaries cancel pathing clear keys reset pause and invalidate route`() {
        val calls = mutableListOf<String>()
        val policy = policy(calls)

        policy.apply(BaritoneLifecycleEvent.DEATH)

        assertEquals(listOf("cancel", "keys", "pause", "route"), calls)
    }

    @Test
    fun `dimension changes reset pause timers and invalidate presentation without cancelling the task`() {
        val calls = mutableListOf<String>()
        val policy = policy(calls)

        policy.apply(BaritoneLifecycleEvent.DIMENSION_CHANGE)

        assertEquals(listOf("automaticPause", "route"), calls)
    }

    private fun policy(calls: MutableList<String>) = BaritoneLifecyclePolicy(
        cancelEverything = { calls += "cancel" },
        clearAllKeys = { calls += "keys" },
        resetPause = { calls += "pause" },
        resetAutomaticPause = { calls += "automaticPause" },
        invalidateRoute = { calls += "route" },
    )
}
