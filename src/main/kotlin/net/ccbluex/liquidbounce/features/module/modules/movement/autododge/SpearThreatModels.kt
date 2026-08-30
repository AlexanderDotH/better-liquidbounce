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
    val spearUseTicks: Int = 0,
    val spearDelayTicks: Int? = null,
    val spearDamageUseDurationTicks: Int? = null,
    val isAlive: Boolean = true,
    val isRemoved: Boolean = false,
    val isBot: Boolean = false,
    val isSelf: Boolean = false,
    val isFriend: Boolean = false,
    val isTeammate: Boolean = false,
    val hasSignificantPositionJump: Boolean = false,
    val visibilityAgeTicks: Int = Int.MAX_VALUE,
)

enum class SpearThreatKind(val priority: Int) {
    HOLDING_NEWLY_VISIBLE(0),
    HOLDING_AIMED(1),
    USING_PACKET_CAPABLE(2),
    USING_AIMED(3),
    ATTACK_COMMITTED(4),
}

/** How aggressively movement should react while shield preparation can still use every telegraph. */
enum class SpearThreatResponse(val priority: Int) {
    MONITOR(0),
    FEINT(1),
    EVADE(2),
    EMERGENCY(3),
}

data class SpearThreat(
    val candidate: SpearThreatCandidate,
    val kind: SpearThreatKind,
    val response: SpearThreatResponse,
    val distanceSquared: Double,
    val trustsAttackerLook: Boolean = kind == SpearThreatKind.HOLDING_AIMED ||
        kind == SpearThreatKind.USING_AIMED,
)

val SpearThreat?.requiresJuke: Boolean
    get() = this != null && response.priority >= SpearThreatResponse.FEINT.priority

val SpearThreat?.requiresTeleport: Boolean
    get() = this != null && response.priority >= SpearThreatResponse.EVADE.priority
