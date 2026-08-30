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
package net.ccbluex.liquidbounce.utils.client

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerRequestQueueTest {

    @Test
    fun `higher priority wins and requests expire at the same tick boundary`() {
        val queue = TimerRequestQueue(ownerRunning = { true }, mayPrune = { true })
        queue.request(owner = LOW, value = 0.5f, priority = 1, expiresIn = 4)
        queue.request(owner = HIGH, value = 2.0f, priority = 10, expiresIn = 2)

        assertEquals(2.0f, queue.activeValue())
        queue.tick()
        assertEquals(2.0f, queue.activeValue())
        queue.tick()
        assertEquals(0.5f, queue.activeValue())
    }

    @Test
    fun `new request from identical owner replaces the old request`() {
        val queue = TimerRequestQueue(ownerRunning = { true }, mayPrune = { true })
        queue.request(owner = LOW, value = 0.5f, priority = 100, expiresIn = 20)
        queue.request(owner = LOW, value = 1.25f, priority = 1, expiresIn = 20)

        assertEquals(1.25f, queue.activeValue())
    }

    @Test
    fun `disabled owner is removed before its speed becomes visible`() {
        val queue = TimerRequestQueue(ownerRunning = { owner -> owner !== HIGH }, mayPrune = { true })
        queue.request(owner = LOW, value = 0.75f, priority = 1, expiresIn = 20)
        queue.request(owner = HIGH, value = 2.0f, priority = 10, expiresIn = 20)

        assertEquals(0.75f, queue.activeValue())
    }

    private companion object {
        val LOW = Any()
        val HIGH = Any()
    }
}
