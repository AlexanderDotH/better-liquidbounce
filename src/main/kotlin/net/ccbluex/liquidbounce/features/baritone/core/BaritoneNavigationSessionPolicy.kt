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

package net.ccbluex.liquidbounce.features.baritone.core

data class BaritoneNavigationPolicyConfig(
    val armTimeoutTicks: Int = DEFAULT_ARM_TIMEOUT_TICKS,
    val maxRestarts: Int = BaritoneNavigationSnapshot.DEFAULT_MAX_RESTARTS,
    val retryDistanceBlocks: Int = DEFAULT_RETRY_DISTANCE_BLOCKS,
) {
    init {
        require(armTimeoutTicks > 0) { "Fly arm timeout must be positive" }
        require(maxRestarts >= 0) { "Maximum Fly restarts cannot be negative" }
        require(retryDistanceBlocks > 0) { "Fly retry distance must be positive" }
    }

    companion object {
        const val DEFAULT_ARM_TIMEOUT_TICKS = 200
        const val DEFAULT_RETRY_DISTANCE_BLOCKS = 32
    }
}

@Suppress("LongParameterList")
data class BaritoneNavigationSession(
    val navigation: BaritoneNavigationSnapshot = BaritoneNavigationSnapshot(),
    val armingActiveTicks: Int = 0,
    val walkedPathBlocks: Int = 0,
    val taskActive: Boolean = false,
    internal val resumeNavigation: BaritoneNavigationSnapshot? = null,
    internal val retryFlyMode: String? = null,
    internal val retryFlyOwnership: BaritoneFlyOwnership? = null,
    internal val retryAllowed: Boolean = true,
) {
    init {
        require(armingActiveTicks >= 0) { "Active arming ticks cannot be negative" }
        require(walkedPathBlocks >= 0) { "Walk progress cannot be negative" }
        require((retryFlyMode == null) == (retryFlyOwnership == null)) {
            "Retry Fly mode and ownership must be present together"
        }
    }
}

sealed interface BaritoneNavigationEvent {
    data class TaskStarted(val requestedMode: BaritoneNavigationMode) : BaritoneNavigationEvent
    data object PathAvailable : BaritoneNavigationEvent

    data class FlightLeaseAcquired(
        val flyMode: String,
        val ownership: BaritoneFlyOwnership,
    ) : BaritoneNavigationEvent {
        init {
            require(flyMode.isNotBlank()) { "Fly mode cannot be blank" }
        }
    }

    data class ArmingTick(
        val active: Boolean,
        val paused: Boolean,
    ) : BaritoneNavigationEvent

    data object FlightReady : BaritoneNavigationEvent

    data class FlightEnded(
        val detail: String,
        val safeLandingAvailable: Boolean,
    ) : BaritoneNavigationEvent {
        init {
            require(detail.isNotBlank()) { "Flight ending detail cannot be blank" }
        }
    }

    data class FlightRouteUnavailable(
        val detail: String,
        val safeLandingAvailable: Boolean,
    ) : BaritoneNavigationEvent {
        init {
            require(detail.isNotBlank()) { "Flight route failure detail cannot be blank" }
        }
    }

    data class WalkProgress(val completedPathBlocks: Int) : BaritoneNavigationEvent {
        init {
            require(completedPathBlocks >= 0) { "Completed path blocks cannot be negative" }
        }
    }

    data object UserInputStarted : BaritoneNavigationEvent
    data object UserInputEnded : BaritoneNavigationEvent

    data class UserIntervention(val detail: String) : BaritoneNavigationEvent {
        init {
            require(detail.isNotBlank()) { "User intervention detail cannot be blank" }
        }
    }

    data object TaskTerminated : BaritoneNavigationEvent
    data object DimensionChanged : BaritoneNavigationEvent
}

sealed interface BaritoneNavigationAction {
    data object None : BaritoneNavigationAction
    data object PlanFlight : BaritoneNavigationAction
    data object RestartFlight : BaritoneNavigationAction
    data object UseWalk : BaritoneNavigationAction
    data object SuspendForUser : BaritoneNavigationAction
    data object ResumeAutomation : BaritoneNavigationAction
    data object Replan : BaritoneNavigationAction
    data object ReleaseFlight : BaritoneNavigationAction
    data class FailTask(val detail: String) : BaritoneNavigationAction
    data class CancelTask(val detail: String) : BaritoneNavigationAction
}

data class BaritoneNavigationTransition(
    val state: BaritoneNavigationSession,
    val action: BaritoneNavigationAction = BaritoneNavigationAction.None,
)

/** Pure navigation session reducer. Runtime integrations execute the returned action after committing [state]. */
@Suppress("TooManyFunctions")
class BaritoneNavigationSessionPolicy(
    private val config: BaritoneNavigationPolicyConfig = BaritoneNavigationPolicyConfig(),
) {

    fun reduce(
        state: BaritoneNavigationSession,
        event: BaritoneNavigationEvent,
    ): BaritoneNavigationTransition = when (event) {
        is BaritoneNavigationEvent.TaskStarted -> start(event.requestedMode)
        BaritoneNavigationEvent.PathAvailable -> pathAvailable(state)
        is BaritoneNavigationEvent.FlightLeaseAcquired -> acquireFlight(state, event)
        is BaritoneNavigationEvent.ArmingTick -> arm(state, event)
        BaritoneNavigationEvent.FlightReady -> flightReady(state)
        is BaritoneNavigationEvent.FlightEnded -> flightEnded(state, event)
        is BaritoneNavigationEvent.FlightRouteUnavailable -> routeUnavailable(state, event)
        is BaritoneNavigationEvent.WalkProgress -> walk(state, event.completedPathBlocks)
        BaritoneNavigationEvent.UserInputStarted -> waitForUser(state)
        BaritoneNavigationEvent.UserInputEnded -> resumeAfterUser(state)
        is BaritoneNavigationEvent.UserIntervention -> cancelForUser(state, event.detail)
        BaritoneNavigationEvent.TaskTerminated -> terminate(state)
        BaritoneNavigationEvent.DimensionChanged -> dimensionChanged(state)
    }

    private fun start(requestedMode: BaritoneNavigationMode): BaritoneNavigationTransition = transition(
        BaritoneNavigationSession(
            navigation = BaritoneNavigationSnapshot(
                requestedMode = requestedMode,
                phase = BaritoneNavigationPhase.WAITING_FOR_PATH,
                restartsRemaining = config.maxRestarts,
            ),
            taskActive = true,
        ),
    )

    private fun pathAvailable(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        if (!state.taskActive) return transition(state)
        if (state.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_USER) return transition(state)
        if (state.navigation.requestedMode == BaritoneNavigationMode.WALK) {
            return transition(
                state.copy(
                    navigation = state.navigation.copy(
                        activeMode = BaritoneNavigationMode.WALK,
                        phase = BaritoneNavigationPhase.IDLE,
                    ),
                ),
                BaritoneNavigationAction.UseWalk,
            )
        }

        return transition(
            state.copy(
                navigation = state.navigation.copy(
                    activeMode = BaritoneNavigationMode.FLY,
                    phase = BaritoneNavigationPhase.PLANNING,
                    detail = null,
                ),
            ),
            BaritoneNavigationAction.PlanFlight,
        )
    }

    private fun acquireFlight(
        state: BaritoneNavigationSession,
        event: BaritoneNavigationEvent.FlightLeaseAcquired,
    ): BaritoneNavigationTransition {
        if (!state.taskActive || state.navigation.requestedMode != BaritoneNavigationMode.FLY) {
            return transition(state)
        }
        if (state.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_USER) return transition(state)

        val navigation = state.navigation.copy(
            activeMode = BaritoneNavigationMode.FLY,
            phase = BaritoneNavigationPhase.ARMING,
            flyMode = event.flyMode,
            flyOwnership = event.ownership,
            detail = null,
        )
        return transition(
            state.copy(
                navigation = navigation,
                armingActiveTicks = 0,
                retryFlyMode = event.flyMode,
                retryFlyOwnership = event.ownership,
                retryAllowed = true,
            ),
        )
    }

    private fun arm(
        state: BaritoneNavigationSession,
        event: BaritoneNavigationEvent.ArmingTick,
    ): BaritoneNavigationTransition {
        if (state.navigation.phase != BaritoneNavigationPhase.ARMING || !event.active || event.paused) {
            return transition(state)
        }

        val activeTicks = state.armingActiveTicks + 1
        if (activeTicks < config.armTimeoutTicks) {
            return transition(state.copy(armingActiveTicks = activeTicks))
        }

        val detail = "Fly mode did not become ready within ${config.armTimeoutTicks} active ticks"
        return fail(state, detail)
    }

    private fun flightReady(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        if (state.navigation.phase != BaritoneNavigationPhase.ARMING) return transition(state)
        return transition(
            state.copy(
                navigation = state.navigation.copy(
                    phase = BaritoneNavigationPhase.FLYING,
                    detail = null,
                ),
                armingActiveTicks = 0,
            ),
        )
    }

    private fun flightEnded(
        state: BaritoneNavigationSession,
        event: BaritoneNavigationEvent.FlightEnded,
    ): BaritoneNavigationTransition {
        if (!state.isInFlight()) return transition(state)
        if (!event.safeLandingAvailable) return fail(state, event.detail)
        if (state.retryFlyOwnership == BaritoneFlyOwnership.USER) {
            return fallback(state, event.detail, allowRetry = false)
        }
        if (state.navigation.restartsRemaining == 0) {
            return fallback(state, event.detail, allowRetry = false)
        }
        return restart(state, event.detail)
    }

    private fun routeUnavailable(
        state: BaritoneNavigationSession,
        event: BaritoneNavigationEvent.FlightRouteUnavailable,
    ): BaritoneNavigationTransition {
        if (!state.taskActive) return transition(state)
        if (state.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_USER) return transition(state)
        if (!event.safeLandingAvailable) return fail(state, event.detail)
        return fallback(state, event.detail, allowRetry = state.navigation.restartsRemaining > 0)
    }

    private fun walk(state: BaritoneNavigationSession, completedBlocks: Int): BaritoneNavigationTransition {
        if (state.navigation.phase != BaritoneNavigationPhase.WALK_FALLBACK) return transition(state)
        if (!state.retryAllowed || state.navigation.restartsRemaining == 0) return transition(state)

        val walkedBlocks = state.walkedPathBlocks + completedBlocks
        if (walkedBlocks < config.retryDistanceBlocks) {
            return transition(state.copy(walkedPathBlocks = walkedBlocks))
        }
        return retryAfterWalking(state)
    }

    private fun retryAfterWalking(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        val flyMode = state.retryFlyMode
        val ownership = state.retryFlyOwnership
        val navigation = if (flyMode == null || ownership == null) {
            state.navigation.copy(
                activeMode = BaritoneNavigationMode.FLY,
                phase = BaritoneNavigationPhase.PLANNING,
                detail = "Retrying Fly after ${config.retryDistanceBlocks} walked path blocks",
                restartsRemaining = state.navigation.restartsRemaining - 1,
            )
        } else {
            state.navigation.copy(
                activeMode = BaritoneNavigationMode.FLY,
                phase = BaritoneNavigationPhase.ARMING,
                flyMode = flyMode,
                flyOwnership = ownership,
                detail = "Retrying Fly after ${config.retryDistanceBlocks} walked path blocks",
                restartsRemaining = state.navigation.restartsRemaining - 1,
            )
        }
        return transition(
            state.copy(navigation = navigation, armingActiveTicks = 0, walkedPathBlocks = 0),
            BaritoneNavigationAction.RestartFlight,
        )
    }

    private fun waitForUser(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        if (!state.taskActive || state.navigation.phase == BaritoneNavigationPhase.WAITING_FOR_USER) {
            return transition(state)
        }
        return transition(
            state.copy(
                navigation = state.navigation.copy(
                    activeMode = null,
                    phase = BaritoneNavigationPhase.WAITING_FOR_USER,
                    flyMode = null,
                    flyOwnership = null,
                    detail = "Waiting for user input to become quiet",
                ),
                resumeNavigation = state.navigation,
            ),
            BaritoneNavigationAction.SuspendForUser,
        )
    }

    private fun resumeAfterUser(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        val resumeNavigation = state.resumeNavigation ?: return transition(state)
        return transition(
            state.copy(navigation = resumeNavigation, resumeNavigation = null),
            BaritoneNavigationAction.ResumeAutomation,
        )
    }

    private fun cancelForUser(state: BaritoneNavigationSession, detail: String): BaritoneNavigationTransition =
        transition(finishedState(state, detail), BaritoneNavigationAction.CancelTask(detail))

    private fun terminate(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        val action = if (state.retryFlyMode == null) {
            BaritoneNavigationAction.None
        } else {
            BaritoneNavigationAction.ReleaseFlight
        }
        return transition(
            BaritoneNavigationSession(
                navigation = BaritoneNavigationSnapshot(
                    requestedMode = state.navigation.requestedMode,
                    restartsRemaining = config.maxRestarts,
                ),
            ),
            action,
        )
    }

    private fun dimensionChanged(state: BaritoneNavigationSession): BaritoneNavigationTransition {
        if (!state.taskActive) return transition(state)
        return transition(
            state.copy(
                navigation = BaritoneNavigationSnapshot(
                    requestedMode = state.navigation.requestedMode,
                    phase = BaritoneNavigationPhase.WAITING_FOR_PATH,
                    restartsRemaining = state.navigation.restartsRemaining,
                ),
                armingActiveTicks = 0,
                walkedPathBlocks = 0,
                resumeNavigation = null,
            ),
            BaritoneNavigationAction.Replan,
        )
    }

    private fun restart(state: BaritoneNavigationSession, detail: String): BaritoneNavigationTransition {
        val flyMode = requireNotNull(state.retryFlyMode)
        val ownership = requireNotNull(state.retryFlyOwnership)
        return transition(
            state.copy(
                navigation = state.navigation.copy(
                    activeMode = BaritoneNavigationMode.FLY,
                    phase = BaritoneNavigationPhase.ARMING,
                    flyMode = flyMode,
                    flyOwnership = ownership,
                    detail = detail,
                    restartsRemaining = state.navigation.restartsRemaining - 1,
                ),
                armingActiveTicks = 0,
            ),
            BaritoneNavigationAction.RestartFlight,
        )
    }

    private fun fallback(
        state: BaritoneNavigationSession,
        detail: String,
        allowRetry: Boolean,
    ): BaritoneNavigationTransition = transition(
        state.copy(
            navigation = state.navigation.copy(
                activeMode = BaritoneNavigationMode.WALK,
                phase = BaritoneNavigationPhase.WALK_FALLBACK,
                flyMode = null,
                flyOwnership = null,
                detail = detail,
            ),
            armingActiveTicks = 0,
            walkedPathBlocks = 0,
            retryAllowed = allowRetry,
        ),
        BaritoneNavigationAction.UseWalk,
    )

    private fun fail(state: BaritoneNavigationSession, detail: String): BaritoneNavigationTransition =
        transition(finishedState(state, detail), BaritoneNavigationAction.FailTask(detail))

    private fun finishedState(state: BaritoneNavigationSession, detail: String) = BaritoneNavigationSession(
        navigation = BaritoneNavigationSnapshot(
            requestedMode = state.navigation.requestedMode,
            detail = detail,
            restartsRemaining = state.navigation.restartsRemaining,
        ),
    )

    private fun BaritoneNavigationSession.isInFlight() =
        navigation.phase == BaritoneNavigationPhase.ARMING || navigation.phase == BaritoneNavigationPhase.FLYING

    private fun transition(
        state: BaritoneNavigationSession,
        action: BaritoneNavigationAction = BaritoneNavigationAction.None,
    ) = BaritoneNavigationTransition(state, action)
}
