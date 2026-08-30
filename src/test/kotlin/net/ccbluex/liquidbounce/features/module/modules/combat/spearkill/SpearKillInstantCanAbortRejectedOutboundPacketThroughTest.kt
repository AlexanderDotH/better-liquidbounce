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

class SpearKillInstantCanAbortRejectedOutboundPacketThroughTest {

    @Test
    fun `Instant can abort a rejected outbound packet through the confirmed exact return prefix`() {
        val route = exactRoundTrip(
            listOf(
                Vec3(8.0, 0.0, 0.0),
                Vec3(6.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
            ),
        )
        val burst = requireNotNull(buildSpearKillInstantPacketBurst(route, maxPackets = 6))
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        startSpearKillInstantPacketSession(session, burst)

        repeat(2) {
            assertNotNull(session.prepareNextStep())
            session.confirmStep(delivered = true)
        }
        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = false)

        session.beginExactReturn()
        assertNull(session.consumePhysicalPositionOffset())
        val recovery = mutableListOf<Vec3>()
        while (session.active) {
            assertNotNull(session.prepareNextStep())
            recovery += requireNotNull(session.pendingMovement)
            assertTrue(session.recovering)
            session.confirmStep(delivered = true)
            assertNull(session.consumePhysicalPositionOffset())
        }

        assertEquals(listOf(Vec3(-6.0, 0.0, 0.0), Vec3(-8.0, 0.0, 0.0)), recovery)
        assertEquals(Vec3.ZERO, session.committedOffset)
    }

    @Test
    fun `Instant replans a rejected return immediately instead of stalling airborne`() {
        assertEquals(
            SpearKillInstantRejectedStepAction.REPLAN_RETURN,
            resolveSpearKillInstantRejectedStepAction(outboundStep = false, recovering = true),
        )
        assertEquals(
            SpearKillInstantRejectedStepAction.TERMINATE_OUTBOUND,
            resolveSpearKillInstantRejectedStepAction(outboundStep = true, recovering = false),
        )
        assertEquals(
            SpearKillInstantRejectedStepAction.PAUSE,
            resolveSpearKillInstantRejectedStepAction(outboundStep = true, recovering = true),
        )
    }

    @Test
    fun `Safe rejects a blocked corridor while Primed admits the same free endpoint once`() {
        val origin = Vec3.ZERO
        val destination = Vec3(40.0, 0.0, 0.0)
        val blockedCorridor = SpearKillAStarSegmentValidator { _, _ -> false }

        val safe = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(destination),
            maxSpeed = 40.0,
            segmentValidator = blockedCorridor,
        )
        val primed = requireNotNull(buildSpearKillInstantDirectPacketRoute(
            origin = origin,
            destination = destination,
            isEndpointFree = { true },
        ))

        assertNull(safe)
        assertEquals(listOf(Vec3(40.0, 0.0, 0.0)), primed.outboundMovements)
    }

    @Test
    fun `Primed refuses a blocked destination before constructing a partial burst`() {
        val origin = Vec3.ZERO
        val destination = Vec3(100.0, 0.0, 0.0)

        val route = buildSpearKillInstantDirectPacketRoute(
            origin = origin,
            destination = destination,
            isEndpointFree = { it != destination },
        )

        assertNull(route)
    }

    @Test
    fun `Primed attack revalidates target and terminal ray while a move probe needs only its endpoint`() {
        assertFalse(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = true,
            attackTargetPresent = true,
            targetValid = true,
            terminalRaytraceClear = false,
        ))
        assertFalse(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = false,
            terminalRaytraceClear = true,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = true,
            terminalRaytraceClear = false,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = true,
            terminalOutboundStep = true,
            attackTargetPresent = false,
            targetValid = false,
            terminalRaytraceClear = false,
        ))
        assertTrue(isSpearKillPrimedInstantStepAdmissible(
            endpointFree = true,
            outboundStep = false,
            terminalOutboundStep = false,
            attackTargetPresent = true,
            targetValid = false,
            terminalRaytraceClear = false,
        ))
    }

    @Test
    fun `Instant direct route contains one lunge and its exact inverse only`() {
        val movement = Vec3(75.0, -4.0, 3.0)
        val route = requireNotNull(buildSpearKillInstantDirectPacketRoute(
            origin = Vec3.ZERO,
            destination = movement,
            isEndpointFree = { true },
        ))

        assertEquals(listOf(movement), route.outboundMovements)
        assertEquals(listOf(movement, movement.scale(-1.0), Vec3.ZERO), route.roundTripMovements)
    }

    @Test
    fun `reported ninety nine block lunge reaches its endpoint in one outbound tick`() {
        val route = requireNotNull(buildSpearKillInstantDirectPacketRoute(
            origin = Vec3.ZERO,
            destination = Vec3(99.305, 0.0, 0.0),
            isEndpointFree = { true },
        ))

        assertEquals(1, route.outboundTickCount)
        assertEquals(Vec3(99.305, 0.0, 0.0), route.outboundMovements.single())
        assertEquals(
            1,
            spearKillDirectRouteHitTicks(
                routingMode = SpearKillRoutingMode.INSTANT,
                outboundTickCount = route.outboundTickCount,
                stepWaitTicks = 0,
                strikeHoldTicks = SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS,
            ),
        )
    }
}
