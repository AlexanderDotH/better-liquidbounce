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

import net.ccbluex.liquidbounce.common.runtime.TimerOwnerLifecycle
import net.ccbluex.liquidbounce.utils.client.Timer.requestTimerSpeed
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.Minecraft

/** Global minecraft timer */
object Timer {
    private val requests = TimerRequestQueue(
        ownerRunning = TimerOwnerLifecycle::isRunning,
        mayPrune = { Minecraft.getInstance()?.isSameThread != false },
    )

    /**
     * You cannot set this manually. Use [requestTimerSpeed] instead.
     */
    val timerSpeed: Float
        get() = requests.activeValue() ?: 1.0f

    internal fun advanceTick() = requests.tick()

    /**
     * Requests a timer speed change. If another module requests with a higher priority,
     * the other module is prioritized.
     */
    fun requestTimerSpeed(timerSpeed: Float, priority: Priority, provider: Any, resetAfterTicks: Int = 1) {
        requests.request(
            owner = provider,
            value = timerSpeed,
            priority = priority.priority,
            // this prevents requests from being instantly removed
            expiresIn = resetAfterTicks + 1,
        )
    }
}
