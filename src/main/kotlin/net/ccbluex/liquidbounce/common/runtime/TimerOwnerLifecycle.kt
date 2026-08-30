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
package net.ccbluex.liquidbounce.common.runtime

fun interface TimerOwnerRunningProvider {
    fun isRunning(owner: Any): Boolean
}

object TimerOwnerLifecycle {

    private val RUNNING_BY_DEFAULT = TimerOwnerRunningProvider { true }

    @Volatile
    private var provider = RUNNING_BY_DEFAULT

    @Synchronized
    fun install(provider: TimerOwnerRunningProvider) {
        this.provider = provider
    }

    fun isRunning(owner: Any): Boolean = provider.isRunning(owner)

    @Synchronized
    internal fun <T> withProviderForTest(
        provider: TimerOwnerRunningProvider,
        block: () -> T,
    ): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
