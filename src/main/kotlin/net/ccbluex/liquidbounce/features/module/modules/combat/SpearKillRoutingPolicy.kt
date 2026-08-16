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

package net.ccbluex.liquidbounce.features.module.modules.combat

/** The route launcher the attack runtime must invoke next. */
internal enum class SpearKillRoutingAttempt {
    DIRECT,
    A_STAR,
}

/** A pure routing-policy outcome: start one route or finish with its existing result. */
internal sealed interface SpearKillRoutingDecision {
    data class Attempt(val route: SpearKillRoutingAttempt) : SpearKillRoutingDecision

    data class Finished(val result: SpearKillAttackStartResult) : SpearKillRoutingDecision
}

/**
 * Selects the next Packet SpearKill route without launching either route itself.
 *
 * AStar uses the validated direct round trip when it is clear. Its collision-aware planner is
 * invoked only after Direct returns [SpearKillAttackStartResult.BLOCKED].
 */
internal object SpearKillRoutingPolicy {

    fun decide(
        mode: SpearKillRoutingMode,
        directResult: SpearKillAttackStartResult?,
        aStarAvailable: Boolean,
        aStarResult: SpearKillAttackStartResult?,
    ): SpearKillRoutingDecision = when (mode) {
        SpearKillRoutingMode.DIRECT -> directDecision(directResult)
        SpearKillRoutingMode.A_STAR,
        SpearKillRoutingMode.NETWORK_OPTIMIZED,
        -> directThenAStarDecision(directResult, aStarAvailable, aStarResult)
    }

    private fun directDecision(
        directResult: SpearKillAttackStartResult?,
    ): SpearKillRoutingDecision = directResult?.let(SpearKillRoutingDecision::Finished)
        ?: SpearKillRoutingDecision.Attempt(SpearKillRoutingAttempt.DIRECT)

    private fun directThenAStarDecision(
        directResult: SpearKillAttackStartResult?,
        aStarAvailable: Boolean,
        aStarResult: SpearKillAttackStartResult?,
    ): SpearKillRoutingDecision {
        if (directResult == null) return SpearKillRoutingDecision.Attempt(SpearKillRoutingAttempt.DIRECT)
        if (directResult != SpearKillAttackStartResult.BLOCKED) {
            return SpearKillRoutingDecision.Finished(directResult)
        }
        return aStarDecision(aStarAvailable, aStarResult, unavailableResult = directResult)
    }

    private fun aStarDecision(
        aStarAvailable: Boolean,
        aStarResult: SpearKillAttackStartResult?,
        unavailableResult: SpearKillAttackStartResult,
    ): SpearKillRoutingDecision {
        if (!aStarAvailable) return SpearKillRoutingDecision.Finished(unavailableResult)
        return aStarResult?.let(SpearKillRoutingDecision::Finished)
            ?: SpearKillRoutingDecision.Attempt(SpearKillRoutingAttempt.A_STAR)
    }
}

/** Executes the pure decision loop while guaranteeing that each planner is invoked at most once. */
internal fun startSpearKillPacketRoute(
    mode: SpearKillRoutingMode,
    aStarAvailable: Boolean = true,
    startDirect: () -> SpearKillAttackStartResult,
    startAStar: () -> SpearKillAttackStartResult,
): SpearKillAttackStartResult {
    var directResult: SpearKillAttackStartResult? = null
    var aStarResult: SpearKillAttackStartResult? = null

    while (true) {
        when (val decision = SpearKillRoutingPolicy.decide(mode, directResult, aStarAvailable, aStarResult)) {
            is SpearKillRoutingDecision.Finished -> return decision.result
            is SpearKillRoutingDecision.Attempt -> when (decision.route) {
                SpearKillRoutingAttempt.DIRECT -> directResult = startDirect()
                SpearKillRoutingAttempt.A_STAR -> aStarResult = startAStar()
            }
        }
    }
}
