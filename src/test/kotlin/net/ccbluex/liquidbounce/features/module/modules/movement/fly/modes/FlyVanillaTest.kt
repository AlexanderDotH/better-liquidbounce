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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlyVanillaTest {

    @Test
    fun `shift descent stays local instead of changing the server collision pose`() {
        assertTrue(
            shouldSuppressVanillaFlyServerSneak(
                Input(false, false, false, false, false, true, false),
            )
        )
        assertFalse(
            shouldSuppressVanillaFlyServerSneak(
                Input(false, false, false, false, true, true, false),
            )
        )
    }

    @Test
    fun `explicit route sneak wins over local fly shift suppression`() {
        assertFalse(
            resolveServerboundPlayerInputSneak(
                rawSneak = true,
                suppressSneak = true,
                forceSneak = false,
            )
        )
        assertTrue(
            resolveServerboundPlayerInputSneak(
                rawSneak = true,
                suppressSneak = true,
                forceSneak = true,
            )
        )
    }

    @Test
    fun `vanilla check bypass runs every forty ticks only when enabled`() {
        assertFalse(shouldRunVanillaFlyCheckBypass(enabled = false, tickCount = 40))
        assertFalse(shouldRunVanillaFlyCheckBypass(enabled = true, tickCount = 39))
        assertTrue(shouldRunVanillaFlyCheckBypass(enabled = true, tickCount = 40))
    }

    @Test
    fun `vanilla check bypass packet dips only the current player Y`() {
        val packet = ServerboundMovePlayerPacket.Pos(12.5, 90.0, -4.25, true, true)

        applyVanillaFlyCheckBypass(packet, currentY = 64.0)

        assertEquals(12.5, packet.x)
        assertEquals(63.96, packet.y, 1e-9)
        assertEquals(-4.25, packet.z)
        assertTrue(packet.isOnGround)
        assertTrue(packet.horizontalCollision())
    }

    @Test
    fun `packet bypass follows current Y during 3 point 6 block sprint descent`() {
        val previousTickY = 64.0
        val currentY = 60.4

        val bypassY = vanillaFlyCheckBypassY(currentY)

        assertEquals(60.36, bypassY, 1e-9)
        assertNotEquals(previousTickY - 0.04, bypassY, 1e-9)
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
