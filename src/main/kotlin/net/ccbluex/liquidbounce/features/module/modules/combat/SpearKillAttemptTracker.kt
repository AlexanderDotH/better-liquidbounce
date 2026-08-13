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

package net.ccbluex.liquidbounce.features.module.modules.combat

/** Immutable launch metadata supplied by the SpearKill integration boundary. */
internal data class SpearKillAttemptPlan(
    val targetIdentity: String,
    val targetName: String,
    val targetSource: String,
    val plannedRouteMode: String,
    val plannedOutboundStepCount: Int,
    val predictedHitTick: Int,
    val chargeTicks: Int,
    val terminalAuthorizationRequired: Boolean,
) {
    init {
        require(targetIdentity.isNotBlank()) { "Target identity must not be blank" }
        require(targetName.isNotBlank()) { "Target name must not be blank" }
        require(targetSource.isNotBlank()) { "Target source must not be blank" }
        require(plannedRouteMode.isNotBlank()) { "Planned route mode must not be blank" }
        require(plannedOutboundStepCount >= 0) { "Planned outbound step count must not be negative" }
        require(predictedHitTick >= 0) { "Predicted hit tick must not be negative" }
        require(chargeTicks >= 0) { "Charge ticks must not be negative" }
    }
}

/** A completed attempt is never reported as a miss without direct evidence. */
internal enum class SpearKillAttemptOutcome {
    DAMAGE_CONFIRMED,
    DEFEATED,
    BLOCKED,
    ABORTED,
    UNCONFIRMED,
}

/** Immutable state that can be rendered directly by a future debug surface. */
internal data class SpearKillAttemptSnapshot(
    val attemptId: Long,
    val targetIdentity: String,
    val targetName: String,
    val targetSource: String,
    val plannedRouteMode: String,
    val plannedOutboundStepCount: Int,
    val outboundStepCount: Int,
    val predictedHitTick: Int,
    val chargeTicks: Int,
    val terminalAuthorizationRequired: Boolean,
    val terminalAuthorized: Boolean,
    val terminalAuthorizationTick: Int?,
    val setback: Boolean,
    val blocked: Boolean,
    val recovery: Boolean,
    val defeated: Boolean,
    val targetRemoved: Boolean,
    val damageEvidence: Boolean,
    val outcome: SpearKillAttemptOutcome? = null,
    val abortReason: String? = null,
)

/**
 * Captures one route attempt without depending on entities, packets, or event classes.
 *
 * Callers retain [current] while an attempt is active and [lastCompleted] for diagnostics after it
 * finishes. Signals are intentionally independent from final classification so an unverified route
 * completion remains [SpearKillAttemptOutcome.UNCONFIRMED], never a guessed miss.
 */
@Suppress("TooManyFunctions")
internal class SpearKillAttemptTracker {

    private var nextAttemptId = 1L

    var current: SpearKillAttemptSnapshot? = null
        private set

    var lastCompleted: SpearKillAttemptSnapshot? = null
        private set

    fun begin(plan: SpearKillAttemptPlan): SpearKillAttemptSnapshot {
        abort("superseded")

        return SpearKillAttemptSnapshot(
            attemptId = nextAttemptId++,
            targetIdentity = plan.targetIdentity,
            targetName = plan.targetName,
            targetSource = plan.targetSource,
            plannedRouteMode = plan.plannedRouteMode,
            plannedOutboundStepCount = plan.plannedOutboundStepCount,
            outboundStepCount = 0,
            predictedHitTick = plan.predictedHitTick,
            chargeTicks = plan.chargeTicks,
            terminalAuthorizationRequired = plan.terminalAuthorizationRequired,
            terminalAuthorized = false,
            terminalAuthorizationTick = null,
            setback = false,
            blocked = false,
            recovery = false,
            defeated = false,
            targetRemoved = false,
            damageEvidence = false,
        ).also { current = it }
    }

    fun recordOutboundStep(): SpearKillAttemptSnapshot? = update { attempt ->
        attempt.copy(outboundStepCount = attempt.outboundStepCount + 1)
    }

    fun recordChargeTicks(chargeTicks: Int): SpearKillAttemptSnapshot? {
        require(chargeTicks >= 0) { "Charge ticks must not be negative" }
        return update { attempt -> attempt.copy(chargeTicks = chargeTicks) }
    }

    fun authorizeTerminal(tick: Int? = null): SpearKillAttemptSnapshot? = update { attempt ->
        attempt.copy(terminalAuthorized = true, terminalAuthorizationTick = tick)
    }

    fun markSetback(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(setback = true) }

    fun markBlocked(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(blocked = true) }

    fun markRecovery(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(recovery = true) }

    fun markDefeated(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(defeated = true) }

    fun markTargetRemoved(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(targetRemoved = true) }

    fun markDamageEvidence(): SpearKillAttemptSnapshot? = update { attempt -> attempt.copy(damageEvidence = true) }

    fun complete(): SpearKillAttemptSnapshot? = finishAttempt(
        outcomeFor = { attempt -> observedOutcome(attempt) ?: SpearKillAttemptOutcome.UNCONFIRMED },
    )

    fun abort(reason: String = "aborted"): SpearKillAttemptSnapshot? {
        require(reason.isNotBlank()) { "Abort reason must not be blank" }
        return finishAttempt(
            outcomeFor = { attempt -> observedOutcome(attempt) ?: SpearKillAttemptOutcome.ABORTED },
            abortReason = reason,
        )
    }

    fun reset() {
        current = null
        lastCompleted = null
    }

    private fun update(
        transform: (SpearKillAttemptSnapshot) -> SpearKillAttemptSnapshot,
    ): SpearKillAttemptSnapshot? {
        val activeAttempt = current ?: return null
        return transform(activeAttempt).also { current = it }
    }

    private fun finishAttempt(
        outcomeFor: (SpearKillAttemptSnapshot) -> SpearKillAttemptOutcome,
        abortReason: String? = null,
    ): SpearKillAttemptSnapshot? {
        val activeAttempt = current ?: return null
        val completedAttempt = activeAttempt.copy(
            outcome = outcomeFor(activeAttempt),
            abortReason = abortReason,
        )
        current = null
        lastCompleted = completedAttempt
        return completedAttempt
    }

    private fun observedOutcome(attempt: SpearKillAttemptSnapshot): SpearKillAttemptOutcome? = when {
        attempt.defeated -> SpearKillAttemptOutcome.DEFEATED
        attempt.targetRemoved -> SpearKillAttemptOutcome.ABORTED
        attempt.damageEvidence -> SpearKillAttemptOutcome.DAMAGE_CONFIRMED
        attempt.blocked -> SpearKillAttemptOutcome.BLOCKED
        else -> null
    }
}
