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
package net.ccbluex.liquidbounce.features.module.modules.misc.autoaccount.command

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandAutoAccountContractTest {

    @Test
    fun `autoaccount keeps ingame hub and subcommand order`() {
        val command = CommandAutoAccount.createCommand()

        assertEquals("autoaccount", command.name)
        assertEquals(emptyList(), command.aliases)
        assertEquals(listOf("register", "login"), command.subcommands.map { it.name })
        assertTrue(command.requiresIngame)
        assertFalse(command.executable)
        assertTrue(command.parameters.isEmpty())
        assertTrue(command.subcommands.all { it.parameters.isEmpty() && it.executable })
    }
}
