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

internal val SpearThreatTargetSnapshot.center: Vec3
    get() = Vec3(
        (boundingBox.minX + boundingBox.maxX) * 0.5,
        (boundingBox.minY + boundingBox.maxY) * 0.5,
        (boundingBox.minZ + boundingBox.maxZ) * 0.5,
    )

internal fun SpearThreatTargetSnapshot.sweptBoundingBox(aimMargin: Double): AABB {
    val futureBox = boundingBox.move(velocity.scale(SWEEP_TICKS))
    return boundingBox.minmax(futureBox).inflate(aimMargin.coerceAtLeast(0.0))
}

internal fun SpearThreatCandidate.isEligible(): Boolean =
    !isSelf && isAlive && !isRemoved && !isBot

internal fun SpearThreatCandidate.toThreat(
    targetBox: AABB,
    targetPosition: Vec3,
    visibilityGraceTicks: Int,
): SpearThreat? {
    val aimed = isAimedAt(targetBox)
    val distanceSquared = position.distanceToSqr(targetPosition)
    val packetCapable = distanceSquared <= SPEAR_PACKET_THREAT_RANGE_SQUARED
    val kind = when {
        hasSignificantPositionJump && (aimed || packetCapable) && (isUsingSpear || isHoldingSpear) ->
            SpearThreatKind.ATTACK_COMMITTED
        isUsingSpear && aimed -> SpearThreatKind.USING_AIMED
        isUsingSpear && packetCapable -> SpearThreatKind.USING_PACKET_CAPABLE
        isHoldingSpear && aimed -> SpearThreatKind.HOLDING_AIMED
        isHoldingSpear && visibilityAgeTicks < visibilityGraceTicks.coerceAtLeast(0) ->
            SpearThreatKind.HOLDING_NEWLY_VISIBLE
        else -> return null
    }
    val response = when (kind) {
        SpearThreatKind.ATTACK_COMMITTED -> SpearThreatResponse.EMERGENCY
        SpearThreatKind.USING_AIMED,
        SpearThreatKind.USING_PACKET_CAPABLE -> spearUseResponse()
        SpearThreatKind.HOLDING_AIMED,
        SpearThreatKind.HOLDING_NEWLY_VISIBLE -> SpearThreatResponse.EVADE
    }

    return SpearThreat(
        candidate = this,
        kind = kind,
        response = response,
        distanceSquared = distanceSquared,
        trustsAttackerLook = aimed,
    )
}

private fun SpearThreatCandidate.spearUseResponse(): SpearThreatResponse {
    val damageUseDurationTicks = spearDamageUseDurationTicks
    if (spearDelayTicks != null && spearDelayTicks < 0 || spearUseTicks < 0 ||
        damageUseDurationTicks != null && spearUseTicks >= damageUseDurationTicks) {
        return SpearThreatResponse.MONITOR
    }

    // Packet routes can reach the defender before remote aim or position state is useful.
    // Move as soon as a valid use starts instead of waiting for the first damaging tick.
    return SpearThreatResponse.EVADE
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

internal fun List<SpearThreat>.bestThreat(): SpearThreat? = minWithOrNull(SPEAR_THREAT_ORDER)

internal fun SpearThreat.isHigherRankedThan(other: SpearThreat): Boolean = when {
    response.priority != other.response.priority -> response.priority > other.response.priority
    kind.priority != other.kind.priority -> kind.priority > other.kind.priority
    else -> distanceSquared < other.distanceSquared
}

private val SPEAR_THREAT_ORDER = compareByDescending<SpearThreat> { it.response.priority }
    .thenByDescending { it.kind.priority }
    .thenBy { it.distanceSquared }
    .thenBy { it.candidate.entityId }

private const val SWEEP_TICKS = 2.0
private const val MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12
private const val SPEAR_PACKET_THREAT_RANGE = 512.0
private const val SPEAR_PACKET_THREAT_RANGE_SQUARED = SPEAR_PACKET_THREAT_RANGE * SPEAR_PACKET_THREAT_RANGE
