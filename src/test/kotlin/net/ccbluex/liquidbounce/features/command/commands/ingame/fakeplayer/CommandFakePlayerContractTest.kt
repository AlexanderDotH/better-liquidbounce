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
package net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandFakePlayerContractTest {

    @Test
    fun `fakeplayer retains command tree and parameter order`() {
        val command = CommandFakePlayer.createCommand()

        assertEquals("fakeplayer", command.name)
        assertEquals(
            listOf("spawn", "remove", "clear", "startrecording", "endrecording"),
            command.subcommands.map { it.name },
        )
        assertEquals(listOf("name"), command.subcommands[0].parameters.map { it.name })
        assertEquals(listOf("name"), command.subcommands[1].parameters.map { it.name })
        assertEquals(listOf("name"), command.subcommands[4].parameters.map { it.name })
    }

    @Test
    fun `estimated damage consumes absorption before health`() {
        assertEquals(FakePlayerDamageState(20f, 2f), estimateFakePlayerDamage(20f, 4f, 2f))
        assertEquals(FakePlayerDamageState(18f, 0f), estimateFakePlayerDamage(20f, 4f, 6f))
        assertEquals(FakePlayerDamageState(20f, 4f), estimateFakePlayerDamage(20f, 4f, 0f))
    }
}
