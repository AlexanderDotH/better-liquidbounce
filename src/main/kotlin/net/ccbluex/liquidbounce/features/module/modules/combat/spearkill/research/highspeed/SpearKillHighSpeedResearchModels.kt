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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed


internal const val SPEAR_KILL_HIGH_SPEED_RESEARCH_SCHEMA_VERSION = 2

internal enum class SpearKillHighSpeedResearchOutcome {
    CORRECTED,
    DELIVERY_FAILED,
    NO_CORRECTION_OBSERVED,
}

internal enum class SpearKillHighSpeedResearchPacketType {
    POSITION,
    POSITION_ROTATION,
    ROTATION,
    STATUS_ONLY,
}

internal enum class SpearKillHighSpeedResearchFinalPacketType {
    POSITION,
    POSITION_ROTATION,
}

internal sealed interface SpearKillHighSpeedResearchProbeRequest {

    val primingPackets: Int
    val primingPacketType: SpearKillHighSpeedResearchPacketType
    val finalPacketType: SpearKillHighSpeedResearchFinalPacketType

    data class Move(
        val distance: Double,
        override val primingPackets: Int,
        override val primingPacketType: SpearKillHighSpeedResearchPacketType,
        override val finalPacketType: SpearKillHighSpeedResearchFinalPacketType,
    ) : SpearKillHighSpeedResearchProbeRequest

    data class Attack(
        override val primingPackets: Int,
        override val primingPacketType: SpearKillHighSpeedResearchPacketType,
        override val finalPacketType: SpearKillHighSpeedResearchFinalPacketType,
    ) : SpearKillHighSpeedResearchProbeRequest
}

internal enum class SpearKillHighSpeedResearchProbeStartResult {
    STARTED,
    ACTIVE_SESSION,
    INVALID_CONTEXT,
    NO_TARGET,
    ROUTE_REJECTED,
}

internal data class SpearKillHighSpeedResearchVector(
    val x: Double,
    val y: Double,
    val z: Double,
)

internal data class SpearKillHighSpeedResearchTiming(
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    val completedAtMonotonicNanos: Long,
    val clientTick: Int,
    val completionTick: Int,
)

internal data class SpearKillHighSpeedResearchPacketPlan(
    val primingPacketsRequested: Int,
    val primingPacketsSent: Int,
    val primingPacketType: SpearKillHighSpeedResearchPacketType,
    val finalPacketType: SpearKillHighSpeedResearchFinalPacketType,
    val noFallPacketsSent: Int,
    val packetBudget: Int,
)

@Suppress("LongParameterList")
internal data class SpearKillHighSpeedResearchMovement(
    val origin: SpearKillHighSpeedResearchVector,
    val destination: SpearKillHighSpeedResearchVector,
    val localPositionBefore: SpearKillHighSpeedResearchVector,
    val observedLocalPosition: SpearKillHighSpeedResearchVector?,
    val requestedDistance: Double,
    val observedLocalDisplacement: Double?,
    val targetSpeed: Double,
    val currentSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val routeStepLimit: Double,
    val expectedVelocity: Double,
    val elytraFlying: Boolean,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
    val corridorBlocked: Boolean,
    val destinationSpaceFree: Boolean,
    val terminalRaytraceClear: Boolean,
)

internal data class SpearKillHighSpeedResearchSourcePrediction(
    val squaredDistanceThresholdPerPacket: Double,
    val expectedVelocitySquared: Double,
    val effectivePacketCount: Int,
    val packetCountReset: Boolean,
    val predictedMaximumDistance: Double,
    val predictedAccepted: Boolean,
)

internal data class SpearKillHighSpeedResearchDelivery(
    val primingPacketsDelivered: Int,
    val finalPacketDelivered: Boolean,
    val blinkQueued: Boolean,
    val tickEndPacketsSuppressed: Int,
    val tickEndBoundariesObserved: Int,
)

internal data class SpearKillHighSpeedResearchCorrection(
    val receivedAtEpochMs: Long,
    val distance: Double,
    val latencyMs: Long,
    val latencyTicks: Int,
)

@Suppress("LongParameterList")
internal data class SpearKillHighSpeedResearchTargetEvidence(
    val entityId: Int,
    val name: String,
    val healthBefore: Double,
    val healthAfter: Double,
    val observedHealthDelta: Double,
    val damageEventObserved: Boolean,
    val damageEventAmount: Double?,
    val deathObserved: Boolean,
    val estimatedKineticDamage: Double,
)

internal data class SpearKillHighSpeedResearchEntry(
    val schemaVersion: Int = SPEAR_KILL_HIGH_SPEED_RESEARCH_SCHEMA_VERSION,
    val burstId: String,
    val timing: SpearKillHighSpeedResearchTiming,
    val packetPlan: SpearKillHighSpeedResearchPacketPlan,
    val movement: SpearKillHighSpeedResearchMovement,
    val sourcePrediction: SpearKillHighSpeedResearchSourcePrediction,
    val delivery: SpearKillHighSpeedResearchDelivery,
    val correction: SpearKillHighSpeedResearchCorrection?,
    val target: SpearKillHighSpeedResearchTargetEvidence?,
    val outcome: SpearKillHighSpeedResearchOutcome,
)
