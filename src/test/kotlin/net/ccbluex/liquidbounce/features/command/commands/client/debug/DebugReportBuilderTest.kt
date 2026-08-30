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
package net.ccbluex.liquidbounce.features.command.commands.client.debug

import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DebugReportBuilderTest {
    @Test
    fun `client section uses neutral build metadata`() {
        val client = clientDebugJson()

        assertEquals(ClientBuildMetadata.NAME, client["name"].asString)
        assertEquals(ClientBuildMetadata.version, client["version"].asString)
        assertEquals(ClientBuildMetadata.commit, client["commit"].asString)
        assertEquals(ClientBuildMetadata.branch, client["branch"].asString)
        assertEquals(ClientBuildMetadata.IN_DEVELOPMENT, client["development"].asBoolean)
    }

    @Test
    fun `script section preserves report field names and values`() {
        val script = DebugScriptDescriptor("Example", "1.2.3", "Alex, CCBlueX", "/tmp/example.js")

        val scripts = DebugScriptInventoryBridge.withProviderForTest(DebugScriptInventoryProvider { listOf(script) }) {
            scriptsDebugJson()
        }

        assertEquals(1, scripts.size())
        assertEquals(script.name, scripts[0].asJsonObject["name"].asString)
        assertEquals(script.version, scripts[0].asJsonObject["version"].asString)
        assertEquals(script.authors, scripts[0].asJsonObject["author"].asString)
        assertEquals(script.path, scripts[0].asJsonObject["path"].asString)
    }

    @Test
    fun `missing script provider is fail closed`() {
        val scripts = DebugScriptInventoryBridge.withProviderForTest(null) {
            scriptsDebugJson()
        }

        assertFalse(scripts.iterator().hasNext())
    }

    @Test
    fun `script provider can only be installed once`() {
        val provider = DebugScriptInventoryProvider(::emptyList)

        DebugScriptInventoryBridge.withProviderForTest(null) {
            DebugScriptInventoryBridge.install(provider)
            assertThrows(IllegalStateException::class.java) {
                DebugScriptInventoryBridge.install(provider)
            }
        }
    }
}
