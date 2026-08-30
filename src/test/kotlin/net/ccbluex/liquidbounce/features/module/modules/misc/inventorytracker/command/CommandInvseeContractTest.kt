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
package net.ccbluex.liquidbounce.features.module.modules.misc.inventorytracker.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.module.modules.misc.inventorytracker.contract.InventoryTrackerCommandActions
import net.ccbluex.liquidbounce.features.module.modules.misc.inventorytracker.contract.InventoryTrackerCommandBridge
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandInvseeContractTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `invsee keeps player parameter and ingame contract`() {
        val command = CommandInvsee.createCommand()
        val player = command.parameters.single()

        assertEquals("invsee", command.name)
        assertEquals(emptyList(), command.aliases)
        assertEquals("playerName", player.name)
        assertTrue(player.required)
        assertFalse(player.vararg)
        assertNull(player.default)
        assertTrue(command.requiresIngame)
        assertTrue(command.executable)
        assertTrue(command.subcommands.isEmpty())
    }

    @Test
    fun `invsee delegates the player name and retains its viewed player surface`() {
        val viewedPlayer = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val requestedPlayers = mutableListOf<String>()
        val actions = InventoryTrackerCommandActions { playerName ->
            requestedPlayers += playerName
            InventoryTrackerCommandBridge.viewedPlayer = viewedPlayer
            true
        }

        InventoryTrackerCommandBridge.withActionsForTest(actions) {
            execute(CommandInvsee.createCommand(), "Alice")

            assertEquals(listOf("Alice"), requestedPlayers)
            assertEquals(viewedPlayer, CommandInvsee.viewedPlayer)
        }
    }

    @Test
    fun `invsee keeps player not found error semantics`() {
        val command = CommandInvsee.createCommand()
        val actions = InventoryTrackerCommandActions { false }

        InventoryTrackerCommandBridge.withActionsForTest(actions) {
            val exception = assertFailsWith<CommandException> {
                execute(command, "MissingPlayer")
            }

            assertEquals(command.result("playerNotFound", "MissingPlayer").string, exception.text.string)
        }
    }

    private fun execute(command: Command, playerName: String) {
        val context = Command.Handler.Context(command, arrayOf(playerName))
        with(requireNotNull(command.handler)) {
            context()
        }
    }
}
