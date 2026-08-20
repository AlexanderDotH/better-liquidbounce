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

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FlyPacketTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `packet is an opt in top level mode and vanilla stays the default`() {
        assertSame(FlyVanilla, ModuleFly.modes.activeMode)
        assertTrue(FlyPacket in ModuleFly.modes.modes)
    }

    @Test
    fun `packet settings have independent vanilla defaults and extended speed ranges`() {
        FlyPacket.restore()
        val baseSpeed = FlyPacket.group("BaseSpeed")
        val sprintSpeed = FlyPacket.toggleableGroup("SprintSpeed")

        assertEquals(0.44F, baseSpeed.setting("Horizontal").get())
        assertEquals(0.44F, baseSpeed.setting("Vertical").get())
        assertEquals(0.1F..500.0F, baseSpeed.rangedSetting("Horizontal").range)
        assertEquals(0.1F..500.0F, baseSpeed.rangedSetting("Vertical").range)
        assertTrue(sprintSpeed.enabled)
        assertEquals(1.0F, sprintSpeed.setting("Horizontal").get())
        assertEquals(1.0F, sprintSpeed.setting("Vertical").get())
        assertEquals(0.1F..500.0F, sprintSpeed.rangedSetting("Horizontal").range)
        assertEquals(0.1F..500.0F, sprintSpeed.rangedSetting("Vertical").range)
        assertEquals(0.0F, FlyPacket.setting("Glide").get())
        assertEquals(true, FlyPacket.setting("BypassVanillaCheck").get())
        assertEquals(VanillaFlyCheckBypassMode.PACKET, FlyPacket.setting("BypassMode").get())
        assertEquals(false, FlyPacket.setting("NoFall").get())
        assertEquals(128, FlyPacket.setting("MaxPackets").get())
        assertEquals(2..512, FlyPacket.rangedSetting("MaxPackets").range)
        assertEquals(PacketFlySpeedExploit.SAFE, FlyPacket.setting("SpeedExploit").get())
        assertEquals(PacketFlyPrimingPacketShape.Position, FlyPacket.setting("PrimingPacketType").get())
    }

    @Test
    fun `shared runtime keeps existing vanilla settings and ranges unchanged`() {
        FlyVanilla.restore()
        val baseSpeed = FlyVanilla.group("BaseSpeed")
        val sprintSpeed = FlyVanilla.toggleableGroup("SprintSpeed")

        assertEquals(
            listOf("Glide", "BypassVanillaCheck", "BypassMode", "NoFall", "BaseSpeed", "SprintSpeed"),
            FlyVanilla.inner.map { it.name },
        )
        assertEquals(0.44F, baseSpeed.setting("Horizontal").get())
        assertEquals(0.44F, baseSpeed.setting("Vertical").get())
        assertEquals(0.1F..10.0F, baseSpeed.rangedSetting("Horizontal").range)
        assertEquals(0.1F..10.0F, baseSpeed.rangedSetting("Vertical").range)
        assertTrue(sprintSpeed.enabled)
        assertEquals(1.0F, sprintSpeed.setting("Horizontal").get())
        assertEquals(1.0F, sprintSpeed.setting("Vertical").get())
        assertEquals(0.1F..10.0F, sprintSpeed.rangedSetting("Horizontal").range)
        assertEquals(0.1F..10.0F, sprintSpeed.rangedSetting("Vertical").range)
    }

    @Test
    fun `zero resolved movement does not create a packet plan`() {
        assertFalse(PacketFlyRuntimePolicy.shouldPlan(Vec3.ZERO, spearKillOwnsPacketRoute = false))
    }

    @Test
    fun `spear kill ownership suspends physical packet fly movement`() {
        val collisionResolvedMovement = Vec3(12.0, -3.0, 4.0)

        assertEquals(
            Vec3.ZERO,
            PacketFlyRuntimePolicy.resolvePhysicalMovement(
                collisionResolvedMovement,
                spearKillOwnsPacketRoute = true,
            ),
        )
        assertFalse(PacketFlyRuntimePolicy.shouldPlan(collisionResolvedMovement, spearKillOwnsPacketRoute = true))
    }

    @Test
    fun `spear kill ownership also suppresses the shared POST anti kick packet`() {
        assertFalse(shouldSendVanillaFlyPacketBypass(
            eventState = EventState.POST,
            enabled = true,
            tickCount = 40,
            configuredMode = VanillaFlyCheckBypassMode.PACKET,
            isFallFlying = false,
            movementSuspended = true,
        ))
    }

    @Test
    fun `packet fly keeps collision resolved movement physical when spear kill is idle`() {
        val collisionResolvedMovement = Vec3(12.0, -3.0, 4.0)

        assertEquals(
            collisionResolvedMovement,
            PacketFlyRuntimePolicy.resolvePhysicalMovement(
                collisionResolvedMovement,
                spearKillOwnsPacketRoute = false,
            ),
        )
        assertTrue(PacketFlyRuntimePolicy.shouldPlan(collisionResolvedMovement, spearKillOwnsPacketRoute = false))
    }

    @Test
    fun `nofall reservation search selects the smallest complete packet plan`() {
        val reservation = findMinimumFeasiblePacketReservation(maxReservation = 8) { reservedPackets ->
            when (reservedPackets) {
                0 -> 6
                1 -> 5
                2 -> 4
                else -> 3
            }
        }

        assertEquals(3, reservation)
    }

    @Test
    fun `nofall reservation search rejects movement when even the shortest plan is incomplete`() {
        val reservation = findMinimumFeasiblePacketReservation(maxReservation = 2) { 3 }

        assertEquals(null, reservation)
    }

    @Test
    fun `auxiliary delivery is tracked by identity and cancelled packets do not advance`() {
        val first = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val cancelled = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val equalShapeButUnowned = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val tracker = PacketFlyDeliveryTracker<ServerboundMovePlayerPacket>()
        tracker.stage(listOf(first, cancelled))

        assertEquals(PacketFlyDeliveryResult.UNRELATED, tracker.confirm(equalShapeButUnowned, delivered = true))
        assertEquals(PacketFlyDeliveryResult.AUXILIARY_DELIVERED, tracker.confirm(first, delivered = true))
        assertEquals(PacketFlyDeliveryResult.AUXILIARY_REJECTED, tracker.confirm(cancelled, delivered = false))
        assertEquals(1, tracker.deliveredAuxiliaryCount)
        assertFalse(tracker.allAuxiliariesDelivered)
    }

    @Test
    fun `plan stays tracked until the ordinary final packet delivery`() {
        val auxiliary = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val vanillaFinal = ServerboundMovePlayerPacket.Pos(4.0, 70.0, -2.0, false, false)
        val tracker = PacketFlyDeliveryTracker<ServerboundMovePlayerPacket>()
        tracker.stage(listOf(auxiliary))

        tracker.confirm(auxiliary, delivered = true)
        tracker.expectFinalPacket(vanillaFinal)

        assertTrue(tracker.active)
        assertEquals(PacketFlyDeliveryResult.FINAL_DELIVERED, tracker.confirm(vanillaFinal, delivered = true))
        assertFalse(tracker.active)
    }

    @Test
    fun `ordinary final packet must preserve the collision resolved endpoint`() {
        val endpoint = Vec3(4.0, 70.0, -2.0)

        assertTrue(matchesPacketFlyEndpoint(
            ServerboundMovePlayerPacket.Pos(endpoint.x, endpoint.y, endpoint.z, false, false),
            endpoint,
        ))
        assertFalse(matchesPacketFlyEndpoint(
            ServerboundMovePlayerPacket.Pos(endpoint.x, endpoint.y + 0.01, endpoint.z, false, false),
            endpoint,
        ))
        assertFalse(matchesPacketFlyEndpoint(
            ServerboundMovePlayerPacket.Rot(0.0F, 0.0F, false, false),
            endpoint,
        ))
    }

    @Test
    fun `lifecycle reset clears staged identities and counters`() {
        val auxiliary = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val tracker = PacketFlyDeliveryTracker<ServerboundMovePlayerPacket>()
        tracker.stage(listOf(auxiliary))
        tracker.confirm(auxiliary, delivered = true)

        tracker.clear()

        assertFalse(tracker.active)
        assertEquals(0, tracker.deliveredAuxiliaryCount)
        assertEquals(PacketFlyDeliveryResult.UNRELATED, tracker.confirm(auxiliary, delivered = true))
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

private fun ValueGroup.rangedSetting(name: String): RangedValue<*> = setting(name) as RangedValue<*>

private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup

private fun ValueGroup.toggleableGroup(name: String): ToggleableValueGroup =
    inner.single { it.name == name } as ToggleableValueGroup
