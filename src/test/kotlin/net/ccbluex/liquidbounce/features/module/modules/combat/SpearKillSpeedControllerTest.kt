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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillSpeedControllerTest {

    @Test
    fun `confirmed outbound steps accelerate from zero through five and ten`() {
        val controller = SpearKillSpeedController()
        val limits = speedLimits(targetSpeed = 100.0, acceleration = 5.0)
        controller.begin(observedSpeed = 0.0, targetSpeed = limits.targetSpeed)

        assertEquals(5.0, controller.preview(limits).requestedSpeed, 1e-9)
        assertEquals(0.0, controller.currentSpeed, 1e-9)
        assertEquals(5.0, controller.confirmOutbound(limits).requestedSpeed, 1e-9)
        assertEquals(5.0, controller.currentSpeed, 1e-9)
        assertEquals(10.0, controller.confirmOutbound(limits).requestedSpeed, 1e-9)
        assertEquals(10.0, controller.currentSpeed, 1e-9)
    }

    @Test
    fun `cancelled preview and Packet wait do not advance current speed`() {
        val controller = SpearKillSpeedController()
        val limits = speedLimits(targetSpeed = 50.0, acceleration = 4.0)
        controller.begin(observedSpeed = 2.0, targetSpeed = limits.targetSpeed)

        repeat(3) { assertEquals(6.0, controller.preview(limits).requestedSpeed, 1e-9) }

        assertEquals(2.0, controller.currentSpeed, 1e-9)
        assertEquals(6.0, controller.confirmOutbound(limits).requestedSpeed, 1e-9)
    }

    @Test
    fun `server setback rejects accumulated outbound acceleration without ending recovery state`() {
        val controller = SpearKillSpeedController()
        val limits = speedLimits(targetSpeed = 50.0, acceleration = 4.0)
        controller.begin(observedSpeed = 2.0, targetSpeed = limits.targetSpeed)
        controller.confirmOutbound(limits)
        controller.confirmOutbound(limits)

        controller.rejectOutboundProgress()

        assertTrue(controller.active)
        assertEquals(2.0, controller.currentSpeed, 1e-9)
    }

    @Test
    fun `lowered target decelerates without resetting a chained session`() {
        val controller = SpearKillSpeedController()
        controller.begin(observedSpeed = 12.0, targetSpeed = 100.0)
        val lowerTarget = speedLimits(targetSpeed = 4.0, deceleration = 3.0)

        assertEquals(9.0, controller.confirmOutbound(lowerTarget).requestedSpeed, 1e-9)
        assertEquals(6.0, controller.confirmOutbound(lowerTarget).requestedSpeed, 1e-9)
        assertEquals(4.0, controller.confirmOutbound(lowerTarget).requestedSpeed, 1e-9)
        assertTrue(controller.active)

        controller.begin(observedSpeed = 0.0, targetSpeed = 100.0)
        assertEquals(4.0, controller.currentSpeed, 1e-9, "target chaining must retain current speed")

        controller.reset()

        assertFalse(controller.active)
        assertEquals(0.0, controller.currentSpeed, 1e-9)
    }

    @Test
    fun `default acceleration reaches experimental target immediately`() {
        val controller = SpearKillSpeedController()
        val limits = speedLimits(targetSpeed = 500.0, acceleration = 500.0)
        controller.begin(observedSpeed = 0.0, targetSpeed = limits.targetSpeed)

        assertEquals(500.0, controller.confirmOutbound(limits).requestedSpeed, 1e-9)
        assertEquals(10.0, controller.preview(limits).stepLimit, 1e-9)
    }

    @Test
    fun `vanilla budget uses physics velocity and not delivered kinetic movement`() {
        val normalBudget = calculateSpearKillVanillaMovementBudget(
            serverPhysicsVelocity = Vec3(3.0, 4.0, 0.0),
            fallFlying = false,
        )
        val elytraBudget = calculateSpearKillVanillaMovementBudget(
            serverPhysicsVelocity = Vec3.ZERO,
            fallFlying = true,
        )

        assertEquals(Math.sqrt(125.0), normalBudget, 1e-9)
        assertEquals(Math.sqrt(300.0), elytraBudget, 1e-9)
        assertFalse(
            isSpearKillWithinVanillaMovementBudget(
                movementFromFirstGood = Vec3(Math.nextUp(normalBudget), 0.0, 0.0),
                serverPhysicsVelocity = Vec3(3.0, 4.0, 0.0),
                fallFlying = false,
            ),
        )
        val kinetic = estimateSpearKillKineticSpeed(
            deliveredMovement = Vec3(500.0, 0.0, 0.0),
            targetMovement = Vec3.ZERO,
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )
        assertEquals(10_000.0, kinetic.attackerSpeed, 1e-9)
        assertEquals(10.0, calculateSpearKillVanillaMovementBudget(Vec3.ZERO, fallFlying = false), 1e-9)
    }

    @Test
    fun `profiled direct movement uses five ten and exact remainder`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 100.0, acceleration = 5.0, stepDistance = 500.0, budget = 500.0),
        )
        val travel = calculateSpearKillProfiledTravel(distance = 30.0, profile = profile)
        val movements = buildSpearKillProfiledMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = travel.distance,
            profile = profile,
        )

        assertEquals(3, travel.stepCount)
        assertEquals(180.0 / 7.0, travel.distance, 1e-9)
        listOf(5.0, 10.0, 75.0 / 7.0).zip(movements).forEach { (expected, movement) ->
            assertEquals(expected, movement.length(), 1e-9)
        }
    }

    @Test
    fun `profiled AStar route respects every cap and retains an exact inverse`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 100.0, acceleration = 5.0, stepDistance = 500.0, budget = 500.0),
        )
        val route = buildSpearKillProfiledAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(30.0, 0.0, 0.0)),
            profile = profile,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(listOf(5.0, 10.0, 15.0), route.outboundMovements.map { it.length() })
        assertTrue(route.roundTripMovements.fold(Vec3.ZERO, Vec3::add).lengthSqr() < 1e-12)
    }

    @Test
    fun `lower target Direct route stays collinear and keeps one full speed downward step`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 10.0, stepDistance = 10.0, budget = 10.0),
        )
        val attackRoute = buildSpearKillProfiledDirectAttackRoute(
            origin = Vec3.ZERO,
            targetBox = AABB(3.0, -20.0, -0.3, 3.6, -18.2, 0.3),
            targetEyePosition = Vec3(3.3, -18.38, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(3.3, -20.0, 0.0),
            profile = profile,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val route = attackRoute.packetRoute
        val outbound = route.outboundMovements
        val routeDirection = attackRoute.line.direction

        assertEquals(0, route.terminalBurstSteps)
        assertTrue(outbound.all { it.cross(routeDirection).lengthSqr() < 1e-12 })
        assertTrue(outbound.all { it.dot(routeDirection) > 0.0 })
        assertEquals(profile.maximumStepLimit, outbound.last().length(), 1e-9)
        assertTrue(kotlin.math.abs(outbound.last().y) > 2.95)
        assertTrue(outbound.all { it.length() <= profile.maximumStepLimit + 1e-9 })
        assertVec3Equals(attackRoute.line.terminalWaypoint, outbound.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertEquals(
            outbound.asReversed().map { it.scale(-1.0) },
            route.roundTripMovements.drop(outbound.size).dropLast(1),
        )
        val fallPlan = (SpearKillServerFallSafetyPlan.create(
            outboundMovements = outbound,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = List(outbound.size * 2) { index -> index == outbound.size * 2 - 1 },
        ) as SpearKillServerFallSafetyPlanResult.Ready).plan
        assertFalse(fallPlan.steps.take(outbound.size).last().groundExactPacket)
        assertTrue(fallPlan.steps.last().groundExactPacket)
    }

    @Test
    fun `Direct route terminal-loads the maximum final movement`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 10.0, stepDistance = 10.0, budget = 10.0),
        )
        val attackRoute = buildSpearKillProfiledDirectAttackRoute(
            origin = Vec3.ZERO,
            targetBox = AABB(25.0, 0.0, -0.3, 25.6, 1.8, 0.3),
            targetEyePosition = Vec3(25.3, 1.62, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
            profile = profile,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        val outbound = attackRoute.packetRoute.outboundMovements
        assertEquals(listOf(2.75, 10.0, 10.0), outbound.map(Vec3::length))
        assertEquals(attackRoute.line.terminalWaypoint, outbound.fold(Vec3.ZERO, Vec3::add))
        assertEquals(attackRoute.line.terminalWaypoint.subtract(outbound.last()), attackRoute.approach.plannerGoal)
    }

    @Test
    fun `Direct route rejects a blocked segment instead of staging around it`() {
        var validations = 0
        val route = buildSpearKillProfiledDirectAttackRoute(
            origin = Vec3.ZERO,
            targetBox = AABB(25.0, 0.0, -0.3, 25.6, 1.8, 0.3),
            targetEyePosition = Vec3(25.3, 1.62, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
            profile = SpearKillSpeedProfile(
                currentSpeed = 0.0,
                limits = speedLimits(targetSpeed = 10.0),
            ),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ ->
                validations++
                validations < 2
            },
        )

        assertEquals(null, route)
        assertEquals(2, validations)
    }

    @Test
    fun `Direct route rejects a final step below the supplied kinetic requirement`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(
                targetSpeed = 10.0,
                stepDistance = 500.0,
                budget = 500.0,
            ),
        )
        val route = buildSpearKillProfiledDirectAttackRoute(
            origin = Vec3.ZERO,
            targetBox = AABB(25.0, 0.0, -0.3, 25.6, 1.8, 0.3),
            targetEyePosition = Vec3(25.3, 1.62, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
            profile = profile,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            kineticRequirements = SpearKillKineticDamageRequirements(
                minimumAttackerSpeed = 201.0,
                minimumRelativeSpeed = 201.0,
                damageMultiplier = 1.075,
            ),
            targetMovement = Vec3.ZERO,
        )

        assertEquals(null, route)
    }

    @Test
    fun `physical dive packets advance acceleration only on logical completion`() {
        val dive = listOf(
            Vec3(0.0, -2.95, 0.0),
            Vec3(0.0, -2.95, 0.0),
            Vec3(0.0, -2.10, 0.0),
        )
        val session = SpearKillPacketBootSession()
        session.start(
            path = dive + dive.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            outboundSteps = dive.size,
            terminalSuffixSteps = dive.size,
            terminalBurstSteps = dive.size,
        )
        val controller = SpearKillSpeedController()
        val limits = speedLimits(targetSpeed = 100.0, acceleration = 5.0)
        controller.begin(observedSpeed = 0.0, targetSpeed = limits.targetSpeed)

        repeat(dive.size) { index ->
            session.prepareNextStep()
            if (session.pendingLogicalOutboundCompletion) controller.confirmOutbound(limits)
            session.confirmStep(delivered = true)
            assertEquals(if (index == dive.lastIndex) 5.0 else 0.0, controller.currentSpeed, 1e-9)
        }
    }

    @Test
    fun `shrunk budget resegments only untouched Motion route and retains confirmed recovery`() {
        val validatedSegments = mutableListOf<Pair<Vec3, Vec3>>()
        val result = resegmentSpearKillUnconfirmedMotionRoute(
            origin = Vec3(10.0, 0.0, 0.0),
            pendingOutboundMovement = Vec3(8.0, 0.0, 0.0),
            queuedMovements = listOf(
                Vec3(8.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
                Vec3(-4.0, 0.0, 0.0),
                Vec3(-8.0, 0.0, 0.0),
                Vec3(-8.0, 0.0, 0.0),
                Vec3(-10.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            remainingOutboundSteps = 3,
            profile = SpearKillSpeedProfile(
                currentSpeed = 10.0,
                limits = speedLimits(targetSpeed = 100.0, stepDistance = 500.0, budget = 5.0),
            ),
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                validatedSegments += from to to
                true
            },
        )!!

        val outbound = result.movements.take(result.outboundStepCount)
        assertTrue(outbound.isNotEmpty())
        assertTrue(outbound.all { it.length() <= 5.0 })
        assertEquals(20.0, outbound.fold(Vec3.ZERO, Vec3::add).x, 1e-9)
        assertEquals(-10.0, result.movements.fold(Vec3.ZERO, Vec3::add).x, 1e-9)
        assertEquals(Vec3(-10.0, 0.0, 0.0), result.movements[result.movements.lastIndex - 1])
        assertTrue(validatedSegments.size >= result.outboundStepCount * 2)
    }

    @Test
    fun `failed Motion replan keeps only the exact inverse of confirmed movement`() {
        val recovery = spearKillConfirmedMotionRecoveryTail(
            queuedMovements = listOf(
                Vec3(8.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
                Vec3(-4.0, 0.0, 0.0),
                Vec3(-8.0, 0.0, 0.0),
                Vec3(-8.0, 0.0, 0.0),
                Vec3(-10.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            remainingOutboundSteps = 3,
        )!!

        assertEquals(listOf(Vec3(-10.0, 0.0, 0.0), Vec3.ZERO), recovery)
    }

    @Test
    fun `KillAura disable drops unconfirmed Motion travel and returns only confirmed movement`() {
        val first = Vec3(4.0, 0.0, 0.0)
        val second = Vec3(6.0, 0.0, 0.0)
        val third = Vec3(8.0, 0.0, 0.0)

        assertEquals(
            listOf(first.scale(-1.0), Vec3.ZERO),
            spearKillMotionReturnTailOnDisable(
                queuedMovements = listOf(
                    second,
                    third,
                    third.scale(-1.0),
                    second.scale(-1.0),
                    first.scale(-1.0),
                    Vec3.ZERO,
                ),
                plannedOutboundSteps = 3,
                confirmedOutboundSteps = 1,
            ),
        )
        assertEquals(
            listOf(Vec3.ZERO),
            spearKillMotionReturnTailOnDisable(
                queuedMovements = listOf(
                    first,
                    second,
                    third,
                    third.scale(-1.0),
                    second.scale(-1.0),
                    first.scale(-1.0),
                    Vec3.ZERO,
                ),
                plannedOutboundSteps = 3,
                confirmedOutboundSteps = 0,
            ),
        )
    }

    @Test
    fun `exact confirmed return is not reshaped by a lower outbound budget`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(Vec3(12.0, 0.0, 0.0), Vec3(-12.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        val lowerBudget = speedLimits(targetSpeed = 100.0, stepDistance = 500.0, budget = 5.0)
        assertEquals(5.0, SpearKillSpeedProfile(12.0, lowerBudget).stepAt(0).stepLimit, 1e-9)
        val exactReturn = session.exactRecoveryMovementsFrom(session.committedOffset)!!
        assertEquals(1, exactReturn.size)
        assertEquals(-12.0, exactReturn.single().x, 1e-9)
        assertEquals(0.0, exactReturn.single().y, 1e-9)
        assertEquals(0.0, exactReturn.single().z, 1e-9)
    }

    @Test
    fun `Packet budget replan replaces untouched outbound and preserves committed inverse history`() {
        val controller = SpearKillSpeedController()
        val initialLimits = speedLimits(targetSpeed = 500.0, stepDistance = 500.0, budget = 10.0)
        controller.begin(observedSpeed = 0.0, targetSpeed = initialLimits.targetSpeed)
        val initialRoute = buildSpearKillProfiledAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(30.0, 0.0, 0.0)),
            profile = controller.profile(initialLimits),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(initialRoute.roundTripMovements, initialRoute.outboundMovements.size)
        session.prepareNextStep()
        session.confirmStep(delivered = true)
        controller.confirmOutbound(initialLimits)

        val reducedLimits = initialLimits.copy(vanillaBudget = 5.0)
        val replacement = buildSpearKillProfiledAStarPacketRoute(
            origin = session.committedOffset,
            outboundWaypoints = listOf(Vec3(30.0, 0.0, 0.0)),
            profile = controller.profile(reducedLimits),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        assertTrue(session.replaceRemainingOutbound(replacement.outboundMovements, strikeHoldTicks = 0))

        val replacementDeliveries = mutableListOf<Vec3>()
        while (!session.recovering) {
            session.prepareNextStep()
            val movement = session.pendingMovement!!
            assertTrue(session.pendingOutboundStep)
            assertTrue(movement.length() <= 5.0)
            replacementDeliveries += movement
            session.confirmStep(delivered = true)
            controller.confirmOutbound(reducedLimits)
        }

        assertEquals(4, replacementDeliveries.size)
        assertEquals(30.0, session.committedOffset.x, 1e-9)
        val exactReturn = session.exactRecoveryMovementsFrom(session.committedOffset)!!
        assertEquals(-30.0, exactReturn.fold(Vec3.ZERO, Vec3::add).x, 1e-9)
        assertEquals(10.0, exactReturn.last().length(), 1e-9)
    }

    @Test
    fun `kinetic estimate projects actual movement instead of requested speed`() {
        val estimate = estimateSpearKillKineticSpeed(
            deliveredMovement = Vec3(3.0, 0.0, 4.0),
            targetMovement = Vec3(1.0, 0.0, 0.0),
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(60.0, estimate.attackerSpeed, 1e-9)
        assertEquals(20.0, estimate.targetSpeed, 1e-9)
        assertEquals(40.0, estimate.relativeSpeed, 1e-9)
    }

    @Test
    fun `kinetic damage estimate applies minimum speeds and vanilla floored multiplier`() {
        val estimate = estimateSpearKillKineticDamage(
            deliveredMovement = Vec3(0.25, 0.0, 0.0),
            targetMovement = Vec3(0.02, 0.0, 0.0),
            lookDirection = Vec3(1.0, 0.0, 0.0),
            requirements = SpearKillKineticDamageRequirements(
                minimumAttackerSpeed = 4.6,
                minimumRelativeSpeed = 4.5,
                damageMultiplier = 1.075,
            ),
        )

        assertEquals(5.0, estimate.speed.attackerSpeed, 1e-9)
        assertEquals(0.4, estimate.speed.targetSpeed, 1e-9)
        assertEquals(4.6, estimate.speed.relativeSpeed, 1e-9)
        assertTrue(estimate.meetsRequirements)
        assertEquals(4, estimate.bonusDamage)
    }

    private fun speedLimits(
        targetSpeed: Double,
        acceleration: Double = 500.0,
        deceleration: Double = 500.0,
        stepDistance: Double = 500.0,
        budget: Double = 10.0,
    ) = SpearKillSpeedLimits(
        targetSpeed = targetSpeed,
        acceleration = acceleration,
        deceleration = deceleration,
        stepDistance = stepDistance,
        vanillaBudget = budget,
    )
}
