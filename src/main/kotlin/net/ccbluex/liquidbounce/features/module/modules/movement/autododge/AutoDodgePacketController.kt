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

import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal data class AutoDodgePacketUpdateRequest(
    val player: LocalPlayer,
    val world: ClientLevel,
    val cooldownTicks: Int,
    val holdTicks: Int,
    val projectileThreat: AutoDodgePacketProjectileThreat?,
    val maceThreat: MaceThreat?,
    val spearThreat: SpearThreat?,
    val currentConnection: () -> Connection?,
    val blinkMovementQueued: () -> Boolean,
    val sendPacket: (ServerboundMovePlayerPacket.Pos) -> Unit,
)

/** Coordinates threat priority, collision preflight, ownership, and the held packet dodge. */
internal class AutoDodgePacketController(
    private val runtime: AutoDodgePacketRuntime = AutoDodgePacketRuntime(),
    private val acquireMovementLease: () -> AutoCloseable? = {
        RemoteMovementOwnership.tryAcquire(AUTO_DODGE_PACKET_MOVEMENT_OWNER)
    },
) {
    val debug: AutoDodgePacketRuntimeDebug
        get() = runtime.debug

    val suppressesMovementPackets: Boolean
        get() = runtime.suppressesMovementPackets

    fun update(request: AutoDodgePacketUpdateRequest) {
        val tick = request.player.tickCount.toLong()
        val origin = request.player.position()
        val predictions = threatPredictions(request, tick, origin)

        runtime.activeThreatKey?.let { activeThreatKey ->
            predictions.firstOrNull { it.key == activeThreatKey }?.let { prediction ->
                runtime.extendHold(
                    threatKey = activeThreatKey,
                    predictedImpactTick = prediction.impactSchedule.predictedImpactTick,
                    postImpactHoldTicks = request.holdTicks,
                )
            }
        }
        if (runtime.progressHold(
                tick = tick,
                preflight = { preflightHeldReturn(request, it) },
                sendPacket = request.sendPacket,
            )
        ) {
            return
        }

        val candidates = predictions.mapNotNull { plan(request, origin, it) }
        val candidate = selectDueAutoDodgePacketCandidate(candidates, tick)
        if (candidate == null) {
            selectArmedAutoDodgePacketCandidate(candidates, tick)?.let(runtime::arm) ?: runtime.idle()
            return
        }

        startHold(request, origin, candidate)
    }

    fun reset(sendReturn: ((ServerboundMovePlayerPacket.Pos) -> Unit)? = null) {
        runtime.reset(sendReturn)
    }

    private fun threatPredictions(
        request: AutoDodgePacketUpdateRequest,
        tick: Long,
        origin: Vec3,
    ) = listOfNotNull(
        request.projectileThreat?.toPacketThreatPrediction(tick, request.holdTicks),
        request.maceThreat?.toPacketThreatPrediction(origin, tick, request.holdTicks),
        request.spearThreat?.toPacketThreatPrediction(origin, tick, request.holdTicks),
    )

    private fun plan(
        request: AutoDodgePacketUpdateRequest,
        origin: Vec3,
        prediction: AutoDodgePacketThreatPrediction,
    ): AutoDodgePacketCandidate? {
        val destination = AutoDodgePacketPlanner.plan(
            origin = origin,
            attackAxisOrigin = prediction.axis.origin,
            attackAxisDirection = prediction.axis.direction,
            fallbackDirection = prediction.axis.fallbackDirection,
            isSafe = { AutoDodgePacketWorldSafety.isSafe(request, origin, it, request.player.onGround()) },
        ) ?: return null
        return AutoDodgePacketCandidate(prediction.key, prediction.impactSchedule, destination)
    }

    private fun startHold(
        request: AutoDodgePacketUpdateRequest,
        plannedOrigin: Vec3,
        candidate: AutoDodgePacketCandidate,
    ) {
        val capturedConnection = request.currentConnection()
        val grounded = request.player.onGround()
        runtime.start(
            request = AutoDodgePacketRuntimeRequest(
                tick = request.player.tickCount.toLong(),
                cooldownTicks = request.cooldownTicks,
                holdTicks = request.holdTicks,
                selectedThreat = candidate.threatType,
                threatEntityId = candidate.threatKey.entityId,
                predictedImpactTick = candidate.impactSchedule.predictedImpactTick,
                dodgeAtTick = candidate.impactSchedule.dodgeAtTick,
                returnNotBeforeTick = candidate.impactSchedule.returnNotBeforeTick,
                destination = AutoDodgePacketEndpoint(candidate.destination, grounded, false),
            ),
            snapshotOrigin = {
                AutoDodgePacketEndpoint(request.player.position(), request.player.onGround(), false)
            },
            acquireMovementLease = acquireMovementLease,
            preflight = { preflight(request, capturedConnection, plannedOrigin, it) },
            sendPacket = request.sendPacket,
        )
    }

    private fun preflightHeldReturn(
        request: AutoDodgePacketUpdateRequest,
        burst: AutoDodgePacketBurst,
    ): AutoDodgePacketPreflightResult = when {
        request.currentConnection()?.isConnected != true ->
            AutoDodgePacketPreflightResult.CONNECTION_UNAVAILABLE
        request.player.isPassenger || request.player.isSleeping || request.player.isDeadOrDying ->
            AutoDodgePacketPreflightResult.BURST_REJECTED
        !AutoDodgePacketWorldSafety.isSafe(
            request,
            burst.origin.position,
            burst.destination.position,
            burst.origin.onGround,
        ) -> AutoDodgePacketPreflightResult.SAFETY_REJECTED
        else -> AutoDodgePacketPreflightResult.READY
    }

    private fun preflight(
        request: AutoDodgePacketUpdateRequest,
        capturedConnection: Connection?,
        plannedOrigin: Vec3,
        burst: AutoDodgePacketBurst,
    ): AutoDodgePacketPreflightResult = when {
        capturedConnection == null || !capturedConnection.isConnected ||
            request.currentConnection() !== capturedConnection ->
            AutoDodgePacketPreflightResult.CONNECTION_UNAVAILABLE
        request.blinkMovementQueued() || request.player.isPassenger ||
            request.player.isSleeping || request.player.isDeadOrDying ->
            AutoDodgePacketPreflightResult.BURST_REJECTED
        burst.origin.position.distanceToSqr(plannedOrigin) > POSITION_EPSILON_SQUARED ||
            burst.origin.onGround != burst.destination.onGround ->
            AutoDodgePacketPreflightResult.BURST_REJECTED
        !AutoDodgePacketWorldSafety.isSafe(
            request,
            burst.origin.position,
            burst.destination.position,
            burst.origin.onGround,
        ) -> AutoDodgePacketPreflightResult.SAFETY_REJECTED
        else -> AutoDodgePacketPreflightResult.READY
    }

    private companion object {
        const val POSITION_EPSILON_SQUARED = 1.0E-12
    }
}
