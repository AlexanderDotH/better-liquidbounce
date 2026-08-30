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

class SpearKillSameTickBurstValidatesCumulativeAccelerationDefersTest {

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
}
