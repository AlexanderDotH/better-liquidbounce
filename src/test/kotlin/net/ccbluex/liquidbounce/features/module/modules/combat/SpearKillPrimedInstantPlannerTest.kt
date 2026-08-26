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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class SpearKillPrimedInstantPlannerTest {

    @TestFactory
    fun `explicit priming predicts the complete normal and Elytra research matrix`(): List<DynamicTest> =
        MOVEMENT_PROFILES.flatMap { profile ->
            PRIMING_PACKET_COUNTS.flatMap { primingPackets ->
                REQUESTED_DISTANCES.map { distance ->
                    DynamicTest.dynamicTest(
                        "${profile.name} N=$primingPackets distance=$distance",
                    ) {
                        val result = planExplicitBurst(
                            profile = profile,
                            distance = distance,
                            primingPackets = primingPackets,
                        )
                        val plan = assertInstanceOf(
                            SpearKillPrimedInstantPlanResult.Ready::class.java,
                            result,
                        ).plan
                        val finalPacketOrdinal = primingPackets + 1
                        val countedPackets = if (finalPacketOrdinal > 5) 1 else finalPacketOrdinal
                        val threshold = profile.squaredDistanceThreshold

                        assertEquals(primingPackets, plan.dedicatedPrimingPackets)
                        assertEquals(primingPackets, plan.totalPreFinalPackets)
                        assertEquals(finalPacketOrdinal, plan.finalPacketOrdinal)
                        assertEquals(countedPackets, plan.serverCountedPackets)
                        assertEquals(
                            distance * distance <= threshold * countedPackets,
                            plan.sourcePredictedAccepted,
                        )
                    }
                }
            }
        }

    @Test
    fun `packet six resets the source prediction to one counted packet`() {
        val fifthPacket = readyPlan(distance = 20.0, primingPackets = 4)
        val sixthPacket = readyPlan(distance = 20.0, primingPackets = 5)

        assertEquals(5, fifthPacket.serverCountedPackets)
        assertTrue(fifthPacket.sourcePredictedAccepted)
        assertEquals(1, sixthPacket.serverCountedPackets)
        assertFalse(sixthPacket.sourcePredictedAccepted)
    }

    @Test
    fun `all movement packet shapes consume the same server packet ordinal`() {
        SpearKillPrimedInstantPacketType.entries.forEach { packetType ->
            val result = SpearKillPrimedInstantPlanner.plan(
                request(
                    distance = 20.0,
                    priming = SpearKillPrimedInstantPriming.Explicit(4),
                ).copy(primingPacketType = packetType),
            )
            val plan = assertInstanceOf(SpearKillPrimedInstantPlanResult.Ready::class.java, result).plan
            assertEquals(packetType, plan.primingPacketType)
            assertEquals(5, plan.finalPacketOrdinal)
            assertTrue(plan.sourcePredictedAccepted)
        }
    }

    @Test
    fun `priming and final packet factories preserve the requested wire shape`() {
        val position = Vec3(1.0, 2.0, 3.0)
        val expectedShapes = mapOf(
            SpearKillPrimedInstantPacketType.Position to (true to false),
            SpearKillPrimedInstantPacketType.PositionRotation to (true to true),
            SpearKillPrimedInstantPacketType.Rotation to (false to true),
            SpearKillPrimedInstantPacketType.StatusOnly to (false to false),
        )

        expectedShapes.forEach { (type, shape) ->
            val packet = createSpearKillPrimingPacket(type, position, 30f, 15f, true, false)
            assertEquals(shape.first, packet.hasPosition(), type.name)
            assertEquals(shape.second, packet.hasRotation(), type.name)
        }
        assertTrue(createSpearKillPrimedFinalPacket(
            SpearKillHighSpeedResearchFinalPacketType.POSITION,
            position,
            30f,
            15f,
            true,
            false,
        ).hasPosition())
        assertFalse(createSpearKillPrimedFinalPacket(
            SpearKillHighSpeedResearchFinalPacketType.POSITION,
            position,
            30f,
            15f,
            true,
            false,
        ).hasRotation())
        assertTrue(createSpearKillPrimedFinalPacket(
            SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
            position,
            30f,
            15f,
            true,
            false,
        ).hasRotation())
    }

    @Test
    fun `automatic priming follows the 26_2 required-packet formula and caps at four`() {
        val accepted = readyAutoPlan(distance = 20.0)
        val capped = readyAutoPlan(distance = 30.0)

        assertEquals(4, accepted.requiredServerPackets)
        assertEquals(3, accepted.targetPrimingPackets)
        assertEquals(3, accepted.dedicatedPrimingPackets)
        assertTrue(accepted.sourcePredictedAccepted)
        assertEquals(9, capped.requiredServerPackets)
        assertEquals(4, capped.targetPrimingPackets)
        assertEquals(4, capped.dedicatedPrimingPackets)
        assertFalse(capped.sourcePredictedAccepted)
    }

    @Test
    fun `expected velocity is subtracted before automatic priming is calculated`() {
        val plan = readyAutoPlan(distance = 20.0, expectedVelocitySquared = 100.0)

        assertEquals(3, plan.requiredServerPackets)
        assertEquals(2, plan.targetPrimingPackets)
        assertTrue(plan.sourcePredictedAccepted)
    }

    @Test
    fun `owned and NoFall packets reduce dedicated automatic priming without entering movement history`() {
        val plan = readyAutoPlan(
            distance = 20.0,
            accounting = SpearKillPrimedInstantPacketAccounting(
                ownedPreFinalPackets = 1,
                noFallPreFinalPackets = 2,
                reservedPacketsAfterFinal = 1,
                maxPackets = 5,
            ),
        )

        assertEquals(3, plan.targetPrimingPackets)
        assertEquals(0, plan.dedicatedPrimingPackets)
        assertEquals(3, plan.totalPreFinalPackets)
        assertEquals(5, plan.totalOwnedPacketBudget)
        assertTrue(plan.sourcePredictedAccepted)
    }

    @Test
    fun `automatic priming fails closed when existing packets would make the lunge packet six`() {
        val result = SpearKillPrimedInstantPlanner.plan(
            request(
                distance = 10.0,
                priming = SpearKillPrimedInstantPriming.Auto,
                accounting = SpearKillPrimedInstantPacketAccounting(
                    ownedPreFinalPackets = 4,
                    noFallPreFinalPackets = 1,
                    reservedPacketsAfterFinal = 1,
                    maxPackets = 32,
                ),
            ),
        )

        assertBlocked(result, SpearKillPrimedInstantBlockReason.SERVER_PACKET_WINDOW_EXCEEDED)
    }

    @Test
    fun `same tick burst validates cumulative acceleration and defers before packet six`() {
        val first = planSpearKillPrimedBurstStep(
            windowOrigin = Vec3.ZERO,
            currentPosition = Vec3.ZERO,
            movement = Vec3(3.0, 0.0, 0.0),
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            priming = SpearKillPrimedInstantPriming.Auto,
            packetAccounting = SpearKillPrimedInstantPacketAccounting(0, 0, 0, 512),
            primingPacketType = SpearKillPrimedInstantPacketType.Position,
        )
        val firstPlan = assertInstanceOf(
            SpearKillPrimedBurstStepResult.Send::class.java,
            first,
        ).plan
        assertEquals(1, firstPlan.finalPacketOrdinal)

        val second = planSpearKillPrimedBurstStep(
            windowOrigin = Vec3.ZERO,
            currentPosition = Vec3(3.0, 0.0, 0.0),
            movement = Vec3(17.0, 0.0, 0.0),
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            priming = SpearKillPrimedInstantPriming.Auto,
            packetAccounting = SpearKillPrimedInstantPacketAccounting(1, 0, 0, 512),
            primingPacketType = SpearKillPrimedInstantPacketType.Position,
        )
        val secondPlan = assertInstanceOf(
            SpearKillPrimedBurstStepResult.Send::class.java,
            second,
        ).plan
        assertEquals(20.0, secondPlan.requestedDistance, 1.0E-12)
        assertEquals(4, secondPlan.finalPacketOrdinal)

        val third = planSpearKillPrimedBurstStep(
            windowOrigin = Vec3.ZERO,
            currentPosition = Vec3(20.0, 0.0, 0.0),
            movement = Vec3(5.0, 0.0, 0.0),
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            priming = SpearKillPrimedInstantPriming.Auto,
            packetAccounting = SpearKillPrimedInstantPacketAccounting(4, 0, 0, 512),
            primingPacketType = SpearKillPrimedInstantPacketType.Position,
        )
        assertInstanceOf(SpearKillPrimedBurstStepResult.Defer::class.java, third)
    }

    @Test
    fun `one hop Instant attempts the complete displacement without a paced defer`() {
        val result = planSpearKillPrimedBurstStep(
            windowOrigin = Vec3.ZERO,
            currentPosition = Vec3.ZERO,
            movement = Vec3(99.305, 0.0, 0.0),
            expectedVelocitySquared = 0.0,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            priming = SpearKillPrimedInstantPriming.Auto,
            packetAccounting = SpearKillPrimedInstantPacketAccounting(0, 0, 0, 512),
            primingPacketType = SpearKillPrimedInstantPacketType.Position,
            instantDirectTeleport = true,
        )

        val plan = assertInstanceOf(SpearKillPrimedBurstStepResult.Send::class.java, result).plan
        assertEquals(99.305, plan.requestedDistance, 1.0E-12)
        assertFalse(plan.sourcePredictedAccepted)
    }

    @Test
    fun `packet budget reserves the complete return before admitting any burst`() {
        val accounting = SpearKillPrimedInstantPacketAccounting(
            ownedPreFinalPackets = 0,
            noFallPreFinalPackets = 0,
            reservedPacketsAfterFinal = 1,
            maxPackets = 4,
        )
        val result = SpearKillPrimedInstantPlanner.plan(
            request(distance = 20.0, priming = SpearKillPrimedInstantPriming.Auto, accounting = accounting),
        )

        assertBlocked(result, SpearKillPrimedInstantBlockReason.PACKET_BUDGET_EXCEEDED)
    }

    @Test
    fun `packet accounting and movement inputs fail closed`() {
        val invalidRequests = listOf(
            request(distance = Double.NaN),
            request(distance = Double.MAX_VALUE),
            request(expectedVelocitySquared = -1.0),
            request(priming = SpearKillPrimedInstantPriming.Explicit(-1)),
            request(accounting = SpearKillPrimedInstantPacketAccounting(-1, 0, 0, 10)),
            request(accounting = SpearKillPrimedInstantPacketAccounting(0, -1, 0, 10)),
            request(accounting = SpearKillPrimedInstantPacketAccounting(0, 0, -1, 10)),
            request(accounting = SpearKillPrimedInstantPacketAccounting(0, 0, 0, 0)),
        )

        invalidRequests.forEach { invalid ->
            assertInstanceOf(
                SpearKillPrimedInstantPlanResult.Blocked::class.java,
                SpearKillPrimedInstantPlanner.plan(invalid),
            )
        }
    }

    private fun readyPlan(
        distance: Double,
        primingPackets: Int,
    ): SpearKillPrimedInstantPlan = assertInstanceOf(
        SpearKillPrimedInstantPlanResult.Ready::class.java,
        planExplicitBurst(SpearKillPrimedInstantMovementProfile.NORMAL, distance, primingPackets),
    ).plan

    private fun readyAutoPlan(
        distance: Double,
        expectedVelocitySquared: Double = 0.0,
        accounting: SpearKillPrimedInstantPacketAccounting = DEFAULT_ACCOUNTING,
    ): SpearKillPrimedInstantPlan = assertInstanceOf(
        SpearKillPrimedInstantPlanResult.Ready::class.java,
        SpearKillPrimedInstantPlanner.plan(
            request(
                distance = distance,
                expectedVelocitySquared = expectedVelocitySquared,
                priming = SpearKillPrimedInstantPriming.Auto,
                accounting = accounting,
            ),
        ),
    ).plan

    private fun planExplicitBurst(
        profile: SpearKillPrimedInstantMovementProfile,
        distance: Double,
        primingPackets: Int,
    ) = SpearKillPrimedInstantPlanner.plan(
        request(
            distance = distance,
            profile = profile,
            priming = SpearKillPrimedInstantPriming.Explicit(primingPackets),
        ),
    )

    private fun request(
        distance: Double = 10.0,
        expectedVelocitySquared: Double = 0.0,
        profile: SpearKillPrimedInstantMovementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
        priming: SpearKillPrimedInstantPriming = SpearKillPrimedInstantPriming.Auto,
        accounting: SpearKillPrimedInstantPacketAccounting = DEFAULT_ACCOUNTING,
    ) = SpearKillPrimedInstantPlanRequest(
        requestedDistance = distance,
        expectedVelocitySquared = expectedVelocitySquared,
        movementProfile = profile,
        priming = priming,
        packetAccounting = accounting,
        primingPacketType = SpearKillPrimedInstantPacketType.Position,
    )

    private fun assertBlocked(
        result: SpearKillPrimedInstantPlanResult,
        reason: SpearKillPrimedInstantBlockReason,
    ) {
        val blocked = assertInstanceOf(SpearKillPrimedInstantPlanResult.Blocked::class.java, result)
        assertEquals(reason, blocked.reason)
    }

    private companion object {
        val MOVEMENT_PROFILES = SpearKillPrimedInstantMovementProfile.entries
        val PRIMING_PACKET_COUNTS = listOf(0, 1, 2, 3, 4, 5, 9, 14, 18)
        val REQUESTED_DISTANCES = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 75.0, 100.0, 125.0, 150.0, 200.0)
        val DEFAULT_ACCOUNTING = SpearKillPrimedInstantPacketAccounting(
            ownedPreFinalPackets = 0,
            noFallPreFinalPackets = 0,
            reservedPacketsAfterFinal = 0,
            maxPackets = 512,
        )
    }
}
