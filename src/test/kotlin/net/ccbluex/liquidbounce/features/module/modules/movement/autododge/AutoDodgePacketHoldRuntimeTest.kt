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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoDodgePacketHoldRuntimeTest {

    @Test
    fun `future impact arms a precomputed plan without taking ownership or suppressing movement`() {
        val runtime = AutoDodgePacketRuntime()
        val candidate = AutoDodgePacketCandidate(
            threatKey = AutoDodgePacketThreatKey(AutoDodgePacketThreatType.PROJECTILE, entityId = 12),
            impactSchedule = AutoDodgePacketImpactSchedule(
                predictedImpactTick = 20,
                dodgeAtTick = 18,
                returnNotBeforeTick = 22,
            ),
            destination = DESTINATION.position,
        )

        runtime.arm(candidate)

        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeState.ARMED, runtime.debug.state)
        assertEquals(20L, runtime.debug.predictedImpactTick)
        assertEquals(18L, runtime.debug.dodgeAtTick)
        assertEquals(DESTINATION.position, runtime.debug.destination)
    }

    @Test
    fun `two tick hold sends destination now and exact origin only after both ticks`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()

        assertTrue(runtime.start(request(tick = 10, holdTicks = 2), { ORIGIN }, { lease }, READY, sent::add))

        assertEquals(1, sent.size)
        assertEndpoint(DESTINATION, sent.single())
        assertTrue(runtime.suppressesMovementPackets)
        assertEquals(0, lease.closeCount)
        assertEquals(AutoDodgePacketRuntimeState.HOLDING, runtime.debug.state)
        assertEquals(12L, runtime.debug.holdUntilTick)

        assertTrue(runtime.progressHold(tick = 11, preflight = READY, sendPacket = sent::add))
        assertEquals(1, sent.size)
        assertTrue(runtime.suppressesMovementPackets)
        assertEquals(0, lease.closeCount)

        assertTrue(runtime.progressHold(tick = 12, preflight = READY, sendPacket = sent::add))
        assertEquals(2, sent.size)
        assertEndpoint(ORIGIN, sent.last())
        assertTrue(sent.all { it.hasPosition() && !it.hasRotation() })
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(1, lease.closeCount)
        assertEquals(AutoDodgePacketRuntimeState.RETURNED, runtime.debug.state)
        assertEquals(12L, runtime.debug.lastSuccessfulBurstTick)
        assertNull(runtime.debug.holdUntilTick)
    }

    @Test
    fun `one tick hold cannot return in the start tick`() {
        val runtime = AutoDodgePacketRuntime()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()

        assertTrue(runtime.start(request(tick = 20, holdTicks = 1), { ORIGIN }, ::RecordingLease, READY, sent::add))
        assertTrue(runtime.progressHold(tick = 20, preflight = READY, sendPacket = sent::add))

        assertEquals(1, sent.size)
        assertTrue(runtime.suppressesMovementPackets)

        assertTrue(runtime.progressHold(tick = 21, preflight = READY, sendPacket = sent::add))
        assertEquals(2, sent.size)
        assertFalse(runtime.suppressesMovementPackets)
    }

    @Test
    fun `predicted impact sets an absolute minimum return tick`() {
        val runtime = AutoDodgePacketRuntime()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()

        assertTrue(runtime.start(
            request(tick = 80, holdTicks = 2, predictedImpactTick = 83),
            { ORIGIN },
            ::RecordingLease,
            READY,
            sent::add,
        ))

        assertEquals(85L, runtime.debug.holdUntilTick)
        assertTrue(runtime.progressHold(tick = 84, preflight = READY, sendPacket = sent::add))
        assertEquals(1, sent.size)
        assertTrue(runtime.progressHold(tick = 85, preflight = READY, sendPacket = sent::add))
        assertEquals(2, sent.size)
        assertEndpoint(ORIGIN, sent.last())
    }

    @Test
    fun `matching rolling melee forecast extends hold while a threat remains active`() {
        val runtime = AutoDodgePacketRuntime()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()
        val key = AutoDodgePacketThreatKey(AutoDodgePacketThreatType.MACE, entityId = 7)

        assertTrue(runtime.start(
            request(
                tick = 90,
                holdTicks = 2,
                threat = AutoDodgePacketThreatType.MACE,
                threatEntityId = 7,
                predictedImpactTick = 91,
            ),
            { ORIGIN },
            ::RecordingLease,
            READY,
            sent::add,
        ))
        assertTrue(runtime.extendHold(key, predictedImpactTick = 93, postImpactHoldTicks = 2))

        assertEquals(95L, runtime.debug.holdUntilTick)
        assertTrue(runtime.progressHold(tick = 93, preflight = READY, sendPacket = sent::add))
        assertEquals(1, sent.size)
        assertTrue(runtime.progressHold(tick = 95, preflight = READY, sendPacket = sent::add))
        assertEquals(2, sent.size)
    }

    @Test
    fun `unrelated threat cannot extend an active hold`() {
        val runtime = AutoDodgePacketRuntime()

        assertTrue(runtime.start(
            request(tick = 100, threatEntityId = 4, predictedImpactTick = 101),
            { ORIGIN },
            ::RecordingLease,
            READY,
        ) {})

        assertFalse(runtime.extendHold(
            AutoDodgePacketThreatKey(AutoDodgePacketThreatType.PROJECTILE, entityId = 5),
            predictedImpactTick = 110,
            postImpactHoldTicks = 2,
        ))
        assertEquals(103L, runtime.debug.holdUntilTick)
    }

    @Test
    fun `failed return preflight releases ownership without queuing a partial return`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()

        assertTrue(runtime.start(request(tick = 30, holdTicks = 1), { ORIGIN }, { lease }, READY, sent::add))
        assertTrue(runtime.progressHold(
            tick = 31,
            preflight = { AutoDodgePacketPreflightResult.SAFETY_REJECTED },
            sendPacket = sent::add,
        ))

        assertEquals(1, sent.size)
        assertEndpoint(DESTINATION, sent.single())
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(1, lease.closeCount)
        assertEquals(AutoDodgePacketRuntimeState.SAFETY_REJECTED, runtime.debug.state)
        assertNull(runtime.debug.lastSuccessfulBurstTick)
    }

    @Test
    fun `shared cooldown starts only after the delayed return is queued`() {
        val runtime = AutoDodgePacketRuntime()

        assertTrue(runtime.start(request(tick = 40, holdTicks = 1), { ORIGIN }, ::RecordingLease, READY) {})
        assertFalse(runtime.start(
            request(tick = 40, threat = AutoDodgePacketThreatType.MACE),
            { ORIGIN },
            ::RecordingLease,
            READY,
        ) {})
        assertNull(runtime.debug.lastSuccessfulBurstTick)

        assertTrue(runtime.progressHold(tick = 41, preflight = READY) {})
        assertEquals(41L, runtime.debug.lastSuccessfulBurstTick)
        assertFalse(runtime.start(
            request(tick = 41, threat = AutoDodgePacketThreatType.SPEAR),
            { ORIGIN },
            ::RecordingLease,
            READY,
        ) {})
        assertEquals(AutoDodgePacketRuntimeState.COOLDOWN, runtime.debug.state)

        assertTrue(runtime.start(
            request(tick = 42, threat = AutoDodgePacketThreatType.SPEAR),
            { ORIGIN },
            ::RecordingLease,
            READY,
        ) {})
    }

    @Test
    fun `reset during hold releases ownership and clears packet suppression`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()

        assertTrue(runtime.start(request(), { ORIGIN }, { lease }, READY) {})

        runtime.reset()

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeDebug(), runtime.debug)
    }

    @Test
    fun `reset with recovery queues the exact origin before releasing ownership`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()
        val sent = mutableListOf<ServerboundMovePlayerPacket.Pos>()
        assertTrue(runtime.start(request(), { ORIGIN }, { lease }, READY, sent::add))

        runtime.reset(sent::add)

        assertEquals(2, sent.size)
        assertEndpoint(DESTINATION, sent.first())
        assertEndpoint(ORIGIN, sent.last())
        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeDebug(), runtime.debug)
    }

    @Test
    fun `failed reset recovery still releases ownership and clears suppression`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()
        assertTrue(runtime.start(request(), { ORIGIN }, { lease }, READY) {})

        runtime.reset { error("connection closed during reset") }

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeDebug(), runtime.debug)
    }

    @Test
    fun `failed initial preflight sends nothing and releases ownership`() {
        AutoDodgePacketPreflightResult.entries
            .filterNot { it == AutoDodgePacketPreflightResult.READY }
            .forEach { rejection ->
                val runtime = AutoDodgePacketRuntime()
                val lease = RecordingLease()
                var sent = false

                assertFalse(runtime.start(request(), { ORIGIN }, { lease }, { rejection }) { sent = true })

                assertFalse(sent)
                assertEquals(1, lease.closeCount)
                assertFalse(runtime.suppressesMovementPackets)
                assertNull(runtime.debug.lastSuccessfulBurstTick)
            }
    }

    @Test
    fun `movement lease conflict sends nothing and does not start a hold`() {
        val runtime = AutoDodgePacketRuntime()
        var preflightCalled = false
        var sent = false

        assertFalse(runtime.start(
            request = request(),
            snapshotOrigin = { ORIGIN },
            acquireMovementLease = { null },
            preflight = {
                preflightCalled = true
                AutoDodgePacketPreflightResult.READY
            },
            sendPacket = { sent = true },
        ))

        assertFalse(preflightCalled)
        assertFalse(sent)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeState.LEASE_UNAVAILABLE, runtime.debug.state)
    }

    @Test
    fun `no-op destination is rejected before preflight and transmission`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()
        var preflightCalled = false
        var sent = false

        assertFalse(runtime.start(
            request = request(destination = ORIGIN),
            snapshotOrigin = { ORIGIN },
            acquireMovementLease = { lease },
            preflight = {
                preflightCalled = true
                AutoDodgePacketPreflightResult.READY
            },
            sendPacket = { sent = true },
        ))

        assertFalse(preflightCalled)
        assertFalse(sent)
        assertEquals(1, lease.closeCount)
        assertEquals(AutoDodgePacketRuntimeState.BURST_REJECTED, runtime.debug.state)
    }

    @Test
    fun `destination send failure releases ownership without starting cooldown`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()

        assertFailsWith<IllegalStateException> {
            runtime.start(request(tick = 50), { ORIGIN }, { lease }, READY) {
                error("destination queue failed")
            }
        }

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeState.SEND_FAILED, runtime.debug.state)
        assertNull(runtime.debug.lastSuccessfulBurstTick)
    }

    @Test
    fun `return send failure releases ownership without starting cooldown`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()

        assertTrue(runtime.start(request(tick = 60, holdTicks = 1), { ORIGIN }, { lease }, READY) {})
        assertFailsWith<IllegalStateException> {
            runtime.progressHold(tick = 61, preflight = READY) {
                error("return queue failed")
            }
        }

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
        assertEquals(AutoDodgePacketRuntimeState.SEND_FAILED, runtime.debug.state)
        assertNull(runtime.debug.lastSuccessfulBurstTick)
    }

    @Test
    fun `initial preflight exception releases ownership before propagating`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()

        assertFailsWith<IllegalStateException> {
            runtime.start(request(), { ORIGIN }, { lease }, { error("initial preflight failed") }) {}
        }

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
    }

    @Test
    fun `held return preflight exception releases ownership before propagating`() {
        val runtime = AutoDodgePacketRuntime()
        val lease = RecordingLease()

        assertTrue(runtime.start(request(), { ORIGIN }, { lease }, READY) {})
        assertFailsWith<IllegalStateException> {
            runtime.progressHold(tick = 11, preflight = { error("return preflight failed") }) {}
        }

        assertEquals(1, lease.closeCount)
        assertFalse(runtime.suppressesMovementPackets)
    }

    private fun request(
        tick: Long = 10,
        holdTicks: Int = 2,
        threat: AutoDodgePacketThreatType = AutoDodgePacketThreatType.PROJECTILE,
        threatEntityId: Int = 1,
        predictedImpactTick: Long = tick,
        destination: AutoDodgePacketEndpoint = DESTINATION,
    ) = AutoDodgePacketRuntimeRequest(
        tick = tick,
        cooldownTicks = 1,
        holdTicks = holdTicks,
        selectedThreat = threat,
        threatEntityId = threatEntityId,
        predictedImpactTick = predictedImpactTick,
        destination = destination,
    )

    private fun assertEndpoint(expected: AutoDodgePacketEndpoint, packet: ServerboundMovePlayerPacket) {
        assertEquals(expected.position.x, packet.getX(0.0))
        assertEquals(expected.position.y, packet.getY(0.0))
        assertEquals(expected.position.z, packet.getZ(0.0))
        assertEquals(expected.onGround, packet.isOnGround)
        assertEquals(expected.horizontalCollision, packet.horizontalCollision())
    }

    private class RecordingLease : AutoCloseable {
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
        }
    }

    private companion object {
        val READY: (AutoDodgePacketBurst) -> AutoDodgePacketPreflightResult = {
            AutoDodgePacketPreflightResult.READY
        }
        val ORIGIN = AutoDodgePacketEndpoint(Vec3(2.25, 64.0, -3.5), true, false)
        val DESTINATION = AutoDodgePacketEndpoint(Vec3(3.75, 64.0, -3.5), true, false)
    }
}
