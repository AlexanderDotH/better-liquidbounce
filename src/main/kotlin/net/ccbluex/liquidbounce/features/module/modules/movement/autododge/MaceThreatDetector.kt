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

import net.minecraft.world.phys.Vec3

data class MaceThreatCandidate(
    val entityId: Int,
    val name: String,
    val position: Vec3,
    val lookDirection: Vec3,
    val isHoldingMace: Boolean,
    val isAlive: Boolean = true,
    val isRemoved: Boolean = false,
    val isBot: Boolean = false,
    val isSelf: Boolean = false,
)

enum class MaceThreatKind {
    PACKET_CAPABLE,
}

data class MaceThreat(
    val candidate: MaceThreatCandidate,
    val kind: MaceThreatKind,
    val distanceSquared: Double,
)

/** Treats every nearby mace holder as packet-capable because fake fall height can be committed in one attack tick. */
class MaceThreatDetector {

    private var selectedThreat: MaceThreat? = null
    private var remainingMemoryTicks = 0

    fun update(
        targetPosition: Vec3,
        candidates: Iterable<MaceThreatCandidate>,
        packetThreatRange: Double,
        threatMemoryTicks: Int,
    ): MaceThreat? {
        require(packetThreatRange.isFinite() && packetThreatRange > 0.0) {
            "Mace packet threat range must be finite and positive"
        }
        val candidateList = candidates.toList()
        discardIneligibleSelection(candidateList)
        val rangeSquared = packetThreatRange * packetThreatRange
        val detected = candidateList.asSequence()
            .filter(MaceThreatCandidate::isEligible)
            .map { candidate -> candidate to candidate.position.distanceToSqr(targetPosition) }
            .filter { (_, distanceSquared) -> distanceSquared <= rangeSquared }
            .minWithOrNull(compareBy<Pair<MaceThreatCandidate, Double>> { it.second }.thenBy { it.first.entityId })
            ?.let { (candidate, distanceSquared) ->
                MaceThreat(candidate, MaceThreatKind.PACKET_CAPABLE, distanceSquared)
            }
        if (detected != null) {
            remember(detected, threatMemoryTicks)
            return detected
        }
        if (remainingMemoryTicks > 0) {
            remainingMemoryTicks--
            return selectedThreat
        }

        reset()
        return null
    }

    fun reset() {
        selectedThreat = null
        remainingMemoryTicks = 0
    }

    private fun remember(threat: MaceThreat, memoryTicks: Int) {
        selectedThreat = threat
        remainingMemoryTicks = memoryTicks.coerceAtLeast(0)
    }

    private fun discardIneligibleSelection(candidates: List<MaceThreatCandidate>) {
        val selectedId = selectedThreat?.candidate?.entityId ?: return
        val selectedCandidate = candidates.firstOrNull { it.entityId == selectedId } ?: return
        if (!selectedCandidate.isPotentiallyEligible()) {
            reset()
        }
    }
}

private fun MaceThreatCandidate.isEligible(): Boolean =
    isHoldingMace && isPotentiallyEligible()

private fun MaceThreatCandidate.isPotentiallyEligible(): Boolean =
    !isSelf && isAlive && !isRemoved && !isBot
