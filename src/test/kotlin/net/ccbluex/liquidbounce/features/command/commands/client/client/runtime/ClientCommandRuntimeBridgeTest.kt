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
package net.ccbluex.liquidbounce.features.command.commands.client.client.runtime

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

class ClientCommandRuntimeBridgeTest {
    @Test
    fun `provider preserves integration links and theme descriptors`() {
        val theme = TestTheme(ClientThemeDescription("default", "Default", "1", listOf("CCBlueX"), "local"))
        val provider = TestProvider(theme)

        ClientCommandRuntimeBridge.withProviderForTest(provider) {
            runBlocking {
                assertEquals("http://localhost/", ClientCommandRuntimeBridge.integrationBaseUrl())
                assertEquals(
                    listOf(ClientIntegrationLink("clickgui", "http://localhost/clickgui")),
                    ClientCommandRuntimeBridge.integrationLinks(),
                )
                assertEquals(listOf(theme), ClientCommandRuntimeBridge.themes())
                assertEquals(theme, ClientCommandRuntimeBridge.findTheme("DEFAULT"))
                assertEquals(theme, ClientCommandRuntimeBridge.loadRemoteTheme("http://localhost/theme"))
                assertEquals(ClientThemeReloadResult(1, 1), ClientCommandRuntimeBridge.reloadThemes())
            }
        }
    }

    @Test
    fun `missing provider fails fast`() {
        ClientCommandRuntimeBridge.withProviderForTest(null) {
            assertThrows(IllegalStateException::class.java, ClientCommandRuntimeBridge::integrationBaseUrl)
        }
    }

    private data class TestTheme(override val description: ClientThemeDescription) : ClientThemeHandle

    private class TestProvider(private val theme: ClientThemeHandle) : ClientCommandRuntimeProvider {
        override fun openBrowser(name: String) = Unit
        override fun resetIntegration() = Unit
        override fun integrationBaseUrl() = "http://localhost/"
        override fun integrationLinks() = listOf(ClientIntegrationLink("clickgui", "http://localhost/clickgui"))
        override fun themesFolder() = File("themes")
        override fun themeIds() = listOf(theme.description.id)
        override fun themes() = listOf(theme)
        override suspend fun loadRemoteTheme(url: String) = theme
        override fun findTheme(id: String) = theme.takeIf { it.description.id.equals(id, true) }
        override fun activateTheme(theme: ClientThemeHandle) = Result.success(Unit)
        override suspend fun reloadThemes() = ClientThemeReloadResult(1, 1)
    }
}
