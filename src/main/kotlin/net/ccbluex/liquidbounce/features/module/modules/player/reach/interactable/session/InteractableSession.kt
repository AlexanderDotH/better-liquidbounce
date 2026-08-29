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
@Suppress("TooManyFunctions") // Public operations intentionally mirror the lifecycle state machine.
internal class InteractableSession<T : Any, P : Any> {

    private data class QueuedMovement<P : Any>(
        val movement: InteractableMovement<P>,
    )

    private data class PendingMovement<P : Any>(
        val packetIdentity: Any,
        val queuedMovement: QueuedMovement<P>,
    )

    private data class RecoveryCheckpoint<P : Any>(
        val position: Vec3,
        val remainingMovements: List<InteractableMovement<P>>,
    )

    private val movementQueue = ArrayDeque<QueuedMovement<P>>()
    private var pendingMovement: PendingMovement<P>? = null
    private var route: InteractableSessionRoute<P>? = null
    private var confirmedOutboundSteps = 0
    private var recoveryCheckpoints = emptyList<RecoveryCheckpoint<P>>()
    private var ownedContainer: Int? = null

    var state: InteractableSessionState = InteractableSessionState.Idle
        private set
    var target: T? = null
        private set
    var origin: Vec3? = null
        private set
    var settings: InteractableSessionSettings? = null
        private set
    var confirmedPosition: Vec3? = null
        private set

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
        route.steps.forEach { step -> movementQueue += QueuedMovement(step.outbound) }
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
        pendingMovement = PendingMovement(packetIdentity, queued)
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
    ): InteractableCorrectionDecision {
        if (state === InteractableSessionState.Idle) return InteractableCorrectionDecision.Ignored
        require(authoritativePosition.hasFiniteCoordinates()) { "Authoritative correction must be finite" }
        val sessionOrigin = requireNotNull(origin)
        if (authoritativePosition.matches(sessionOrigin)) {
            val effects = closeOwnedContainerEffect() + releaseAtPosition(
                InteractableSessionCause.COMPLETED,
                sessionOrigin,
            )
            return InteractableCorrectionDecision.Completed(effects)
        }

        val recovery = knownRecoveryFrom(authoritativePosition) ?: validatedRecovery?.also {
            requireValidRecovery(authoritativePosition, it)
        }
        if (recovery != null) {
            val effects = installRecovery(
                cause = InteractableSessionCause.CORRECTION,
                fromPosition = authoritativePosition,
                recovery = recovery,
                tick = tick,
                closeOwnedContainer = true,
            )
            return InteractableCorrectionDecision.Recovering(effects)
        }

        val effects = closeOwnedContainerEffect() + listOf(
            InteractableSessionEffect.AcceptCorrectionLocally(authoritativePosition),
            InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
        )
        clearActiveSession(authoritativePosition)
        return InteractableCorrectionDecision.AcceptLocally(effects)
    }

    /** World loss, disconnect, and death intentionally skip return because its world is no longer valid. */
    fun hardReset(cause: InteractableSessionCause): List<InteractableSessionEffect> {
        require(cause in HARD_RESET_CAUSES) { "Only terminal world lifecycle causes may hard-reset Interactable" }
        if (state === InteractableSessionState.Idle) return emptyList()
        clearActiveSession(confirmedPosition)
        return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
    }

    private fun movementCommitted(tick: Int): List<InteractableSessionEffect> = when (val current = state) {
        is InteractableSessionState.Outbound -> commitOutbound(current, tick)
        is InteractableSessionState.Returning -> commitReturn(current)
        is InteractableSessionState.Recovering -> commitRecovery(current)
        else -> error("Interactable movement committed outside a travel state")
    }

    private fun commitOutbound(
        current: InteractableSessionState.Outbound,
        tick: Int,
    ): List<InteractableSessionEffect> {
        confirmedOutboundSteps++
        if (confirmedOutboundSteps < current.totalSteps) {
            state = current.copy(confirmedSteps = confirmedOutboundSteps)
            return emptyList()
        }

        val verificationTicks = capturedSettings().endpointVerifyTicks
        state = InteractableSessionState.Opening(
            attemptsSent = if (verificationTicks == 0) 1 else 0,
            attemptStartedTick = tick,
        )
        return if (verificationTicks == 0) listOf(InteractableSessionEffect.OpenAttempt(1)) else emptyList()
    }

    private fun commitReturn(current: InteractableSessionState.Returning): List<InteractableSessionEffect> {
        val committed = current.confirmedMovements + 1
        if (movementQueue.isNotEmpty()) {
            state = current.copy(confirmedMovements = committed)
            return emptyList()
        }
        return releaseAtPosition(InteractableSessionCause.COMPLETED, requireNotNull(origin))
    }

    private fun commitRecovery(current: InteractableSessionState.Recovering): List<InteractableSessionEffect> {
        val committed = current.confirmedMovements + 1
        if (movementQueue.isNotEmpty()) {
            state = current.copy(confirmedMovements = committed)
            return emptyList()
        }
        return releaseAtPosition(InteractableSessionCause.COMPLETED, requireNotNull(origin))
    }

    private fun tickOpening(
        current: InteractableSessionState.Opening,
        tick: Int,
    ): List<InteractableSessionEffect> {
        val settings = capturedSettings()
        if (current.attemptsSent == 0) return tickEndpointVerification(current, tick, settings)
        if (!hasElapsed(current.attemptStartedTick, tick, settings.openTimeoutTicks)) return emptyList()
        return if (current.attemptsSent <= settings.openRetries) {
            nextOpenAttempt(current, tick)
        } else {
            beginRecovery(InteractableSessionCause.OPEN_TIMEOUT, tick)
        }
    }

    private fun tickEndpointVerification(
        current: InteractableSessionState.Opening,
        tick: Int,
        settings: InteractableSessionSettings,
    ): List<InteractableSessionEffect> {
        if (!hasElapsed(current.attemptStartedTick, tick, settings.endpointVerifyTicks)) return emptyList()
        state = current.copy(attemptsSent = 1, attemptStartedTick = tick)
        return listOf(InteractableSessionEffect.OpenAttempt(1))
    }

    private fun nextOpenAttempt(
        current: InteractableSessionState.Opening,
        tick: Int,
    ): List<InteractableSessionEffect> {
        val nextAttempt = current.attemptsSent + 1
        state = current.copy(attemptsSent = nextAttempt, attemptStartedTick = tick)
        return listOf(InteractableSessionEffect.OpenAttempt(nextAttempt))
    }

    private fun tickHolding(
        current: InteractableSessionState.Holding,
        tick: Int,
    ): List<InteractableSessionEffect> {
        val timeout = capturedSettings().holdTimeoutTicks
        if (timeout == 0 || !hasElapsed(current.startedTick, tick, timeout)) return emptyList()
        return beginReturn(InteractableSessionCause.HOLD_TIMEOUT, tick, closeOwnedContainer = true)
    }

    private fun tickReturning(
        current: InteractableSessionState.Returning,
        tick: Int,
    ): List<InteractableSessionEffect> {
        if (!hasElapsed(current.startedTick, tick, capturedSettings().routeTimeoutTicks)) return emptyList()
        return promoteReturnToRecovery(InteractableSessionCause.ROUTE_TIMEOUT, tick)
    }

    private fun tickRecovering(
        current: InteractableSessionState.Recovering,
        tick: Int,
    ): List<InteractableSessionEffect> {
        if (current.timeoutReported || !hasElapsed(
                current.startedTick,
                tick,
                capturedSettings().routeTimeoutTicks,
            )) {
            return emptyList()
        }
        val position = requireNotNull(confirmedPosition)
        clearActiveSession(position)
        return listOf(
            InteractableSessionEffect.AcceptCorrectionLocally(position),
            InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
        )
    }

    private fun beginReturn(
        cause: InteractableSessionCause,
        tick: Int,
        closeOwnedContainer: Boolean,
    ): List<InteractableSessionEffect> {
        val exactReturn = capturedRoute().exactReturnForPrefix(confirmedOutboundSteps)
        return installTravel(
            cause = cause,
            recovery = false,
            movements = exactReturn,
            tick = tick,
            closeOwnedContainer = closeOwnedContainer,
        )
    }

    private fun beginRecovery(
        cause: InteractableSessionCause,
        tick: Int,
    ): List<InteractableSessionEffect> {
        val exactReturn = capturedRouteOrNull()?.exactReturnForPrefix(confirmedOutboundSteps).orEmpty()
        return installTravel(
            cause = cause,
            recovery = true,
            movements = exactReturn,
            tick = tick,
            closeOwnedContainer = true,
        )
    }

    private fun promoteReturnToRecovery(
        cause: InteractableSessionCause,
        tick: Int,
    ): List<InteractableSessionEffect> {
        pendingMovement = null
        val remaining = movementQueue.map(QueuedMovement<P>::movement)
        return installRecovery(
            cause = cause,
            fromPosition = requireNotNull(confirmedPosition),
            recovery = remaining,
            tick = tick,
            closeOwnedContainer = true,
        )
    }

    private fun installTravel(
        cause: InteractableSessionCause,
        recovery: Boolean,
        movements: List<InteractableMovement<P>>,
        tick: Int,
        closeOwnedContainer: Boolean,
    ): List<InteractableSessionEffect> {
        val fromPosition = requireNotNull(confirmedPosition)
        if (movements.isEmpty()) {
            val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
            return closeEffects + releaseAtPosition(cause, requireNotNull(origin))
        }
        return if (recovery) {
            installRecovery(cause, fromPosition, movements, tick, closeOwnedContainer)
        } else {
            installReturn(cause, fromPosition, movements, tick, closeOwnedContainer)
        }
    }

    private fun installReturn(
        cause: InteractableSessionCause,
        fromPosition: Vec3,
        movements: List<InteractableMovement<P>>,
        tick: Int,
        closeOwnedContainer: Boolean,
    ): List<InteractableSessionEffect> {
        installMovementPlan(fromPosition, movements)
        state = InteractableSessionState.Returning(cause, tick, 0, movements.size)
        val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
        return closeEffects + InteractableSessionEffect.ReturnStarted(cause, fromPosition)
    }

    private fun installRecovery(
        cause: InteractableSessionCause,
        fromPosition: Vec3,
        recovery: List<InteractableMovement<P>>,
        tick: Int,
        closeOwnedContainer: Boolean,
    ): List<InteractableSessionEffect> {
        requireValidRecovery(fromPosition, recovery)
        confirmedPosition = fromPosition
        installMovementPlan(fromPosition, recovery)
        state = InteractableSessionState.Recovering(cause, tick, 0, recovery.size)
        val closeEffects = if (closeOwnedContainer) closeOwnedContainerEffect() else emptyList()
        return closeEffects + InteractableSessionEffect.RecoveryStarted(cause, fromPosition)
    }

    private fun installMovementPlan(
        fromPosition: Vec3,
        movements: List<InteractableMovement<P>>,
    ) {
        pendingMovement = null
        movementQueue.clear()
        movements.forEach { movementQueue += QueuedMovement(it) }
        recoveryCheckpoints = buildRecoveryCheckpoints(fromPosition, movements)
    }

    private fun buildRecoveryCheckpoints(
        fromPosition: Vec3,
        movements: List<InteractableMovement<P>>,
    ): List<RecoveryCheckpoint<P>> = buildList(movements.size + 1) {
        add(RecoveryCheckpoint(fromPosition, movements))
        movements.forEachIndexed { index, movement ->
            add(RecoveryCheckpoint(movement.confirmedPosition, movements.drop(index + 1)))
        }
    }

    private fun knownRecoveryFrom(authoritativePosition: Vec3): List<InteractableMovement<P>>? {
        recoveryCheckpoints.lastOrNull { it.position.matches(authoritativePosition) }
            ?.let { return it.remainingMovements }

        val route = capturedRouteOrNull() ?: return null
        val matchingPrefix = (0 until confirmedOutboundSteps).lastOrNull { index ->
            route.steps[index].outbound.confirmedPosition.matches(authoritativePosition)
        } ?: return null
        return route.exactReturnForPrefix(matchingPrefix + 1)
    }

    private fun requireValidRecovery(
        authoritativePosition: Vec3,
        recovery: List<InteractableMovement<P>>,
    ) {
        val sessionOrigin = requireNotNull(origin)
        require(recovery.isNotEmpty() || authoritativePosition.matches(sessionOrigin)) {
            "Recovery outside the origin must contain movement"
        }
        require(recovery.lastOrNull()?.confirmedPosition?.matches(sessionOrigin) == true || recovery.isEmpty()) {
            "Validated interactable recovery must end at the captured origin"
        }
    }

    private fun releaseWithoutMovement(cause: InteractableSessionCause): List<InteractableSessionEffect> {
        val position = confirmedPosition
        clearActiveSession(position)
        return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
    }

    private fun releaseAtPosition(
        cause: InteractableSessionCause,
        position: Vec3,
    ): List<InteractableSessionEffect> {
        clearActiveSession(position)
        return listOf(InteractableSessionEffect.ReleaseMovementLease(cause))
    }

    private fun closeOwnedContainerEffect(): List<InteractableSessionEffect> {
        val containerId = ownedContainer ?: return emptyList()
        ownedContainer = null
        return listOf(InteractableSessionEffect.CloseOwnedContainer(containerId))
    }

    private fun clearActiveSession(preservedPosition: Vec3?) {
        movementQueue.clear()
        pendingMovement = null
        route = null
        recoveryCheckpoints = emptyList()
        confirmedOutboundSteps = 0
        ownedContainer = null
        target = null
        origin = null
        settings = null
        confirmedPosition = preservedPosition
        state = InteractableSessionState.Idle
    }

    private fun capturedSettings() = requireNotNull(settings) { "Interactable settings were not captured" }

    private fun capturedRoute() = requireNotNull(route) { "Interactable route was not captured" }

    private fun capturedRouteOrNull() = route

    private fun InteractableSessionState.acceptsMovement(): Boolean =
        this is InteractableSessionState.Outbound ||
            this is InteractableSessionState.Returning ||
            this is InteractableSessionState.Recovering

    private fun hasElapsed(startTick: Int, currentTick: Int, durationTicks: Int): Boolean =
        currentTick.toLong() - startTick.toLong() >= durationTicks.toLong()

    private companion object {
        val HARD_RESET_CAUSES = setOf(
            InteractableSessionCause.WORLD_CHANGE,
            InteractableSessionCause.DISCONNECT,
            InteractableSessionCause.DEATH,
        )
    }
}
