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

/**
 * Selects the primary spear threat and bridges short gaps in remote player state.
 *
 * Position jumps can extend an existing selection, but never create one. Call [reset] when the world changes.
 */
class SpearThreatDetector {

    private var selectedThreat: SpearThreat? = null
    private var remainingMemoryTicks = 0

    fun update(
        target: SpearThreatTargetSnapshot,
        candidates: Iterable<SpearThreatCandidate>,
        aimMargin: Double,
        threatMemoryTicks: Int,
        visibilityGraceTicks: Int = 0,
    ): SpearThreat? {
        val candidateList = candidates.toList()
        discardSelectionIfNowIneligible(candidateList)

        val targetBox = target.sweptBoundingBox(aimMargin)
        val eligibleCandidates = candidateList.filter(SpearThreatCandidate::isEligible)
        val detectedThreats = eligibleCandidates.mapNotNull {
            it.toThreat(targetBox, target.center, visibilityGraceTicks)
        }

        val refreshed = refreshSelectedThreat(eligibleCandidates, detectedThreats, target, threatMemoryTicks)
        replaceWithHigherRankedThreat(detectedThreats, threatMemoryTicks)

        return retainOrSelect(detectedThreats, threatMemoryTicks, refreshed)
    }

    fun reset() {
        selectedThreat = null
        remainingMemoryTicks = 0
    }

    private fun discardSelectionIfNowIneligible(candidates: List<SpearThreatCandidate>) {
        val selectedId = selectedThreat?.candidate?.entityId ?: return
        val currentCandidate = candidates.firstOrNull { it.entityId == selectedId } ?: return

        if (!currentCandidate.isEligible()) {
            reset()
        }
    }

    private fun refreshSelectedThreat(
        candidates: List<SpearThreatCandidate>,
        threats: List<SpearThreat>,
        target: SpearThreatTargetSnapshot,
        memoryTicks: Int,
    ): Boolean {
        val selected = selectedThreat ?: return false
        val detected = threats.firstOrNull { it.candidate.entityId == selected.candidate.entityId }

        if (detected != null) {
            remember(detected, memoryTicks)
            return true
        }

        val jumped = candidates.firstOrNull {
            it.entityId == selected.candidate.entityId && it.hasSignificantPositionJump
        } ?: return false

        val refreshed = selected.copy(
            candidate = jumped,
            kind = SpearThreatKind.ATTACK_COMMITTED,
            response = SpearThreatResponse.EMERGENCY,
            distanceSquared = jumped.position.distanceToSqr(target.center),
        )
        remember(refreshed, memoryTicks)
        return true
    }

    private fun replaceWithHigherRankedThreat(threats: List<SpearThreat>, memoryTicks: Int) {
        val selected = selectedThreat ?: return
        val best = threats.bestThreat() ?: return

        if (best.isHigherRankedThan(selected)) {
            remember(best, memoryTicks)
        }
    }

    private fun retainOrSelect(
        threats: List<SpearThreat>,
        memoryTicks: Int,
        refreshed: Boolean,
    ): SpearThreat? {
        val selected = selectedThreat
        if (selected == null) {
            return threats.bestThreat()?.also { remember(it, memoryTicks) }
        }

        if (refreshed || threats.any { it.candidate.entityId == selected.candidate.entityId }) {
            return selectedThreat
        }

        if (remainingMemoryTicks > 0) {
            remainingMemoryTicks--
            return selectedThreat
        }

        reset()
        return threats.bestThreat()?.also { remember(it, memoryTicks) }
    }

    private fun remember(threat: SpearThreat, memoryTicks: Int) {
        selectedThreat = threat
        remainingMemoryTicks = memoryTicks.coerceAtLeast(0)
    }
}
