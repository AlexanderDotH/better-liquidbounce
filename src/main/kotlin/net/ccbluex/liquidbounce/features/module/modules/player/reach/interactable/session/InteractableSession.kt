/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

import net.minecraft.world.phys.Vec3

/** Pure lifecycle coordinator. Minecraft packet, world, screen, and ownership I/O stays in its adapter. */
internal class InteractableSession<T : Any, P : Any> {

    internal val movementQueue = ArrayDeque<InteractableQueuedMovement<P>>()
    internal var pendingMovement: InteractablePendingMovement<P>? = null
    internal var route: InteractableSessionRoute<P>? = null
    internal var confirmedOutboundSteps = 0
    internal var recoveryCheckpoints = emptyList<InteractableRecoveryCheckpoint<P>>()
    internal var ownedContainer: Int? = null

    var state: InteractableSessionState = InteractableSessionState.Idle
        internal set
    var target: T? = null
        internal set
    var origin: Vec3? = null
        internal set
    var settings: InteractableSessionSettings? = null
        internal set
    var confirmedPosition: Vec3? = null
        internal set

    val movementLeaseRequired: Boolean
        get() = state !== InteractableSessionState.Idle

    val pendingPacketIdentity: Any?
        get() = pendingMovement?.packetIdentity

    val ownedContainerId: Int?
        get() = ownedContainer

    val serverAnchorPosition: Vec3?
        get() = confirmedPosition.takeIf { movementLeaseRequired }

    val suppressMovementInput: Boolean
        get() = state is InteractableSessionState.Holding

    fun beginPlanning(
        target: T,
        origin: Vec3,
        settings: InteractableSessionSettings,
        tick: Int,
    ): Boolean {
        if (state !== InteractableSessionState.Idle) return false
        require(origin.hasFiniteCoordinates()) { "Interactable session origin must be finite" }

        this.target = target
        this.origin = origin
        this.settings = settings.copy()
        confirmedPosition = origin
        state = InteractableSessionState.Planning(tick)
        return true
    }

    fun acceptRoute(route: InteractableSessionRoute<P>, tick: Int): Boolean {
        val planning = state as? InteractableSessionState.Planning ?: return false
        val sessionOrigin = requireNotNull(origin)
        require(tick >= planning.startedTick) { "Interactable route cannot predate its planning session" }
        require(route.origin.matches(sessionOrigin)) { "Interactable route must start at the captured origin" }

        this.route = route
        confirmedOutboundSteps = 0
        movementQueue.clear()
        route.steps.forEach { step -> movementQueue += InteractableQueuedMovement(step.outbound) }
        state = InteractableSessionState.Outbound(
            startedTick = planning.startedTick,
            confirmedSteps = 0,
            totalSteps = route.steps.size,
        )
        return true
    }

    /** Binds exactly one route instruction to the packet object that will carry it. */
    fun nextMovement(): InteractableMovement<P>? =
        movementQueue.firstOrNull()?.movement.takeIf { pendingMovement == null && state.acceptsMovement() }

    fun prepareMovement(packetIdentity: Any): InteractableMovement<P>? {
        if (pendingMovement != null || !state.acceptsMovement()) return null
        val queued = movementQueue.firstOrNull() ?: return null
        pendingMovement = InteractablePendingMovement(packetIdentity, queued)
        return queued.movement
    }

    /** Only [DELIVERED][InteractablePacketDisposition.DELIVERED] advances the confirmed prefix. */
    fun confirmMovement(
        packetIdentity: Any,
        disposition: InteractablePacketDisposition,
        tick: Int,
    ): InteractableMovementConfirmation {
        val pending = pendingMovement?.takeIf { it.packetIdentity === packetIdentity }
            ?: return InteractableMovementConfirmation(matchedPacket = false, committed = false)
        pendingMovement = null
        if (disposition != InteractablePacketDisposition.DELIVERED) {
            return InteractableMovementConfirmation(matchedPacket = true, committed = false)
        }

        check(movementQueue.firstOrNull() === pending.queuedMovement) {
            "Pending interactable packet no longer matches the movement queue"
        }
        movementQueue.removeFirst()
        confirmedPosition = pending.queuedMovement.movement.confirmedPosition
        val effects = movementCommitted(tick)
        return InteractableMovementConfirmation(matchedPacket = true, committed = true, effects = effects)
    }

    fun rejectMovement(
        packetIdentity: Any,
        disposition: InteractablePacketDisposition,
        cause: InteractableSessionCause,
        tick: Int,
    ): List<InteractableSessionEffect> {
        require(disposition != InteractablePacketDisposition.DELIVERED) {
            "Delivered interactable movement must be confirmed instead of rejected"
        }
        val confirmation = confirmMovement(packetIdentity, disposition, tick)
        if (!confirmation.matchedPacket || confirmation.committed) return emptyList()
        return abort(cause, tick)
    }

    fun claimOpenedContainer(containerId: Int, tick: Int): Boolean {
        if (state !is InteractableSessionState.Opening || ownedContainer != null || containerId < 0) return false
        ownedContainer = containerId
        state = InteractableSessionState.Holding(containerId, tick)
        return true
    }

    fun isOwnedContainer(containerId: Int): Boolean = ownedContainer == containerId

    fun containerClosed(
        containerId: Int,
        cause: InteractableContainerCloseCause,
        tick: Int,
    ): List<InteractableSessionEffect> {
        if (!isOwnedContainer(containerId)) return emptyList()
        ownedContainer = null
        val sessionCause = when (cause) {
            InteractableContainerCloseCause.USER -> InteractableSessionCause.USER_CLOSE
            InteractableContainerCloseCause.SERVER -> InteractableSessionCause.SERVER_CLOSE
        }
        return beginReturn(sessionCause, tick, closeOwnedContainer = false)
    }

    /** Advances retry, timeout, return, and recovery deadlines without performing any I/O. */
    fun tick(tick: Int): List<InteractableSessionEffect> = when (val current = state) {
        InteractableSessionState.Idle -> emptyList()
        is InteractableSessionState.Planning -> if (hasElapsed(
                current.startedTick,
                tick,
                capturedSettings().routeTimeoutTicks,
            )) {
                abort(InteractableSessionCause.PLANNING_TIMEOUT, tick)
            } else {
                emptyList()
            }
        is InteractableSessionState.Outbound -> if (hasElapsed(
                current.startedTick,
                tick,
                capturedSettings().routeTimeoutTicks,
            )) {
                abort(InteractableSessionCause.ROUTE_TIMEOUT, tick)
            } else {
                emptyList()
            }
        is InteractableSessionState.Opening -> tickOpening(current, tick)
        is InteractableSessionState.Holding -> tickHolding(current, tick)
        is InteractableSessionState.Returning -> tickReturning(current, tick)
        is InteractableSessionState.Recovering -> tickRecovering(current, tick)
    }

    /** Stops new work and retraces only movement already confirmed by the outgoing packet pipeline. */
    fun abort(cause: InteractableSessionCause, tick: Int): List<InteractableSessionEffect> = when (state) {
        InteractableSessionState.Idle -> emptyList()
        is InteractableSessionState.Planning -> releaseWithoutMovement(cause)
        is InteractableSessionState.Returning -> promoteReturnToRecovery(cause, tick)
        is InteractableSessionState.Recovering -> closeOwnedContainerEffect()
        else -> beginRecovery(cause, tick)
    }

    /**
     * Handles an acknowledged server correction after vanilla derives its authoritative position.
     * Unknown corrections are accepted locally unless the adapter supplies a validated bounded return.
     */
    fun corrected(
        authoritativePosition: Vec3,
        validatedRecovery: List<InteractableMovement<P>>?,
        tick: Int,
    ): InteractableCorrectionDecision = decideCorrection(authoritativePosition, validatedRecovery, tick)

    /** World loss, disconnect, and death intentionally skip return because its world is no longer valid. */
    fun hardReset(cause: InteractableSessionCause): List<InteractableSessionEffect> {
        require(cause in HARD_RESET_CAUSES) { "Only terminal world lifecycle causes may hard-reset Interactable" }
        if (state === InteractableSessionState.Idle) return emptyList()
        clearActiveSession(confirmedPosition)
        return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
    }
}
