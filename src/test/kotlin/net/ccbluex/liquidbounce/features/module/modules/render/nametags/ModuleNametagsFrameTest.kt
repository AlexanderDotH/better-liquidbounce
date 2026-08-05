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
package net.ccbluex.liquidbounce.features.module.modules.render.nametags

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleNametagsFrameTest {

    @Test
    fun `Modern is the default frame and every frame owns only its settings`() {
        MinecraftBootstrap.ensureInitialized()

        val frames = ModuleNametags.frameModes

        assertEquals("Modern", frames.activeMode.name)
        assertEquals(listOf("Classic", "Modern", "Glow"), frames.modes.map { it.name })
        assertEquals(listOf("BorderWidth", "BackgroundRadius"), ModuleNametags.ClassicFrame.inner.map { it.name })
        assertTrue(ModuleNametags.ModernFrame.inner.isEmpty())
        assertEquals(
            listOf("Color", "Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            ModuleNametags.GlowFrame.inner.map { it.name },
        )
        assertEquals(Color4b(70, 119, 255, 255), ModuleNametags.GlowFrame.color)
    }

    @Test
    fun `Classic Modern and Glow resolve their specified appearances`() {
        assertEquals(
            NametagFrameAppearance(
                fill = Color4b.DEFAULT_BG_COLOR,
                border = Color4b.BLACK,
                borderWidth = 1f,
                radius = 2f,
                glow = null,
            ),
            resolveNametagFrameAppearance(NametagFrameKind.CLASSIC, 1f, 2f),
        )

        val modern = NametagFrameAppearance(
            fill = Color4b(15, 18, 23, 214),
            border = Color4b(255, 255, 255, 26),
            borderWidth = 1f,
            radius = 6f,
            glow = null,
        )
        assertEquals(modern, resolveNametagFrameAppearance(NametagFrameKind.MODERN, 8f, 16f))

        val glowStyle = EspGlowStyle(18f, 1.2f, 1.4f, 2f, 0.8f)
        val glowColor = Color4b(0, 128, 255, 255)
        assertEquals(
            modern.copy(glow = NametagFrameGlow(glowColor, glowStyle)),
            resolveNametagFrameAppearance(
                NametagFrameKind.GLOW,
                classicBorderWidth = 8f,
                classicRadius = 16f,
                glowColor = glowColor,
                glowStyle = glowStyle,
            ),
        )
    }

    @Test
    fun `legacy frame geometry migrates into Classic while existing profiles adopt Modern`() {
        val config = JsonObject().apply {
            addProperty("name", "Nametags")
            add("value", JsonArray().apply {
                add(storedValue("BorderWidth", 4.5f))
                add(storedValue("BackgroundRadius", 9f))
                add(storedValue("Scale", 1f))
            })
        }

        migrateLegacyNametagFrame(config)

        val values = config.getAsJsonArray("value")
        assertFalse(values.any { it.asJsonObject["name"].asString == "BorderWidth" })
        assertFalse(values.any { it.asJsonObject["name"].asString == "BackgroundRadius" })

        val frame = values.single { it.asJsonObject["name"].asString == "Frame" }.asJsonObject
        assertEquals("Modern", frame["active"].asString)

        val classicValues = frame.getAsJsonObject("choices")
            .getAsJsonObject("Classic")
            .getAsJsonArray("value")
        assertEquals(4.5f, classicValues.single { it.asJsonObject["name"].asString == "BorderWidth" }
            .asJsonObject["value"].asFloat)
        assertEquals(9f, classicValues.single { it.asJsonObject["name"].asString == "BackgroundRadius" }
            .asJsonObject["value"].asFloat)
    }

    @Test
    fun `already nested frame configuration is not rewritten`() {
        val existingFrame = JsonObject().apply {
            addProperty("name", "Frame")
            addProperty("active", "Classic")
            add("value", JsonArray())
            add("choices", JsonObject())
        }
        val config = JsonObject().apply {
            addProperty("name", "Nametags")
            add("value", JsonArray().apply { add(existingFrame) })
        }

        migrateLegacyNametagFrame(config)

        assertTrue(config.getAsJsonArray("value").single().asJsonObject === existingFrame)
    }

    private fun storedValue(name: String, value: Number) = JsonObject().apply {
        addProperty("name", name)
        addProperty("value", value)
    }
}
