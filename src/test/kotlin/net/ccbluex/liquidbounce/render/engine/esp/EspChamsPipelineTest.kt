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
package net.ccbluex.liquidbounce.render.engine.esp

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspChamsMode
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.RenderShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EspChamsPipelineTest {

    @Test
    fun `entity and storage schemas append Chams without changing Glow defaults`() {
        assertEquals("Glow", ModuleESP.modes.activeMode.name)
        assertEquals("Chams", ModuleESP.modes.modes.last().name)
        assertEquals(EspChamsStyle.DEFAULT, EspChamsMode.style)

        val storageSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleStorageESP.kt",
        ))
        assertTrue(storageSource.contains(
            "choices(\"Mode\", GlowMode, arrayOf(BoxMode, OutlineMode, GlowMode, ChamsMode))",
        ))
        assertTrue(storageSource.contains(
            "object ChamsMode : ShaderMode(\"Chams\", StorageShaderEffect.CHAMS)",
        ))
    }

    @Test
    fun `Chams owns independent prepared masks and a dedicated composite pass`() {
        assertTrue(EspMaskLayer.entries.contains(EspMaskLayer.ENTITY_CHAMS))
        assertTrue(EspMaskLayer.entries.contains(EspMaskLayer.STORAGE_CHAMS))
        assertEquals(
            listOf(EspPostProcessPass.CHAMS_COMPOSITE),
            EspPostProcessPlan.create(hasGlow = false, hasOutline = false, hasChams = true),
        )
    }

    @Test
    fun `storage Chams caches model blocks instead of depending on Sodium terrain submission`() {
        assertTrue(StorageShaderMaskPolicy.requiresCachedGeometry(RenderShape.MODEL, StorageShaderEffect.CHAMS))
        assertFalse(StorageShaderMaskPolicy.requiresCachedGeometry(RenderShape.MODEL, StorageShaderEffect.GLOW))
        assertTrue(StorageShaderMaskPolicy.requiresCachedGeometry(RenderShape.INVISIBLE, StorageShaderEffect.GLOW))
    }

    @Test
    fun `stronger Chams opacity wins when entity and storage masks share a frame`() {
        assertEquals(
            EspChamsStyle(opacity = 0.8f),
            EspShaderStyleResolver.resolveChams(EspChamsStyle(0.35f), EspChamsStyle(0.8f)),
        )
    }

    @Test
    fun `English and German describe entity and storage Chams settings`() {
        val keys = listOf(
            "liquidbounce.module.esp.mode.chams.extendedDescription",
            "liquidbounce.module.esp.mode.chams.opacity.description",
            "liquidbounce.module.storageESP.mode.chams.extendedDescription",
            "liquidbounce.module.storageESP.mode.chams.opacity.description",
        )
        for (locale in listOf("en_us", "de_de")) {
            val translations = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/resources/liquidbounce/lang/$locale.json",
            ))).asJsonObject
            keys.forEach { key -> assertTrue(translations[key]?.asString?.isNotBlank() == true, "$locale: $key") }
        }
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
