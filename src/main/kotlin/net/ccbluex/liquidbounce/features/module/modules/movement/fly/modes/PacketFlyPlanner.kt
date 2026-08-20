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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantBlockReason
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPacketAccounting
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPlanRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPlanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPlanner
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillPrimedInstantPriming
import net.ccbluex.liquidbounce.features.module.modules.combat.boundedSpearKillProfileStep
import net.ccbluex.liquidbounce.features.module.modules.combat.calculateSpearKillVanillaMovementBudget
import net.minecraft.world.phys.Vec3

/** Runtime-facing packet shape, deliberately isolated from SpearKill's combat configuration. */
internal enum class PacketFlyPrimingPacketShape(
    override val tag: String,
    val includesPosition: Boolean,
    val includesRotation: Boolean,
) : Tagged {
    Position("Position", includesPosition = true, includesRotation = false),
    PositionRotation("PositionRotation", includesPosition = true, includesRotation = true),
    Rotation("Rotation", includesPosition = false, includesRotation = true),
    StatusOnly("StatusOnly", includesPosition = false, includesRotation = false),
}

/** Complete same-tick accounting around the ordinary Vanilla-generated endpoint packet. */
internal data class PacketFlyPacketAccounting(
    val existingPreFinalPackets: Int,
    val forecastNoFallPackets: Int,
    val vanillaFinalPacketReserved: Boolean,
    val reservedPacketsAfterFinal: Int,
    val maxPackets: Int,
)

/** Collision-resolved physical endpoints and server state known before physical movement. */
internal data class PacketFlyPlanRequest(
    val start: Vec3,
    val requestedEnd: Vec3,
    val serverPhysicsVelocity: Vec3,
    val fallFlying: Boolean,
    val packetAccounting: PacketFlyPacketAccounting,
)

/** Only these packets are emitted manually; the final endpoint always remains Vanilla-owned. */
internal sealed interface PacketFlyAuxiliaryPacketPlan {
    data class Position(val endpoint: Vec3) : PacketFlyAuxiliaryPacketPlan

    data class Priming(
        val shape: PacketFlyPrimingPacketShape,
        val position: Vec3?,
    ) : PacketFlyAuxiliaryPacketPlan
}

internal data class PacketFlyPacketPlan(
    val requestedEnd: Vec3,
    val finalEndpoint: Vec3,
    val auxiliaryPackets: List<PacketFlyAuxiliaryPacketPlan>,
    val perMovementPacketBudget: Double,
    val packetAccounting: PacketFlyPacketAccounting,
    val totalPacketBudget: Int,
    val finalVanillaPacketReserved: Boolean,
    val clamped: Boolean,
)

internal enum class PacketFlyPlanBlockReason {
    INVALID_MOVEMENT,
    INVALID_PACKET_ACCOUNTING,
    SERVER_PACKET_WINDOW_EXCEEDED,
    PACKET_BUDGET_EXCEEDED,
}

internal sealed interface PacketFlyPlanResult {
    data class Ready(val plan: PacketFlyPacketPlan) : PacketFlyPlanResult
    data class Blocked(val reason: PacketFlyPlanBlockReason) : PacketFlyPlanResult
}

/** Pure adapter over the stable SpearKill movement budget and automatic Primed planner. */
@Suppress("TooManyFunctions")
internal object PacketFlyPlanner {

    @Suppress("ReturnCount")
    fun safe(request: PacketFlyPlanRequest): PacketFlyPlanResult {
        validate(request)?.let { return PacketFlyPlanResult.Blocked(it) }
        val movement = request.requestedEnd.subtract(request.start)
        val budget = request.movementBudget()
        if (movement.lengthSqr() == 0.0) return idlePlan(request, budget)

        val accounting = request.packetAccounting
        if (!accounting.vanillaFinalPacketReserved) {
            return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING)
        }
        val availableMovementPackets = accounting.availableMovementPackets()
        if (availableMovementPackets < 1L) {
            return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
        }

        val segmentation = segmentSafeMovement(request, budget, availableMovementPackets.toInt())
        val totalPacketBudget = accounting.nonFinalPacketCount() + segmentation.movementPacketCount
        check(totalPacketBudget <= accounting.maxPackets)
        return PacketFlyPlanResult.Ready(
            PacketFlyPacketPlan(
                requestedEnd = request.requestedEnd,
                finalEndpoint = segmentation.finalEndpoint,
                auxiliaryPackets = segmentation.intermediateEndpoints.map(
                    PacketFlyAuxiliaryPacketPlan::Position,
                ),
                perMovementPacketBudget = budget,
                packetAccounting = accounting,
                totalPacketBudget = totalPacketBudget.toInt(),
                finalVanillaPacketReserved = true,
                clamped = segmentation.clamped,
            ),
        )
    }

    fun primed(
        request: PacketFlyPlanRequest,
        shape: PacketFlyPrimingPacketShape,
    ): PacketFlyPlanResult {
        validate(request)?.let { return PacketFlyPlanResult.Blocked(it) }
        val movement = request.requestedEnd.subtract(request.start)
        val budget = request.movementBudget()
        if (movement.lengthSqr() == 0.0) return idlePlan(request, budget)
        if (!request.packetAccounting.vanillaFinalPacketReserved) {
            return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING)
        }

        return when (val requestedAdmission = request.planPrimed(movement.length(), shape)) {
            is SpearKillPrimedInstantPlanResult.Ready -> {
                if (requestedAdmission.plan.sourcePredictedAccepted) {
                    readyPrimedPlan(request, request.requestedEnd, shape, requestedAdmission.plan, clamped = false)
                } else {
                    clampPrimedPlan(request, movement, shape, budget)
                }
            }
            is SpearKillPrimedInstantPlanResult.Blocked -> when (requestedAdmission.reason) {
                SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED,
                SpearKillPrimedInstantBlockReason.INVALID_MOVEMENT,
                SpearKillPrimedInstantBlockReason.INVALID_PACKET_ACCOUNTING,
                -> PacketFlyPlanResult.Blocked(requestedAdmission.reason.toPacketFlyReason())
                SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED ->
                    clampPrimedPlan(request, movement, shape, budget)
            }
        }
    }

    private fun clampPrimedPlan(
        request: PacketFlyPlanRequest,
        requestedMovement: Vec3,
        shape: PacketFlyPrimingPacketShape,
        budget: Double,
    ): PacketFlyPlanResult {
        val baseline = request.planPrimed(0.0, shape)
        if (baseline !is SpearKillPrimedInstantPlanResult.Ready || !baseline.plan.sourcePredictedAccepted) {
            return PacketFlyPlanResult.Blocked(baseline.blockReason())
        }

        var lowerDistance = 0.0
        var upperDistance = requestedMovement.length()
        repeat(PRIMED_CLAMP_SEARCH_STEPS) {
            val candidateDistance = lowerDistance + (upperDistance - lowerDistance) * 0.5
            val candidate = request.planPrimed(candidateDistance, shape)
            if (candidate is SpearKillPrimedInstantPlanResult.Ready && candidate.plan.sourcePredictedAccepted) {
                lowerDistance = candidateDistance
            } else {
                upperDistance = candidateDistance
            }
        }

        if (lowerDistance <= 0.0) {
            return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
        }
        val acceptedMovement = boundedSpearKillProfileStep(requestedMovement, Math.nextDown(lowerDistance))
        val finalAdmission = request.planPrimed(acceptedMovement.length(), shape)
        if (finalAdmission !is SpearKillPrimedInstantPlanResult.Ready ||
            !finalAdmission.plan.sourcePredictedAccepted
        ) {
            return PacketFlyPlanResult.Blocked(finalAdmission.blockReason())
        }
        return readyPrimedPlan(
            request = request,
            finalEndpoint = request.start.add(acceptedMovement),
            shape = shape,
            primedPlan = finalAdmission.plan,
            clamped = true,
            budget = budget,
        )
    }

    private fun readyPrimedPlan(
        request: PacketFlyPlanRequest,
        finalEndpoint: Vec3,
        shape: PacketFlyPrimingPacketShape,
        primedPlan: SpearKillPrimedInstantPlan,
        clamped: Boolean,
        budget: Double = request.movementBudget(),
    ): PacketFlyPlanResult.Ready {
        val stationaryPosition = request.start.takeIf { shape.includesPosition }
        val auxiliaryPackets = List(primedPlan.dedicatedPrimingPackets) {
            PacketFlyAuxiliaryPacketPlan.Priming(shape, stationaryPosition)
        }
        return PacketFlyPlanResult.Ready(
            PacketFlyPacketPlan(
                requestedEnd = request.requestedEnd,
                finalEndpoint = finalEndpoint,
                auxiliaryPackets = auxiliaryPackets,
                perMovementPacketBudget = budget,
                packetAccounting = request.packetAccounting,
                totalPacketBudget = primedPlan.totalOwnedPacketBudget,
                finalVanillaPacketReserved = true,
                clamped = clamped,
            ),
        )
    }

    private fun idlePlan(
        request: PacketFlyPlanRequest,
        budget: Double,
    ): PacketFlyPlanResult {
        val totalPacketBudget = request.packetAccounting.nonFinalPacketCount()
        if (totalPacketBudget > request.packetAccounting.maxPackets) {
            return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
        }
        return PacketFlyPlanResult.Ready(
            PacketFlyPacketPlan(
                requestedEnd = request.requestedEnd,
                finalEndpoint = request.start,
                auxiliaryPackets = emptyList(),
                perMovementPacketBudget = budget,
                packetAccounting = request.packetAccounting,
                totalPacketBudget = totalPacketBudget.toInt(),
                finalVanillaPacketReserved = false,
                clamped = false,
            ),
        )
    }

    private fun segmentSafeMovement(
        request: PacketFlyPlanRequest,
        budget: Double,
        availableMovementPackets: Int,
    ): SafeSegmentation {
        val intermediateEndpoints = ArrayList<Vec3>(availableMovementPackets - 1)
        var cursor = request.start
        for (packetOrdinal in 1..availableMovementPackets) {
            val remaining = request.requestedEnd.subtract(cursor)
            if (remaining.length() <= budget) {
                return SafeSegmentation(intermediateEndpoints, request.requestedEnd, clamped = false)
            }

            cursor = cursor.add(boundedSpearKillProfileStep(remaining, budget))
            if (packetOrdinal == availableMovementPackets) {
                return SafeSegmentation(intermediateEndpoints, cursor, clamped = true)
            }
            intermediateEndpoints += cursor
        }
        error("Safe Packet Fly segmentation exhausted without a final endpoint")
    }

    private fun validate(request: PacketFlyPlanRequest): PacketFlyPlanBlockReason? {
        val movement = request.requestedEnd.subtract(request.start)
        if (!request.start.hasFiniteCoordinates() || !request.requestedEnd.hasFiniteCoordinates() ||
            !movement.hasFiniteCoordinates() || !movement.lengthSqr().isFinite()
        ) {
            return PacketFlyPlanBlockReason.INVALID_MOVEMENT
        }

        val accounting = request.packetAccounting
        if (accounting.existingPreFinalPackets < 0 || accounting.forecastNoFallPackets < 0 ||
            accounting.reservedPacketsAfterFinal < 0 ||
            accounting.maxPackets !in PACKET_FLY_MIN_PACKETS..PACKET_FLY_MAX_PACKETS
        ) {
            return PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING
        }
        return null
    }

    private fun PacketFlyPlanRequest.planPrimed(
        distance: Double,
        shape: PacketFlyPrimingPacketShape,
    ): SpearKillPrimedInstantPlanResult = SpearKillPrimedInstantPlanner.plan(
        SpearKillPrimedInstantPlanRequest(
            requestedDistance = distance,
            expectedVelocitySquared = serverPhysicsVelocity.sanitizedLengthSquared(),
            movementProfile = if (fallFlying) {
                SpearKillPrimedInstantMovementProfile.ELYTRA
            } else {
                SpearKillPrimedInstantMovementProfile.NORMAL
            },
            priming = SpearKillPrimedInstantPriming.Auto,
            packetAccounting = SpearKillPrimedInstantPacketAccounting(
                ownedPreFinalPackets = packetAccounting.existingPreFinalPackets,
                noFallPreFinalPackets = packetAccounting.forecastNoFallPackets,
                reservedPacketsAfterFinal = packetAccounting.reservedPacketsAfterFinal,
                maxPackets = packetAccounting.maxPackets,
            ),
            primingPacketType = shape.toSpearKillShape(),
        ),
    )

    private fun PacketFlyPacketAccounting.availableMovementPackets(): Long =
        maxPackets.toLong() - nonFinalPacketCount()

    private fun PacketFlyPacketAccounting.nonFinalPacketCount(): Long =
        existingPreFinalPackets.toLong() + forecastNoFallPackets.toLong() + reservedPacketsAfterFinal.toLong()

    private fun PacketFlyPlanRequest.movementBudget(): Double =
        calculateSpearKillVanillaMovementBudget(serverPhysicsVelocity, fallFlying)

    private fun SpearKillPrimedInstantPlanResult.blockReason(): PacketFlyPlanBlockReason =
        (this as? SpearKillPrimedInstantPlanResult.Blocked)?.reason?.toPacketFlyReason()
            ?: PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED

    private fun SpearKillPrimedInstantBlockReason.toPacketFlyReason(): PacketFlyPlanBlockReason = when (this) {
        SpearKillPrimedInstantBlockReason.INVALID_MOVEMENT -> PacketFlyPlanBlockReason.INVALID_MOVEMENT
        SpearKillPrimedInstantBlockReason.INVALID_PACKET_ACCOUNTING ->
            PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING
        SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED ->
            PacketFlyPlanBlockReason.SERVER_PACKET_WINDOW_EXCEEDED
        SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED ->
            PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED
    }

    private fun PacketFlyPrimingPacketShape.toSpearKillShape(): SpearKillPrimedInstantPacketType = when (this) {
        PacketFlyPrimingPacketShape.Position -> SpearKillPrimedInstantPacketType.Position
        PacketFlyPrimingPacketShape.PositionRotation -> SpearKillPrimedInstantPacketType.PositionRotation
        PacketFlyPrimingPacketShape.Rotation -> SpearKillPrimedInstantPacketType.Rotation
        PacketFlyPrimingPacketShape.StatusOnly -> SpearKillPrimedInstantPacketType.StatusOnly
    }

    private fun Vec3.sanitizedLengthSquared(): Double = takeIf { it.hasFiniteCoordinates() }
        ?.lengthSqr()
        ?.takeIf(Double::isFinite)
        ?: 0.0

    private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    private data class SafeSegmentation(
        val intermediateEndpoints: List<Vec3>,
        val finalEndpoint: Vec3,
        val clamped: Boolean,
    ) {
        val movementPacketCount: Int
            get() = intermediateEndpoints.size + 1
    }

    private const val PRIMED_CLAMP_SEARCH_STEPS = 80
    private const val PACKET_FLY_MIN_PACKETS = 2
    private const val PACKET_FLY_MAX_PACKETS = 512
}
