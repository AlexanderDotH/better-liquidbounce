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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug


/** Limits repeated route-failure notifications without hiding the first useful warning. */
internal class SpearKillFailureNotificationGate(private val cooldownTicks: Int) {

    private var nextNotificationTick: Int? = null

    init {
        require(cooldownTicks > 0) { "cooldownTicks must be positive" }
    }

    fun shouldNotify(currentTick: Int): Boolean {
        val nextTick = nextNotificationTick
        if (nextTick != null && currentTick < nextTick) return false

        nextNotificationTick = currentTick + cooldownTicks
        return true
    }

    fun clear() {
        nextNotificationTick = null
    }
}
