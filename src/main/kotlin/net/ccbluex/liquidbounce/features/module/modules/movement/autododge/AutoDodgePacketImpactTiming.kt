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

internal data class AutoDodgePacketThreatKey(
    val type: AutoDodgePacketThreatType,
    val entityId: Int,
)

internal data class AutoDodgePacketImpactSchedule(
    val predictedImpactTick: Long,
    val dodgeAtTick: Long,
    val returnNotBeforeTick: Long,
) {
    fun isDodgeDue(tick: Long): Boolean = tick >= dodgeAtTick
}

/**
 * Converts a relative collision forecast into absolute client ticks.
 *
 * Packet movement is sent up to one complete server sample before impact, then retained through
 * [postImpactHoldTicks] additional ticks. Clamping the start to the observation tick also keeps an
 * already-imminent threat actionable instead of producing a timestamp in the past.
 */
internal fun predictAutoDodgePacketImpact(
    observedAtTick: Long,
    ticksUntilImpact: Int,
    postImpactHoldTicks: Int,
): AutoDodgePacketImpactSchedule {
    require(ticksUntilImpact >= 1) { "Auto-Dodge impact must be at least one simulated tick ahead" }
    require(postImpactHoldTicks in AUTO_DODGE_PACKET_MIN_HOLD_TICKS..AUTO_DODGE_PACKET_MAX_HOLD_TICKS) {
        "Auto-Dodge post-impact hold must be between $AUTO_DODGE_PACKET_MIN_HOLD_TICKS and " +
            "$AUTO_DODGE_PACKET_MAX_HOLD_TICKS ticks"
    }

    val predictedImpactTick = observedAtTick + ticksUntilImpact
    return AutoDodgePacketImpactSchedule(
        predictedImpactTick = predictedImpactTick,
        dodgeAtTick = maxOf(observedAtTick, predictedImpactTick - AUTO_DODGE_PACKET_IMPACT_LEAD_TICKS),
        returnNotBeforeTick = predictedImpactTick + postImpactHoldTicks,
    )
}

internal fun AutoDodgePacketProjectileThreat.predictImpact(
    observedAtTick: Long,
    postImpactHoldTicks: Int,
): AutoDodgePacketImpactSchedule? = takeIf { tickDelta >= 0 }?.let {
    predictAutoDodgePacketImpact(
        observedAtTick = observedAtTick,
        // tickDelta zero is the collision produced by the next simulated arrow tick.
        ticksUntilImpact = tickDelta + 1,
        postImpactHoldTicks = postImpactHoldTicks,
    )
}

internal const val AUTO_DODGE_PACKET_IMPACT_LEAD_TICKS = 2
internal const val AUTO_DODGE_PACKET_MELEE_IMPACT_TICKS = 1
