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
package net.ccbluex.liquidbounce.injection

import net.ccbluex.liquidbounce.injection.mixins.authlib.MixinYggdrasilMinecraftSessionServiceAccessor
import net.ccbluex.liquidbounce.injection.mixins.minecraft.entity.MixinColorParticleOptionAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FeatureRuntimeInjectionAdapterTest {

    @Test
    fun `ClickGUI provider maps readiness typing settings screen types and cache lifecycle`() {
        val source = read(CLICK_GUI_ADAPTER)

        assertTrue(source.contains("ClientLifecycleState.isInitialized"))
        assertTrue(source.contains("restIsTyping"))
        assertTrue(source.contains("ScreenManager.browserSettings"))
        assertTrue(source.contains("CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI)"))
        assertTrue(source.contains("CustomStandaloneMinecraftScreen(CustomScreenType.CLICK_GUI)"))
        assertTrue(source.contains("screen.browser.visible"))
        assertTrue(source.contains("screen.sync()"))
        assertTrue(source.contains("screen.close()"))
    }

    @Test
    fun `Chams provider preserves accessor name and output-target remap`() {
        val source = read(CHAMS_ADAPTER)

        assertTrue(source.contains("renderType.accessor().name"))
        assertTrue(source.contains("\"liquidbounce_chams/\${accessor.name}\""))
        assertTrue(source.contains("accessor.state.withOutputTarget(outputTarget)"))
    }

    @Test
    fun `particle and session providers use safe accessor casts`() {
        val particle = object : MixinColorParticleOptionAccessor {
            override fun getColor(): Int = 0x12345678
        }
        val session = object : MixinYggdrasilMinecraftSessionServiceAccessor {
            override fun getBaseUrl(): String = "https://session.example"
        }

        assertEquals(0x12345678, particleColor(particle))
        assertNull(particleColor(Any()))
        assertEquals("https://session.example", skinSessionBaseUrl(session))
        assertNull(skinSessionBaseUrl(Any()))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val CLICK_GUI_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/injection/ClickGuiRuntimeInjectionAdapter.kt"
        const val CHAMS_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/injection/ChamsRenderTypeInjectionAdapter.kt"
    }
}
