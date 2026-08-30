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

package net.ccbluex.liquidbounce.config

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigMigrationRegistryTest {

    @Test
    fun `migrations run by explicit order and then stable id`() {
        val registry = ConfigMigrationSequence()
        val applications = mutableListOf<String>()
        registry.register("reach", 300) { applications += "reach" }
        registry.register("mace-kill", 200) { applications += "mace-kill" }
        registry.register("fight-bot-z", 100) { applications += "fight-bot-z" }
        registry.register("fight-bot-a", 100) { applications += "fight-bot-a" }

        registry.applyAll(JsonObject())

        assertEquals(
            listOf("fight-bot-a", "fight-bot-z", "mace-kill", "reach"),
            applications,
        )
    }

    @Test
    fun `duplicate migration id fails before replacing its owner`() {
        val registry = ConfigMigrationSequence()
        val applications = mutableListOf<String>()
        registry.register("fight-bot", 100) { applications += "first" }

        assertThrows(IllegalStateException::class.java) {
            registry.register("fight-bot", 100) { applications += "replacement" }
        }

        registry.applyAll(JsonObject())
        assertEquals(listOf("first"), applications)
    }

    @Test
    fun `blank migration id is rejected`() {
        val registry = ConfigMigrationSequence()

        assertThrows(IllegalArgumentException::class.java) {
            registry.register("  ", 100) { }
        }
    }

    @Test
    fun `migration targets isolate module config from other roots`() {
        val registry = ConfigMigrationSequence()
        val applications = mutableListOf<String>()
        registry.register(
            target = ConfigMigrationTarget.MODULES,
            id = "fight-bot",
            order = ConfigMigrationOrder.FIGHT_BOT,
        ) { applications += "modules" }

        registry.applyAll(ConfigMigrationTarget.named("friends"), JsonObject())
        registry.applyAll(ConfigMigrationTarget.named("Modules"), JsonObject())

        assertEquals(listOf("modules"), applications)
    }
}
