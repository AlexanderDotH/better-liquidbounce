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

import java.util.concurrent.PriorityBlockingQueue

internal class TimerRequestQueue(
    private val ownerRunning: (Any) -> Boolean,
    private val mayPrune: () -> Boolean,
) {
    private val requests = PriorityBlockingQueue<TimerRequest>(11, compareBy { -it.priority })
    private var currentTick = 0

    fun tick(deltaTime: Int = 1) {
        currentTick += deltaTime
    }

    fun request(owner: Any, value: Float, priority: Int, expiresIn: Int) {
        requests.removeIf { it.owner === owner }
        requests += TimerRequest(currentTick + expiresIn, priority, owner, value)
    }

    fun activeValue(): Float? {
        var top = requests.peek() ?: return null
        if (!mayPrune()) return top.value

        while (top.expiresAt <= currentTick || !ownerRunning(top.owner)) {
            requests.remove()
            top = requests.peek() ?: return null
        }
        return top.value
    }

    private data class TimerRequest(
        val expiresAt: Int,
        val priority: Int,
        val owner: Any,
        val value: Float,
    )
}
