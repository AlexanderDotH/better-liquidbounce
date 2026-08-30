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
package net.ccbluex.liquidbounce.integration

import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientIntegrationLink
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeDescription
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeHandle
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeReloadResult
import net.ccbluex.liquidbounce.integration.theme.Background
import net.ccbluex.liquidbounce.integration.theme.ThemeMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ClientCommandRuntimeAdapterTest {

    @Test
    fun `theme description preserves public metadata and origin tag`() {
        val metadata = ThemeMetadata(
            id = "modern", name = "Modern", version = "2.1", authors = listOf("Alex", "CCBlueX"),
            colors = null, screens = emptyList(), overlays = emptyList(), components = emptyList(),
            fonts = emptyList(), backgrounds = listOf(Background("default", setOf("png"))), values = null,
        )

        assertEquals(
            ClientThemeDescription("modern", "Modern", "2.1", listOf("Alex", "CCBlueX"), "remote"),
            describeTheme(metadata, "remote"),
        )
    }

    @Test
    fun `link mapping skips route failures and theme lookup ignores id case`() {
        val links = mapAvailableIntegrationLinks(listOf("hud", "broken", "clickgui"), { it }) { route ->
            if (route == "broken") error("unsupported") else "http://client/$route"
        }
        val theme = TestThemeHandle("Modern")

        assertEquals(
            listOf(
                ClientIntegrationLink("hud", "http://client/hud"),
                ClientIntegrationLink("clickgui", "http://client/clickgui"),
            ),
            links,
        )
        assertSame(theme, findThemeHandle(listOf(theme), "modern"))
    }

    @Test
    fun `provider delegates browser reset theme activation and remote load`() = runBlocking {
        val folder = File("themes")
        val theme = TestThemeHandle("modern")
        val operations = mutableListOf<String>()
        val provider = RuntimeClientCommandProvider(
            browserOpener = { operations += "browser:$it" },
            integrationResetter = { operations += "reset" },
            baseUrlSupplier = { "http://client" },
            linkSupplier = { listOf(ClientIntegrationLink("hud", "http://client/hud")) },
            themesFolderSupplier = { folder },
            themeSupplier = { listOf(theme) },
            remoteThemeLoader = { url -> operations += "load:$url"; theme },
            themeActivator = { operations += "activate:${it.description.id}" },
            themeReloader = { operations += "reload"; ClientThemeReloadResult(1, 2) },
        )

        provider.openBrowser("https://example.invalid")
        provider.resetIntegration()
        assertEquals("http://client", provider.integrationBaseUrl())
        assertSame(folder, provider.themesFolder())
        assertEquals(listOf("modern"), provider.themeIds())
        assertSame(theme, provider.loadRemoteTheme("http://localhost/theme"))
        assertTrue(provider.activateTheme(theme).isSuccess)
        assertEquals(ClientThemeReloadResult(1, 2), provider.reloadThemes())
        assertEquals(
            listOf(
                "browser:https://example.invalid", "reset", "load:http://localhost/theme",
                "activate:modern", "reload",
            ),
            operations,
        )
    }

    @Test
    fun `reload result captures counts around the reload operation`() = runBlocking {
        var count = 3
        var reloaded = false
        val result = reloadThemeCatalog({ count }) {
            reloaded = true
            count = 5
        }

        assertTrue(reloaded)
        assertEquals(ClientThemeReloadResult(3, 5), result)
    }

    @Test
    fun `activation failures stay inside the result contract`() {
        val provider = RuntimeClientCommandProvider(
            browserOpener = {}, integrationResetter = {}, baseUrlSupplier = { "" }, linkSupplier = { emptyList() },
            themesFolderSupplier = { File(".") },
            themeSupplier = { emptyList() },
            remoteThemeLoader = { TestThemeHandle("") },
            themeActivator = { error("failed") }, themeReloader = { ClientThemeReloadResult(0, 0) },
        )

        val result = provider.activateTheme(TestThemeHandle("broken"))
        assertFalse(result.isSuccess)
        assertEquals("failed", result.exceptionOrNull()?.message)
    }

    private data class TestThemeHandle(private val id: String) : ClientThemeHandle {
        override val description = ClientThemeDescription(id, id, "1", emptyList(), "test")
    }
}
