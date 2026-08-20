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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

internal class MerchantCpsGate(private val sampleCps: (IntRange) -> Int = IntRange::random) {

    private var nextAttemptTick = Double.NEGATIVE_INFINITY

    fun canAttempt(tick: Int): Boolean = tick >= nextAttemptTick

    fun recordAttempt(tick: Int, cps: IntRange) {
        val sampled = sampleCps(cps).coerceIn(cps)
        val carriedDeadline = nextAttemptTick.takeIf { tick - it in 0.0..MAX_PHASE_CARRY_TICKS }
        nextAttemptTick = (carriedDeadline ?: tick.toDouble()) + TICKS_PER_SECOND / sampled
    }

    fun reset() {
        nextAttemptTick = Double.NEGATIVE_INFINITY
    }

    companion object {
        private const val TICKS_PER_SECOND = 20.0
        private const val MAX_PHASE_CARRY_TICKS = 1.0

        fun delayTicks(cps: Int): Double = TICKS_PER_SECOND / cps.coerceAtLeast(1)
    }
}
