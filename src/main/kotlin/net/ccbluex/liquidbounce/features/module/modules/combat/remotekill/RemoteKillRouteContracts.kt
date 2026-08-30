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

package net.ccbluex.liquidbounce.features.module.modules.combat.remotekill

import net.minecraft.world.phys.Vec3

/** Shared runtime ceiling; individual weapon settings may expose a narrower range. */
internal const val REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS = 20

/**
 * Weapon-neutral description of one server-visible outbound route and its exact inverse return.
 *
 * The request deliberately owns only immutable route/session policy. Collision and target
 * validation stay with the route planner, while packet delivery stays with [RemoteKillRouteSession].
 */
internal class RemoteKillRouteRequest(
    val origin: Vec3,
    outboundMovements: List<Vec3>,
    val strikeHoldTicks: Int = 0,
    val stepWaitTicks: Int = 0,
    val physicalReturn: Boolean = false,
    val preStrikeHoldTicks: Int = 0,
    val terminalSuffixSteps: Int = 1,
    val terminalBurstSteps: Int = 0,
    val requireTerminalAuthorization: Boolean = false,
    returnMovements: List<Vec3>? = null,
) {

    val outboundMovements: List<Vec3> = outboundMovements.toList()
    val endpoint: Vec3
    val returnMovements: List<Vec3>
    val roundTripMovements: List<Vec3>

    init {
        require(origin.hasFiniteRemoteKillCoordinates()) { "Remote-kill origin must be finite" }
        require(this.outboundMovements.isNotEmpty()) { "Remote-kill route must contain outbound movement" }
        require(this.outboundMovements.all(RemoteKillRouteRequest::isValidMovement)) {
            "Remote-kill route movements must be finite and non-zero"
        }
        require(strikeHoldTicks >= 0) { "Strike hold duration must not be negative" }
        require(stepWaitTicks >= 0) { "Step wait duration must not be negative" }
        require(preStrikeHoldTicks >= 0) { "Pre-strike hold duration must not be negative" }
        require(terminalSuffixSteps in 1..this.outboundMovements.size) {
            "Terminal suffix must fit inside the outbound route"
        }
        require(terminalBurstSteps == 0 || terminalBurstSteps in 2..terminalSuffixSteps) {
            "Terminal burst must be disabled or fit inside the terminal suffix"
        }
        require(!requireTerminalAuthorization || preStrikeHoldTicks > 0) {
            "Terminal authorization requires a pre-strike hold"
        }

        endpoint = this.outboundMovements.fold(origin, Vec3::add)
        require(endpoint.hasFiniteRemoteKillCoordinates()) { "Remote-kill endpoint must be finite" }
        this.returnMovements = returnMovements?.toList()
            ?: this.outboundMovements.asReversed().map { it.scale(-1.0) }
        require(this.returnMovements.isNotEmpty() && this.returnMovements.all(::isValidMovement)) {
            "Remote-kill return movements must be finite and non-zero"
        }
        val finalPosition = this.returnMovements.fold(endpoint, Vec3::add)
        require(finalPosition.distanceToSqr(origin) < MOVEMENT_EPSILON_SQUARED) {
            "Remote-kill return movements must end at the exact origin"
        }
        roundTripMovements = buildList(this.outboundMovements.size + this.returnMovements.size + 1) {
            addAll(this@RemoteKillRouteRequest.outboundMovements)
            addAll(this@RemoteKillRouteRequest.returnMovements)
            add(Vec3.ZERO)
        }
    }

    private companion object {
        private const val MOVEMENT_EPSILON_SQUARED = 1.0E-12

        private fun isValidMovement(movement: Vec3): Boolean =
            movement.hasFiniteRemoteKillCoordinates() && movement.lengthSqr() >= MOVEMENT_EPSILON_SQUARED
    }
}

/** Delivery-confirmed movement boundary shared by remote weapon routes. */
internal interface RemoteKillRouteSession {
    val active: Boolean
    val recovering: Boolean
    val committedOffset: Vec3
    val virtualOffset: Vec3
    val requiresDelivery: Boolean
    val pendingOutboundStep: Boolean
    val pendingMovement: Vec3?

    fun start(request: RemoteKillRouteRequest)

    /** Prepares one packet and returns its session-relative virtual offset; [pendingMovement] is its delta. */
    fun prepareNextStep(): Vec3?
    fun confirmStep(delivered: Boolean)
    fun beginExactReturn()
    fun exactRecoveryMovementsFrom(authoritativeOffset: Vec3): List<Vec3>?
    fun beginPacketExactRecoveryFrom(
        authoritativeOffset: Vec3,
        recoveryMovements: List<Vec3>,
        stepWaitTicks: Int = 0,
    )
    fun clear()
}

internal data class RemoteKillStrikeRequest<T : Any>(
    val target: T,
    val origin: Vec3,
    val endpoint: Vec3,
)

/** Weapon-specific terminal action; only [Committed] permits a success claim. */
internal sealed interface RemoteKillStrikeResult {
    data object Committed : RemoteKillStrikeResult
    data object Deferred : RemoteKillStrikeResult
    data class Rejected(val reason: String) : RemoteKillStrikeResult {
        init {
            require(reason.isNotBlank()) { "Remote-kill rejection reason must not be blank" }
        }
    }
}

internal fun interface RemoteKillWeaponAdapter<T : Any> {
    fun strike(request: RemoteKillStrikeRequest<T>): RemoteKillStrikeResult
}
