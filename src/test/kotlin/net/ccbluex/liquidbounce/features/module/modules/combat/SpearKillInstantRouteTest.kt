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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillInstantRouteTest {

    @Test
    fun `Instant aims at the first server damage sample instead of the end of its hold`() {
        assertEquals(0, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 0))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 1))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 2))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 20))
        assertEquals(20, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 20, paced = true))
    }

    @Test
    fun `Instant keeps every owned movement grounded while other routes retain collision ground`() {
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.INSTANT, physicallyNearGround = false))
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.INSTANT, physicallyNearGround = true))
        assertFalse(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.DIRECT, physicallyNearGround = false))
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.DIRECT, physicallyNearGround = true))
    }

    @Test
    fun `Instant keeps correction-window movement grounded between route ticks`() {
        assertTrue(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.INSTANT,
            ownsMovementWindow = true,
        ))
        assertFalse(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.INSTANT,
            ownsMovementWindow = false,
        ))
        assertFalse(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.DIRECT,
            ownsMovementWindow = true,
        ))
    }

    @Test
    fun `Instant holds its terminal outbound across a full server damage window before returning`() {
        val outbound = listOf(
            Vec3(10.0, 0.0, 0.0),
            Vec3(10.0, -1.0, 0.0),
            Vec3(4.0, 0.0, 0.0),
        )
        val route = exactRoundTrip(outbound)
        val burst = requireNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        val session = SpearKillPacketBootSession()

        startSpearKillInstantPacketSession(session, burst)

        val deliveredOutbound = mutableListOf<Vec3>()
        repeat(outbound.size) { index ->
            assertNotNull(session.prepareNextStep())
            val movement = requireNotNull(session.pendingMovement)
            assertTrue(session.pendingOutboundStep)
            assertEquals(index == outbound.lastIndex, session.pendingFinalOutboundStep)
            deliveredOutbound += movement
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        assertTrue(outbound.zip(deliveredOutbound).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        val terminalOffset = outbound.fold(Vec3.ZERO, Vec3::add)
        assertEquals(terminalOffset, session.committedOffset)
        assertTrue(session.holdingStrike)
        assertEquals(2, SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS)
        assertEquals(1, SPEAR_KILL_INSTANT_DAMAGE_SAMPLE_TICKS)
        repeat(SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS) {
            assertNull(session.prepareNextStep())
            assertTrue(session.holdingStrike)
        }

        val deliveredReturn = mutableListOf<Vec3>()
        while (session.active) {
            assertNotNull(session.prepareNextStep())
            deliveredReturn += requireNotNull(session.pendingMovement)
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        val expectedReturn = outbound.asReversed().map { it.scale(-1.0) }
        assertEquals(expectedReturn.size, deliveredReturn.size)
        assertTrue(expectedReturn.zip(deliveredReturn).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        assertEquals(Vec3.ZERO, session.committedOffset)
        assertFalse(session.active)
    }

    @Test
    fun `Instant refuses a round trip that exceeds its configured packet cap`() {
        val route = exactRoundTrip(
            listOf(
                Vec3(10.0, 0.0, 0.0),
                Vec3(10.0, 0.0, 0.0),
                Vec3(10.0, 0.0, 0.0),
            ),
        )

        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 5))
        assertNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 513))
    }

    @Test
    fun `Instant stabilizes a dangerous descent with the Direct nofall trick`() {
        val plan = readyFallPlan(
            movements = listOf(
                Vec3(2.0, -2.0, 0.0),
                Vec3(2.0, -2.0, 0.0),
                Vec3(-2.0, 2.0, 0.0),
                Vec3(-2.0, 2.0, 0.0),
            ),
            groundedSteps = listOf(false, false, false, true),
            outboundSteps = 2,
        )
        val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan) }
        val firstDescent = plan.steps[0].movement
        val secondDescent = plan.steps[1].movement

        assertFalse(lifecycle.shouldStabilizePendingMovement(firstDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmMovement(firstDescent, delivered = true))
        assertTrue(lifecycle.shouldStabilizePendingMovement(secondDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmStabilization(delivered = true))
        assertFalse(lifecycle.shouldStabilizePendingMovement(secondDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmMovement(secondDescent, delivered = true))
        assertEquals(2, lifecycle.confirmedMovementCount)
    }

    @Test
    fun `Instant refreshes one expired prehold before emitting its outbound burst`() {
        assertEquals(
            SpearKillInstantChargeAction.READY,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 8,
                delayTicks = 3,
                damageUseDuration = 20,
                hitTicks = 1,
            ),
        )
        assertEquals(
            SpearKillInstantChargeAction.REFRESH,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 20,
                delayTicks = 3,
                damageUseDuration = 20,
                hitTicks = 1,
            ),
        )
        assertEquals(
            SpearKillInstantChargeAction.READY,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 3,
                delayTicks = 3,
                damageUseDuration = 4,
                hitTicks = 1,
            ),
        )
    }

    @Test
    fun `Instant rejects a return that is not the exact inverse of the outbound route`() {
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(Vec3(5.0, 0.0, 0.0)),
            roundTripMovements = listOf(
                Vec3(5.0, 0.0, 0.0),
                Vec3(-4.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
        )

        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 2))
    }

    @Test
    fun `Instant can abort a rejected outbound packet through the confirmed exact return prefix`() {
        val route = exactRoundTrip(
            listOf(
                Vec3(8.0, 0.0, 0.0),
                Vec3(6.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
            ),
        )
        val burst = requireNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        val session = SpearKillPacketBootSession()
        startSpearKillInstantPacketSession(session, burst)

        repeat(2) {
            assertNotNull(session.prepareNextStep())
            session.confirmStep(delivered = true)
        }
        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = false)

        session.beginExactReturn()
        assertNull(session.consumePhysicalPositionOffset())
        val recovery = mutableListOf<Vec3>()
        while (session.active) {
            assertNotNull(session.prepareNextStep())
            recovery += requireNotNull(session.pendingMovement)
            assertTrue(session.recovering)
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        assertEquals(listOf(Vec3(-6.0, 0.0, 0.0), Vec3(-8.0, 0.0, 0.0)), recovery)
        assertEquals(Vec3.ZERO, session.committedOffset)
    }

    @Test
    fun `Safe rejects a blocked corridor while Primed admits the same free endpoint once`() {
        val origin = Vec3.ZERO
        val destination = Vec3(40.0, 0.0, 0.0)
        val blockedCorridor = SpearKillAStarSegmentValidator { _, _ -> false }

        val safe = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(destination),
            maxSpeed = 40.0,
            segmentValidator = blockedCorridor,
        )
        val primed = requireNotNull(buildSpearKillPrimedInstantPacketRoute(
            origin = origin,
            destination = destination,
            isEndpointFree = { true },
        ))

        assertNull(safe)
        assertEquals(listOf(Vec3(40.0, 0.0, 0.0)), primed.outboundMovements)
    }

    @Test
    fun `Primed refuses a blocked destination before constructing a partial burst`() {
        val origin = Vec3.ZERO
        val destination = Vec3(100.0, 0.0, 0.0)

        val route = buildSpearKillPrimedInstantPacketRoute(
            origin = origin,
            destination = destination,
            isEndpointFree = { it != destination },
        )

        assertNull(route)
    }

    @Test
    fun `Primed attack revalidates target and terminal ray while a move probe needs only its endpoint`() {
        assertFalse(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = true,
            attackTargetPresent = true,
            targetValid = true,
            terminalRaytraceClear = false,
        ))
        assertFalse(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = false,
            terminalRaytraceClear = true,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = true,
            terminalRaytraceClear = false,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = true,
            attackTargetPresent = false,
            targetValid = false,
            terminalRaytraceClear = false,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = false,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = false,
            terminalRaytraceClear = false,
        ))
    }

    @Test
    fun `Primed route contains one lunge and its exact inverse only`() {
        val movement = Vec3(75.0, -4.0, 3.0)
        val route = requireNotNull(buildSpearKillPrimedInstantPacketRoute(
            origin = Vec3.ZERO,
            destination = movement,
            isEndpointFree = { true },
        ))

        assertEquals(listOf(movement), route.outboundMovements)
        assertEquals(listOf(movement, movement.scale(-1.0), Vec3.ZERO), route.roundTripMovements)
    }

    @Test
    fun `normal Primed attacks split a long lunge into source predicted accepted tick steps`() {
        val movement = Vec3(75.0, -4.0, 3.0)
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = SpearKillSpeedLimits(
                targetSpeed = 100.0,
                acceleration = 100.0,
                deceleration = 100.0,
                stepDistance = 100.0,
                vanillaBudget = 10.0,
            ),
        )
        val route = requireNotNull(buildSpearKillPacedPrimedInstantPacketRoute(
            origin = Vec3.ZERO,
            destination = movement,
            profile = profile,
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxVerticalStep = 100.0,
            isEndpointFree = { true },
        ))

        assertTrue(route.outboundMovements.size > 1)
        assertTrue(
            movement.distanceToSqr(route.outboundMovements.fold(Vec3.ZERO, Vec3::add)) < 1.0E-12,
        )
        assertEquals(
            route.outboundMovements.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            route.roundTripMovements.drop(route.outboundMovements.size),
        )
        route.outboundMovements.forEach { step ->
            val result = SpearKillPrimedInstantPlanner.plan(
                SpearKillPrimedInstantPlanRequest(
                    requestedDistance = step.length(),
                    expectedVelocitySquared = 0.0,
                    movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
                    priming = SpearKillPrimedInstantPriming.Auto,
                    packetAccounting = SpearKillPrimedInstantPacketAccounting(0, 0, 0, 512),
                    primingPacketType = SpearKillPrimedInstantPacketType.Position,
                ),
            ) as SpearKillPrimedInstantPlanResult.Ready
            assertTrue(result.plan.sourcePredictedAccepted, "step=${step.length()}")
        }
    }

    @Test
    fun `reported ninety nine block lunge becomes six grounded speed steps with a full terminal hit`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = SpearKillSpeedLimits(
                targetSpeed = 17.32,
                acceleration = 500.0,
                deceleration = 500.0,
                stepDistance = 500.0,
                vanillaBudget = 10.0,
            ),
        )
        val route = requireNotNull(buildSpearKillPacedPrimedInstantPacketRoute(
            origin = Vec3.ZERO,
            destination = Vec3(99.305, 0.0, 0.0),
            profile = profile,
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxVerticalStep = 2.95,
            isEndpointFree = { true },
        ))

        assertEquals(6, route.outboundTickCount)
        assertTrue(route.outboundMovements.all { it.length() <= 17.32 })
        assertEquals(17.32, route.outboundMovements.last().length(), 1.0E-9)
        assertEquals(
            6,
            spearKillDirectRouteHitTicks(
                routingMode = SpearKillRoutingMode.INSTANT,
                outboundTickCount = route.outboundTickCount,
                stepWaitTicks = 0,
                strikeHoldTicks = SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS,
                pacedInstant = true,
            ),
        )
    }

    @Test
    fun `paced Primed validates every segment endpoint without validating the blocked corridor`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = SpearKillSpeedLimits(100.0, 100.0, 100.0, 100.0, 10.0),
        )

        val route = buildSpearKillPacedPrimedInstantPacketRoute(
            origin = Vec3.ZERO,
            destination = Vec3(40.0, 0.0, 0.0),
            profile = profile,
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxVerticalStep = 100.0,
            isEndpointFree = { position -> position.x !in 17.0..18.0 },
        )

        assertNull(route)
    }

    @Test
    fun `normal Primed rejects a source predicted setback while explicit probes remain research capable`() {
        assertTrue(isSpearKillPrimedPlanSendable(sourcePredictedAccepted = true, researchProbe = false))
        assertFalse(isSpearKillPrimedPlanSendable(sourcePredictedAccepted = false, researchProbe = false))
        assertTrue(isSpearKillPrimedPlanSendable(sourcePredictedAccepted = false, researchProbe = true))
    }

    @Test
    fun `only explicit Primed probes retain endpoint-only collision research`() {
        assertFalse(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = true,
            priming = SpearKillPrimedInstantPriming.Auto,
        ))
        assertTrue(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = true,
            priming = SpearKillPrimedInstantPriming.Explicit(4),
        ))
        assertFalse(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = false,
            priming = SpearKillPrimedInstantPriming.Explicit(4),
        ))
    }

    @Test
    fun `Primed budget reserves both movements NoFall and final grounding before admission`() {
        val route = requireNotNull(buildSpearKillPrimedInstantPacketRoute(
            origin = Vec3.ZERO,
            destination = Vec3(20.0, 0.0, 0.0),
            isEndpointFree = { true },
        ))

        assertNull(calculateSpearKillPrimedInstantSessionBudget(
            route = route,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 8,
        ))
        val budget = requireNotNull(calculateSpearKillPrimedInstantSessionBudget(
            route = route,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 9,
        ))
        assertEquals(2, budget.movementPackets)
        assertEquals(4, budget.primingPackets)
        assertEquals(2, budget.noFallPacketsReserved)
        assertEquals(0, budget.recoveryConfirmationPacketsReserved)
        assertEquals(1, budget.finalGroundingPacketReserved)
        assertEquals(9, budget.totalPackets)
    }

    @Test
    fun `Primed replacement recovery is rejected before its first packet when remaining budget is too small`() {
        val recovery = listOf(
            Vec3(-20.0, 0.0, 0.0),
            Vec3(-20.0, 0.0, 0.0),
        )

        assertNull(calculateSpearKillPrimedInstantMovementBudget(
            movements = recovery,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 10,
            recoveryConfirmationPackets = 2,
        ))
        val budget = requireNotNull(calculateSpearKillPrimedInstantMovementBudget(
            movements = recovery,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 11,
            recoveryConfirmationPackets = 2,
        ))
        assertEquals(2, budget.recoveryConfirmationPacketsReserved)
        assertEquals(11, budget.totalPackets)
    }

    @Test
    fun `virtual Instant can chain while retaining inverse history to its original origin`() {
        val initialMovement = Vec3(20.0, 0.0, 0.0)
        val chainedMovement = Vec3(15.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        startSpearKillInstantPacketSession(
            session,
            requireNotNull(buildSpearKillInstantPacketBurst(exactRoundTrip(listOf(initialMovement)), 2)),
        )
        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = true)
        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(chainedMovement),
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ))

        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = true)
        repeat(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS) {
            assertNull(session.prepareNextStep())
        }
        val recovery = mutableListOf<Vec3>()
        while (session.active) {
            session.prepareNextStep()?.let {
                recovery += requireNotNull(session.pendingMovement)
                session.confirmStep(delivered = true)
            }
        }

        val expectedRecovery = listOf(chainedMovement.scale(-1.0), initialMovement.scale(-1.0))
        assertTrue(expectedRecovery.zip(recovery).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        assertEquals(Vec3.ZERO, session.committedOffset)
    }

    private fun exactRoundTrip(outbound: List<Vec3>) = SpearKillAStarPacketRoute(
        outboundMovements = outbound,
        roundTripMovements = buildList(outbound.size * 2 + 1) {
            addAll(outbound)
            outbound.asReversed().forEach { add(it.scale(-1.0)) }
            add(Vec3.ZERO)
        },
    )

    private fun readyFallPlan(
        movements: List<Vec3>,
        groundedSteps: List<Boolean>,
        outboundSteps: Int = 1,
    ): SpearKillServerFallSafetyPlan = when (val result = SpearKillServerFallSafetyPlan.createForMovements(
        movements = movements,
        outboundStepCount = outboundSteps,
        initialFallDistance = 0.0,
        safeFallDistance = 3.0,
        groundedSteps = groundedSteps,
        expectedNetMovement = Vec3.ZERO,
    )) {
        is SpearKillServerFallSafetyPlanResult.Ready -> result.plan
        is SpearKillServerFallSafetyPlanResult.Blocked -> error("Expected a ready fall plan, got ${result.reason}")
    }
}
