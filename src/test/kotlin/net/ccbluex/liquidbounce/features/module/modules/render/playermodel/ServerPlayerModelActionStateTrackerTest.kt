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

package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerPlayerModelActionStateTrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @AfterEach
    fun resetTracker() {
        ServerPlayerModelStateTracker.reset()
    }

    @Test
    fun `slot use swing release and reset follow transmitted packets`() {
        ServerPlayerModelStateTracker.onPacketSent(ServerboundSetCarriedItemPacket(4), nowNanos = 1L)
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 90f, 15f),
            nowNanos = 2L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.OFF_HAND),
            nowNanos = 3L,
        )

        val active = ServerPlayerModelStateTracker.snapshot
        assertEquals(4, active.selectedHotbarSlot)
        assertEquals(InteractionHand.MAIN_HAND, active.activeUseHand)
        assertEquals(2L, active.useStartedAtNanos)
        assertEquals(InteractionHand.OFF_HAND, active.swingHand)
        assertEquals(3L, active.swingStartedAtNanos)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN,
            ),
            nowNanos = 4L,
        )

        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
        ServerPlayerModelStateTracker.reset()
        assertEquals(ServerPlayerModelSnapshot.EMPTY, ServerPlayerModelStateTracker.snapshot)
    }

    @Test
    fun `slot change clears main hand item use`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0f, 0f),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onPacketSent(ServerboundSetCarriedItemPacket(7), nowNanos = 2L)
        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
    }

    @Test
    fun `repeated block breaking packets restart swing only after vanilla half duration`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.snapshotForRender(nowNanos = 1L, swingDurationTicks = 6)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 50_000_001L,
        )
        assertEquals(1L, ServerPlayerModelStateTracker.snapshot.swingStartedAtNanos)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 100_000_001L,
        )
        assertEquals(1L, ServerPlayerModelStateTracker.snapshot.swingStartedAtNanos)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 150_000_001L,
        )
        assertEquals(150_000_001L, ServerPlayerModelStateTracker.snapshot.swingStartedAtNanos)
    }

    @Test
    fun `render snapshot expires swing after vanilla duration`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 1L,
        )

        val expired = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 300_000_001L,
            swingDurationTicks = 6,
        )

        assertNull(expired.swingHand)
        assertNull(expired.swingStartedAtNanos)
    }

    @Test
    fun `item use expires when no release packet arrives`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0f, 0f),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 60_000_000_001L,
            swingDurationTicks = 6,
        )
        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
    }
}
