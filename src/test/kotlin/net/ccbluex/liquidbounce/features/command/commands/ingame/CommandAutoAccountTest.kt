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
package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.module.modules.misc.autoaccount.contract.AutoAccountCommandActions
import net.ccbluex.liquidbounce.features.module.modules.misc.autoaccount.contract.AutoAccountCommandBridge
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CommandAutoAccountTest {

    @Test
    fun `register and login subcommands dispatch their matching account actions`() {
        val invokedActions = mutableListOf<String>()
        val actions = AutoAccountCommandActions(
            register = { invokedActions += "register" },
            login = { invokedActions += "login" },
        )

        AutoAccountCommandBridge.withActionsForTest(actions) {
            val command = CommandAutoAccount.createCommand()

            execute(command.subcommands.single { it.name == "register" })
            execute(command.subcommands.single { it.name == "login" })

            assertEquals(listOf("register", "login"), invokedActions)
        }
    }

    private fun execute(command: Command) {
        val context = Command.Handler.Context(command, emptyArray())
        with(requireNotNull(command.handler)) {
            context()
        }
    }
}
