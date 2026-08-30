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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.startChainedOutbound

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

class RemoteKillSetbackRecoveryCancelsDeferredStrikeRetainsOwnershipTest {

    @Test
    fun `setback recovery cancels a deferred strike and retains ownership until exact return`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter(ArrayDeque(listOf(RemoteKillStrikeResult.Deferred)))
        val engine = RemoteKillRouteEngine(session, adapter)
        engine.start("target", routeRequest())
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        assertTrue(engine.awaitingStrike)

        engine.beginPacketExactRecoveryFrom(
            authoritativeOffset = session.committedOffset,
            recoveryMovements = listOf(Vec3(-2.0, 0.0, 0.0)),
        )

        assertFalse(engine.awaitingStrike)
        assertTrue(engine.ownsMovement)
        assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)
        assertVec3Equals(Vec3.ZERO, engine.prepareNextStep()!!, 1.0E-12)
        assertVec3Equals(Vec3(-2.0, 0.0, 0.0), session.pendingMovement!!, 1.0E-12)
        engine.confirmStep(delivered = true)
        assertFalse(engine.ownsMovement)
        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
    }

    @Test
    fun `endpoint handoff commits the chained target without losing the original return`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter()
        val engine = RemoteKillRouteEngine(session, adapter)
        val first = RemoteKillRouteRequest(Vec3.ZERO, listOf(Vec3(2.0, 0.0, 0.0)))
        engine.start("first", first)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        val second = RemoteKillRouteRequest(first.endpoint, listOf(Vec3(1.0, 0.0, 0.0)))

        assertTrue(session.startChainedOutbound(second.outboundMovements, strikeHoldTicks = 0))
        engine.handoff("second", second)
        engine.prepareNextStep()
        assertEquals(RemoteKillStrikeResult.Committed, engine.confirmStep(delivered = true))

        assertEquals(listOf("first", "second"), adapter.requests.map { it.target })
        assertVec3Equals(second.endpoint, adapter.requests.last().endpoint, 1.0E-12)
        val recovery = session.exactRecoveryMovementsFrom(session.committedOffset)!!
        assertVec3Equals(Vec3.ZERO, recovery.fold(session.committedOffset, Vec3::add), 1.0E-12)
        engine.clear()
    }

    @Test
    fun `abort before delivery clears without inventing recovery movement`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter())

        engine.start("target", routeRequest())
        engine.prepareNextStep()
        engine.abort()

        assertFalse(engine.ownsMovement)
        assertFalse(session.active)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
    }

    @Test
    fun `abort retraces only confirmed outbound movement`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter())
        val request = RemoteKillRouteRequest(
            origin = Vec3.ZERO,
            outboundMovements = listOf(Vec3(1.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0)),
        )
        engine.start("target", request)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        engine.prepareNextStep()

        engine.abort()

        assertTrue(engine.ownsMovement)
        assertVec3Equals(Vec3.ZERO, engine.prepareNextStep()!!, 1.0E-12)
        assertVec3Equals(Vec3(-1.0, 0.0, 0.0), session.pendingMovement!!, 1.0E-12)
        engine.confirmStep(delivered = true)
        assertFalse(engine.ownsMovement)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
    }

    @Test
    fun `clear releases target request and packet ownership`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val adapter = RecordingWeaponAdapter()
        val engine = RemoteKillRouteEngine(session, adapter)
        val request = routeRequest()

        engine.start("target", request)
        assertSame(request, engine.activeRequest)
        assertEquals("target", engine.activeTarget)

        engine.clear()

        assertFalse(engine.ownsMovement)
        assertNull(engine.activeRequest)
        assertNull(engine.activeTarget)
        assertFalse(session.active)
    }
}
