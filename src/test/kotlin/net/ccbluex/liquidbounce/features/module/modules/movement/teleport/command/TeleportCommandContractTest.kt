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
package net.ccbluex.liquidbounce.features.module.modules.movement.teleport.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeleportCommandContractTest {

    @Test
    fun `teleport retains name aliases and parameter contract`() {
        val command = CommandTeleport.createCommand()

        assertEquals("teleport", command.name)
        assertEquals(listOf("tp"), command.aliases)
        assertEquals(listOf("x", "y|z", "z"), command.parameters.map { it.name })
        assertEquals(listOf(true, true, false), command.parameters.map { it.required })
        assertTrue(command.requiresIngame)
        assertTrue(command.subcommands.isEmpty())
    }

    @Test
    fun `player teleport retains name aliases and parameter contract`() {
        val command = CommandPlayerTeleport.createCommand()

        assertEquals("playerteleport", command.name)
        assertEquals(listOf("playertp", "ptp"), command.aliases)
        assertEquals(listOf("player", "copy"), command.parameters.map { it.name })
        assertEquals(listOf(true, false), command.parameters.map { it.required })
        assertTrue(command.requiresIngame)
        assertTrue(command.subcommands.isEmpty())
    }

}
