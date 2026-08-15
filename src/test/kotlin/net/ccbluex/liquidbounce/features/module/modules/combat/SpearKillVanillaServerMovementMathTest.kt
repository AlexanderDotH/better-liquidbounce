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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SpearKillVanillaServerMovementMathTest {

    @Test
    fun `Minecraft 26_2 accepts ten blocks but rejects a larger normal first packet`() {
        assertFalse(Minecraft262ServerMovementMath.movedTooQuickly(Vec3(10.0, 0.0, 0.0)))
        assertTrue(Minecraft262ServerMovementMath.movedTooQuickly(Vec3(10.0001, 0.0, 0.0)))
    }

    @Test
    fun `Minecraft 26_2 accepts 17_32 blocks only while fall flying`() {
        val movement = Vec3(17.32, 0.0, 0.0)

        assertTrue(Minecraft262ServerMovementMath.movedTooQuickly(movement, fallFlying = false))
        assertFalse(Minecraft262ServerMovementMath.movedTooQuickly(movement, fallFlying = true))
        assertTrue(
            Minecraft262ServerMovementMath.movedTooQuickly(
                movementFromFirstGood = Vec3(17.33, 0.0, 0.0),
                fallFlying = true,
            ),
        )
    }

    @Test
    fun `five hundred target is segmented to the one-packet Vanilla budget`() {
        val packetOrigin = Vec3(12.0, 64.0, -4.0)
        for (fallFlying in listOf(false, true)) {
            val budget = calculateSpearKillVanillaMovementBudget(Vec3.ZERO, fallFlying)
            val route = buildSpearKillProfiledAStarPacketRoute(
                origin = packetOrigin,
                outboundWaypoints = listOf(packetOrigin.add(500.0, 0.0, 0.0)),
                profile = SpearKillSpeedProfile(
                    currentSpeed = 0.0,
                    limits = SpearKillSpeedLimits(
                        targetSpeed = 500.0,
                        acceleration = 500.0,
                        deceleration = 500.0,
                        stepDistance = 500.0,
                        vanillaBudget = budget,
                    ),
                ),
                segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            )!!

            assertTrue(route.outboundMovements.size > 1)
            assertTrue(route.outboundMovements.all { it.length() <= budget })
            assertTrue(route.outboundMovements.all {
                !Minecraft262ServerMovementMath.movedTooQuickly(it, fallFlying = fallFlying)
            })
            assertEquals(500.0, route.outboundMovements.fold(Vec3.ZERO, Vec3::add).length(), 1e-8)
        }
    }

    @Test
    fun `production normal and Elytra budgets accept the exact boundary and reject nextUp`() {
        val velocity = Vec3(3.0, 4.0, 0.0)
        for (fallFlying in listOf(false, true)) {
            val boundary = calculateSpearKillVanillaMovementBudget(velocity, fallFlying)
            val exact = Vec3(boundary, 0.0, 0.0)
            val above = Vec3(Math.nextUp(boundary), 0.0, 0.0)

            assertFalse(Minecraft262ServerMovementMath.movedTooQuickly(exact, velocity, fallFlying = fallFlying))
            assertTrue(Minecraft262ServerMovementMath.movedTooQuickly(above, velocity, fallFlying = fallFlying))
            assertTrue(isSpearKillWithinVanillaMovementBudget(exact, velocity, fallFlying))
            assertFalse(isSpearKillWithinVanillaMovementBudget(above, velocity, fallFlying))
        }
    }

    @Test
    fun `extra same-tick packets never create a five hundred block vanilla budget`() {
        for (packetsSinceTick in 1..6) {
            assertTrue(
                Minecraft262ServerMovementMath.movedTooQuickly(
                    movementFromFirstGood = Vec3(500.0, 0.0, 0.0),
                    packetsSinceTick = packetsSinceTick,
                    fallFlying = true,
                ),
                "packetsSinceTick=$packetsSinceTick",
            )
        }
    }

    @Test
    fun `Minecraft 26_2 scales through packet five and resets packet six to one`() {
        val fifthPacketLimit = sqrt(300.0 * 5.0)
        val movement = Vec3(fifthPacketLimit, 0.0, 0.0)

        assertFalse(
            Minecraft262ServerMovementMath.movedTooQuickly(
                movementFromFirstGood = movement,
                packetsSinceTick = 5,
                fallFlying = true,
            ),
        )
        assertTrue(
            Minecraft262ServerMovementMath.movedTooQuickly(
                movementFromFirstGood = Vec3(Math.nextUp(fifthPacketLimit), 0.0, 0.0),
                packetsSinceTick = 5,
                fallFlying = true,
            ),
        )
        assertTrue(
            Minecraft262ServerMovementMath.movedTooQuickly(
                movementFromFirstGood = movement,
                packetsSinceTick = 6,
                fallFlying = true,
            ),
        )
    }

    @Test
    fun `Minecraft 26_2 subtracts expected velocity squared from moved distance squared`() {
        val movement = Vec3(10.0, 10.0, 0.0)
        val expectedVelocity = Vec3(10.0, 0.0, 0.0)

        assertTrue(Minecraft262ServerMovementMath.movedTooQuickly(movement))
        assertFalse(
            Minecraft262ServerMovementMath.movedTooQuickly(
                movementFromFirstGood = movement,
                expectedVelocity = expectedVelocity,
            ),
        )
    }

    @Test
    fun `SpearKill moved-wrongly preflight matches Minecraft 26_2 residual math`() {
        val cases = listOf(
            Vec3(0.25, 0.0, 0.0) to Vec3.ZERO,
            Vec3(0.2501, 0.0, 0.0) to Vec3.ZERO,
            Vec3(0.0, 20.0, 0.0) to Vec3.ZERO,
            Vec3(0.18, 0.0, 0.18) to Vec3.ZERO,
        )

        for ((requestedMovement, resolvedMovement) in cases) {
            assertEquals(
                !Minecraft262ServerMovementMath.movedWrongly(requestedMovement, resolvedMovement),
                isSpearKillServerPacketMovementAccepted(requestedMovement, resolvedMovement),
                "requested=$requestedMovement resolved=$resolvedMovement",
            )
        }
    }
}

/**
 * Independent test oracle transcribed from Minecraft 26.2
 * [net.minecraft.server.network.ServerGamePacketListenerImpl.handleMovePlayer].
 *
 * Keep this test-only copy structurally separate from SpearKill's movement implementation so the
 * assertions can detect drift instead of repeating production logic through a shared helper.
 */
private object Minecraft262ServerMovementMath {

    fun movedTooQuickly(
        movementFromFirstGood: Vec3,
        expectedVelocity: Vec3 = Vec3.ZERO,
        packetsSinceTick: Int = 1,
        fallFlying: Boolean = false,
    ): Boolean {
        require(packetsSinceTick > 0)
        val packetFactor = if (packetsSinceTick > MAX_PACKETS_WITH_SCALING) 1 else packetsSinceTick
        val distanceBudget = if (fallFlying) FALL_FLYING_DISTANCE_SQUARED else NORMAL_DISTANCE_SQUARED
        return movementFromFirstGood.lengthSqr() - expectedVelocity.lengthSqr() > distanceBudget * packetFactor
    }

    fun movedWrongly(requestedMovement: Vec3, resolvedMovement: Vec3): Boolean {
        val residual = requestedMovement.subtract(resolvedMovement)
        var verticalResidual = residual.y
        // Minecraft 26.2 bytecode uses this OR exactly, so every finite vertical residual is zeroed.
        if (verticalResidual > -0.5 || verticalResidual < 0.5) {
            verticalResidual = 0.0
        }
        return residual.x * residual.x + verticalResidual * verticalResidual + residual.z * residual.z >
            MOVED_WRONGLY_DISTANCE_SQUARED
    }

    private const val MAX_PACKETS_WITH_SCALING = 5
    private const val NORMAL_DISTANCE_SQUARED = 100.0
    private const val FALL_FLYING_DISTANCE_SQUARED = 300.0
    private const val MOVED_WRONGLY_DISTANCE_SQUARED = 0.0625
}
