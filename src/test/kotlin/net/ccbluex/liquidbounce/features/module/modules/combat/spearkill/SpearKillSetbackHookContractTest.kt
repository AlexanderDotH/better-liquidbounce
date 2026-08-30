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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

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
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class SpearKillSetbackHookContractTest {

    @Test
    fun `uninstalled SpearKill callback binding is neutral`() {
        val binding = SpearKillSetbackCallbackBinding<Any, Any>()

        assertDoesNotThrow {
            binding.beforeCorrection(Any(), Any())
            binding.afterCorrection(Any(), Any())
        }
    }

    @Test
    fun `installed SpearKill callbacks receive both correction phases in order`() {
        val binding = SpearKillSetbackCallbackBinding<Any, Any>()
        val packet = Any()
        val player = Any()
        val phases = mutableListOf<String>()
        binding.install(SpearKillSetbackCallbacks(
            beforeCorrection = { receivedPacket, receivedPlayer ->
                assertSame(packet, receivedPacket)
                assertSame(player, receivedPlayer)
                phases += "before"
            },
            afterCorrection = { receivedPacket, receivedPlayer ->
                assertSame(packet, receivedPacket)
                assertSame(player, receivedPlayer)
                phases += "after"
            },
        ))

        binding.beforeCorrection(packet, player)
        binding.afterCorrection(packet, player)

        assertEquals(listOf("before", "after"), phases)
    }

    @Test
    fun `a second SpearKill callback owner cannot replace the installed owner`() {
        val binding = SpearKillSetbackCallbackBinding<Any, Any>()
        binding.install(emptyCallbacks())

        assertThrows(IllegalStateException::class.java) {
            binding.install(emptyCallbacks())
        }
    }

    @Test
    fun `root hook preserves Java entry points and invokes SpearKill before shared listeners`() {
        val source = Files.readString(SPEAR_KILL_SETBACK_SOURCE)

        assertFalse("ModuleSpearKill" in source)
        assertTrue("object SpearKillSetbackHook" in source)
        assertTrue("callbackBinding.install(callbacks)" in source)
        assertStaticHookMethod("beforeCorrection")
        assertStaticHookMethod("afterCorrection")
        assertInOrder(
            source,
            "callbackBinding.beforeCorrection(packet, player)",
            "RemoteKillSetbackRegistry.beforeCorrection(packet, player)",
        )
        assertInOrder(
            source,
            "callbackBinding.afterCorrection(packet, player)",
            "RemoteKillSetbackRegistry.afterCorrection(packet, player)",
        )
    }

    private fun emptyCallbacks() = SpearKillSetbackCallbacks<Any, Any>(
        beforeCorrection = { _, _ -> },
        afterCorrection = { _, _ -> },
    )

    private fun assertStaticHookMethod(name: String) {
        val method = SpearKillSetbackHook::class.java.getDeclaredMethod(
            name,
            ClientboundPlayerPositionPacket::class.java,
            Player::class.java,
        )
        assertTrue(Modifier.isStatic(method.modifiers), name)
    }

    private fun assertInOrder(source: String, first: String, second: String) {
        val firstIndex = source.indexOf(first)
        assertTrue(firstIndex >= 0, "$first is missing")
        assertTrue(
            source.indexOf(second, firstIndex + first.length) > firstIndex,
            "$second is missing or out of order",
        )
    }

    private companion object {
        val SPEAR_KILL_SETBACK_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill/",
            "SpearKillSetbackRollback.kt",
        )
    }
}
