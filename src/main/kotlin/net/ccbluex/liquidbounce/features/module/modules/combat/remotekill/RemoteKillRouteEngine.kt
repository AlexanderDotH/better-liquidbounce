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
/** Owns one target from route admission through strike resolution and exact return completion. */
internal class RemoteKillRouteEngine<T : Any>(
    private val session: RemoteKillRouteSession,
    private val weaponAdapter: RemoteKillWeaponAdapter<T>,
    private val movementOwner: String = "RemoteKillRouteEngine",
    private val retainMovementAfterCompletion: Boolean = false,
) {

    init {
        require(movementOwner.isNotBlank()) { "Remote-kill movement owner must not be blank" }
    }

    var activeRequest: RemoteKillRouteRequest? = null
        private set
    var activeTarget: T? = null
        private set
    var awaitingStrike: Boolean = false
        private set

    private var strikeResolved = false
    private var movementLease: RemoteKillMovementOwnership.Lease? = null

    val ownsMovement: Boolean
        get() = activeRequest != null

    fun start(target: T, request: RemoteKillRouteRequest) {
        check(!ownsMovement && !session.active) { "A remote-kill route is already active" }
        activeTarget = target
        activeRequest = request
        awaitingStrike = false
        strikeResolved = false
        try {
            movementLease = RemoteKillMovementOwnership.acquire(movementOwner)
            session.start(request)
        } catch (throwable: Throwable) {
            session.clear()
            releaseOwnership()
            throw throwable
        }
    }

    fun prepareNextStep(): Vec3? {
        if (awaitingStrike) return null
        return session.prepareNextStep().also { releaseOwnershipIfComplete() }
    }

    /** Returns a strike outcome only when this delivery confirmed the final outbound movement. */
    fun confirmStep(delivered: Boolean): RemoteKillStrikeResult? {
        val confirmedOutbound = delivered && session.pendingOutboundStep
        session.confirmStep(delivered)
        val strikeResult = if (confirmedOutbound && session.recovering && !strikeResolved) {
            attemptStrike()
        } else {
            null
        }
        releaseOwnershipIfComplete()
        return strikeResult
    }

    fun retryStrike(): RemoteKillStrikeResult? {
        if (!awaitingStrike || !session.recovering) return null
        return attemptStrike()
    }

    /** Updates target ownership after the session atomically installed a chained outbound route. */
    fun handoff(target: T, request: RemoteKillRouteRequest) {
        check(ownsMovement && session.active) { "A remote-kill route is not active" }
        check(!awaitingStrike) { "A deferred strike must resolve before target handoff" }
        activeTarget = target
        activeRequest = request
        strikeResolved = false
    }

    /** Replaces an interrupted route with a validated packet-first recovery after a correction. */
    fun beginPacketExactRecoveryFrom(
        authoritativeOffset: Vec3,
        recoveryMovements: List<Vec3>,
        stepWaitTicks: Int = 0,
    ) {
        check(ownsMovement) { "A remote-kill route is not active" }
        awaitingStrike = false
        strikeResolved = true
        session.beginPacketExactRecoveryFrom(authoritativeOffset, recoveryMovements, stepWaitTicks)
        releaseOwnershipIfComplete()
    }

    /** Stops new work, then retraces only movement that the session confirmed as delivered. */
    fun abort() {
        awaitingStrike = false
        strikeResolved = true
        session.beginExactReturn()
        releaseOwnershipIfComplete()
    }

    fun clear() {
        session.clear()
        releaseOwnership()
    }

    /**
     * Releases a completed route after caller-owned finalization made the session inactive.
     * Retained correction-window ownership is deliberately left untouched.
     */
    fun reconcileCompletedOwnership(): Boolean {
        val ownedBefore = ownsMovement
        releaseOwnershipIfComplete()
        return ownedBefore && !ownsMovement
    }

    /** Releases an opt-in post-completion lease after the caller's correction window closes. */
    fun releaseCompletedOwnership() {
        check(!session.active && !awaitingStrike) { "A remote-kill route is still active" }
        releaseOwnership()
    }

    private fun attemptStrike(): RemoteKillStrikeResult {
        val target = requireNotNull(activeTarget) { "Remote-kill target ownership was lost" }
        val route = requireNotNull(activeRequest) { "Remote-kill route ownership was lost" }
        val result = try {
            weaponAdapter.strike(RemoteKillStrikeRequest(target, route.origin, route.endpoint))
        } catch (_: Exception) {
            RemoteKillStrikeResult.Rejected("weapon-adapter-failure")
        }
        awaitingStrike = result == RemoteKillStrikeResult.Deferred
        strikeResolved = !awaitingStrike
        return result
    }

    private fun releaseOwnershipIfComplete() {
        if (session.active || awaitingStrike) return
        if (retainMovementAfterCompletion) return
        releaseOwnership()
    }

    private fun releaseOwnership() {
        movementLease?.close()
        movementLease = null
        activeRequest = null
        activeTarget = null
        awaitingStrike = false
        strikeResolved = false
    }
}

internal fun Vec3.hasFiniteRemoteKillCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
