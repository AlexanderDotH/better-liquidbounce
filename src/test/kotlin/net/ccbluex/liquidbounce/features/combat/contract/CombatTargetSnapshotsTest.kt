/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.combat.contract

import net.ccbluex.liquidbounce.common.interop.PlayerDataPayload
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.nio.file.Files
import java.nio.file.Path

class CombatTargetSnapshotsTest {

    @Test
    fun `provider receives the attacked player and returns the identical payload`() {
        val player = playerIdentity()
        val payload = object : PlayerDataPayload {}

        val result = CombatTargetSnapshots.withFactoryForTest(CombatTargetSnapshotFactory { requested ->
            assertSame(player, requested)
            payload
        }) {
            CombatTargetSnapshots.fromPlayer(player)
        }

        assertSame(payload, result)
    }

    @Test
    fun `missing adapter fails before publishing a partial target snapshot`() {
        val error = CombatTargetSnapshots.withFactoryForTest(null) {
            assertThrows(IllegalStateException::class.java) {
                CombatTargetSnapshots.fromPlayer(playerIdentity())
            }
        }

        assertTrue(error.message!!.contains("combat target snapshot adapter"))
    }

    @Test
    fun `combat runtime delegates once and rest adapter retains concrete mapping`() {
        val combat = Files.readString(Path.of(COMBAT_MANAGER))
        val adapter = Files.readString(Path.of(REST_ADAPTER))

        assertTrue(combat.contains("TargetChangeEvent(CombatTargetSnapshots.fromPlayer(entity))"))
        assertTrue(adapter.contains(
            "override fun create(player: Player): PlayerDataPayload = PlayerData.fromPlayer(player)"
        ))
    }

    private fun playerIdentity(): Player = unsafe.allocateInstance(RemotePlayer::class.java) as Player

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        const val COMBAT_MANAGER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/combat/runtime/CombatManager.kt"
        const val REST_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/game/" +
                "CombatTargetSnapshotAdapter.kt"

        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
    }
}
