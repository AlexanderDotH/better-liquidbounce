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

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

data class SpearThreatTargetSnapshot(
    val boundingBox: AABB,
    val velocity: Vec3,
)

/**
 * Immutable input boundary between loaded Minecraft players and spear threat policy.
 *
 * Friend and teammate flags are deliberately informational: defensive protection applies to both.
 */
data class SpearThreatCandidate(
    val entityId: Int,
    val name: String,
    val position: Vec3,
    val eyePosition: Vec3,
    val lookDirection: Vec3,
    val isHoldingSpear: Boolean,
    val isUsingSpear: Boolean,
    val isAlive: Boolean = true,
    val isRemoved: Boolean = false,
    val isBot: Boolean = false,
    val isSelf: Boolean = false,
    val isFriend: Boolean = false,
    val isTeammate: Boolean = false,
    val hasSignificantPositionJump: Boolean = false,
)

enum class SpearThreatKind(val priority: Int) {
    HOLDING_AIMED(1),
    USING(2),
    USING_AIMED(3),
}

data class SpearThreat(
    val candidate: SpearThreatCandidate,
    val kind: SpearThreatKind,
    val distanceSquared: Double,
)

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
    ): SpearThreat? {
        val candidateList = candidates.toList()
        discardSelectionIfNowIneligible(candidateList)

        val targetBox = target.sweptBoundingBox(aimMargin)
        val eligibleCandidates = candidateList.filter(SpearThreatCandidate::isEligible)
        val detectedThreats = eligibleCandidates.mapNotNull { it.toThreat(targetBox, target.center) }

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

private val SpearThreatTargetSnapshot.center: Vec3
    get() = Vec3(
        (boundingBox.minX + boundingBox.maxX) * 0.5,
        (boundingBox.minY + boundingBox.maxY) * 0.5,
        (boundingBox.minZ + boundingBox.maxZ) * 0.5,
    )

private fun SpearThreatTargetSnapshot.sweptBoundingBox(aimMargin: Double): AABB {
    val futureBox = boundingBox.move(velocity.scale(SWEEP_TICKS))
    return boundingBox.minmax(futureBox).inflate(aimMargin.coerceAtLeast(0.0))
}

private fun SpearThreatCandidate.isEligible(): Boolean =
    !isSelf && isAlive && !isRemoved && !isBot

private fun SpearThreatCandidate.toThreat(targetBox: AABB, targetPosition: Vec3): SpearThreat? {
    val aimed = isAimedAt(targetBox)
    val kind = when {
        isUsingSpear && aimed -> SpearThreatKind.USING_AIMED
        isUsingSpear -> SpearThreatKind.USING
        isHoldingSpear && aimed -> SpearThreatKind.HOLDING_AIMED
        else -> return null
    }

    return SpearThreat(
        candidate = this,
        kind = kind,
        distanceSquared = position.distanceToSqr(targetPosition),
    )
}

private fun SpearThreatCandidate.isAimedAt(targetBox: AABB): Boolean {
    if (lookDirection.lengthSqr() <= MIN_DIRECTION_LENGTH_SQUARED) {
        return false
    }

    if (targetBox.contains(eyePosition)) {
        return true
    }

    val centerDistance = eyePosition.distanceTo(targetBox.center)
    val diagonal = sqrt(
        targetBox.xsize * targetBox.xsize +
            targetBox.ysize * targetBox.ysize +
            targetBox.zsize * targetBox.zsize
    )
    val rayEnd = eyePosition.add(lookDirection.normalize().scale(centerDistance + diagonal))

    return targetBox.clip(eyePosition, rayEnd).isPresent
}

private fun List<SpearThreat>.bestThreat(): SpearThreat? = minWithOrNull(
    SPEAR_THREAT_ORDER
)

private fun SpearThreat.isHigherRankedThan(other: SpearThreat): Boolean = when {
    kind.priority != other.kind.priority -> kind.priority > other.kind.priority
    else -> distanceSquared < other.distanceSquared
}

private val SPEAR_THREAT_ORDER = compareByDescending<SpearThreat> { it.kind.priority }
    .thenBy { it.distanceSquared }
    .thenBy { it.candidate.entityId }

private const val SWEEP_TICKS = 2.0
private const val MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12
