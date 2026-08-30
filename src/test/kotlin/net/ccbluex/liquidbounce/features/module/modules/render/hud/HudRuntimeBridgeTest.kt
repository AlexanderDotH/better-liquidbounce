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
package net.ccbluex.liquidbounce.features.module.modules.render.hud

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.client.gui.screens.Screen
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HudRuntimeBridgeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `missing provider exposes no integration-owned settings`() {
        HudRuntimeBridge.withProviderForTest(null) {
            assertFalse(HudRuntimeBridge.isClickGuiScreen(null))
            assertEquals(emptyList(), HudRuntimeBridge.themeSettings())
            assertEquals(emptyList(), HudRuntimeBridge.additionalComponents())
        }
    }

    @Test
    fun `installed provider keeps overlay themes and components intact`() {
        val themes = listOf(ValueGroup("FirstTheme"), ValueGroup("SecondTheme"))
        val components = listOf(ValueGroup("Minimap"), ValueGroup("SeedCracker"))
        val overlay = RecordingOverlay()
        val provider = object : HudRuntimeProvider {
            override fun createOverlay(reopen: () -> Unit) = overlay
            override fun isClickGuiScreen(screen: Screen?) = screen == null
            override fun themeSettings() = themes
            override fun additionalComponents() = components
        }

        HudRuntimeBridge.withProviderForTest(provider) {
            assertSame(overlay, HudRuntimeBridge.createOverlay {})
            assertTrue(HudRuntimeBridge.isClickGuiScreen(null))
            assertEquals(themes, HudRuntimeBridge.themeSettings())
            assertEquals(components, HudRuntimeBridge.additionalComponents())
        }
    }

    private class RecordingOverlay : HudOverlayHandle {
        override val browserSettings = ValueGroup("BrowserSettings")
        override var visible = false
        override fun close() = Unit
    }
}
