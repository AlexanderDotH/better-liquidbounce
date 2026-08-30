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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import kotlin.random.Random

data class SpearJukeDecision(
    val plan: SpearDodgePlan,
    val ticksRemaining: Int,
    val replanned: Boolean,
)

class SpearJukeCommitment(private val random: Random = Random.Default) {
    private var active: ActiveCommitment? = null

    fun update(
        durationTicks: IntRange = DEFAULT_DURATION_TICKS,
        isCurrentInputSafe: (DirectionalInput) -> Boolean,
        replan: () -> SpearDodgePlan,
    ): SpearJukeDecision {
        require(
            !durationTicks.isEmpty() &&
                durationTicks.first >= MIN_DURATION_TICKS &&
                durationTicks.last <= MAX_DURATION_TICKS,
        ) { "Juke duration must stay within $MIN_DURATION_TICKS..$MAX_DURATION_TICKS ticks" }
        val current = active
        if (current != null && isCurrentInputSafe(current.plan.input)) return continueCommitment(current)
        return beginCommitment(replan(), durationTicks)
    }

    fun reset() {
        active = null
    }

    private fun continueCommitment(current: ActiveCommitment): SpearJukeDecision {
        val remaining = current.ticksRemaining
        active = current.takeIf { remaining > 1 }?.copy(ticksRemaining = remaining - 1)
        return SpearJukeDecision(current.plan, remaining, replanned = false)
    }

    private fun beginCommitment(plan: SpearDodgePlan, durationTicks: IntRange): SpearJukeDecision {
        if (!plan.input.isMoving) {
            active = null
            return SpearJukeDecision(plan, ticksRemaining = 0, replanned = true)
        }
        val duration = random.nextInt(durationTicks.first, durationTicks.last + 1)
        active = ActiveCommitment(plan, ticksRemaining = duration - 1).takeIf { duration > 1 }
        return SpearJukeDecision(plan, duration, replanned = true)
    }

    private data class ActiveCommitment(val plan: SpearDodgePlan, val ticksRemaining: Int)

    companion object {
        val DEFAULT_DURATION_TICKS = 2..5
        private const val MIN_DURATION_TICKS = 1
        private const val MAX_DURATION_TICKS = 10
    }
}
