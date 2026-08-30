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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import kotlinx.coroutines.test.runTest
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachHitSentinelTest {

    @Test
    fun `sentinel destination clears the target hitbox toward the player`() {
        assertVec3Equals(
            Vec3(9.3, 64.0, 0.0),
            calculateReachHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 0.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )

        assertVec3Equals(
            Vec3(9.3, 64.0, 9.3),
            calculateReachHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 10.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )
    }

    @Test
    fun `sentinel attacks during a short stay and returns to its origin`() = runTest {
        val origin = Vec3(1.0, 64.0, 2.0)
        val target = Vec3(10.0, 64.0, 20.0)
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = origin,
            destination = target,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == target) "forward" else "return"
                true
            },
            shouldRecover = { true },
            synchronizeRotation = { events += "rotate" },
            attack = {
                events += "attack"
                true
            },
            wait = { ticks -> events += "wait:$ticks" },
        )

        assertTrue(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("forward", "rotate", "attack", "wait:2", "return"), events)
    }

    @Test
    fun `sentinel does not attack or return when ClickTP rejects without displacement`() = runTest {
        var attacked = false
        var teleportCalls = 0

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = {
                teleportCalls++
                false
            },
            shouldRecover = { false },
            synchronizeRotation = { error("rotation must not be synchronized") },
            attack = {
                attacked = true
                true
            },
            wait = { error("rejected teleport must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertFalse(outcome.returned)
        assertFalse(attacked)
        assertEquals(1, teleportCalls)
    }

    @Test
    fun `sentinel recovers to origin when an unreliable forward teleport displaces the player`() = runTest {
        val origin = Vec3(1.0, 0.0, 0.0)
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = origin,
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == Vec3.ZERO) "failed-forward" else "recover"
                destination == origin
            },
            shouldRecover = { true },
            synchronizeRotation = { error("rotation must not be synchronized") },
            attack = { error("attack must not run") },
            wait = { error("failed teleport must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("failed-forward", "recover"), events)
    }

    @Test
    fun `sentinel returns immediately when the post-teleport attack is rejected`() = runTest {
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == Vec3.ZERO) "forward" else "return"
                true
            },
            shouldRecover = { true },
            synchronizeRotation = { events += "rotate" },
            attack = {
                events += "rejected"
                false
            },
            wait = { error("rejected attack must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("forward", "rotate", "rejected", "return"), events)
    }

    @Test
    fun `sentinel preserves attack success when its return teleport fails`() = runTest {
        var teleportCalls = 0

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 0,
            teleport = {
                teleportCalls++
                teleportCalls == 1
            },
            shouldRecover = { true },
            synchronizeRotation = {},
            attack = { true },
            wait = { error("zero stay ticks must not wait") },
        )

        assertTrue(outcome.attacked)
        assertFalse(outcome.returned)
        assertEquals(2, teleportCalls)
    }
}
