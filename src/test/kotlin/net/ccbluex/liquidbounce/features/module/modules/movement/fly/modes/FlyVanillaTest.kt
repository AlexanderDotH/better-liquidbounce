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
    fun `disabled vanilla nofall never owns fall protection`() {
        assertFalse(
            VanillaFlyNoFall.shouldRun(
                enabled = false,
                fallDamagePossible = true,
                remoteKillPacketRouteActive = false,
            )
        )
    }

    @Test
    fun `vanilla nofall runs only when survival fall damage is possible`() {
        assertTrue(
            VanillaFlyNoFall.shouldRun(
                enabled = true,
                fallDamagePossible = true,
                remoteKillPacketRouteActive = false,
            )
        )
        assertFalse(
            VanillaFlyNoFall.shouldRun(
                enabled = true,
                fallDamagePossible = false,
                remoteKillPacketRouteActive = false,
            )
        )
    }

    @Test
    fun `remote kill packet route retains exclusive fall protection ownership`() {
        assertFalse(
            VanillaFlyNoFall.shouldRun(
                enabled = true,
                fallDamagePossible = true,
                remoteKillPacketRouteActive = true,
            )
        )
    }

    @Test
    fun `ground probe includes the ten block boundary with only collision epsilon`() {
        val playerBox = AABB(4.25, 64.0, -3.75, 4.85, 65.8, -3.15)

        val probeBox = VanillaFlyNoFall.groundProbeBox(playerBox)

        assertEquals(4.25, probeBox.minX)
        assertEquals(64.0 - 10.0 - 1.0E-7, probeBox.minY)
        assertEquals(-3.75, probeBox.minZ)
        assertEquals(4.85, probeBox.maxX)
        assertEquals(64.0, probeBox.maxY)
        assertEquals(-3.15, probeBox.maxZ)
        assertTrue(playerBox.minY - probeBox.minY < 10.000001)
    }

    @Test
    fun `ten block sprint descent keeps its endpoint through safe grounded segments`() {
        val state = VanillaFlyServerFallState().apply {
            initialize(position = Vec3(0.0, 100.0, 0.0), fallDistance = 0.0)
        }

        val target = Vec3(10.0, 90.0, -5.0)
        val groundingPositions = state.groundingPositions(
            target = target,
            safeFallDistance = 3.0,
        )

        assertEquals(
            listOf(
                Vec3(2.75, 97.25, -1.375),
                Vec3(5.5, 94.5, -2.75),
                Vec3(8.25, 91.75, -4.125),
            ),
            groundingPositions,
        )

        groundingPositions.forEach { state.confirm(position = it, onGround = true) }
        state.confirm(position = target, onGround = false)

        assertEquals(target, state.position)
        assertEquals(1.75, state.fallDistance)
    }

    @Test
    fun `existing server fall budget shortens only the first grounded segment`() {
        val state = VanillaFlyServerFallState().apply {
            initialize(position = Vec3(0.0, 100.0, 0.0), fallDistance = 2.0)
        }

        val groundingPositions = state.groundingPositions(
            target = Vec3(0.0, 98.0, 0.0),
            safeFallDistance = 3.0,
        )

        assertEquals(listOf(Vec3(0.0, 99.25, 0.0)), groundingPositions)
    }

    @Test
    fun `arbitrarily fast ascent emits no ground resets and preserves the server fall budget`() {
        val state = VanillaFlyServerFallState().apply {
            initialize(position = Vec3(0.0, 100.0, 0.0), fallDistance = 2.0)
        }

        val target = Vec3(4.0, 250.0, -8.0)

        assertTrue(state.groundingPositions(target, safeFallDistance = 3.0).isEmpty())
        state.confirm(position = target, onGround = false)
        assertEquals(2.0, state.fallDistance)
    }

    @Test
    fun `server correction invalidates position without forgetting conservative fall budget`() {
        val state = VanillaFlyServerFallState().apply {
            initialize(position = Vec3(0.0, 100.0, 0.0), fallDistance = 2.0)
            confirm(position = Vec3(0.0, 99.5, 0.0), onGround = false)
        }

        state.invalidatePosition()
        assertEquals(null, state.position)

        state.initialize(position = Vec3(12.0, 72.0, -4.0), fallDistance = 0.25)
        assertEquals(Vec3(12.0, 72.0, -4.0), state.position)
        assertEquals(2.5, state.fallDistance)
    }

    @Test
    fun `ground packet smart threshold is strict and includes downward movement`() {
        assertFalse(
            VanillaFlyNoFall.shouldSendGroundPacket(
                fallDistance = 2.5,
                verticalMovement = -0.5,
                safeFallDistance = 3.0,
                tickCount = 21,
            )
        )
        assertTrue(
            VanillaFlyNoFall.shouldSendGroundPacket(
                fallDistance = 2.5,
                verticalMovement = -0.5001,
                safeFallDistance = 3.0,
                tickCount = 21,
            )
        )
    }

    @Test
    fun `ground packet starts only after tick twenty`() {
        assertFalse(
            VanillaFlyNoFall.shouldSendGroundPacket(
                fallDistance = 4.0,
                verticalMovement = 0.0,
                safeFallDistance = 3.0,
                tickCount = 20,
            )
        )
        assertTrue(
            VanillaFlyNoFall.shouldSendGroundPacket(
                fallDistance = 4.0,
                verticalMovement = 0.0,
                safeFallDistance = 3.0,
                tickCount = 21,
            )
        )
    }

    @Test
    fun `packet jump requires airborne fall distance above the strict safe threshold`() {
        assertFalse(
            VanillaFlyNoFall.shouldSendPacketJump(
                onGround = false,
                fallDistance = 3.0,
                safeFallDistance = 3.0,
            )
        )
        assertTrue(
            VanillaFlyNoFall.shouldSendPacketJump(
                onGround = false,
                fallDistance = 3.0001,
                safeFallDistance = 3.0,
            )
        )
        assertFalse(
            VanillaFlyNoFall.shouldSendPacketJump(
                onGround = true,
                fallDistance = 4.0,
                safeFallDistance = 3.0,
            )
        )
    }

}
