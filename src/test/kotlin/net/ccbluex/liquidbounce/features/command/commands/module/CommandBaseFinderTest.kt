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

package net.ccbluex.liquidbounce.features.command.commands.module

import net.ccbluex.liquidbounce.features.command.Parameter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandBaseFinderTest {

    @Test
    fun `basefinder exposes list export and clear management commands`() {
        val command = CommandBaseFinder.createCommand()

        assertEquals("basefinder", command.name)
        assertFalse(command.executable)
        assertEquals(listOf("list", "export", "clear"), command.subcommands.map { it.name })
    }

    @Test
    fun `list accepts one optional page`() {
        val list = CommandBaseFinder.createCommand().subcommands.single { it.name == "list" }

        assertTrue(list.executable)
        assertEquals(1, list.parameters.size)
        assertEquals("page", list.parameters.single().name)
        assertFalse(list.parameters.single().required)
        assertTrue(list.requiresIngame)
        val verifier = requireNotNull(list.parameters.single().verifier)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("1"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("0"))
    }

    @Test
    fun `export format is an explicit subcommand`() {
        val export = CommandBaseFinder.createCommand().subcommands.single { it.name == "export" }

        assertFalse(export.executable)
        assertEquals(listOf("json", "csv"), export.subcommands.map { it.name })
        assertTrue(export.subcommands.all { it.executable && it.parameters.isEmpty() && it.requiresIngame })
    }

    @Test
    fun `clear current requires the exact confirm token`() {
        val clear = CommandBaseFinder.createCommand().subcommands.single { it.name == "clear" }
        val current = clear.subcommands.single { it.name == "current" }
        val confirm = current.parameters.single()
        val verifier = requireNotNull(confirm.verifier)

        assertFalse(clear.executable)
        assertTrue(current.executable)
        assertTrue(current.requiresIngame)
        assertTrue(confirm.required)
        assertEquals("confirm", confirm.name)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("confirm"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("CONFIRM"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("yes"))
    }

}
