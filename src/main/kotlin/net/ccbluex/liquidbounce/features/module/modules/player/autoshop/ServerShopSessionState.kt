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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import it.unimi.dsi.fastutil.ints.IntArrayList

internal data class PurchaseDebugSummary(
    val elapsedMilliseconds: Long,
    val clickedSlots: List<Int>,
)

internal class ServerShopSessionState {

    var waitedBeforeFirstClick = false
        private set
    var canAutoClose = false
        private set
    var previousCategorySlot = -1

    private val recordedClicks = IntArrayList()
    private var startMilliseconds = 0L

    fun beginDebugSession(now: Long) {
        startMilliseconds = now
    }

    fun markInitialDelayComplete() {
        waitedBeforeFirstClick = true
    }

    fun markPurchaseStarted() {
        canAutoClose = true
    }

    fun recordClick(slot: Int, debug: Boolean) {
        if (debug) {
            recordedClicks.add(slot)
        }
    }

    fun reset(initialCategorySlot: Int, debug: Boolean, now: () -> Long): PurchaseDebugSummary? {
        val summary = debugSummary(debug, now)
        previousCategorySlot = initialCategorySlot
        waitedBeforeFirstClick = false
        canAutoClose = false
        return summary
    }

    private fun debugSummary(debug: Boolean, now: () -> Long): PurchaseDebugSummary? {
        if (!debug || startMilliseconds == 0L || !canAutoClose) {
            return null
        }

        val summary = PurchaseDebugSummary(
            elapsedMilliseconds = now() - startMilliseconds,
            clickedSlots = recordedClicks.toList(),
        )
        recordedClicks.clear()
        startMilliseconds = 0L
        return summary
    }
}
