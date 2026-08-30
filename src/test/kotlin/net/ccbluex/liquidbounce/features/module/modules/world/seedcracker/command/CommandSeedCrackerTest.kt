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

package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.command

import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSeedCrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `seedcracker exposes only local management commands`() {
        val command = CommandSeedCracker.createCommand()

        assertEquals("seedcracker", command.name)
        assertFalse(command.executable)
        assertEquals(
            listOf("status", "confirm", "reject", "undo", "pause", "resume", "reset"),
            command.subcommands.map { it.name },
        )
    }

    @Test
    fun `evidence management commands accept an optional id with autocomplete`() {
        val command = CommandSeedCracker.createCommand()

        listOf("confirm", "reject", "undo").forEach { name ->
            val subcommand = command.subcommands.single { it.name == name }
            assertTrue(subcommand.executable, name)
            assertTrue(subcommand.requiresIngame, name)
            assertEquals("id", subcommand.parameters.single().name, name)
            assertFalse(subcommand.parameters.single().required, name)
            assertTrue(subcommand.parameters.single().autocompletionHandler != null, name)
        }
    }

    @Test
    fun `status and execution controls are in game local commands`() {
        val command = CommandSeedCracker.createCommand()

        listOf("status", "pause", "resume").forEach { name ->
            val subcommand = command.subcommands.single { it.name == name }
            assertTrue(subcommand.executable, name)
            assertTrue(subcommand.requiresIngame, name)
            assertTrue(subcommand.parameters.isEmpty(), name)
        }
    }

    @Test
    fun `reset scopes require the literal --confirm guard`() {
        val reset = CommandSeedCracker.createCommand().subcommands.single { it.name == "reset" }

        assertFalse(reset.executable)
        assertEquals(listOf("current", "all"), reset.subcommands.map { it.name })
        reset.subcommands.forEach { scope ->
            assertTrue(scope.executable)
            assertTrue(scope.requiresIngame)
            val confirm = scope.parameters.single()
            assertEquals("confirm", confirm.name)
            assertTrue(confirm.required)
            val verifier = requireNotNull(confirm.verifier)
            assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("--confirm"))
            assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("confirm"))
        }
    }
}
