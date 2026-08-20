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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerStepSuccessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.outgoingMovementPacket
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

internal enum class PacketFlySpeedExploit(override val tag: String) : Tagged {
    SAFE("Safe"),
    PRIMED("Primed"),
}

internal object PacketFlyRuntimePolicy {

    fun shouldPlan(collisionResolvedMovement: Vec3, spearKillOwnsPacketRoute: Boolean) =
        !spearKillOwnsPacketRoute && collisionResolvedMovement != Vec3.ZERO

    fun resolvePhysicalMovement(collisionResolvedMovement: Vec3, spearKillOwnsPacketRoute: Boolean) =
        if (spearKillOwnsPacketRoute) Vec3.ZERO else collisionResolvedMovement
}

/**
 * Finds the least packet reservation whose resulting endpoint forecasts no more auxiliaries than it reserved.
 * A null forecast means that reservation itself cannot produce a complete plan.
 */
internal fun findMinimumFeasiblePacketReservation(
    maxReservation: Int,
    forecastForReservation: (Int) -> Int?,
): Int? {
    require(maxReservation >= 0) { "Maximum reservation must not be negative" }
    val forecasts = mutableMapOf<Int, Int?>()
    fun forecast(reservation: Int): Int? = if (forecasts.containsKey(reservation)) {
        forecasts[reservation]
    } else {
        forecastForReservation(reservation).also { forecasts[reservation] = it }
    }

    if (forecast(0) == null) {
        return null
    }

    var greatestPlannable = 0
    var firstBlocked = maxReservation + 1
    while (greatestPlannable + 1 < firstBlocked) {
        val candidate = greatestPlannable + (firstBlocked - greatestPlannable) / 2
        if (forecast(candidate) == null) {
            firstBlocked = candidate
        } else {
            greatestPlannable = candidate
        }
    }
    if (requireNotNull(forecast(greatestPlannable)) > greatestPlannable) {
        return null
    }

    var firstInfeasible = -1
    var leastFeasible = greatestPlannable
    while (firstInfeasible + 1 < leastFeasible) {
        val candidate = firstInfeasible + (leastFeasible - firstInfeasible) / 2
        val required = forecast(candidate)
        if (required != null && required <= candidate) {
            leastFeasible = candidate
        } else {
            firstInfeasible = candidate
        }
    }
    return leastFeasible
}

internal enum class PacketFlyDeliveryResult {
    UNRELATED,
    AUXILIARY_DELIVERED,
    AUXILIARY_REJECTED,
    FINAL_DELIVERED,
    FINAL_REJECTED,
}

internal fun matchesPacketFlyEndpoint(packet: ServerboundMovePlayerPacket, endpoint: Vec3) =
    packet.hasPos && packet.getX(endpoint.x) == endpoint.x &&
        packet.getY(endpoint.y) == endpoint.y && packet.getZ(endpoint.z) == endpoint.z

/** Tracks only one fully calculated movement plan and deliberately uses packet identity. */
internal class PacketFlyDeliveryTracker<T : Any> {

    private val auxiliaryDelivery = Collections.synchronizedMap(IdentityHashMap<T, Boolean>())
    private var orderedAuxiliaries = emptyList<T>()
    private var finalPacket: T? = null

    var deliveredAuxiliaryCount = 0
        private set

    val active: Boolean
        get() = orderedAuxiliaries.isNotEmpty() || finalPacket != null

    val auxiliaryPackets: List<T>
        get() = orderedAuxiliaries

    val allAuxiliariesDelivered: Boolean
        get() = synchronized(auxiliaryDelivery) { auxiliaryDelivery.values.all { it } }

    fun stage(auxiliaries: List<T>) {
        clear()
        orderedAuxiliaries = auxiliaries.toList()
        auxiliaries.forEach { auxiliaryDelivery[it] = false }
    }

    fun ownsAuxiliary(packet: T) = auxiliaryDelivery.containsKey(packet)

    fun owns(packet: T) = ownsAuxiliary(packet) || packet === finalPacket

    fun wasDelivered(packet: T) = auxiliaryDelivery[packet] == true

    fun expectFinalPacket(packet: T) {
        finalPacket = packet
    }

    fun isExpectedFinalPacket(packet: T) = packet === finalPacket

    fun confirm(packet: T, delivered: Boolean): PacketFlyDeliveryResult {
        if (auxiliaryDelivery.containsKey(packet)) {
            return confirmAuxiliary(packet, delivered)
        }
        if (packet !== finalPacket) {
            return PacketFlyDeliveryResult.UNRELATED
        }

        val result = if (delivered && allAuxiliariesDelivered) {
            PacketFlyDeliveryResult.FINAL_DELIVERED
        } else {
            PacketFlyDeliveryResult.FINAL_REJECTED
        }
        clear()
        return result
    }

    private fun confirmAuxiliary(packet: T, delivered: Boolean): PacketFlyDeliveryResult {
        val alreadyDelivered = auxiliaryDelivery[packet] == true
        if (delivered && !alreadyDelivered) {
            auxiliaryDelivery[packet] = true
            deliveredAuxiliaryCount++
        }
        return if (delivered) {
            PacketFlyDeliveryResult.AUXILIARY_DELIVERED
        } else {
            PacketFlyDeliveryResult.AUXILIARY_REJECTED
        }
    }

    fun clear() {
        auxiliaryDelivery.clear()
        orderedAuxiliaries = emptyList()
        finalPacket = null
        deliveredAuxiliaryCount = 0
    }
}

/**
 * Physical Vanilla Fly whose extra wire packets are supplied by [PacketFlyPlanner]. Minecraft still creates the
 * ordinary endpoint packet after PRE, so camera, hitbox, collisions, and movement remain at the real client position.
 */
internal object FlyPacket : VanillaFlyMode("Packet", 0.1f..500f) {

    private val maxPackets by int("MaxPackets", 128, 2..512)
    private val speedExploit by enumChoice("SpeedExploit", PacketFlySpeedExploit.SAFE)
    private val primingPacketType by enumChoice("PrimingPacketType", PacketFlyPrimingPacketShape.Position)

    private val deliveryTracker = PacketFlyDeliveryTracker<ServerboundMovePlayerPacket>()
    private var expectedEndpoint: Vec3? = null

    override val movementSuspended: Boolean
        get() = ModuleSpearKill.usesPacketMovement

    override fun onVanillaFlyRuntimeReset() = clearPacketPlan()

    override fun onVanillaFlyMovementSuspended() = clearPacketPlan()

    private fun clearPacketPlan() {
        BlinkManager.packetQueue.removeIf { snapshot ->
            (snapshot.packet as? ServerboundMovePlayerPacket)?.let(deliveryTracker::owns) == true
        }
        deliveryTracker.clear()
        expectedEndpoint = null
    }

    @Suppress("unused")
    private val resolvedMovementHandler = handler<PlayerStepSuccessEvent>(priority = FINAL_DECISION) { event ->
        val spearKillOwnsPacketRoute = ModuleSpearKill.usesPacketMovement
        val resolvedMovement = PacketFlyRuntimePolicy.resolvePhysicalMovement(
            collisionResolvedMovement = event.adjustedVec,
            spearKillOwnsPacketRoute = spearKillOwnsPacketRoute,
        )
        event.adjustedVec = resolvedMovement
        if (!PacketFlyRuntimePolicy.shouldPlan(resolvedMovement, spearKillOwnsPacketRoute)) {
            clearPacketPlan()
            return@handler
        }

        val start = player.position()
        val requestedEnd = start.add(resolvedMovement)
        val result = calculatePacketPlan(start, requestedEnd)
        val plan = (result as? PacketFlyPlanResult.Ready)?.plan
        if (plan == null || !plan.finalVanillaPacketReserved) {
            event.adjustedVec = Vec3.ZERO
            clearPacketPlan()
            return@handler
        }

        val auxiliaryPackets = plan.auxiliaryPackets.map(::createAuxiliaryPacket)
        deliveryTracker.stage(auxiliaryPackets)
        expectedEndpoint = plan.finalEndpoint
        event.adjustedVec = plan.finalEndpoint.subtract(start)
    }

    private fun calculatePacketPlan(start: Vec3, requestedEnd: Vec3): PacketFlyPlanResult {
        val existingPackets = existingDeliveredMovementPacketCount
        val reservedAfterFinal = forecastPostBypassPacketCount()
        val maximumNoFallReservation =
            (maxPackets - existingPackets - reservedAfterFinal - 1).coerceAtLeast(0)
        val plans = mutableMapOf<Int, PacketFlyPlanResult>()
        fun plan(reservedNoFallPackets: Int): PacketFlyPlanResult = plans.getOrPut(reservedNoFallPackets) {
            planWithNoFallReservation(
                start = start,
                requestedEnd = requestedEnd,
                existingPackets = existingPackets,
                reservedNoFallPackets = reservedNoFallPackets,
                reservedAfterFinal = reservedAfterFinal,
            )
        }

        val unrestricted = plan(0)
        val unrestrictedPlan = (unrestricted as? PacketFlyPlanResult.Ready)?.plan ?: return unrestricted
        if (forecastNoFallPacketCount(unrestrictedPlan.finalEndpoint) == 0) {
            return unrestricted
        }

        val reservation = findMinimumFeasiblePacketReservation(maximumNoFallReservation) { candidate ->
            val candidatePlan = (plan(candidate) as? PacketFlyPlanResult.Ready)?.plan
                ?: return@findMinimumFeasiblePacketReservation null
            forecastNoFallPacketCount(candidatePlan.finalEndpoint)
        } ?: return PacketFlyPlanResult.Blocked(PacketFlyPlanBlockReason.PACKET_BUDGET_EXCEEDED)
        return plan(reservation)
    }

    private fun planWithNoFallReservation(
        start: Vec3,
        requestedEnd: Vec3,
        existingPackets: Int,
        reservedNoFallPackets: Int,
        reservedAfterFinal: Int,
    ): PacketFlyPlanResult {
        val request = PacketFlyPlanRequest(
            start = start,
            requestedEnd = requestedEnd,
            serverPhysicsVelocity = player.deltaMovement,
            fallFlying = player.isFallFlying,
            packetAccounting = PacketFlyPacketAccounting(
                existingPreFinalPackets = existingPackets,
                forecastNoFallPackets = reservedNoFallPackets,
                vanillaFinalPacketReserved = true,
                reservedPacketsAfterFinal = reservedAfterFinal,
                maxPackets = maxPackets,
            ),
        )
        return when (speedExploit) {
            PacketFlySpeedExploit.SAFE -> PacketFlyPlanner.safe(request)
            PacketFlySpeedExploit.PRIMED -> PacketFlyPlanner.primed(request, primingPacketType)
        }
    }

    private fun createAuxiliaryPacket(plan: PacketFlyAuxiliaryPacketPlan): ServerboundMovePlayerPacket = when (plan) {
        is PacketFlyAuxiliaryPacketPlan.Position -> ServerboundMovePlayerPacket.Pos(
            plan.endpoint.x,
            plan.endpoint.y,
            plan.endpoint.z,
            player.onGround(),
            player.horizontalCollision,
        )
        is PacketFlyAuxiliaryPacketPlan.Priming -> createPrimingPacket(plan)
    }

    private fun createPrimingPacket(plan: PacketFlyAuxiliaryPacketPlan.Priming): ServerboundMovePlayerPacket =
        when (plan.shape) {
            PacketFlyPrimingPacketShape.Position -> ServerboundMovePlayerPacket.Pos(
                requireNotNull(plan.position).x,
                plan.position.y,
                plan.position.z,
                player.onGround(),
                player.horizontalCollision,
            )
            PacketFlyPrimingPacketShape.PositionRotation -> ServerboundMovePlayerPacket.PosRot(
                requireNotNull(plan.position).x,
                plan.position.y,
                plan.position.z,
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision,
            )
            PacketFlyPrimingPacketShape.Rotation -> ServerboundMovePlayerPacket.Rot(
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision,
            )
            PacketFlyPrimingPacketShape.StatusOnly -> ServerboundMovePlayerPacket.StatusOnly(
                player.onGround(),
                player.horizontalCollision,
            )
        }

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.state == EventState.POST) {
            clearPacketPlan()
            return@handler
        }
        if (ModuleSpearKill.usesPacketMovement) {
            clearPacketPlan()
            return@handler
        }

        val endpoint = expectedEndpoint ?: return@handler
        if (player.position().distanceToSqr(endpoint) > ENDPOINT_EPSILON_SQUARED) {
            clearPacketPlan()
            event.cancelEvent()
            return@handler
        }

        deliveryTracker.auxiliaryPackets.forEach { packet ->
            network.send(packet)
            if (!deliveryTracker.wasDelivered(packet)) {
                return@handler
            }
        }
    }

    @Suppress("unused")
    private val endpointSafetyHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.origin == TransferOrigin.INCOMING &&
            event.packet is ClientboundPlayerPositionPacket &&
            !event.isCancelled
        ) {
            clearPacketPlan()
            return@handler
        }

        val packet = event.outgoingMovementPacket ?: return@handler
        if (deliveryTracker.ownsAuxiliary(packet)) {
            return@handler
        }
        if (isTrackedNoFallGroundPacket(packet)) {
            return@handler
        }
        val endpoint = expectedEndpoint ?: return@handler
        if (!matchesPacketFlyEndpoint(packet, endpoint)) {
            event.cancelEvent()
            return@handler
        }

        deliveryTracker.expectFinalPacket(packet)
        if (!deliveryTracker.allAuxiliariesDelivered) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val finalEndpointValidationHandler = handler<PacketEvent>(
        priority = (READ_FINAL_STATE + 1).toShort(),
    ) { event ->
        val packet = event.outgoingMovementPacket ?: return@handler
        if (!deliveryTracker.isExpectedFinalPacket(packet)) {
            return@handler
        }
        val endpoint = expectedEndpoint ?: run {
            event.cancelEvent()
            return@handler
        }
        if (!matchesPacketFlyEndpoint(packet, endpoint)) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val deliveryHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        if (event.origin != TransferOrigin.OUTGOING) {
            return@handler
        }
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        val queuedByBlink = deliveryTracker.owns(packet) && BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) {
            BlinkManager.packetQueue.removeIf { it.packet === packet }
        }
        val result = deliveryTracker.confirm(packet, delivered = !event.isCancelled && !queuedByBlink)
        if (result == PacketFlyDeliveryResult.FINAL_DELIVERED ||
            result == PacketFlyDeliveryResult.FINAL_REJECTED
        ) {
            expectedEndpoint = null
        }
    }

    private const val ENDPOINT_EPSILON_SQUARED = 1.0E-12
}
