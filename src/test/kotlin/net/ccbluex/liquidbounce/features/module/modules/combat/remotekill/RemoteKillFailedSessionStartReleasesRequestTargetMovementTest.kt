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

package net.ccbluex.liquidbounce.features.module.modules.combat.remotekill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteKillFailedSessionStartReleasesRequestTargetMovementTest {

    @Test
    fun `failed session start releases request target and movement ownership`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter(), movementOwner = "test-engine")
        val invalidForSession = RemoteKillRouteRequest(
            origin = Vec3.ZERO,
            outboundMovements = listOf(Vec3(1.0, 0.0, 0.0)),
            stepWaitTicks = MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS + 1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            engine.start("target", invalidForSession)
        }

        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
        assertFalse(engine.ownsMovement)
        assertNull(engine.activeRequest)
        assertNull(engine.activeTarget)
        assertFalse(session.active)
    }

    @Test
    fun `shared session admits the full bounded ClipReach research cadence`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter(), movementOwner = "clip-research")
        val request = RemoteKillRouteRequest(
            origin = Vec3.ZERO,
            outboundMovements = listOf(Vec3(1.0, 0.0, 0.0)),
            stepWaitTicks = MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS,
        )

        engine.start("target", request)

        assertTrue(engine.ownsMovement)
        assertTrue(session.active)
        engine.clear()
    }

    @Test
    fun `request derives the endpoint and an exact inverse return`() {
        val origin = Vec3(10.0, 64.0, -4.0)
        val outbound = listOf(
            Vec3(2.0, 1.0, 0.5),
            Vec3(-0.25, 0.0, 3.0),
        )

        val request = RemoteKillRouteRequest(origin, outbound)

        assertVec3Equals(Vec3(11.75, 65.0, -0.5), request.endpoint, 1.0E-12)
        assertEquals(
            listOf(Vec3(0.25, -0.0, -3.0), Vec3(-2.0, -1.0, -0.5)),
            request.returnMovements,
        )
        assertVec3Equals(
            origin,
            request.roundTripMovements.fold(origin, Vec3::add),
            1.0E-12,
        )
        assertEquals(Vec3.ZERO, request.roundTripMovements.last())
    }

    @Test
    fun `request rejects movement that cannot form a safe route contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteKillRouteRequest(Vec3.ZERO, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteKillRouteRequest(Vec3.ZERO, listOf(Vec3.ZERO))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteKillRouteRequest(Vec3.ZERO, listOf(Vec3(Double.NaN, 0.0, 0.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteKillRouteRequest(
                origin = Vec3.ZERO,
                outboundMovements = listOf(Vec3(1.0, 0.0, 0.0)),
                terminalSuffixSteps = 2,
            )
        }
    }

    @Test
    fun `cancelled packet stays pending and cannot commit the strike`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter()
        val engine = RemoteKillRouteEngine(session, adapter)
        val request = routeRequest()

        engine.start("target", request)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), engine.prepareNextStep()!!, 1.0E-12)

        assertNull(engine.confirmStep(delivered = false))
        assertEquals(0, adapter.requests.size)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), engine.prepareNextStep()!!, 1.0E-12)
        assertEquals(RemoteKillStrikeResult.Committed, engine.confirmStep(delivered = true))
        assertEquals(1, adapter.requests.size)
        assertVec3Equals(request.endpoint, adapter.requests.single().endpoint, 1.0E-12)
        engine.clear()
    }

    @Test
    fun `deferred strike owns the endpoint until a retry commits`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter(
            ArrayDeque(listOf(RemoteKillStrikeResult.Deferred, RemoteKillStrikeResult.Committed)),
        )
        val engine = RemoteKillRouteEngine(session, adapter)

        engine.start("target", routeRequest())
        engine.prepareNextStep()
        assertEquals(RemoteKillStrikeResult.Deferred, engine.confirmStep(delivered = true))

        assertTrue(engine.ownsMovement)
        assertTrue(engine.awaitingStrike)
        assertNull(engine.prepareNextStep())
        assertEquals(RemoteKillStrikeResult.Committed, engine.retryStrike())
        assertFalse(engine.awaitingStrike)
        assertVec3Equals(Vec3.ZERO, engine.prepareNextStep()!!, 1.0E-12)
        assertVec3Equals(Vec3(-2.0, 0.0, 0.0), session.pendingMovement!!, 1.0E-12)
        engine.clear()
    }

    @Test
    fun `rejected strike returns by the exact confirmed inverse`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter(
            ArrayDeque(listOf(RemoteKillStrikeResult.Rejected("weapon-unavailable"))),
        )
        val engine = RemoteKillRouteEngine(session, adapter)
        val request = routeRequest()

        engine.start("target", request)
        engine.prepareNextStep()
        assertEquals(
            RemoteKillStrikeResult.Rejected("weapon-unavailable"),
            engine.confirmStep(delivered = true),
        )

        assertVec3Equals(Vec3.ZERO, engine.prepareNextStep()!!, 1.0E-12)
        assertVec3Equals(request.returnMovements.single(), session.pendingMovement!!, 1.0E-12)
        assertNull(engine.confirmStep(delivered = true))
        assertFalse(engine.ownsMovement)
        assertFalse(session.active)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
    }

    @Test
    fun `weapon adapter failure becomes a rejection so exact return remains available`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RemoteKillWeaponAdapter<String> {
                throw IllegalStateException("test adapter failure")
            },
        )
        engine.start("target", routeRequest())
        engine.prepareNextStep()

        assertEquals(
            RemoteKillStrikeResult.Rejected("weapon-adapter-failure"),
            engine.confirmStep(delivered = true),
        )
        assertTrue(engine.ownsMovement)
        assertNotNull(session.exactRecoveryMovementsFrom(session.committedOffset))
        engine.clear()
    }
}
