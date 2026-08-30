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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.consumePhysicalPositionOffset

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

class RemoteKillMovementOwnershipLeaseExclusiveClosesIdempotentlyTest {

    @Test
    fun `movement ownership lease is exclusive and closes idempotently`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val first = RemoteKillMovementOwnership.acquire("test-first")

        try {
            assertTrue(RemoteKillMovementOwnership.active)
            assertEquals("test-first", RemoteKillMovementOwnership.currentOwner)
            assertTrue(first.active)
            assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)
            assertNull(RemoteKillMovementOwnership.tryAcquire("test-second"))
            assertThrows(IllegalStateException::class.java) {
                RemoteKillMovementOwnership.acquire("test-second")
            }

            first.close()
            first.close()

            assertFalse(first.active)
            assertNull(RemoteKillMovementOwnership.currentOwner)
            assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)

            RemoteKillMovementOwnership.acquire("test-second").use { second ->
                assertTrue(second.active)
                assertEquals("test-second", RemoteKillMovementOwnership.currentOwner)
                assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)
            }
            assertNull(RemoteKillMovementOwnership.currentOwner)
        } finally {
            first.close()
        }

        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
    }

    @Test
    fun `second engine cannot start while another route owns movement`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val firstSession = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val first = RemoteKillRouteEngine(firstSession, RecordingWeaponAdapter(), movementOwner = "first-engine")
        val secondSession = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val second = RemoteKillRouteEngine(secondSession, RecordingWeaponAdapter(), movementOwner = "second-engine")

        first.start("first-target", routeRequest())

        assertThrows(IllegalStateException::class.java) {
            second.start("second-target", routeRequest())
        }
        assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)
        assertTrue(first.ownsMovement)
        assertFalse(second.ownsMovement)
        assertFalse(secondSession.active)

        first.clear()
        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
    }

    @Test
    fun `engine clear releases its movement ownership lease`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter(), movementOwner = "test-engine")

        engine.start("target", routeRequest())
        assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)

        engine.clear()

        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
        assertFalse(engine.ownsMovement)
    }

    @Test
    fun `completed exact return releases the engine movement ownership lease`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter(), movementOwner = "test-engine")

        engine.start("target", routeRequest())
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
        assertFalse(engine.ownsMovement)
    }

    @Test
    fun `physical return releases ownership after its final position is consumed`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(session, RecordingWeaponAdapter(), movementOwner = "physical-engine")
        val request = RemoteKillRouteRequest(
            origin = Vec3(10.0, 64.0, 10.0),
            outboundMovements = listOf(Vec3(2.0, 0.0, 0.0)),
            physicalReturn = true,
        )

        engine.start("target", request)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        assertNotNull(session.consumePhysicalPositionOffset())
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        assertTrue(session.active)
        assertTrue(engine.ownsMovement)
        assertNotNull(session.consumePhysicalPositionOffset())
        assertFalse(session.active)

        assertTrue(engine.reconcileCompletedOwnership())
        assertFalse(engine.ownsMovement)
        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
    }

    @Test
    fun `opt in completion retention keeps ownership through a correction confirmation window`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RecordingWeaponAdapter(),
            movementOwner = "retained-engine",
            retainMovementAfterCompletion = true,
        )

        engine.start("target", routeRequest())
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        assertFalse(session.active)
        assertTrue(engine.ownsMovement)
        assertEquals(initialLeaseCount + 1, RemoteKillMovementOwnership.leaseCount)
        assertFalse(engine.reconcileCompletedOwnership())
        assertTrue(engine.ownsMovement)

        engine.beginPacketExactRecoveryFrom(
            authoritativeOffset = Vec3(0.5, 0.0, 0.0),
            recoveryMovements = listOf(Vec3(-0.5, 0.0, 0.0)),
        )
        assertTrue(session.active)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        assertTrue(engine.ownsMovement)

        engine.releaseCompletedOwnership()
        assertFalse(engine.ownsMovement)
        assertEquals(initialLeaseCount, RemoteKillMovementOwnership.leaseCount)
    }

    @Test
    fun `retained ownership cannot be released while recovery is active`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RecordingWeaponAdapter(),
            retainMovementAfterCompletion = true,
        )
        engine.start("target", routeRequest())

        assertThrows(IllegalStateException::class.java) {
            engine.releaseCompletedOwnership()
        }

        engine.clear()
    }
}
