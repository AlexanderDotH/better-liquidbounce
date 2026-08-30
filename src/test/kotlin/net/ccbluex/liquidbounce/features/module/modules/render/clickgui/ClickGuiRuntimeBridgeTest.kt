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
package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.client.gui.screens.Screen
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClickGuiRuntimeBridgeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `missing provider remains fail closed`() {
        ClickGuiRuntimeBridge.withProviderForTest(null) {
            assertFalse(ClickGuiRuntimeBridge.isClientInitialized())
            assertFalse(ClickGuiRuntimeBridge.isTyping())
            assertFalse(ClickGuiRuntimeBridge.isClickGuiScreen(null))
        }
    }

    @Test
    fun `installed provider preserves readiness typing and browser settings`() {
        val settings = ValueGroup("BrowserSettings")
        val provider = object : ClickGuiRuntimeHook {
            override fun isClientInitialized() = true
            override fun isTyping() = true
            override fun isClickGuiScreen(screen: Screen?) = screen == null
            override fun browserSettings() = settings
            override fun createSharedScreen(): Screen = error("unused")
            override fun createStandaloneScreen(): CachedClickGuiScreenBridge = error("unused")
        }

        ClickGuiRuntimeBridge.withProviderForTest(provider) {
            assertTrue(ClickGuiRuntimeBridge.isClientInitialized())
            assertTrue(ClickGuiRuntimeBridge.isTyping())
            assertTrue(ClickGuiRuntimeBridge.isClickGuiScreen(null))
            assertSame(settings, ClickGuiRuntimeBridge.browserSettings())
        }
    }
}
