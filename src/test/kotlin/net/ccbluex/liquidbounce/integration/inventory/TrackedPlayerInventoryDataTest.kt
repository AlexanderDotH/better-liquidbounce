/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.integration.inventory

import net.ccbluex.liquidbounce.common.interop.PlayerInventoryDataPayload
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.event.WebSocketEvent
import net.ccbluex.liquidbounce.event.events.ClientPlayerInventoryEvent
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class TrackedPlayerInventoryDataTest {

    @Test
    fun `tracked inventory retains the player inventory event contract`() {
        assertTrue(PlayerInventoryDataPayload::class.java.isAssignableFrom(TrackedPlayerInventoryData::class.java))
        assertEquals(
            listOf("armor", "main", "crafting", "enderChest"),
            TrackedPlayerInventoryData::class.java.declaredFields
                .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                .map { it.name },
        )

        MinecraftBootstrap.ensureInitialized()
        val inventory = TrackedPlayerInventoryData(emptyList(), emptyList(), emptyList(), emptyList())
        val event = ClientPlayerInventoryEvent(inventory) as WebSocketEvent
        val payload = event.serializer.toJsonTree(event, event.javaClass)
            .asJsonObject["inventory"]
            .asJsonObject

        assertEquals(listOf("armor", "main", "crafting", "enderChest"), payload.keySet().toList())
    }
}
