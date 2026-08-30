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

class AutoDodgePacketHoldFailureTest {
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
