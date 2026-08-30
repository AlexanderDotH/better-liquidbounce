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
package net.ccbluex.liquidbounce.deeplearn.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

class ModelCommandIntegrationBridgeTest {
    @Test
    fun `provider supplies recorder folders and click gui synchronization`() {
        val calls = mutableListOf<String>()
        val provider = object : ModelCommandIntegrationProvider {
            override val combatRecorderFolder = File("combat")
            override val trainerRecorderFolder = File("trainer")
            override fun syncClickGui() {
                calls += "sync"
            }
        }

        ModelCommandIntegrationBridge.withProviderForTest(provider) {
            assertEquals(File("combat"), ModelCommandIntegrationBridge.combatRecorderFolder())
            assertEquals(File("trainer"), ModelCommandIntegrationBridge.trainerRecorderFolder())
            ModelCommandIntegrationBridge.syncClickGui()
        }
        assertEquals(listOf("sync"), calls)
    }

    @Test
    fun `missing provider fails fast`() {
        ModelCommandIntegrationBridge.withProviderForTest(null) {
            assertThrows(IllegalStateException::class.java, ModelCommandIntegrationBridge::combatRecorderFolder)
        }
    }
}
