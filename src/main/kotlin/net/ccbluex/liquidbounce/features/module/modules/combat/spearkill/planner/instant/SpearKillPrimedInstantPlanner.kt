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

@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.common.Tagged
import kotlin.math.ceil
import kotlin.math.max

/** PlayerMove packet shape used to advance the same-tick server packet counter. */
internal enum class SpearKillPrimedInstantPacketType(override val tag: String) : Tagged {
    Position("Position"),
    PositionRotation("PositionRotation"),
    Rotation("Rotation"),
    StatusOnly("StatusOnly"),
}

/** Squared-distance multiplier used by the Minecraft 26.2 move-too-quickly check. */
internal enum class SpearKillPrimedInstantMovementProfile(
    val squaredDistanceThreshold: Double,
) {
    NORMAL(100.0),
    ELYTRA(300.0),
}

/** Auto is used by Instant routing; Explicit exists for one-shot research probes. */
internal sealed interface SpearKillPrimedInstantPriming {
    data object Auto : SpearKillPrimedInstantPriming
    data class Explicit(val packets: Int) : SpearKillPrimedInstantPriming
}

/** Every SpearKill-owned packet that must fit before the complete burst is admitted. */
internal data class SpearKillPrimedInstantPacketAccounting(
    val ownedPreFinalPackets: Int,
    val noFallPreFinalPackets: Int,
    val reservedPacketsAfterFinal: Int,
    val maxPackets: Int,
)

internal data class SpearKillPrimedInstantPlanRequest(
    val requestedDistance: Double,
    val expectedVelocitySquared: Double,
    val movementProfile: SpearKillPrimedInstantMovementProfile,
    val priming: SpearKillPrimedInstantPriming,
    val packetAccounting: SpearKillPrimedInstantPacketAccounting,
    val primingPacketType: SpearKillPrimedInstantPacketType,
)

/** Complete all-or-nothing burst accounting; no prefix is executable without this value. */
internal data class SpearKillPrimedInstantPlan(
    val requestedDistance: Double,
    val requiredServerPackets: Int,
    val targetPrimingPackets: Int,
    val dedicatedPrimingPackets: Int,
    val totalPreFinalPackets: Int,
    val finalPacketOrdinal: Int,
    val serverCountedPackets: Int,
    val totalOwnedPacketBudget: Int,
    val sourcePredictedAccepted: Boolean,
    val movementProfile: SpearKillPrimedInstantMovementProfile,
    val primingPacketType: SpearKillPrimedInstantPacketType,
)

internal enum class SpearKillPrimedInstantBlockReason {
    INVALID_MOVEMENT,
    INVALID_PACKET_ACCOUNTING,
    SERVER_PACKET_WINDOW_EXCEEDED,
    PACKET_BUDGET_EXCEEDED,
}

internal sealed interface SpearKillPrimedInstantPlanResult {
    data class Ready(val plan: SpearKillPrimedInstantPlan) : SpearKillPrimedInstantPlanResult
    data class Blocked(val reason: SpearKillPrimedInstantBlockReason) : SpearKillPrimedInstantPlanResult
}

private data class SpearKillPrimedInstantPacketCounts(
    val dedicatedPrimingPackets: Long,
    val totalPreFinalPackets: Long,
    val totalPacketBudget: Long,
    val finalPacketOrdinal: Int,
    val serverCountedPackets: Int,
)

/**
 * Mirrors the current 26.2 source check without claiming live-server acceptance.
 *
 * Source path mirrored by this experiment:
 * `handleMovePlayer -> handlePlayerKnownMovement -> ServerPlayer.lastKnownClientMovement ->
 * KineticWeapon.getMotion * 20`. The fifth packet is the last multiplied check; packet six and
 * later reset the effective count to one for that handler invocation.
 */
internal object SpearKillPrimedInstantPlanner {
    fun plan(request: SpearKillPrimedInstantPlanRequest): SpearKillPrimedInstantPlanResult {
        invalidReason(request)?.let { return SpearKillPrimedInstantPlanResult.Blocked(it) }

        val distanceSquared = request.requestedDistance * request.requestedDistance
        val movementExcess = max(0.0, distanceSquared - request.expectedVelocitySquared)
        val requiredPackets = ceil(movementExcess / request.movementProfile.squaredDistanceThreshold).toInt()
        val targetPrimingPackets = request.priming.targetPackets(requiredPackets)
        val accounting = request.packetAccounting
        val existingPreFinalPackets = accounting.existingPreFinalPackets()

        if (request.priming === SpearKillPrimedInstantPriming.Auto &&
            existingPreFinalPackets > MAX_AUTO_PRIMING_PACKETS
        ) {
            return SpearKillPrimedInstantPlanResult.Blocked(
                SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED,
            )
        }

        val counts = calculateSpearKillPrimedPacketCounts(
            targetPrimingPackets,
            existingPreFinalPackets,
            accounting,
        ) ?: run {
            return SpearKillPrimedInstantPlanResult.Blocked(SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED)
        }
        val sourcePredictedAccepted = movementExcess <=
            request.movementProfile.squaredDistanceThreshold * counts.serverCountedPackets

        return SpearKillPrimedInstantPlanResult.Ready(
            createSpearKillPrimedInstantPlan(
                request,
                requiredPackets,
                targetPrimingPackets,
                counts,
                sourcePredictedAccepted,
            ),
        )
    }

    private fun calculateSpearKillPrimedPacketCounts(
        targetPrimingPackets: Int,
        existingPreFinalPackets: Long,
        accounting: SpearKillPrimedInstantPacketAccounting,
    ): SpearKillPrimedInstantPacketCounts? {
        val dedicatedPrimingPackets = max(0L, targetPrimingPackets.toLong() - existingPreFinalPackets)
        val totalPreFinalPackets = existingPreFinalPackets + dedicatedPrimingPackets
        val totalPacketBudget = totalPreFinalPackets + FINAL_MOVEMENT_PACKET + accounting.reservedPacketsAfterFinal
        if (totalPacketBudget > accounting.maxPackets) return null
        val finalPacketOrdinal = (totalPreFinalPackets + FINAL_MOVEMENT_PACKET).toInt()
        val serverCountedPackets = if (finalPacketOrdinal > MAX_SERVER_COUNTED_PACKETS) 1 else finalPacketOrdinal
        return SpearKillPrimedInstantPacketCounts(
            dedicatedPrimingPackets,
            totalPreFinalPackets,
            totalPacketBudget,
            finalPacketOrdinal,
            serverCountedPackets,
        )
    }

    private fun createSpearKillPrimedInstantPlan(
        request: SpearKillPrimedInstantPlanRequest,
        requiredPackets: Int,
        targetPrimingPackets: Int,
        counts: SpearKillPrimedInstantPacketCounts,
        sourcePredictedAccepted: Boolean,
    ) = SpearKillPrimedInstantPlan(
        requestedDistance = request.requestedDistance,
        requiredServerPackets = requiredPackets,
        targetPrimingPackets = targetPrimingPackets,
        dedicatedPrimingPackets = counts.dedicatedPrimingPackets.toInt(),
        totalPreFinalPackets = counts.totalPreFinalPackets.toInt(),
        finalPacketOrdinal = counts.finalPacketOrdinal,
        serverCountedPackets = counts.serverCountedPackets,
        totalOwnedPacketBudget = counts.totalPacketBudget.toInt(),
        sourcePredictedAccepted = sourcePredictedAccepted,
        movementProfile = request.movementProfile,
        primingPacketType = request.primingPacketType,
    )

    private fun invalidReason(request: SpearKillPrimedInstantPlanRequest): SpearKillPrimedInstantBlockReason? {
        val distanceSquared = request.requestedDistance * request.requestedDistance
        val movementExcess = distanceSquared - request.expectedVelocitySquared
        val invalidInput = !request.requestedDistance.isFinite() || request.requestedDistance < 0.0 ||
            !request.expectedVelocitySquared.isFinite() || request.expectedVelocitySquared < 0.0
        val requiredPackets = ceil(max(0.0, movementExcess) / request.movementProfile.squaredDistanceThreshold)
        val invalidDerivedMovement = !distanceSquared.isFinite() || !movementExcess.isFinite() ||
            requiredPackets > Int.MAX_VALUE
        if (invalidInput || invalidDerivedMovement) {
            return SpearKillPrimedInstantBlockReason.INVALID_MOVEMENT
        }

        val accounting = request.packetAccounting
        val invalidExplicitPriming = request.priming is SpearKillPrimedInstantPriming.Explicit &&
            request.priming.packets < 0
        if (accounting.isInvalid() || invalidExplicitPriming) {
            return SpearKillPrimedInstantBlockReason.INVALID_PACKET_ACCOUNTING
        }

        return null
    }

    private fun SpearKillPrimedInstantPriming.targetPackets(requiredPackets: Int): Int = when (this) {
        SpearKillPrimedInstantPriming.Auto -> (requiredPackets - 1).coerceIn(0, MAX_AUTO_PRIMING_PACKETS)
        is SpearKillPrimedInstantPriming.Explicit -> packets
    }

    private fun SpearKillPrimedInstantPacketAccounting.existingPreFinalPackets(): Long =
        ownedPreFinalPackets.toLong() + noFallPreFinalPackets.toLong()

    private fun SpearKillPrimedInstantPacketAccounting.isInvalid(): Boolean =
        listOf(ownedPreFinalPackets, noFallPreFinalPackets, reservedPacketsAfterFinal).any { it < 0 } ||
            maxPackets < 1 || existingPreFinalPackets() > Int.MAX_VALUE

    private const val FINAL_MOVEMENT_PACKET = 1L
    private const val MAX_AUTO_PRIMING_PACKETS = 4
    private const val MAX_SERVER_COUNTED_PACKETS = 5
}
