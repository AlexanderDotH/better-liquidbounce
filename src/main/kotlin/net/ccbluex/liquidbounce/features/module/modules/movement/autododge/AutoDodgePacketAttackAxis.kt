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

internal data class AutoDodgePacketAttackAxis(
    val threatType: AutoDodgePacketThreatType,
    val origin: Vec3,
    val direction: Vec3,
    val fallbackDirection: Vec3? = null,
)

internal fun AutoDodgePacketProjectileThreat.toPacketAttackAxis(): AutoDodgePacketAttackAxis? = takeIf {
    tickDelta >= 0
}?.let {
    AutoDodgePacketAttackAxis(AutoDodgePacketThreatType.PROJECTILE, previousPosition, velocity)
}

internal fun AutoDodgePacketProjectileThreat.toPacketThreatPrediction(
    observedAtTick: Long,
    postImpactHoldTicks: Int,
): AutoDodgePacketThreatPrediction? {
    val axis = toPacketAttackAxis() ?: return null
    val impactSchedule = predictImpact(observedAtTick, postImpactHoldTicks) ?: return null
    return AutoDodgePacketThreatPrediction(
        AutoDodgePacketThreatKey(AutoDodgePacketThreatType.PROJECTILE, entityId),
        axis,
        impactSchedule,
    )
}

internal fun MaceThreat.toPacketAttackAxis(playerOrigin: Vec3) = AutoDodgePacketAttackAxis(
    AutoDodgePacketThreatType.MACE,
    candidate.position,
    playerOrigin.subtract(candidate.position),
)

internal fun MaceThreat.toPacketThreatPrediction(
    playerOrigin: Vec3,
    observedAtTick: Long,
    postImpactHoldTicks: Int,
) = AutoDodgePacketThreatPrediction(
    AutoDodgePacketThreatKey(AutoDodgePacketThreatType.MACE, candidate.entityId),
    toPacketAttackAxis(playerOrigin),
    predictAutoDodgePacketImpact(
        observedAtTick,
        AUTO_DODGE_PACKET_MELEE_IMPACT_TICKS,
        postImpactHoldTicks,
    ),
)

internal fun SpearThreat.toPacketAttackAxis(playerOrigin: Vec3): AutoDodgePacketAttackAxis {
    val attackerToPlayer = playerOrigin.subtract(candidate.position)
    return if (trustsAttackerLook) {
        AutoDodgePacketAttackAxis(
            AutoDodgePacketThreatType.SPEAR,
            candidate.eyePosition,
            candidate.lookDirection,
            fallbackDirection = attackerToPlayer,
        )
    } else {
        AutoDodgePacketAttackAxis(AutoDodgePacketThreatType.SPEAR, candidate.position, attackerToPlayer)
    }
}

internal fun SpearThreat.toPacketThreatPrediction(
    playerOrigin: Vec3,
    observedAtTick: Long,
    postImpactHoldTicks: Int,
) = AutoDodgePacketThreatPrediction(
    AutoDodgePacketThreatKey(AutoDodgePacketThreatType.SPEAR, candidate.entityId),
    toPacketAttackAxis(playerOrigin),
    predictAutoDodgePacketImpact(
        observedAtTick,
        AUTO_DODGE_PACKET_MELEE_IMPACT_TICKS,
        postImpactHoldTicks,
    ),
)
