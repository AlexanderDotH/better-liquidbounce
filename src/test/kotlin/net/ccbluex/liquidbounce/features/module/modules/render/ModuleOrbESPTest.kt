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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleOrbESPTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @BeforeEach
    fun restoreDefaults() {
        ModuleOrbESP.restore()
    }

    @Test
    fun `orb esp exposes its stable public config identity and defaults`() {
        val maximumDistance = ModuleOrbESP.rootSetting("MaximumDistance") as RangedValue<*>
        val tracers = ModuleOrbESP.rootSetting("Tracers") as Value<*>

        assertEquals("OrbESP", ModuleOrbESP.name)
        assertEquals("liquidbounce.module.orbEsp", ModuleOrbESP.baseKey)
        assertEquals(128f, maximumDistance.get())
        assertEquals(1f, maximumDistance.range.start)
        assertEquals(512f, maximumDistance.range.endInclusive)
        assertFalse(tracers.get() as Boolean)
        assertFalse(ModuleOrbESP.showTracers)
    }

    @Test
    fun `glow is the default of the exact orb render mode set`() {
        val mode = ModuleOrbESP.mode()

        assertEquals("Glow", mode.activeMode.name)
        assertEquals(listOf("Glow", "Box", "Legacy2D"), mode.modes.map(Mode::name))
        assertEquals(
            listOf("Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            mode.modes.single { it.name == "Glow" }.inner.map { it.name },
        )
        assertEquals(
            listOf("MergeIntersecting"),
            mode.modes.single { it.name == "Box" }.inner.map { it.name },
        )
        assertEquals(
            listOf("Scale", "YOffset", "BackgroundAlpha"),
            mode.modes.single { it.name == "Legacy2D" }.inner.map { it.name },
        )
    }

    @Test
    fun `orb colors default to opaque xp green and retain rainbow choice`() {
        val colorMode = ModuleOrbESP.colorMode()
        val static = colorMode.modes.single { it.name == "Static" }

        assertEquals("Static", colorMode.activeMode.name)
        assertEquals(listOf("Static", "Rainbow"), colorMode.modes.map(Mode::name))
        assertEquals(listOf("Color"), static.inner.map { it.name })
        assertEquals(Color4b(120, 230, 120, 255), static.inner.single().get())
        assertEquals(emptyList<String>(), colorMode.modes.single { it.name == "Rainbow" }.inner.map { it.name })
    }

    @Suppress("UNCHECKED_CAST")
    private fun net.ccbluex.liquidbounce.config.types.group.ValueGroup.mode(): ModeValueGroup<Mode> =
        rootSetting("Mode") as ModeValueGroup<Mode>

    @Suppress("UNCHECKED_CAST")
    private fun net.ccbluex.liquidbounce.config.types.group.ValueGroup.colorMode(): ModeValueGroup<Mode> =
        rootSetting("ColorMode") as ModeValueGroup<Mode>

    private fun net.ccbluex.liquidbounce.config.types.group.ValueGroup.rootSetting(name: String): Value<*> =
        inner.single { it.name == name }
}
