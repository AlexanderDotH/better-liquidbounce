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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill


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
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillInstantRuntimeTest {

    @Test
    fun `zero delay batches the bounded clip while configured pacing emits one phase per tick`() {
        assertEquals(128, maceKillInstantPacketsPerTick(stepDelayTicks = 0, packetBudget = 128))
        assertEquals(1, maceKillInstantPacketsPerTick(stepDelayTicks = 1, packetBudget = 128))
        assertEquals(1, maceKillInstantPacketsPerTick(stepDelayTicks = 4, packetBudget = 6))
    }

    @Test
    fun `aborted clip segments a confirmed vertical inverse without changing its destination`() {
        val rawRecovery = listOf(
            Vec3(0.0, -99.0, 0.0),
            Vec3(7.0, 0.0, 0.0),
        )

        val safeRecovery = maceKillSafeClipRecoveryMovements(rawRecovery)

        assertEquals(34, safeRecovery.size)
        assertTrue(safeRecovery.take(33).all { it.y == -3.0 })
        assertEquals(rawRecovery.fold(Vec3.ZERO, Vec3::add), safeRecovery.fold(Vec3.ZERO, Vec3::add))
    }

    @Test
    fun `experimental Instant carries its concrete ClipReach plan and exact inverse`() {
        val clipPlan = readyExperimentalPlan()

        val route = maceKillInstantPlannedRoute(clipPlan, stepWaitTicks = 3)

        assertSame(clipPlan, route.clipReachPlan)
        assertEquals(clipPlan.outboundMovements, route.request.outboundMovements)
        assertEquals(clipPlan.returnMovements, route.request.returnMovements)
        assertEquals(clipPlan.profile.parameters.primingPacketCount, route.primingPackets)
        assertEquals(clipPlan.profile.parameters.primingPacketCount, route.returnPrimingPackets)
        assertEquals(
            clipPlan.requiredMovementPackets + clipPlan.profile.parameters.primingPacketCount,
            maceKillInstantRoundTripPacketCount(clipPlan),
        )
        assertEquals(0, route.request.strikeHoldTicks)
        assertEquals(3, route.request.stepWaitTicks)
        assertFalse(route.motion)
    }

    @Test
    fun `terminal Instant failures abort before strike and enter the fixed backoff`() {
        val failures = mapOf(
            MaceClipReachSessionOutcome.CORRECTED to "instantCorrected",
            MaceClipReachSessionOutcome.TIMED_OUT to "instantTimedOut",
            MaceClipReachSessionOutcome.TARGET_LOST to "instantTargetLost",
            MaceClipReachSessionOutcome.REPLAN_REJECTED to "instantReplanRejected",
        )

        failures.forEach { (outcome, notificationKey) ->
            val decision = maceKillInstantTerminalDecision(outcome, strikeCommitted = false)

            assertTrue(decision.abortRoute)
            assertTrue(decision.rejectAttempt)
            assertEquals(40, decision.backoffTicks)
            assertEquals(notificationKey, decision.notificationKey)
        }
    }

    @Test
    fun `initial Instant geometry rejection blocks only that target so KillAura can continue scanning`() {
        val distance = maceKillInstantPlanRejectionDecision(MaceClipReachBlockReason.DISTANCE_EXCEEDED)
        val budget = maceKillInstantPlanRejectionDecision(MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED)

        assertFalse(distance.applyGlobalBackoff)
        assertEquals("routeRejected", distance.notificationKey)
        assertFalse(budget.applyGlobalBackoff)
        assertEquals("instantPacketBudgetExceeded", budget.notificationKey)
    }

    @Test
    fun `completed Instant route does not reject or back off`() {
        val decision = maceKillInstantTerminalDecision(
            MaceClipReachSessionOutcome.COMPLETED,
            strikeCommitted = true,
        )

        assertFalse(decision.abortRoute)
        assertFalse(decision.rejectAttempt)
        assertEquals(0, decision.backoffTicks)
        assertNull(decision.notificationKey)
    }

    @Test
    fun `confirmed terminal keeps its endpoint when a moved target is still attackable`() {
        assertEquals(
            MaceKillInstantTargetMovementAction.KEEP_CONFIRMED_ENDPOINT,
            maceKillInstantTargetMovementAction(
                recovering = true,
                endpointStillReady = true,
            ),
        )
    }

    @Test
    fun `confirmed terminal rejects only when the moved target left the endpoint`() {
        assertEquals(
            MaceKillInstantTargetMovementAction.REJECT,
            maceKillInstantTargetMovementAction(
                recovering = true,
                endpointStillReady = false,
            ),
        )
        assertEquals(
            MaceKillInstantTargetMovementAction.REPLAN_UNCONFIRMED,
            maceKillInstantTargetMovementAction(
                recovering = false,
                endpointStillReady = false,
            ),
        )
    }

    @Test
    fun `Instant exposes only the target range that fits inside its configured movement sphere`() {
        assertEquals(
            98.91915891272024,
            maceKillMaximumTargetRange(
                configuredTargetRange = 500.0,
                instantRouting = true,
                instantMovementAllowance = 99.0,
            ),
            1.0E-12,
        )
        assertEquals(
            50.0,
            maceKillMaximumTargetRange(
                configuredTargetRange = 50.0,
                instantRouting = true,
                instantMovementAllowance = 99.0,
            ),
        )
        assertEquals(
            500.0,
            maceKillMaximumTargetRange(
                configuredTargetRange = 500.0,
                instantRouting = false,
                instantMovementAllowance = 99.0,
            ),
        )
    }

    @Test
    fun `Instant correction retries bounded recovery for Folia follow-up corrections`() {
        assertEquals(
            MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED,
            maceKillCorrectionRecoveryAction(completedRecoveryAttempts = 0),
        )
        assertEquals(
            MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED,
            maceKillCorrectionRecoveryAction(completedRecoveryAttempts = 1),
        )
        assertEquals(
            MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED,
            maceKillCorrectionRecoveryAction(completedRecoveryAttempts = 2),
        )
        assertEquals(
            MaceKillCorrectionRecoveryAction.FORCE_ORIGIN_PACKET_RESET,
            maceKillCorrectionRecoveryAction(completedRecoveryAttempts = 3),
        )
    }

    @Test
    fun `Instant correction recovery is serialized into bounded server-safe steps`() {
        val configured = MaceKillRouteExecutionConfiguration(
            timing = MaceKillRouteTiming(
                transport = MaceKillRouteTransport.PACKET,
                stepDistance = 500.0,
                maxPacketsPerTick = 128,
            ),
            routingMode = MaceKillRoutingMode.INSTANT,
            targetSpeed = 500.0,
            acceleration = 500.0,
            deceleration = 500.0,
        )

        val recovery = maceKillInstantCorrectionRecoveryConfiguration(configured)

        assertEquals(MaceKillRoutingMode.INSTANT, recovery.routingMode)
        assertEquals(3.0, recovery.timing.stepDistance)
        assertEquals(1, recovery.timing.maxPacketsPerTick)
        assertEquals(0, recovery.timing.stepWaitTicks)
        assertEquals(3.0, recovery.targetSpeed)
    }

    @Test
    fun `exhausted inverse retries choose bounded origin reset even when the old inverse still exists`() {
        val inverse = listOf(Vec3(0.0, -99.0, 0.0))
        val collision = listOf(Vec3(0.0, -10.0, 0.0))
        val forced = List(33) { Vec3(0.0, -3.0, 0.0) }

        assertSame(
            forced,
            selectMaceKillCorrectionRecoveryMovements(
                action = MaceKillCorrectionRecoveryAction.FORCE_ORIGIN_PACKET_RESET,
                inverseRecovery = inverse,
                collisionRecovery = collision,
                forcedRecovery = forced,
            ),
        )
        assertSame(
            inverse,
            selectMaceKillCorrectionRecoveryMovements(
                action = MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED,
                inverseRecovery = inverse,
                collisionRecovery = collision,
                forcedRecovery = forced,
            ),
        )
    }

    @Test
    fun `server rejected Instant is circuit broken without blocking Direct or AStar`() {
        assertTrue(shouldBlockMaceKillRouteAfterInstantCorrection(
            instantRouting = true,
            instantServerRejected = true,
        ))
        assertFalse(shouldBlockMaceKillRouteAfterInstantCorrection(
            instantRouting = false,
            instantServerRejected = true,
        ))
        assertFalse(shouldBlockMaceKillRouteAfterInstantCorrection(
            instantRouting = true,
            instantServerRejected = false,
        ))
    }

    private fun readyExperimentalPlan(): MaceClipReachPlan {
        val result = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = Vec3(0.0, 64.0, 0.0),
                endpoint = Vec3(12.0, 64.0, 0.0),
                dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
                profile = MaceClipReachProfile.REFERENCE_UNVALIDATED,
                use = MaceClipReachUse.EXPERIMENTAL,
                anchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
            ),
        )
        return (result as MaceClipReachPlanResult.Ready).plan
    }
}
