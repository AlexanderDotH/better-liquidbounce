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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.additions.resolveServerboundPlayerInputSneak
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlyVanillaPacketRuntimeTest {
    @Test
    fun `ineligible player resolves no nofall action even when both packets are due`() {
        assertEquals(
            VanillaFlyNoFallAction.NONE,
            VanillaFlyNoFall.resolveAction(
                eligible = false,
                nearGround = true,
                groundPacketDue = true,
                packetJumpDue = true,
            )
        )
    }

    @Test
    fun `near ground uses only the protected ground packet when both strategies are due`() {
        assertEquals(
            VanillaFlyNoFallAction.GROUND_PACKET,
            VanillaFlyNoFall.resolveAction(
                eligible = true,
                nearGround = true,
                groundPacketDue = true,
                packetJumpDue = true,
            )
        )
    }

    @Test
    fun `near ground has no packet jump fallback before the smart threshold`() {
        assertEquals(
            VanillaFlyNoFallAction.NONE,
            VanillaFlyNoFall.resolveAction(
                eligible = true,
                nearGround = true,
                groundPacketDue = false,
                packetJumpDue = true,
            )
        )
    }

    @Test
    fun `void or ground farther than ten blocks retains packet jump`() {
        assertEquals(
            VanillaFlyNoFallAction.PACKET_JUMP,
            VanillaFlyNoFall.resolveAction(
                eligible = true,
                nearGround = false,
                groundPacketDue = true,
                packetJumpDue = true,
            )
        )
    }

    @Test
    fun `packet jump changes only full packet Y by one billionth`() {
        val packet = ServerboundMovePlayerPacket.PosRot(
            12.5,
            90.0,
            -4.25,
            87.5f,
            -33.25f,
            false,
            true,
        )

        VanillaFlyNoFall.applyPacketJump(packet)

        assertEquals(12.5, packet.x)
        assertEquals(90.000000001, packet.y, 1e-12)
        assertEquals(-4.25, packet.z)
        assertEquals(87.5f, packet.yRot)
        assertEquals(-33.25f, packet.xRot)
        assertFalse(packet.isOnGround)
        assertTrue(packet.horizontalCollision())
        assertTrue(packet.hasPos)
        assertTrue(packet.hasRot)
    }

    @Test
    fun `hybrid nofall always generates full movement packets`() {
        assertEquals(MovePacketType.FULL, VanillaFlyNoFall.packetType)
    }

    @Test
    fun `ground send protects the exact packet synchronously then discards it`() {
        val tracker = GroundPacketDeliveryTracker()
        val packet = ServerboundMovePlayerPacket.StatusOnly(false, false)
        var sendObserved = false

        VanillaFlyNoFall.sendProtectedGroundPacket(tracker, packet) { sentPacket ->
            assertSame(packet, sentPacket)
            assertTrue(sentPacket.isOnGround)
            assertEquals(1, tracker.pendingCount)
            sendObserved = true
        }

        assertTrue(sendObserved)
        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `only delivered grounded packet confirmation permits fall distance reset`() {
        val tracker = GroundPacketDeliveryTracker()
        var resetCount = 0
        val cancelledPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        tracker.protect(cancelledPacket)

        val cancelledConfirmed = VanillaFlyNoFall.confirmGroundPacketDelivery(
            tracker = tracker,
            packet = cancelledPacket,
            cancelled = true,
        )
        if (cancelledConfirmed) {
            resetCount++
        }

        assertFalse(cancelledConfirmed)
        assertEquals(0, resetCount)
        assertEquals(0, tracker.pendingCount)

        val deliveredPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        tracker.protect(deliveredPacket)
        val deliveredConfirmed = VanillaFlyNoFall.confirmGroundPacketDelivery(
            tracker = tracker,
            packet = deliveredPacket,
            cancelled = false,
        )
        if (deliveredConfirmed) {
            resetCount++
        }

        assertTrue(deliveredConfirmed)
        assertEquals(1, resetCount)
        assertEquals(0, tracker.pendingCount)
    }

    @Test
    fun `packet bypass is emitted only after vanilla movement packet`() {
        assertFalse(
            shouldSendVanillaFlyPacketBypass(
                eventState = EventState.PRE,
                enabled = true,
                tickCount = 40,
                configuredMode = VanillaFlyCheckBypassMode.PACKET,
                isFallFlying = false,
            )
        )
        assertTrue(
            shouldSendVanillaFlyPacketBypass(
                eventState = EventState.POST,
                enabled = true,
                tickCount = 40,
                configuredMode = VanillaFlyCheckBypassMode.PACKET,
                isFallFlying = false,
            )
        )
    }

    @Test
    fun `packet bypass mode remains packet mode during normal flight`() {
        val resolvedMode = resolveVanillaFlyCheckBypassMode(
            configuredMode = VanillaFlyCheckBypassMode.PACKET,
            isFallFlying = false,
        )

        assertEquals(VanillaFlyCheckBypassMode.PACKET, resolvedMode)
    }

    @Test
    fun `motion bypass mode remains motion mode during normal flight`() {
        val resolvedMode = resolveVanillaFlyCheckBypassMode(
            configuredMode = VanillaFlyCheckBypassMode.MOTION,
            isFallFlying = false,
        )

        assertEquals(VanillaFlyCheckBypassMode.MOTION, resolvedMode)
    }

    @Test
    fun `elytra flight forces the bypass to use packets`() {
        val resolvedMode = resolveVanillaFlyCheckBypassMode(
            configuredMode = VanillaFlyCheckBypassMode.MOTION,
            isFallFlying = true,
        )

        assertEquals(VanillaFlyCheckBypassMode.PACKET, resolvedMode)
    }

    @Test
    fun `elytra gravity is replaced with neutral vanilla fly motion`() {
        val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
            isFallFlying = true,
            movementY = -0.08,
            requestedVerticalMotion = 0.0,
        )

        assertEquals(0.0, resolvedMotion)
    }

    @Test
    fun `elytra downward correction updates movement and retained velocity`() {
        val event = PlayerMoveEvent(MoverType.SELF, Vec3(1.25, -0.08, -2.5))
        var retainedVelocityY = event.movement.y

        applyVanillaFlyElytraVerticalMotion(
            event = event,
            isFallFlying = true,
            requestedVerticalMotion = 0.0,
        ) { retainedVelocityY = it }

        assertEquals(Vec3(1.25, 0.0, -2.5), event.movement)
        assertEquals(0.0, retainedVelocityY)
    }

    @Test
    fun `elytra flight preserves intentional vanilla fly descent`() {
        val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
            isFallFlying = true,
            movementY = -0.08,
            requestedVerticalMotion = -0.44,
        )

        assertEquals(-0.44, resolvedMotion)
    }

    @Test
    fun `elytra flight preserves upward motion`() {
        val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
            isFallFlying = true,
            movementY = 0.25,
            requestedVerticalMotion = 0.0,
        )

        assertEquals(0.25, resolvedMotion)
    }

    @Test
    fun `normal flight does not rewrite downward motion`() {
        val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
            isFallFlying = false,
            movementY = -0.08,
            requestedVerticalMotion = 0.0,
        )

        assertEquals(-0.08, resolvedMotion)
    }
}
