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
package net.ccbluex.liquidbounce.render.engine.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GuiGlowLifecycleMixinTest {

    @Test
    fun `GUI glow frame begins before HUD extraction appends requests`() {
        val source = Files.readString(
            Path.of(
                "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java"
            )
        )
        val extractHook = Regex(
            """@Inject\(method = "extract", at = @At\("HEAD"\)\)\s+""" +
                """private void beginGuiGlowFrame\(CallbackInfo callbackInfo\) \{\s+""" +
                """GuiGlowRenderer\.beginFrame\(\);\s+}"""
        )
        val renderHook = source.substringAfter("public void hookGameRender").substringBefore('}')

        assertTrue(extractHook.containsMatchIn(source))
        assertFalse(renderHook.contains("GuiGlowRenderer.beginFrame()"))
    }
}
