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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPolicyConfig
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSession
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSessionPolicy
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneProgress
import kotlin.math.ceil

/** Coordinates the pure navigation policy with Fly and collision-planner ports. */
@Suppress("TooManyFunctions")
class BaritoneFlightRuntimeCoordinator(
    private val fly: BaritoneFlyAutomationPort,
    private val planner: BaritoneFlightPlannerPort,
    private val config: () -> BaritoneFlightRuntimeConfig,
) {
    private var policy = createPolicy(config())
    private var session = BaritoneNavigationSession()
    private var taskActive = false
    private var userInputActive = false
    private var automationPauseActive = false
    private var lease: BaritoneFlyLease? = null
    private var pendingAcquireFailure: String? = null
    private var plannedRoute = emptyList<FlightRuntimePosition>()
    private var routeIndex = 0
    private var planStatus: RuntimeFlightPlanStatus? = null
    private var plannedGoal: FlightRuntimePosition? = null
    private var plannedSource = BaritonePathSource.NONE
    private var completedAnchor: FlightRuntimePosition? = null
    private var suspended = false
    private var pendingWalkFallback = false
    private var replanPending = false
    private var arrivalPending = false
    private var runtimeDetail: String? = null
    private var lastPlayerPosition: FlightRuntimePosition? = null

    fun startTask(mode: BaritoneNavigationMode) {
        releaseLease()
        policy = createPolicy(config())
        session = policy.reduce(
            BaritoneNavigationSession(),
            BaritoneNavigationEvent.TaskStarted(mode),
        ).state
        taskActive = true
        clearRoute()
        userInputActive = false
        automationPauseActive = false
        suspended = false
        pendingWalkFallback = false
        replanPending = false
        arrivalPending = false
        runtimeDetail = null
        if (mode == BaritoneNavigationMode.FLY) preAcquireLease()
    }

    @Suppress("ReturnCount")
    fun tick(input: BaritoneFlightRuntimeInput): BaritoneFlightRuntimeResult {
        lastPlayerPosition = input.playerPosition
        if (!taskActive) return result()
        handleUserInput(input.userInput)?.let { return result(it) }
        if (input.userInput) return result()
        handleAutomationPause(input.paused)
        if (input.paused) return result(pauseNative = false)
        if (session.navigation.requestedMode == BaritoneNavigationMode.WALK) {
            if (
                input.path.anchors.isNotEmpty() &&
                session.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_PATH
            ) {
                reduce(BaritoneNavigationEvent.PathAvailable)
            }
            return result()
        }

        val alreadyWalking = session.navigation.phase == BaritoneNavigationPhase.WALK_FALLBACK
        consumeAutomaticEnd(input)?.let { return result(it) }
        if (!alreadyWalking && session.navigation.phase == BaritoneNavigationPhase.WALK_FALLBACK) return result()
        validateLease()?.let { return result(it) }
        handleWalkFallback(input)?.let { return result(it) }
        if (session.navigation.phase == BaritoneNavigationPhase.WALK_FALLBACK) return result()

        val goal = input.path.anchors.lastOrNull() ?: return result()
        if (completedAnchor == goal) return result()
        if (plannedGoal != null && plannedGoal != goal) prepareChangedGoal()
        if (session.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_PATH) {
            reduce(BaritoneNavigationEvent.PathAvailable)
        }

        ensureLease()?.let { return result(it) }
        handleReadiness()?.let { return result(it) }
        if (session.navigation.phase == BaritoneNavigationPhase.ARMING) return result(pauseNative = true)

        ensurePlan(input, goal)?.let { return result(it) }
        if (session.navigation.phase == BaritoneNavigationPhase.WALK_FALLBACK) return result()
        return followRoute(input.playerPosition, goal)
    }

    fun terminate() {
        if (taskActive) reduce(BaritoneNavigationEvent.TaskTerminated)
        releaseLease()
        clearRoute()
        session = BaritoneNavigationSession()
        taskActive = false
        userInputActive = false
        automationPauseActive = false
        suspended = false
        pendingWalkFallback = false
        replanPending = false
        arrivalPending = false
        pendingAcquireFailure = null
        runtimeDetail = null
        lastPlayerPosition = null
    }

    fun dimensionChanged() {
        if (!taskActive) return
        reduce(BaritoneNavigationEvent.DimensionChanged)
        releaseLease()
        clearRoute()
        userInputActive = false
        suspended = false
        automationPauseActive = false
        pendingWalkFallback = false
        replanPending = false
        arrivalPending = false
    }

    fun snapshot(): BaritoneNavigationSnapshot = runtimeDetail?.let { session.navigation.copy(detail = it) }
        ?: session.navigation

    fun route(): List<FlightRuntimePosition> = plannedRoute

    fun progress(): BaritoneProgress? = routeProgress()

    fun estimatedSeconds(): Long? = estimateSeconds()

    fun ownsNativeMovement(): Boolean = shouldPauseNativeMovement()

    private fun handleUserInput(userInput: Boolean): BaritoneFlightRuntimeSignal? {
        if (userInput == userInputActive) return null
        userInputActive = userInput
        val event = if (userInput) {
            BaritoneNavigationEvent.UserInputStarted
        } else {
            BaritoneNavigationEvent.UserInputEnded
        }
        return applyAction(reduce(event), currentPosition = null)
    }

    private fun handleAutomationPause(paused: Boolean) {
        if (paused == automationPauseActive) return
        automationPauseActive = paused
        if (paused) {
            lease?.let(fly::clearSteering)
        }
    }

    private fun validateLease(): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return null
        if (fly.validate(currentLease)) return null
        val action = reduce(BaritoneNavigationEvent.UserIntervention("Fly mode or state changed by the user"))
        return applyAction(action, currentPosition = null)
    }

    private fun consumeAutomaticEnd(input: BaritoneFlightRuntimeInput): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return null
        val detail = fly.automaticEnd(currentLease) ?: return null
        val landing = planner.safeLanding(input.playerPosition, fly.capabilities(currentLease))
        val action = reduce(BaritoneNavigationEvent.FlightEnded(detail, landing != null))
        return applyAction(action, input.playerPosition, landing)
    }

    private fun handleWalkFallback(input: BaritoneFlightRuntimeInput): BaritoneFlightRuntimeSignal? {
        if (session.navigation.phase != BaritoneNavigationPhase.WALK_FALLBACK) return null
        if (pendingWalkFallback) return followPendingLanding(input.playerPosition)
        if (input.completedWalkPathBlocks == 0) return null
        val action = reduce(BaritoneNavigationEvent.WalkProgress(input.completedWalkPathBlocks))
        return applyAction(action, input.playerPosition)
    }

    private fun ensureLease(): BaritoneFlightRuntimeSignal? {
        pendingAcquireFailure?.let { return failFlight(it) }
        lease?.let { currentLease ->
            if (session.navigation.flyMode == null) {
                reduce(BaritoneNavigationEvent.FlightLeaseAcquired(currentLease.modeName, currentLease.ownership))
            }
            return null
        }
        return when (val acquired = fly.acquire()) {
            is BaritoneFlyAcquireResult.Acquired -> {
                lease = acquired.lease
                publishLease(acquired.lease)
                reduce(BaritoneNavigationEvent.FlightLeaseAcquired(
                    acquired.lease.modeName,
                    acquired.lease.ownership,
                ))
                null
            }
            is BaritoneFlyAcquireResult.Rejected -> failFlight(acquired.detail)
        }
    }

    private fun preAcquireLease() {
        when (val acquired = fly.acquire()) {
            is BaritoneFlyAcquireResult.Acquired -> {
                lease = acquired.lease
                publishLease(acquired.lease)
            }
            is BaritoneFlyAcquireResult.Rejected -> pendingAcquireFailure = acquired.detail
        }
    }

    private fun handleReadiness(): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return failFlight("Fly lease is unavailable")
        return when (val readiness = fly.readiness(currentLease)) {
            BaritoneFlyReadiness.Ready -> {
                runtimeDetail = null
                if (session.navigation.phase == BaritoneNavigationPhase.ARMING) {
                    reduce(BaritoneNavigationEvent.FlightReady)
                }
                null
            }
            is BaritoneFlyReadiness.Arming -> {
                runtimeDetail = readiness.detail
                val action = reduce(BaritoneNavigationEvent.ArmingTick(active = true, paused = false))
                applyAction(action, currentPosition = null)
            }
            is BaritoneFlyReadiness.Unavailable -> {
                runtimeDetail = readiness.detail
                val action = reduce(BaritoneNavigationEvent.ArmingTick(active = true, paused = false))
                applyAction(action, currentPosition = null)
            }
        }
    }

    private fun ensurePlan(
        input: BaritoneFlightRuntimeInput,
        goal: FlightRuntimePosition,
    ): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return failFlight("Fly lease is unavailable")
        val capabilities = fly.capabilities(currentLease)
        if (plannedRoute.isNotEmpty() && currentSegmentSafe(input.playerPosition, capabilities)) return null
        clearRoute()
        val plan = planner.plan(RuntimeFlightPlanRequest(input.playerPosition, goal, input.path.source, capabilities))
        if (plan.status != RuntimeFlightPlanStatus.UNAVAILABLE) {
            plannedRoute = plan.route
            planStatus = plan.status
            plannedGoal = goal
            plannedSource = input.path.source
            routeIndex = 0
            replanPending = false
            return null
        }
        val detail = plan.detail ?: "No collision-safe aerial route is available"
        val action = reduce(BaritoneNavigationEvent.FlightRouteUnavailable(detail, plan.landingAnchor != null))
        return applyAction(action, input.playerPosition, plan.landingAnchor)
    }

    private fun currentSegmentSafe(
        playerPosition: FlightRuntimePosition,
        capabilities: BaritoneFlyCapabilities,
    ): Boolean {
        val target = nextRoutePoint(playerPosition) ?: return true
        return planner.isSegmentSafe(playerPosition, target, capabilities)
    }

    private fun followRoute(
        playerPosition: FlightRuntimePosition,
        goal: FlightRuntimePosition,
    ): BaritoneFlightRuntimeResult {
        val currentLease = lease ?: return result(BaritoneFlightRuntimeSignal.FailTask("Fly lease is unavailable"))
        val target = nextRoutePoint(playerPosition)
        if (target != null) {
            val direction = (target - playerPosition).normalized()
            fly.steer(currentLease, BaritoneFlySteering(direction))
            return result(pauseNative = true)
        }
        if (planStatus == RuntimeFlightPlanStatus.PARTIAL) {
            runtimeDetail = "Waiting for the next loaded flight corridor"
            replanPending = true
            clearRoute()
            return result(pauseNative = true)
        }

        if (plannedSource == BaritonePathSource.ELYTRA_DESTINATION) {
            fly.clearSteering(currentLease)
            arrivalPending = true
            clearRoute()
            return result(BaritoneFlightRuntimeSignal.Arrived, pauseNative = true)
        }

        fly.clearSteering(currentLease)
        fly.suspend(currentLease)
        suspended = true
        completedAnchor = goal
        replanPending = false
        runtimeDetail = "Waiting for Baritone to advance or interact at the flight anchor"
        clearRoute(keepGoal = true)
        return result(pauseNative = false)
    }

    private fun followPendingLanding(playerPosition: FlightRuntimePosition): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return failFlight("Fly lease ended before a safe landing")
        if (plannedRoute.isEmpty()) {
            val goal = plannedGoal ?: return failFlight("Fly lost its validated landing anchor")
            val plan = planner.plan(RuntimeFlightPlanRequest(
                start = playerPosition,
                goal = goal,
                source = BaritonePathSource.WALKING_PATH,
                capabilities = fly.capabilities(currentLease),
            ))
            if (plan.status == RuntimeFlightPlanStatus.UNAVAILABLE) {
                return failFlight("Fly cannot reach its validated landing anchor")
            }
            plannedRoute = plan.route
            planStatus = plan.status
            routeIndex = 0
        }
        val target = nextRoutePoint(playerPosition)
        if (target != null) {
            fly.steer(currentLease, BaritoneFlySteering((target - playerPosition).normalized()))
            return null
        }
        if (planStatus == RuntimeFlightPlanStatus.PARTIAL) {
            clearRoute(keepGoal = true)
            return null
        }
        fly.clearSteering(currentLease)
        fly.suspend(currentLease)
        suspended = true
        pendingWalkFallback = false
        clearRoute()
        return null
    }

    private fun prepareChangedGoal() {
        completedAnchor = null
        runtimeDetail = null
        clearRoute()
        if (!suspended) return
        val currentLease = lease ?: return
        if (fly.resume(currentLease)) suspended = false
    }

    private fun applyAction(
        action: BaritoneNavigationAction,
        currentPosition: FlightRuntimePosition?,
        landingAnchor: FlightRuntimePosition? = null,
    ): BaritoneFlightRuntimeSignal? = when (action) {
        BaritoneNavigationAction.None,
        BaritoneNavigationAction.PlanFlight,
        BaritoneNavigationAction.Replan -> null
        BaritoneNavigationAction.SuspendForUser -> {
            lease?.let(fly::clearSteering)
            null
        }
        BaritoneNavigationAction.ResumeAutomation -> {
            null
        }
        BaritoneNavigationAction.RestartFlight -> {
            restartFlight()
            null
        }
        BaritoneNavigationAction.UseWalk -> {
            beginWalkFallback(currentPosition, landingAnchor)
        }
        BaritoneNavigationAction.ReleaseFlight -> {
            releaseLease()
            null
        }
        is BaritoneNavigationAction.FailTask -> {
            runtimeDetail = runtimeDetail?.let { "$it; ${action.detail}" }
            releaseLease()
            clearRoute()
            taskActive = false
            BaritoneFlightRuntimeSignal.FailTask(action.detail)
        }
        is BaritoneNavigationAction.CancelTask -> {
            runtimeDetail = null
            releaseLease()
            clearRoute()
            taskActive = false
            BaritoneFlightRuntimeSignal.CancelTask(action.detail)
        }
    }

    private fun beginWalkFallback(
        currentPosition: FlightRuntimePosition?,
        landingAnchor: FlightRuntimePosition?,
    ): BaritoneFlightRuntimeSignal? {
        val currentLease = lease ?: return null
        if (
            currentPosition != null &&
            landingAnchor != null &&
            currentPosition.distanceTo(landingAnchor) > ARRIVAL_RADIUS
        ) {
            val landingPlan = planner.plan(RuntimeFlightPlanRequest(
                start = currentPosition,
                goal = landingAnchor,
                source = BaritonePathSource.WALKING_PATH,
                capabilities = fly.capabilities(currentLease),
            ))
            if (landingPlan.status == RuntimeFlightPlanStatus.UNAVAILABLE) {
                return failFlight("Fly cannot reach its validated landing anchor")
            }
            plannedRoute = landingPlan.route
            planStatus = landingPlan.status
            plannedGoal = landingAnchor
            plannedSource = BaritonePathSource.WALKING_PATH
            routeIndex = 0
            pendingWalkFallback = true
            replanPending = false
            return null
        }
        suspendLease()
        clearRoute()
        return null
    }

    private fun restartFlight() {
        clearRoute()
        completedAnchor = null
        pendingWalkFallback = false
        replanPending = false
        val currentLease = lease
        if (currentLease != null && fly.validate(currentLease) && fly.resume(currentLease)) {
            suspended = false
            publishLease(currentLease)
            reduce(BaritoneNavigationEvent.FlightLeaseAcquired(currentLease.modeName, currentLease.ownership))
            return
        }
        releaseLease()
    }

    private fun suspendLease() {
        val currentLease = lease ?: return
        fly.clearSteering(currentLease)
        if (fly.suspend(currentLease)) suspended = true
    }

    private fun resumeLease() {
        val currentLease = lease ?: return
        if (fly.resume(currentLease)) suspended = false
    }

    private fun failFlight(detail: String): BaritoneFlightRuntimeSignal? {
        val action = reduce(BaritoneNavigationEvent.FlightRouteUnavailable(detail, safeLandingAvailable = false))
        return applyAction(action, currentPosition = null)
    }

    private fun reduce(event: BaritoneNavigationEvent): BaritoneNavigationAction {
        val transition = policy.reduce(session, event)
        session = transition.state
        return transition.action
    }

    private fun nextRoutePoint(playerPosition: FlightRuntimePosition): FlightRuntimePosition? {
        val reachedIndex = (routeIndex until plannedRoute.size).lastOrNull { index ->
            playerPosition.distanceTo(plannedRoute[index]) <= ARRIVAL_RADIUS
        }
        if (reachedIndex != null) routeIndex = reachedIndex + 1
        return plannedRoute.getOrNull(routeIndex)
    }

    private fun shouldPauseNativeMovement(): Boolean {
        val automationMayMove = taskActive && !userInputActive && !automationPauseActive && !suspended
        val flightOwnsMovement = plannedRoute.isNotEmpty() ||
            session.navigation.phase == BaritoneNavigationPhase.ARMING ||
            pendingWalkFallback || replanPending || arrivalPending
        return automationMayMove && flightOwnsMovement
    }

    private fun result(
        signal: BaritoneFlightRuntimeSignal? = null,
        pauseNative: Boolean = shouldPauseNativeMovement(),
    ) = BaritoneFlightRuntimeResult(
        navigation = snapshot(),
        pauseNativeMovement = pauseNative,
        route = plannedRoute,
        progress = routeProgress(),
        etaSeconds = estimateSeconds(),
        signal = signal,
    )

    private fun routeProgress(): BaritoneProgress? {
        if (plannedRoute.isEmpty()) return null
        val clampedIndex = routeIndex.coerceIn(0, plannedRoute.lastIndex)
        val remainingAfterTarget = plannedRoute.drop(clampedIndex).zipWithNext().sumOf { (first, second) ->
            first.distanceTo(second)
        }
        val remainingToTarget = lastPlayerPosition?.distanceTo(plannedRoute[clampedIndex]) ?: 0.0
        val remaining = remainingToTarget + remainingAfterTarget
        val total = plannedRoute.zipWithNext().sumOf { (first, second) -> first.distanceTo(second) }
        val fraction = if (total <= MIN_ROUTE_LENGTH) 1.0 else (1.0 - remaining / total).coerceIn(0.0, 1.0)
        return BaritoneProgress(fraction, remaining)
    }

    private fun estimateSeconds(): Long? {
        val currentLease = lease ?: return null
        val speed = fly.capabilities(currentLease).reliableSpeed ?: return null
        val remaining = routeProgress()?.distanceRemaining ?: return null
        return ceil(remaining / speed / TICKS_PER_SECOND).toLong()
    }

    private fun releaseLease() {
        val currentLease = lease ?: run {
            BaritoneFlightLeaseRegistry.clear()
            return
        }
        fly.clearSteering(currentLease)
        fly.release(currentLease)
        BaritoneFlightLeaseRegistry.clear(currentLease)
        lease = null
        pendingAcquireFailure = null
        suspended = false
    }

    private fun publishLease(currentLease: BaritoneFlyLease) {
        BaritoneFlightLeaseRegistry.publish(currentLease) { fly.validate(currentLease) }
    }

    private fun clearRoute(keepGoal: Boolean = false) {
        plannedRoute = emptyList()
        routeIndex = 0
        planStatus = null
        plannedSource = BaritonePathSource.NONE
        if (!keepGoal) plannedGoal = null
    }

    private companion object {
        const val ARRIVAL_RADIUS = 0.65
        const val TICKS_PER_SECOND = 20.0
        const val MIN_ROUTE_LENGTH = 1.0e-9

        fun createPolicy(config: BaritoneFlightRuntimeConfig) = BaritoneNavigationSessionPolicy(
            BaritoneNavigationPolicyConfig(
                armTimeoutTicks = config.armTimeoutTicks,
                maxRestarts = config.maxRestarts,
                retryDistanceBlocks = config.retryDistanceBlocks,
            ),
        )
    }
}
