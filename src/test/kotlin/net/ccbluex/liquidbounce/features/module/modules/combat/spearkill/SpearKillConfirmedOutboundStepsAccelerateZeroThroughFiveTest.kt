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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillConfirmedOutboundStepsAccelerateZeroThroughFiveTest {

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
    fun `profiled AStar route rejects invalid geometry before segment validation`() {
        val profile = SpearKillSpeedProfile(
            currentSpeed = 0.0,
            limits = speedLimits(targetSpeed = 100.0, acceleration = 5.0),
        )
        var validations = 0
        val validator = SpearKillAStarSegmentValidator { _, _ ->
            validations++
            true
        }

        assertNull(
            buildSpearKillProfiledAStarPacketRoute(
                origin = Vec3(Double.NaN, 0.0, 0.0),
                outboundWaypoints = listOf(Vec3(1.0, 0.0, 0.0)),
                profile = profile,
                segmentValidator = validator,
            ),
        )
        assertNull(
            buildSpearKillProfiledAStarPacketRoute(
                origin = Vec3.ZERO,
                outboundWaypoints = listOf(Vec3(Double.POSITIVE_INFINITY, 0.0, 0.0)),
                profile = profile,
                segmentValidator = validator,
            ),
        )
        assertNull(
            buildSpearKillProfiledAStarPacketRoute(
                origin = Vec3.ZERO,
                outboundWaypoints = listOf(Vec3(1.0, 0.0, 0.0)),
                profile = profile,
                segmentValidator = validator,
                maxVerticalStep = 0.0,
            ),
        )
        assertEquals(0, validations)
    }
}
