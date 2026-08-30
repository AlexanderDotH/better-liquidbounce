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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace.revisions

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketplaceUploadRevisionCommandContractTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `upload retains parameter order and optionality`() {
        val command = MarketplaceUploadRevisionCommand.createCommand()

        assertEquals(listOf("id", "file", "version", "changelog", "dependencies"), command.parameters.map { it.name })
        assertEquals(listOf(true, true, true, false, false), command.parameters.map { it.required })
        assertTrue(command.parameters[3].vararg)
        assertFalse(command.parameters[4].vararg)
    }
}
