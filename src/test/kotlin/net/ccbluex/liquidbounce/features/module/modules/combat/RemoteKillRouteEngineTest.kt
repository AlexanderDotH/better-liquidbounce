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

class RemoteKillRouteEngineTest {

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
        val firstSession = SpearKillPacketBootSession()
        val first = RemoteKillRouteEngine(firstSession, RecordingWeaponAdapter(), movementOwner = "first-engine")
        val secondSession = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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

    @Test
    fun `failed session start releases request target and movement ownership`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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

    @Test
    fun `setback recovery cancels a deferred strike and retains ownership until exact return`() {
        val initialLeaseCount = RemoteKillMovementOwnership.leaseCount
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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
        val session = SpearKillPacketBootSession()
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

    private fun routeRequest() = RemoteKillRouteRequest(
        origin = Vec3(10.0, 64.0, 10.0),
        outboundMovements = listOf(Vec3(2.0, 0.0, 0.0)),
        physicalReturn = false,
    )

    private class RecordingWeaponAdapter(
        private val results: ArrayDeque<RemoteKillStrikeResult> =
            ArrayDeque(listOf(RemoteKillStrikeResult.Committed)),
    ) : RemoteKillWeaponAdapter<String> {

        val requests = mutableListOf<RemoteKillStrikeRequest<String>>()

        override fun strike(request: RemoteKillStrikeRequest<String>): RemoteKillStrikeResult {
            requests += request
            return results.removeFirstOrNull() ?: RemoteKillStrikeResult.Committed
        }
    }
}
