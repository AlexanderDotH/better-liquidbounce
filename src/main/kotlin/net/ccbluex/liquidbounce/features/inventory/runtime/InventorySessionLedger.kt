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
package net.ccbluex.liquidbounce.features.inventory.runtime

internal class InventorySessionLedger {

    var isServerSideOpen = false
        private set

    var lastClickedSlot = -1
        private set

    var requiresScheduleRefresh = false
        private set

    private var recentOpening = false

    fun setServerSideOpen(open: Boolean) {
        if (!isServerSideOpen && open) {
            markInventoryOpened()
        }
        isServerSideOpen = open
    }

    fun markInventoryOpened() {
        recentOpening = true
    }

    fun consumeRecentOpening(): Boolean {
        if (!recentOpening) {
            return false
        }
        recentOpening = false
        return true
    }

    fun markClickObserved() {
        requiresScheduleRefresh = true
    }

    fun beginSchedulingPass() {
        requiresScheduleRefresh = false
    }

    fun recordClickedSlot(slot: Int) {
        lastClickedSlot = slot
    }

    fun finishScheduling() {
        lastClickedSlot = -1
    }
}
