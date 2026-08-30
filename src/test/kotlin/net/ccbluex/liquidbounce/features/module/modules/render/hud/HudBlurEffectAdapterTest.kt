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

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudBlurEffectAdapterTest {

    @Test
    fun `adapter preserves HUD blur settings and injected silent-screen state`() {
        val settings = object : HudBlurEffectSettings {
            override fun enabled() = true
            override fun sigma() = 5f
            override fun alphaBlendStart() = 0f
            override fun alphaBlendEnd() = 0.75f
        }
        val state = HudBlurEffectAdapter(settings) { true }.state()

        assertTrue(state.hudBlurEnabled)
        assertTrue(state.hiddenBySilentScreen)
        assertEquals(5f, state.sigma)
        assertEquals(0f, state.alphaBlendStart)
        assertEquals(0.75f, state.alphaBlendEnd)
    }

    @Test
    fun `HUD source enables blur only while module and blur effect are active`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleHud.kt"
        ))

        assertTrue(source.contains("override fun enabled(): Boolean = running && isBlurEffectActive"))
    }
}
