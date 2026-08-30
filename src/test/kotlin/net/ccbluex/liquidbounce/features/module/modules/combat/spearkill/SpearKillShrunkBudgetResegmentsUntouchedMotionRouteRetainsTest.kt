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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillShrunkBudgetResegmentsUntouchedMotionRouteRetainsTest {

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
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
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
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
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
}
