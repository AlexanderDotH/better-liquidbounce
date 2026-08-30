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
package net.ccbluex.liquidbounce.features.module.modules.render.xray.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.module.modules.render.xray.contract.XRayCommandActions
import net.ccbluex.liquidbounce.features.module.modules.render.xray.contract.XRayCommandBridge
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandXRayContractTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `xray keeps root and subcommand order`() {
        val command = CommandXRay.createCommand()

        assertEquals("xray", command.name)
        assertEquals(emptyList(), command.aliases)
        assertEquals(listOf("add", "remove", "list", "clear", "reset"), command.subcommands.map { it.name })
        assertTrue(command.parameters.isEmpty())
        assertFalse(command.requiresIngame)
        assertFalse(command.executable)
    }

    @Test
    fun `xray keeps block and paging parameter contracts`() {
        val command = CommandXRay.createCommand()
        val add = command.subcommands[0].parameters.single()
        val remove = command.subcommands[1].parameters.single()
        val page = command.subcommands[2].parameters.single()

        assertEquals(listOf("block", "block", "page"), listOf(add.name, remove.name, page.name))
        assertEquals(listOf(true, true, false), listOf(add.required, remove.required, page.required))
        assertTrue(listOf(add, remove, page).none { it.vararg })
        assertTrue(listOf(add, remove, page).all { it.default == null })
        assertTrue(command.subcommands.drop(3).all { it.parameters.isEmpty() && it.executable })
        assertNull(page.default)
    }

    @Test
    fun `xray keeps duplicate add and missing remove errors in execution order`() {
        val root = CommandXRay.createCommand()
        val calls = mutableListOf<String>()
        val actions = XRayCommandActions(
            blocks = { emptyList() },
            add = { calls += "add"; false },
            remove = { calls += "remove"; false },
            clear = {},
            reset = {},
        )

        XRayCommandBridge.withActionsForTest(actions) {
            val add = root.subcommands.single { it.name == "add" }
            val remove = root.subcommands.single { it.name == "remove" }
            val addError = assertFailsWith<CommandException> { execute(add) }
            val removeError = assertFailsWith<CommandException> { execute(remove) }

            assertEquals(listOf("add", "remove"), calls)
            assertEquals(add.result("blockIsPresent", Blocks.STONE.name).string, addError.text.string)
            assertEquals(remove.result("blockNotFound", Blocks.STONE.name).string, removeError.text.string)
        }
    }

    private fun execute(command: Command) {
        val context = Command.Handler.Context(command, arrayOf(Blocks.STONE))
        with(requireNotNull(command.handler)) {
            context()
        }
    }
}
