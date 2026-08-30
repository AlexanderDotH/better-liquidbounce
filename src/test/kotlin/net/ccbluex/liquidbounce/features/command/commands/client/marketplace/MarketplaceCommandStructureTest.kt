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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace

import net.ccbluex.liquidbounce.features.command.commands.client.marketplace.item.marketplaceListCommand
import net.ccbluex.liquidbounce.features.command.commands.client.marketplace.item.marketplaceEditItemCommand
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketplaceCommandStructureTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `search retains query and page parameter contract`() {
        val command = MarketplaceSearchCommand.createCommand()

        assertEquals(listOf("query", "page"), command.parameters.map { it.name })
        assertTrue(command.parameters[0].vararg)
        assertTrue(command.parameters[0].required)
        assertTrue(!command.parameters[1].required)
    }

    @Test
    fun `list retains type page and featured parameter contract`() {
        val command = marketplaceListCommand()

        assertEquals(listOf("type", "page", "featured"), command.parameters.map { it.name })
        assertTrue(command.parameters[0].required)
        assertTrue(!command.parameters[1].required)
        assertTrue(!command.parameters[2].required)
    }

    @Test
    fun `edit retains id name type and description parameter contract`() {
        val command = marketplaceEditItemCommand()

        assertEquals(listOf("id", "name", "type", "description"), command.parameters.map { it.name })
        assertTrue(command.parameters.all { it.required })
        assertTrue(command.parameters.last().vararg)
    }
}
