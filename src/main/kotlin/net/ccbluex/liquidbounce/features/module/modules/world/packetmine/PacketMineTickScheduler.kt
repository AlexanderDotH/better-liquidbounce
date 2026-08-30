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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

internal class PacketMineTickScheduler {

    var tick = 0L
        private set
    var nextAllowedStartTick = 0L
        private set

    fun advanceTick() {
        tick++
    }

    fun resetStartDelay() {
        nextAllowedStartTick = 0L
    }

    fun canStart() = tick >= nextAllowedStartTick

    fun shouldFinish(readyTick: Long, postBreakDelay: Int): Boolean {
        if (tick <= readyTick) {
            return false
        }

        nextAllowedStartTick = tick + postBreakDelay
        return true
    }
}
