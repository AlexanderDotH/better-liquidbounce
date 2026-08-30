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
package net.ccbluex.liquidbounce.integration.theme

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeRouteSupportTest {

    @Test
    fun `null route remains unsupported before metadata is loaded`() {
        val routes = MetadataThemeRouteSupport()

        assertFalse(routes.isSupported(null))
        assertFalse(routes.isScreenSupported(null))
        assertFalse(routes.isOverlaySupported(null))
    }

    @Test
    fun `named route still requires loaded metadata`() {
        val routes = MetadataThemeRouteSupport()

        assertThrows(IllegalArgumentException::class.java) {
            routes.isSupported("clickgui")
        }
    }

    @Test
    fun `screen and overlay membership preserve route support semantics`() {
        val routes = MetadataThemeRouteSupport().apply {
            load(metadata(screens = listOf("clickgui"), overlays = listOf("hud")))
        }

        assertTrue(routes.isSupported("clickgui"))
        assertTrue(routes.isSupported("hud"))
        assertTrue(routes.isScreenSupported("clickgui"))
        assertTrue(routes.isOverlaySupported("hud"))
        assertFalse(routes.isScreenSupported("hud"))
        assertFalse(routes.isOverlaySupported("clickgui"))
        assertFalse(routes.isSupported("unknown"))
    }

    private fun metadata(screens: List<String>, overlays: List<String>) = ThemeMetadata(
        id = "test-theme",
        name = "Test Theme",
        version = "1",
        authors = emptyList(),
        colors = null,
        screens = screens,
        overlays = overlays,
        components = emptyList(),
        fonts = emptyList(),
        backgrounds = emptyList(),
    )
}
