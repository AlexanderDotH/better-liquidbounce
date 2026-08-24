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

/** Reasons which can temporarily transfer movement ownership away from Baritone. */
enum class BaritonePauseReason(internal val precedence: Int) {
    MANUAL(800),
    USER_INPUT(700),
    MOVEMENT_OWNER(600),
    REMOTE_MOVEMENT_OWNER(550),
    ROTATION_OWNER(500),
    HOTBAR_OWNER(400),
    INVENTORY_OWNER(300),
    AUTOMATION_CONFLICT(200),
}

data class BaritonePauseCause(
    val reason: BaritonePauseReason,
    val owner: String? = null,
) {
    init {
        require(owner == null || owner.isNotBlank()) { "Pause owners cannot be blank" }
    }
}

data class BaritonePauseState(
    val cause: BaritonePauseCause? = null,
    val quietTicksRemaining: Int = 0,
) {
    val paused: Boolean
        get() = cause != null

    init {
        require(quietTicksRemaining >= 0) { "Remaining quiet ticks cannot be negative" }
    }
}

/**
 * Deterministic pause policy shared by module and adapter layers.
 *
 * Manual pause always wins and persists until [resumeManually]. Automatic causes use stable precedence and are held
 * for [resumeDelayTicks] quiet ticks, preventing movement ownership from oscillating between systems.
 */
class BaritonePauseController(
    private val resumeDelayTicks: () -> Int,
) {
    private var state = ControllerState()

    constructor(resumeDelayTicks: Int = DEFAULT_RESUME_DELAY_TICKS) : this({
        require(resumeDelayTicks >= 0) { "Resume delay cannot be negative" }
        resumeDelayTicks
    })

    @Synchronized
    fun tick(activeCauses: Collection<BaritonePauseCause>): BaritonePauseState {
        require(activeCauses.none { it.reason == BaritonePauseReason.MANUAL }) {
            "Manual pause is controlled through pauseManually()"
        }

        val highestCause = activeCauses.highestPrecedenceCause()
        val configuredDelay = configuredResumeDelay()
        state = when {
            highestCause != null -> state.copy(automaticCause = highestCause, quietTicks = 0)
            state.automaticCause == null -> state
            state.quietTicks + 1 >= configuredDelay -> state.copy(automaticCause = null, quietTicks = 0)
            else -> state.copy(quietTicks = state.quietTicks + 1)
        }
        return visibleState()
    }

    @Synchronized
    fun pauseManually(): BaritonePauseState {
        state = state.copy(manuallyPaused = true)
        return visibleState()
    }

    @Synchronized
    fun resumeManually(): BaritonePauseState {
        state = state.copy(manuallyPaused = false)
        return visibleState()
    }

    @Synchronized
    fun current(): BaritonePauseState = visibleState()

    @Synchronized
    fun reset(): BaritonePauseState {
        state = ControllerState()
        return visibleState()
    }

    @Synchronized
    fun resetAutomatic(): BaritonePauseState {
        state = state.copy(automaticCause = null, quietTicks = 0)
        return visibleState()
    }

    private fun visibleState(): BaritonePauseState {
        if (state.manuallyPaused) {
            return BaritonePauseState(BaritonePauseCause(BaritonePauseReason.MANUAL))
        }

        val cause = state.automaticCause ?: return BaritonePauseState()
        return BaritonePauseState(cause, (configuredResumeDelay() - state.quietTicks).coerceAtLeast(0))
    }

    private fun configuredResumeDelay(): Int = resumeDelayTicks().also {
        require(it >= 0) { "Resume delay cannot be negative" }
    }

    private data class ControllerState(
        val manuallyPaused: Boolean = false,
        val automaticCause: BaritonePauseCause? = null,
        val quietTicks: Int = 0,
    )

    companion object {
        const val DEFAULT_RESUME_DELAY_TICKS = 10
    }
}

private fun Collection<BaritonePauseCause>.highestPrecedenceCause(): BaritonePauseCause? =
    maxWithOrNull(compareBy<BaritonePauseCause> { it.reason.precedence }.thenBy { it.owner.orEmpty() })
