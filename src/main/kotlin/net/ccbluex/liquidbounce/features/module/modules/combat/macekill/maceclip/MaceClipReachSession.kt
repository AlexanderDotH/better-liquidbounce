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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.minecraft.world.phys.Vec3

internal enum class MaceClipReachSessionOutcome {
    ACTIVE,
    COMPLETED,
    CORRECTED,
    TIMED_OUT,
    TARGET_LOST,
    REPLAN_REJECTED,
}

internal enum class MaceClipReachReplanBlockReason {
    SESSION_TERMINAL,
    STRIKE_COMMITTED,
    TERMINAL_CONFIRMED,
    CONFIRMED_PREFIX_INCOMPATIBLE,
    PLAN_BLOCKED,
}

internal sealed interface MaceClipReachReplanResult {
    data class Applied(val plan: MaceClipReachPlan) : MaceClipReachReplanResult

    data class Rejected(
        val reason: MaceClipReachReplanBlockReason,
        val planBlockReason: MaceClipReachBlockReason? = null,
    ) : MaceClipReachReplanResult
}

/** Clip-specific policy state; packet delivery and exact recovery stay in RemoteKillRouteSession. */
internal class MaceClipReachSession(
    initialPlan: MaceClipReachPlan,
    private val startedAtTick: Long,
) {
    var plan: MaceClipReachPlan = initialPlan
        private set

    var outcome: MaceClipReachSessionOutcome = MaceClipReachSessionOutcome.ACTIVE
        private set

    var strikeCommitted: Boolean = false
        private set

    var confirmedOutboundMovementCount: Int = 0
        private set

    fun evaluate(nowTick: Long, targetAlive: Boolean): MaceClipReachSessionOutcome {
        if (outcome != MaceClipReachSessionOutcome.ACTIVE) return outcome
        if (isTimedOut(nowTick)) return finish(MaceClipReachSessionOutcome.TIMED_OUT)
        if (!targetAlive && !strikeCommitted) return finish(MaceClipReachSessionOutcome.TARGET_LOST)
        return outcome
    }

    fun commitStrike(nowTick: Long, targetAlive: Boolean): Boolean {
        if (strikeCommitted) return false
        if (evaluate(nowTick, targetAlive) != MaceClipReachSessionOutcome.ACTIVE) return false
        strikeCommitted = true
        return true
    }

    fun recordOutboundMovementConfirmed(): Boolean {
        if (outcome != MaceClipReachSessionOutcome.ACTIVE || strikeCommitted) return false
        if (confirmedOutboundMovementCount >= plan.outboundMovements.size) return false
        confirmedOutboundMovementCount++
        return true
    }

    fun recordCorrection(): MaceClipReachSessionOutcome {
        if (outcome == MaceClipReachSessionOutcome.ACTIVE) finish(MaceClipReachSessionOutcome.CORRECTED)
        return outcome
    }

    fun recordReplanRejected(): MaceClipReachSessionOutcome {
        if (outcome == MaceClipReachSessionOutcome.ACTIVE) finish(MaceClipReachSessionOutcome.REPLAN_REJECTED)
        return outcome
    }

    fun complete(): MaceClipReachSessionOutcome {
        if (outcome == MaceClipReachSessionOutcome.ACTIVE) finish(MaceClipReachSessionOutcome.COMPLETED)
        return outcome
    }

    fun replanTerminal(
        endpoint: Vec3,
        anchorValidator: MaceClipReachAnchorValidator,
    ): MaceClipReachReplanResult {
        if (outcome != MaceClipReachSessionOutcome.ACTIVE) {
            return MaceClipReachReplanResult.Rejected(MaceClipReachReplanBlockReason.SESSION_TERMINAL)
        }
        if (strikeCommitted) {
            return MaceClipReachReplanResult.Rejected(MaceClipReachReplanBlockReason.STRIKE_COMMITTED)
        }
        if (confirmedOutboundMovementCount >= plan.outboundMovements.size) {
            finish(MaceClipReachSessionOutcome.REPLAN_REJECTED)
            return MaceClipReachReplanResult.Rejected(MaceClipReachReplanBlockReason.TERMINAL_CONFIRMED)
        }

        val result = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = plan.origin,
                endpoint = endpoint,
                dimensionBounds = plan.dimensionBounds,
                profile = plan.profile,
                use = plan.use,
                anchorValidator = anchorValidator,
            ),
        )
        return applyReplanResult(result)
    }

    private fun applyReplanResult(result: MaceClipReachPlanResult): MaceClipReachReplanResult = when (result) {
        is MaceClipReachPlanResult.Ready -> {
            if (confirmedOutboundMovementCount >= result.plan.outboundMovements.size) {
                finish(MaceClipReachSessionOutcome.REPLAN_REJECTED)
                MaceClipReachReplanResult.Rejected(
                    MaceClipReachReplanBlockReason.CONFIRMED_PREFIX_INCOMPATIBLE,
                )
            } else {
                plan = MaceClipReachPlanner.preserveConfirmedPrefix(
                    previous = plan,
                    candidate = result.plan,
                    confirmedMovementCount = confirmedOutboundMovementCount,
                )
                MaceClipReachReplanResult.Applied(plan)
            }
        }
        is MaceClipReachPlanResult.Blocked -> {
            finish(MaceClipReachSessionOutcome.REPLAN_REJECTED)
            MaceClipReachReplanResult.Rejected(
                MaceClipReachReplanBlockReason.PLAN_BLOCKED,
                result.reason,
            )
        }
    }

    private fun isTimedOut(nowTick: Long): Boolean {
        if (nowTick < startedAtTick) return true
        return nowTick - startedAtTick >= plan.profile.parameters.timeoutTicks.toLong()
    }

    private fun finish(nextOutcome: MaceClipReachSessionOutcome): MaceClipReachSessionOutcome {
        outcome = nextOutcome
        return outcome
    }
}
