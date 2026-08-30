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

class SpearKillLowerTargetDirectRouteStaysCollinearKeepsTest {

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
    fun `Instant direct route bounds every downward packet below the safe fall distance`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 500.0, stepDistance = 500.0, budget = 500.0),
        )
        val attackRoute = buildSpearKillProfiledDirectAttackRoute(
            origin = Vec3.ZERO,
            targetBox = AABB(3.0, -20.0, -0.3, 3.6, -18.2, 0.3),
            targetEyePosition = Vec3(3.3, -18.38, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(3.3, -20.0, 0.0),
            profile = profile,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            maxVerticalStep = 2.95,
        )!!
        val outbound = attackRoute.packetRoute.outboundMovements

        assertTrue(outbound.size > 1)
        assertTrue(outbound.all { kotlin.math.abs(it.y) <= 2.95 + 1.0E-9 })
        assertTrue(outbound.all { it.cross(attackRoute.line.direction).lengthSqr() < 1.0E-12 })
        assertVec3Equals(attackRoute.line.terminalWaypoint, outbound.fold(Vec3.ZERO, Vec3::add), 1.0E-9)
        assertEquals(
            outbound.asReversed().map { it.scale(-1.0) },
            attackRoute.packetRoute.roundTripMovements.drop(outbound.size).dropLast(1),
        )
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
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
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
}
