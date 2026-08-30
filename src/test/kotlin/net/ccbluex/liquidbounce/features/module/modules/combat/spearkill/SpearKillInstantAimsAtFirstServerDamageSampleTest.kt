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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillInstantAimsAtFirstServerDamageSampleTest {

    @Test
    fun `Instant aims at the first server damage sample instead of the end of its hold`() {
        assertEquals(0, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 0))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 1))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 2))
        assertEquals(1, spearKillInstantAimPredictionTicks(serverEvaluationTicks = 20))
    }

    @Test
    fun `Instant keeps every owned movement grounded while other routes retain collision ground`() {
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.INSTANT, physicallyNearGround = false))
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.INSTANT, physicallyNearGround = true))
        assertFalse(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.DIRECT, physicallyNearGround = false))
        assertTrue(resolveSpearKillOwnedPacketGrounded(SpearKillRoutingMode.DIRECT, physicallyNearGround = true))
    }

    @Test
    fun `Instant keeps correction-window movement grounded between route ticks`() {
        assertTrue(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.INSTANT,
            ownsMovementWindow = true,
        ))
        assertFalse(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.INSTANT,
            ownsMovementWindow = false,
        ))
        assertFalse(shouldProtectSpearKillInstantGround(
            routingMode = SpearKillRoutingMode.DIRECT,
            ownsMovementWindow = true,
        ))
    }

    @Test
    fun `Instant holds its terminal outbound across a full server damage window before returning`() {
        val outbound = listOf(
            Vec3(10.0, 0.0, 0.0),
            Vec3(10.0, -1.0, 0.0),
            Vec3(4.0, 0.0, 0.0),
        )
        val route = exactRoundTrip(outbound)
        val burst = requireNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())

        startSpearKillInstantPacketSession(session, burst)

        val deliveredOutbound = mutableListOf<Vec3>()
        repeat(outbound.size) { index ->
            assertNotNull(session.prepareNextStep())
            val movement = requireNotNull(session.pendingMovement)
            assertTrue(session.pendingOutboundStep)
            assertEquals(index == outbound.lastIndex, session.pendingFinalOutboundStep)
            deliveredOutbound += movement
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        assertTrue(outbound.zip(deliveredOutbound).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        val terminalOffset = outbound.fold(Vec3.ZERO, Vec3::add)
        assertEquals(terminalOffset, session.committedOffset)
        assertTrue(session.holdingStrike)
        assertEquals(2, SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS)
        assertEquals(1, SPEAR_KILL_INSTANT_DAMAGE_SAMPLE_TICKS)
        repeat(SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS) {
            assertNull(session.prepareNextStep())
            assertTrue(session.holdingStrike)
        }

        val deliveredReturn = mutableListOf<Vec3>()
        while (session.active) {
            assertNotNull(session.prepareNextStep())
            deliveredReturn += requireNotNull(session.pendingMovement)
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        val expectedReturn = outbound.asReversed().map { it.scale(-1.0) }
        assertEquals(expectedReturn.size, deliveredReturn.size)
        assertTrue(expectedReturn.zip(deliveredReturn).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        assertEquals(Vec3.ZERO, session.committedOffset)
        assertFalse(session.active)
    }

    @Test
    fun `Instant refuses a round trip that exceeds its configured packet cap`() {
        val route = exactRoundTrip(
            listOf(
                Vec3(10.0, 0.0, 0.0),
                Vec3(10.0, 0.0, 0.0),
                Vec3(10.0, 0.0, 0.0),
            ),
        )

        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 5))
        assertNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 513))
    }

    @Test
    fun `Instant stabilizes a dangerous descent with the Direct nofall trick`() {
        val plan = readyFallPlan(
            movements = listOf(
                Vec3(2.0, -2.0, 0.0),
                Vec3(2.0, -2.0, 0.0),
                Vec3(-2.0, 2.0, 0.0),
                Vec3(-2.0, 2.0, 0.0),
            ),
            groundedSteps = listOf(false, false, false, true),
            outboundSteps = 2,
        )
        val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan) }
        val firstDescent = plan.steps[0].movement
        val secondDescent = plan.steps[1].movement

        assertFalse(lifecycle.shouldStabilizePendingMovement(firstDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmMovement(firstDescent, delivered = true))
        assertTrue(lifecycle.shouldStabilizePendingMovement(secondDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmStabilization(delivered = true))
        assertFalse(lifecycle.shouldStabilizePendingMovement(secondDescent, physicalFallDanger = false))
        assertTrue(lifecycle.confirmMovement(secondDescent, delivered = true))
        assertEquals(2, lifecycle.confirmedMovementCount)
    }

    @Test
    fun `Instant refreshes one expired prehold before emitting its outbound burst`() {
        assertEquals(
            SpearKillInstantChargeAction.READY,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 8,
                delayTicks = 3,
                damageUseDuration = 20,
                hitTicks = 1,
            ),
        )
        assertEquals(
            SpearKillInstantChargeAction.REFRESH,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 20,
                delayTicks = 3,
                damageUseDuration = 20,
                hitTicks = 1,
            ),
        )
        assertEquals(
            SpearKillInstantChargeAction.READY,
            resolveSpearKillInstantChargeAction(
                ticksUsingItem = 3,
                delayTicks = 3,
                damageUseDuration = 4,
                hitTicks = 1,
            ),
        )
    }

    @Test
    fun `Instant rejects a return that is not the exact inverse of the outbound route`() {
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(Vec3(5.0, 0.0, 0.0)),
            roundTripMovements = listOf(
                Vec3(5.0, 0.0, 0.0),
                Vec3(-4.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
        )

        assertNull(buildSpearKillInstantPacketBurst(route, maxPackets = 2))
    }
}
