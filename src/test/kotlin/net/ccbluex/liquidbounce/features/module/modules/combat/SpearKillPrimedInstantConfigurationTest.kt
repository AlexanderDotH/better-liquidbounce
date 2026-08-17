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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillPrimedInstantConfigurationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `Instant remains Safe by default and scopes research controls to Primed`() {
        val instant = SpearKillMovementConfiguration(null).packet.instant

        assertEquals(listOf("MaxPackets", "Strategy"), instant.inner.map { it.name })
        assertEquals("Safe", instant.strategy.activeMode.name)
        assertEquals(
            mapOf(
                "Safe" to emptyList(),
                "Primed" to listOf("PrimingPacketType", "ResearchLog"),
            ),
            instant.strategy.modes.associate { it.name to it.inner.map { setting -> setting.name } },
        )
        assertEquals(SpearKillPrimedInstantPacketType.Position, instant.primed.primingPacketType)
        assertTrue(instant.primed.researchLog)
    }

    @Test
    fun `Primed exposes every movement packet shape without changing Safe`() {
        val instant = SpearKillMovementConfiguration(null).packet.instant

        assertEquals(
            setOf("Position", "PositionRotation", "Rotation", "StatusOnly"),
            SpearKillPrimedInstantPacketType.entries.map { it.tag }.toSet(),
        )
        assertFalse(instant.strategy.activeMode === instant.primed)
        instant.strategy.setByString("Primed")
        try {
            assertTrue(instant.strategy.activeMode === instant.primed)
        } finally {
            instant.strategy.restore()
        }
        assertEquals("Safe", instant.strategy.activeMode.name)
    }
}
