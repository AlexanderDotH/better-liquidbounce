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

import net.minecraft.world.phys.Vec3
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillFallSafetyTest {

    @Test
    fun `rejects unsafe landing at attack endpoint`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(Vec3(0.0, 8.0, 0.0), grounded = false),
                MaceKillFallSafetyStep(Vec3(4.0, -8.0, 0.0), grounded = true),
            ),
        )

        val unsafe = assertInstanceOf(MaceKillFallSafetyPreflight.UnsafeLanding::class.java, result)
        assertEquals(1, unsafe.stepIndex)
        assertEquals(8.0, unsafe.fallDistance)
    }

    @Test
    fun `rejects unsafe inverse return landing`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(Vec3(0.0, 4.0, 0.0), grounded = false),
                MaceKillFallSafetyStep(Vec3(0.0, -4.0, 0.0), grounded = true),
            ),
        )

        val unsafe = assertInstanceOf(MaceKillFallSafetyPreflight.UnsafeLanding::class.java, result)
        assertEquals(1, unsafe.stepIndex)
    }

    @Test
    fun `rejects existing unsafe fall before a grounded horizontal step`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 4.0,
            safeFallDistance = 3.0,
            steps = listOf(MaceKillFallSafetyStep(Vec3(1.0, 0.0, 0.0), grounded = true)),
        )

        assertInstanceOf(MaceKillFallSafetyPreflight.UnsafeLanding::class.java, result)
    }

    @Test
    fun `upward movement preserves existing fall state and unsafe landing is rejected`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 20.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(Vec3(0.0, 1.0, 0.0), grounded = false),
                MaceKillFallSafetyStep(Vec3(2.0, -2.0, 0.0), grounded = true),
                MaceKillFallSafetyStep(Vec3(-2.0, 0.0, 0.0), grounded = true),
            ),
        )

        val unsafe = assertInstanceOf(MaceKillFallSafetyPreflight.UnsafeLanding::class.java, result)
        assertEquals(1, unsafe.stepIndex)
        assertEquals(22.0, unsafe.fallDistance)
    }

    @Test
    fun `airborne origin is allowed only when the packet route returns exactly`() {
        assertTrue(canBeginMaceKillFallSafetyAtOrigin(
            originNearGround = false,
            routeReturnsExactly = true,
        ))
        assertFalse(canBeginMaceKillFallSafetyAtOrigin(
            originNearGround = false,
            routeReturnsExactly = false,
        ))
        assertTrue(canBeginMaceKillFallSafetyAtOrigin(
            originNearGround = true,
            routeReturnsExactly = false,
        ))
    }

    @Test
    fun `owned Instant ground spoof resets an airborne fall state before routing`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 20.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(
                    Vec3(0.0, 99.0, 0.0),
                    grounded = true,
                    groundSpoofed = true,
                ),
                MaceKillFallSafetyStep(
                    Vec3(12.0, 0.0, 0.0),
                    grounded = true,
                    groundSpoofed = true,
                ),
                MaceKillFallSafetyStep(
                    Vec3(0.0, -99.0, 0.0),
                    grounded = true,
                    groundSpoofed = true,
                ),
            ),
        )

        assertEquals(MaceKillFallSafetyPreflight.Safe, result)
    }

    @Test
    fun `owned Instant ground spoof safely resets a fall state below the damage threshold`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 2.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(
                    Vec3(0.0, 50.0, 0.0),
                    grounded = true,
                    groundSpoofed = true,
                ),
                MaceKillFallSafetyStep(
                    Vec3(30.0, -46.0, 0.0),
                    grounded = true,
                    groundSpoofed = true,
                ),
            ),
        )

        assertEquals(MaceKillFallSafetyPreflight.Safe, result)
    }

    @Test
    fun `safe grounded horizontal route passes`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(Vec3(2.0, 0.0, 0.0), grounded = true),
                MaceKillFallSafetyStep(Vec3(-2.0, 0.0, 0.0), grounded = true),
            ),
        )

        assertEquals(MaceKillFallSafetyPreflight.Safe, result)
    }

    @Test
    fun `correction recovery is re-evaluated from confirmed fall distance`() {
        val result = preflightMaceKillFallSafety(
            initialFallDistance = 2.0,
            safeFallDistance = 3.0,
            steps = listOf(
                MaceKillFallSafetyStep(Vec3(0.0, -2.0, 0.0), grounded = true),
            ),
        )

        val unsafe = assertInstanceOf(MaceKillFallSafetyPreflight.UnsafeLanding::class.java, result)
        assertEquals(4.0, unsafe.fallDistance)
    }

    @Test
    fun `cancelled movement does not advance delivery confirmed lifecycle`() {
        val movement = Vec3(0.0, -2.0, 0.0)
        val plan = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(movement),
            outboundStepCount = 0,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(true),
            expectedNetMovement = movement,
        ) as SpearKillServerFallSafetyPlanResult.Ready
        val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan.plan) }

        assertEquals(false, lifecycle.confirmMovement(movement, delivered = false, exactPacketGrounded = true))
        assertEquals(0, lifecycle.confirmedMovementCount)
        assertEquals(true, lifecycle.confirmMovement(movement, delivered = true, exactPacketGrounded = true))
        assertEquals(1, lifecycle.confirmedMovementCount)
    }

    @Test
    fun `completed route requests and confirms one identity tracked grounding packet before reset`() {
        val movement = Vec3(0.0, 1.0, 0.0)
        val lifecycle = completedAirborneLifecycle(movement)
        val tracker = MaceKillGroundingPacketTracker()
        val groundingPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val unrelatedPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)

        assertEquals(
            MaceKillFallSafetyFinishDecision.SEND_GROUNDING,
            decideMaceKillFallSafetyFinish(
                lifecycle,
                finalPositionKnown = true,
                connectionOpen = true,
                nearGround = true,
            ),
        )
        tracker.protect(groundingPacket)
        assertFalse(tracker.reassertGround(unrelatedPacket))
        assertTrue(tracker.reassertGround(groundingPacket))
        assertEquals(
            MaceKillGroundingPacketResolution.UNRELATED,
            tracker.resolve(unrelatedPacket, cancelled = false, queued = false),
        )
        assertEquals(
            MaceKillGroundingPacketResolution.DELIVERED,
            tracker.resolve(groundingPacket, cancelled = false, queued = false),
        )
        assertTrue(lifecycle.confirmGrounding(delivered = true))

        assertEquals(
            MaceKillFallSafetyFinishDecision.RESET_LOCAL_FALL_DISTANCE,
            decideMaceKillFallSafetyFinish(
                lifecycle,
                finalPositionKnown = true,
                connectionOpen = true,
                nearGround = true,
            ),
        )
        assertFalse(lifecycle.active)
    }

    @Test
    fun `cancelled or Blink queued grounding is rejected and can be retried next tick`() {
        val lifecycle = completedAirborneLifecycle(Vec3(0.0, 1.0, 0.0))
        val tracker = MaceKillGroundingPacketTracker()
        val cancelled = ServerboundMovePlayerPacket.StatusOnly(false, false)

        assertEquals(
            MaceKillFallSafetyFinishDecision.SEND_GROUNDING,
            decideMaceKillFallSafetyFinish(
                lifecycle,
                finalPositionKnown = true,
                connectionOpen = true,
                nearGround = true,
            ),
        )
        tracker.protect(cancelled)
        assertEquals(
            MaceKillGroundingPacketResolution.REJECTED,
            tracker.resolve(cancelled, cancelled = false, queued = true),
        )
        assertFalse(lifecycle.confirmGrounding(delivered = false))
        assertEquals(
            MaceKillFallSafetyFinishDecision.SEND_GROUNDING,
            decideMaceKillFallSafetyFinish(
                lifecycle,
                finalPositionKnown = true,
                connectionOpen = true,
                nearGround = true,
            ),
        )

        val retry = ServerboundMovePlayerPacket.StatusOnly(false, false)
        tracker.protect(retry)
        assertTrue(tracker.discard(retry))
        assertEquals(0, tracker.pendingCount)
    }

    private fun completedAirborneLifecycle(movement: Vec3): SpearKillFallSafetyLifecycle {
        val ready = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(movement),
            outboundStepCount = 0,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(false),
            expectedNetMovement = movement,
        ) as SpearKillServerFallSafetyPlanResult.Ready
        return SpearKillFallSafetyLifecycle().apply {
            begin(ready.plan)
            assertTrue(confirmMovement(movement, delivered = true, exactPacketGrounded = false))
        }
    }
}
