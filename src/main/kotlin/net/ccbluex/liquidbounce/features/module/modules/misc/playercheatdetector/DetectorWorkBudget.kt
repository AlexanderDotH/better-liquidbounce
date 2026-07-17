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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

class DetectorWorkBudget {

    private var lastMovementSampleTick: Int? = null
    private var blockActionTick = Int.MIN_VALUE
    private var blockActionsThisTick = 0

    fun shouldSampleMovement(tick: Int, intervalTicks: Int): Boolean {
        val interval = intervalTicks.coerceAtLeast(1)
        val lastTick = lastMovementSampleTick

        if (lastTick != null && tick >= lastTick && tick - lastTick < interval) {
            return false
        }

        lastMovementSampleTick = tick
        return true
    }

    fun tryConsumeBlockAction(tick: Int, maxActionsPerTick: Int): Boolean {
        if (maxActionsPerTick <= 0) {
            return false
        }

        if (tick != blockActionTick) {
            blockActionTick = tick
            blockActionsThisTick = 0
        }

        if (blockActionsThisTick >= maxActionsPerTick) {
            return false
        }

        blockActionsThisTick++
        return true
    }

    fun reset() {
        lastMovementSampleTick = null
        blockActionTick = Int.MIN_VALUE
        blockActionsThisTick = 0
    }
}
