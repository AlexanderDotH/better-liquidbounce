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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport

import net.minecraft.world.phys.Vec3

internal data class SpearTeleportSettings(
    val behindDistance: Double,
    val maxDistance: Double,
    val searchRadius: Int,
    val cooldownTicks: Int,
    val stepDistance: Double,
    val maxPackets: Int,
)

internal enum class SpearTeleportState(val debugName: String) {
    IDLE("Idle"),
    DISABLED("Disabled"),
    PROJECTILE_PRIORITY("ProjectilePriority"),
    NO_THREAT("NoThreat"),
    COOLDOWN("Cooldown"),
    PLANNING("Planning"),
    NO_SAFE_DESTINATION("NoSafeDestination"),
    READY("Ready"),
    SAFETY_RECHECK_REJECTED("SafetyRecheckRejected"),
    PACKET_BUDGET_REJECTED("PacketBudgetRejected"),
    TELEPORTED("Teleported"),
}

internal data class CombatTeleportThreat(
    val position: Vec3,
    val lookDirection: Vec3,
    val trustsAttackerLook: Boolean,
)
