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

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerStepSuccessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.outgoingMovementPacket
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

/**
 * Physical Vanilla Fly whose extra wire packets are supplied by [PacketFlyPlanner]. Minecraft still creates the
 * ordinary endpoint packet after PRE, so camera, hitbox, collisions, and movement remain at the real client position.
 */
internal object FlyPacket : VanillaFlyMode("Packet", 0.1f..500f) {

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
        reliableSpeed = false,
    )

    private val maxPackets by int("MaxPackets", 128, 2..512)
    private val speedExploit by enumChoice("SpeedExploit", PacketFlySpeedExploit.SAFE)
    private val primingPacketType by enumChoice("PrimingPacketType", PacketFlyPrimingPacketShape.Position)

    private val deliveryTracker = PacketFlyDeliveryTracker<ServerboundMovePlayerPacket>()
    private var expectedEndpoint: Vec3? = null

    override val movementSuspended: Boolean
        get() = RemoteMovementOwnership.active

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
        val remoteKillOwnsPacketRoute = RemoteMovementOwnership.active
        val resolvedMovement = PacketFlyRuntimePolicy.resolvePhysicalMovement(
            collisionResolvedMovement = event.adjustedVec,
            remoteKillOwnsPacketRoute = remoteKillOwnsPacketRoute,
        )
        event.adjustedVec = resolvedMovement
        if (!PacketFlyRuntimePolicy.shouldPlan(resolvedMovement, remoteKillOwnsPacketRoute)) {
            clearPacketPlan()
            return@handler
        }

        val start = player.position()
        val requestedEnd = start.add(resolvedMovement)
        val result = planPacketFlyRuntime(
            PacketFlyRuntimePlanRequest(
                start = start,
                requestedEnd = requestedEnd,
                serverState = PacketFlyServerState(player.deltaMovement, player.isFallFlying),
                limits = PacketFlyRuntimeLimits(
                    existingPreFinalPackets = existingDeliveredMovementPacketCount,
                    reservedPacketsAfterFinal = forecastPostBypassPacketCount(),
                    maxPackets = maxPackets,
                ),
                speedExploit = speedExploit,
                primingPacketShape = primingPacketType,
                forecastNoFallPackets = ::forecastNoFallPacketCount,
            ),
        )
        val plan = (result as? PacketFlyPlanResult.Ready)?.plan
        if (plan == null || !plan.finalVanillaPacketReserved) {
            event.adjustedVec = Vec3.ZERO
            clearPacketPlan()
            return@handler
        }

        val packetState = PacketFlyPlayerPacketState(
            yaw = player.yRot,
            pitch = player.xRot,
            onGround = player.onGround(),
            horizontalCollision = player.horizontalCollision,
        )
        val auxiliaryPackets = plan.auxiliaryPackets.map { createPacketFlyAuxiliaryPacket(it, packetState) }
        deliveryTracker.stage(auxiliaryPackets)
        expectedEndpoint = plan.finalEndpoint
        event.adjustedVec = plan.finalEndpoint.subtract(start)
    }

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.state == EventState.POST) {
            clearPacketPlan()
            return@handler
        }
        if (RemoteMovementOwnership.active) {
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
