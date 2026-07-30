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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleSpeedCategoriesTest {

    @Test
    fun `all speed selectors expose server categories without losing modes`() {
        MinecraftBootstrap.ensureInitialized()

        val speedModeGroups = ModuleSpeed.collectValuesRecursively()
            .filterIsInstance<ModeValueGroup<*>>()
            .filter { it.name == "Mode" }

        assertEquals(3, speedModeGroups.size)
        assertTrue(speedModeGroups.all { it.categories.isNotEmpty() })

        for (group in speedModeGroups) {
            assertEquals(group.modes.size, group.categories.values.sumOf(List<*>::size))
            assertEquals(group.modes.toSet(), group.categories.values.flatten().toSet())
            assertEquals(
                listOf("AAC332", "AAC4310FastHop", "AAC4312LowHop"),
                group.categories.getValue("AAC").map { it.name },
            )
            assertEquals(
                listOf("HypixelBHop", "HypixelLowHop", "Watchdog"),
                group.categories.getValue("Hypixel").map { it.name },
            )
            assertEquals(
                listOf("SentinelDamage", "SentinelFastHop", "SentinelLowHop", "SentinelStrafeHop", "SentinelOnGround"),
                group.categories.getValue("Sentinel").map { it.name },
            )
        }
    }

}
