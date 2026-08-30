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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class SpearKillPacketSixResetsSourcePredictionOneCountedTest {

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
}
