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
package net.ccbluex.liquidbounce.features.command.commands.ingame.creative

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class CommandItemEnchantContractTest {

    @Test
    fun `enchant command retains subcommand order and parameter contracts`() {
        val command = CommandItemEnchant.createCommand()

        assertEquals("enchant", command.name)
        assertFalse(command.executable)
        assertEquals(listOf("add", "remove", "clear", "all", "all_possible"), command.subcommands.map { it.name })
        assertEquals(listOf("enchantment", "level"), command.subcommands[0].parameters.map { it.name })
        assertEquals(listOf("enchantment"), command.subcommands[1].parameters.map { it.name })
        assertEquals(emptyList<String>(), command.subcommands[2].parameters.map { it.name })
        assertEquals(listOf("level"), command.subcommands[3].parameters.map { it.name })
        assertEquals(listOf("level"), command.subcommands[4].parameters.map { it.name })
    }
}
