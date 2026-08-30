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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.planning.calculateSpearKillPrimedInstantMovementBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.planning.calculateSpearKillPrimedInstantSessionBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.planning.startSpearKillInstantPacketSession

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

class SpearKillInstantDirectRouteConstructionValidatesBothEndpointsTest {

    @Test
    fun `Instant direct route construction validates both endpoints`() {
        val inspected = mutableListOf<Vec3>()
        val destination = Vec3(40.0, 0.0, 0.0)
        val route = buildSpearKillInstantDirectPacketRoute(
            origin = Vec3.ZERO,
            destination = destination,
            isEndpointFree = { position -> inspected += position; true },
        )

        assertNotNull(route)
        assertEquals(listOf(Vec3.ZERO, destination), inspected)
    }

    @Test
    fun `Instant direct teleport may attempt a source rejected Primed displacement`() {
        assertTrue(isSpearKillPrimedPlanSendable(
            sourcePredictedAccepted = true,
            instantDirectTeleport = false,
            researchProbe = false,
        ))
        assertFalse(isSpearKillPrimedPlanSendable(
            sourcePredictedAccepted = false,
            instantDirectTeleport = false,
            researchProbe = false,
        ))
        assertTrue(isSpearKillPrimedPlanSendable(
            sourcePredictedAccepted = false,
            instantDirectTeleport = true,
            researchProbe = false,
        ))
        assertTrue(isSpearKillPrimedPlanSendable(
            sourcePredictedAccepted = false,
            instantDirectTeleport = false,
            researchProbe = true,
        ))
    }

    @Test
    fun `only explicit Primed probes retain endpoint-only collision research`() {
        assertFalse(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = true,
            priming = SpearKillPrimedInstantPriming.Auto,
        ))
        assertTrue(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = true,
            priming = SpearKillPrimedInstantPriming.Explicit(4),
        ))
        assertFalse(usesSpearKillPrimedEndpointOnlyPreflight(
            primedInstant = false,
            priming = SpearKillPrimedInstantPriming.Explicit(4),
        ))
    }

    @Test
    fun `Primed budget reserves both movements NoFall and final grounding before admission`() {
        val route = requireNotNull(buildSpearKillInstantDirectPacketRoute(
            origin = Vec3.ZERO,
            destination = Vec3(20.0, 0.0, 0.0),
            isEndpointFree = { true },
        ))

        assertNull(calculateSpearKillPrimedInstantSessionBudget(
            route = route,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 8,
        ))
        val budget = requireNotNull(calculateSpearKillPrimedInstantSessionBudget(
            route = route,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 9,
        ))
        assertEquals(2, budget.movementPackets)
        assertEquals(4, budget.primingPackets)
        assertEquals(2, budget.noFallPacketsReserved)
        assertEquals(0, budget.recoveryConfirmationPacketsReserved)
        assertEquals(1, budget.finalGroundingPacketReserved)
        assertEquals(9, budget.totalPackets)
    }

    @Test
    fun `Primed replacement recovery is rejected before its first packet when remaining budget is too small`() {
        val recovery = listOf(
            Vec3(-20.0, 0.0, 0.0),
            Vec3(-20.0, 0.0, 0.0),
        )

        assertNull(calculateSpearKillPrimedInstantMovementBudget(
            movements = recovery,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 10,
            recoveryConfirmationPackets = 2,
        ))
        val budget = requireNotNull(calculateSpearKillPrimedInstantMovementBudget(
            movements = recovery,
            priming = SpearKillPrimedInstantPriming.Auto,
            movementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
            maxPackets = 11,
            recoveryConfirmationPackets = 2,
        ))
        assertEquals(2, budget.recoveryConfirmationPacketsReserved)
        assertEquals(11, budget.totalPackets)
    }

    @Test
    fun `virtual Instant can chain while retaining inverse history to its original origin`() {
        val initialMovement = Vec3(20.0, 0.0, 0.0)
        val chainedMovement = Vec3(15.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        startSpearKillInstantPacketSession(
            session,
            requireNotNull(buildSpearKillInstantPacketBurst(exactRoundTrip(listOf(initialMovement)), 2)),
        )
        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = true)
        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(chainedMovement),
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ))

        assertNotNull(session.prepareNextStep())
        session.confirmStep(delivered = true)
        repeat(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS) {
            assertNull(session.prepareNextStep())
        }
        val recovery = mutableListOf<Vec3>()
        while (session.active) {
            session.prepareNextStep()?.let {
                recovery += requireNotNull(session.pendingMovement)
                session.confirmStep(delivered = true)
            }
        }

        val expectedRecovery = listOf(chainedMovement.scale(-1.0), initialMovement.scale(-1.0))
        assertTrue(expectedRecovery.zip(recovery).all { (expected, actual) ->
            expected.distanceToSqr(actual) < 1.0E-12
        })
        assertEquals(Vec3.ZERO, session.committedOffset)
    }
}
