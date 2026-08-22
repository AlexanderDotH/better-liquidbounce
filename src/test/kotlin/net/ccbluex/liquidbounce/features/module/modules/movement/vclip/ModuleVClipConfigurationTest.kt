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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ModuleVClipConfigurationTest {

    @Test
    fun `VClip is a persistent movement module with Vanilla and Folia modes`() {
        assertEquals(ModuleCategories.MOVEMENT, ModuleVClip.category)
        assertFalse(ModuleVClip.disableActivation)
        assertTrue(ModuleVClip.disableOnQuit)
        assertEquals(listOf("Vanilla", "Folia"), ModuleVClip.modes.modes.map { it.name })
        assertEquals("Vanilla", ModuleVClip.modes.activeMode.name)
    }

    @Test
    fun `VClip preserves distance and smart command targets as settings`() {
        assertEquals(listOf("Distance", "Smart"), ModuleVClip.targets.modes.map { it.name })
        assertEquals("Smart", ModuleVClip.targets.activeMode.name)
        assertEquals(5.0, ModuleVClip.target("Distance").setting("Blocks").numericValue(), 0.0)

        val smart = ModuleVClip.target("Smart")
        val scanDistance = smart.setting("ScanDistance") as ToggleableValueGroup
        assertEquals(listOf("ScanDistance", "DoNotClipAroundBedrock"), smart.inner.map { it.name })
        assertTrue(scanDistance.enabled)
        assertEquals(10, scanDistance.setting("MaxDistance").get())
        assertEquals(true, smart.setting("DoNotClipAroundBedrock").get())
        assertEquals(5, ModuleVClip.setting("RepeatDelay").get())
    }

    @Test
    fun `Smart exposes ScanDistance as a ClickGUI checkbox with a nested slider`() {
        val smartJson = interopGson.toJsonTree(ModuleVClip.target("Smart")).asJsonObject
        val settings = smartJson.getAsJsonArray("value").map { it.asJsonObject }
        val scanDistance = settings.first()

        assertEquals("ScanDistance", scanDistance["name"].asString)
        assertEquals(ValueType.TOGGLEABLE.name, scanDistance["valueType"].asString)
        assertEquals(
            listOf("Enabled", "MaxDistance"),
            scanDistance.getAsJsonArray("value").map { it.asJsonObject["name"].asString },
        )
        assertEquals(true, scanDistance.getAsJsonArray("value")[0].asJsonObject["value"].asBoolean)
    }

    @Test
    fun `Vanilla keeps relevant command movement settings and Folia defaults to five packets`() {
        val vanilla = ModuleVClip.mode("Vanilla")
        val folia = ModuleVClip.mode("Folia")

        assertEquals(false, vanilla.setting("PaperBypass").get())
        assertEquals(false, vanilla.setting("FullPacket").get())
        assertEquals(VClipGroundMode.CORRECT, vanilla.setting("GroundMode").get())
        assertEquals(true, vanilla.setting("ResetMotion").get())
        assertEquals(5, folia.setting("MovementPackets").get())
        assertEquals(false, folia.setting("FullPacket").get())
        assertEquals(VClipGroundMode.CORRECT, folia.setting("GroundMode").get())
        assertEquals(true, folia.setting("ResetMotion").get())
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}

private fun ModuleVClip.mode(name: String): Mode = modes.modes.single { it.name == name }

private fun ModuleVClip.target(name: String): Mode = targets.modes.single { it.name == name }

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

private fun Value<*>.numericValue(): Double = (get() as Number).toDouble()
