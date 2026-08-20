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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class PacketFlyPlannerTest {

    @Test
    fun `Safe splits a three dimensional diagonal and preserves the exact final endpoint`() {
        val start = Vec3(11.0, 32.0, -7.0)
        val movement = Vec3(2.0, 3.0, 6.0).scale(4.0)
        val plan = ready(PacketFlyPlanner.safe(request(start, start.add(movement))))
        val intermediateEndpoints = plan.auxiliaryPackets.map { packet ->
            assertInstanceOf(PacketFlyAuxiliaryPacketPlan.Position::class.java, packet).endpoint
        }

        assertEquals(2, intermediateEndpoints.size)
        assertEquals(start.add(movement), plan.finalEndpoint)
        assertEquals(plan.finalEndpoint, plan.requestedEnd)
        assertFalse(plan.clamped)
        assertBoundedCollinearSegments(start, intermediateEndpoints + plan.finalEndpoint, movement, 10.0)
        assertEquals(3, plan.totalPacketBudget)
        assertTrue(plan.finalVanillaPacketReserved)
    }

    @Test
    fun `Safe selects the Elytra budget only when fall flying is already active`() {
        val requestedEnd = Vec3(17.0, 0.0, 0.0)
        val normal = ready(PacketFlyPlanner.safe(request(Vec3.ZERO, requestedEnd, fallFlying = false)))
        val activeElytra = ready(PacketFlyPlanner.safe(request(Vec3.ZERO, requestedEnd, fallFlying = true)))

        assertEquals(10.0, normal.perMovementPacketBudget, EPSILON)
        assertEquals(1, normal.auxiliaryPackets.size)
        assertEquals(sqrt(300.0), activeElytra.perMovementPacketBudget, EPSILON)
        assertTrue(activeElytra.auxiliaryPackets.isEmpty())
        assertEquals(requestedEnd, normal.finalEndpoint)
        assertEquals(requestedEnd, activeElytra.finalEndpoint)
    }

    @Test
    fun `Safe accounts existing and NoFall packets before clamping along the requested direction`() {
        val requestedMovement = Vec3(9.0, 12.0, 36.0)
        val plan = ready(PacketFlyPlanner.safe(request(
            start = Vec3.ZERO,
            requestedEnd = requestedMovement,
            accounting = PacketFlyPacketAccounting(
                existingPreFinalPackets = 1,
                forecastNoFallPackets = 1,
                vanillaFinalPacketReserved = true,
                reservedPacketsAfterFinal = 0,
                maxPackets = 4,
            ),
        )))

        assertTrue(plan.clamped)
        assertEquals(20.0, plan.finalEndpoint.length(), EPSILON)
        assertTrue(plan.finalEndpoint.cross(requestedMovement).lengthSqr() < EPSILON_SQUARED)
        assertTrue(plan.finalEndpoint.dot(requestedMovement) > 0.0)
        assertEquals(1, plan.auxiliaryPackets.size)
        assertEquals(4, plan.totalPacketBudget)
        assertBoundedCollinearSegments(
            Vec3.ZERO,
            plan.positionEndpoints() + plan.finalEndpoint,
            requestedMovement,
            10.0,
        )
    }

    @Test
    fun `zero movement produces no auxiliary or ordinary movement packet`() {
        val start = Vec3(4.0, 5.0, 6.0)
        val safe = ready(PacketFlyPlanner.safe(request(start, start)))
        val primed = ready(PacketFlyPlanner.primed(
            request(start, start),
            PacketFlyPrimingPacketShape.PositionRotation,
        ))

        listOf(safe, primed).forEach { plan ->
            assertEquals(start, plan.finalEndpoint)
            assertTrue(plan.auxiliaryPackets.isEmpty())
            assertFalse(plan.finalVanillaPacketReserved)
            assertEquals(0, plan.totalPacketBudget)
            assertFalse(plan.clamped)
        }
    }

    @Test
    fun `Primed uses automatic SpearKill counts and leaves the endpoint to Vanilla`() {
        val requestedEnd = Vec3(12.0, 16.0, 0.0)
        val plan = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, requestedEnd),
            PacketFlyPrimingPacketShape.Position,
        ))

        assertEquals(3, plan.auxiliaryPackets.size)
        assertEquals(4, plan.totalPacketBudget)
        assertEquals(requestedEnd, plan.finalEndpoint)
        assertFalse(plan.clamped)
        assertTrue(plan.finalVanillaPacketReserved)
        plan.auxiliaryPackets.forEach { packet ->
            val priming = assertInstanceOf(PacketFlyAuxiliaryPacketPlan.Priming::class.java, packet)
            assertEquals(PacketFlyPrimingPacketShape.Position, priming.shape)
            assertEquals(Vec3.ZERO, priming.position)
        }
    }

    @Test
    fun `Primed exposes all four stationary packet shapes without a raw final packet`() {
        val start = Vec3(7.0, 8.0, 9.0)

        PacketFlyPrimingPacketShape.entries.forEach { shape ->
            val plan = ready(PacketFlyPlanner.primed(
                request(start, start.add(20.0, 0.0, 0.0)),
                shape,
            ))

            assertEquals(3, plan.auxiliaryPackets.size, shape.name)
            plan.auxiliaryPackets.forEach { packet ->
                val priming = assertInstanceOf(PacketFlyAuxiliaryPacketPlan.Priming::class.java, packet)
                assertEquals(shape, priming.shape)
                if (shape.includesPosition) {
                    assertEquals(start, priming.position, shape.name)
                } else {
                    assertNull(priming.position, shape.name)
                }
            }
            assertEquals(start.add(20.0, 0.0, 0.0), plan.finalEndpoint)
        }
    }

    @Test
    fun `Primed counts existing and forecast NoFall packets before adding dedicated priming`() {
        val plan = ready(PacketFlyPlanner.primed(
            request(
                start = Vec3.ZERO,
                requestedEnd = Vec3(20.0, 0.0, 0.0),
                accounting = PacketFlyPacketAccounting(
                    existingPreFinalPackets = 1,
                    forecastNoFallPackets = 2,
                    vanillaFinalPacketReserved = true,
                    reservedPacketsAfterFinal = 0,
                    maxPackets = 5,
                ),
            ),
            PacketFlyPrimingPacketShape.StatusOnly,
        ))

        assertTrue(plan.auxiliaryPackets.isEmpty())
        assertEquals(4, plan.totalPacketBudget)
        assertEquals(Vec3(20.0, 0.0, 0.0), plan.finalEndpoint)
    }

    @Test
    fun `Primed preserves the normal five packet boundary and clamps anything larger`() {
        val admitted = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, Vec3(22.0, 0.0, 0.0), maxPackets = 5),
            PacketFlyPrimingPacketShape.Rotation,
        ))
        val clamped = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, Vec3(23.0, 0.0, 0.0), maxPackets = 5),
            PacketFlyPrimingPacketShape.Rotation,
        ))

        assertFalse(admitted.clamped)
        assertEquals(4, admitted.auxiliaryPackets.size)
        assertEquals(5, admitted.totalPacketBudget)
        assertTrue(clamped.clamped)
        assertEquals(sqrt(500.0), clamped.finalEndpoint.length(), EPSILON)
        assertEquals(4, clamped.auxiliaryPackets.size)
        assertEquals(5, clamped.totalPacketBudget)
    }

    @Test
    fun `Primed clamps to a smaller complete packet plan without changing direction`() {
        val requestedMovement = Vec3(18.0, 24.0, 0.0)
        val plan = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, requestedMovement, maxPackets = 2),
            PacketFlyPrimingPacketShape.PositionRotation,
        ))

        assertTrue(plan.clamped)
        assertEquals(sqrt(200.0), plan.finalEndpoint.length(), EPSILON)
        assertTrue(plan.finalEndpoint.cross(requestedMovement).lengthSqr() < EPSILON_SQUARED)
        assertTrue(plan.finalEndpoint.dot(requestedMovement) > 0.0)
        assertEquals(1, plan.auxiliaryPackets.size)
        assertEquals(2, plan.totalPacketBudget)
    }

    @Test
    fun `Safe and Primed reserve forecast packets after the Vanilla endpoint`() {
        val accounting = PacketFlyPacketAccounting(
            existingPreFinalPackets = 0,
            forecastNoFallPackets = 0,
            vanillaFinalPacketReserved = true,
            reservedPacketsAfterFinal = 1,
            maxPackets = 3,
        )
        val safe = ready(PacketFlyPlanner.safe(request(
            Vec3.ZERO,
            Vec3(30.0, 0.0, 0.0),
            accounting = accounting,
        )))
        val primed = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, Vec3(30.0, 0.0, 0.0), accounting = accounting),
            PacketFlyPrimingPacketShape.StatusOnly,
        ))

        assertEquals(20.0, safe.finalEndpoint.length(), EPSILON)
        assertEquals(1, safe.auxiliaryPackets.size)
        assertEquals(sqrt(200.0), primed.finalEndpoint.length(), EPSILON)
        assertEquals(1, primed.auxiliaryPackets.size)
        assertEquals(3, safe.totalPacketBudget)
        assertEquals(3, primed.totalPacketBudget)
    }

    @Test
    fun `Primed selects the Elytra movement profile only for active fall flight`() {
        val requestedEnd = Vec3(30.0, 0.0, 0.0)
        val normal = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, requestedEnd, fallFlying = false),
            PacketFlyPrimingPacketShape.Position,
        ))
        val activeElytra = ready(PacketFlyPlanner.primed(
            request(Vec3.ZERO, requestedEnd, fallFlying = true),
            PacketFlyPrimingPacketShape.Position,
        ))

        assertTrue(normal.clamped)
        assertEquals(sqrt(500.0), normal.finalEndpoint.length(), EPSILON)
        assertFalse(activeElytra.clamped)
        assertEquals(requestedEnd, activeElytra.finalEndpoint)
        assertEquals(2, activeElytra.auxiliaryPackets.size)
    }

    @Test
    fun `Primed fails closed when existing packets already crossed the five packet window`() {
        val result = PacketFlyPlanner.primed(
            request(
                Vec3.ZERO,
                Vec3(1.0, 0.0, 0.0),
                accounting = PacketFlyPacketAccounting(
                    existingPreFinalPackets = 5,
                    forecastNoFallPackets = 0,
                    vanillaFinalPacketReserved = true,
                    reservedPacketsAfterFinal = 0,
                    maxPackets = 128,
                ),
            ),
            PacketFlyPrimingPacketShape.StatusOnly,
        )

        val blocked = assertInstanceOf(PacketFlyPlanResult.Blocked::class.java, result)
        assertEquals(PacketFlyPlanBlockReason.SERVER_PACKET_WINDOW_EXCEEDED, blocked.reason)
    }

    @Test
    fun `nonzero plans reject unsupported packet limits and an unreserved Vanilla endpoint`() {
        val invalidAccounting = listOf(
            PacketFlyPacketAccounting(0, 0, true, 0, maxPackets = 1),
            PacketFlyPacketAccounting(0, 0, true, 0, maxPackets = 513),
            PacketFlyPacketAccounting(0, 0, false, 0, maxPackets = 128),
        )

        invalidAccounting.forEach { accounting ->
            val result = PacketFlyPlanner.safe(request(
                Vec3.ZERO,
                Vec3(1.0, 0.0, 0.0),
                accounting = accounting,
            ))
            val blocked = assertInstanceOf(PacketFlyPlanResult.Blocked::class.java, result)
            assertEquals(PacketFlyPlanBlockReason.INVALID_PACKET_ACCOUNTING, blocked.reason)
        }
    }

    private fun request(
        start: Vec3,
        requestedEnd: Vec3,
        fallFlying: Boolean = false,
        maxPackets: Int = 128,
        accounting: PacketFlyPacketAccounting = PacketFlyPacketAccounting(
            existingPreFinalPackets = 0,
            forecastNoFallPackets = 0,
            vanillaFinalPacketReserved = true,
            reservedPacketsAfterFinal = 0,
            maxPackets = maxPackets,
        ),
    ) = PacketFlyPlanRequest(
        start = start,
        requestedEnd = requestedEnd,
        serverPhysicsVelocity = Vec3.ZERO,
        fallFlying = fallFlying,
        packetAccounting = accounting,
    )

    private fun ready(result: PacketFlyPlanResult): PacketFlyPacketPlan = assertInstanceOf(
        PacketFlyPlanResult.Ready::class.java,
        result,
    ).plan

    private fun PacketFlyPacketPlan.positionEndpoints(): List<Vec3> = auxiliaryPackets.map { packet ->
        assertInstanceOf(PacketFlyAuxiliaryPacketPlan.Position::class.java, packet).endpoint
    }

    private fun assertBoundedCollinearSegments(
        start: Vec3,
        endpoints: List<Vec3>,
        direction: Vec3,
        budget: Double,
    ) {
        var previous = start
        endpoints.forEach { endpoint ->
            val segment = endpoint.subtract(previous)
            assertTrue(segment.length() <= budget + EPSILON)
            assertTrue(segment.cross(direction).lengthSqr() < EPSILON_SQUARED)
            assertTrue(segment.dot(direction) > 0.0)
            previous = endpoint
        }
    }

    private companion object {
        const val EPSILON = 1.0E-9
        const val EPSILON_SQUARED = 1.0E-12
    }
}
