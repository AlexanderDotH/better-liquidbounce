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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.RegistryMutableListValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class ModuleBaritoneTest {

    @Test
    fun `Baritone is an opt-in player module with user takeover defaults`() {
        assertEquals(ModuleCategories.PLAYER, ModuleBaritone.category)
        assertFalse(ModuleBaritone.enabled)
        assertFalse(ModuleBaritone.disableOnQuit)
        assertEquals(true, ModuleBaritone.setting("PauseOnUserInput").get())
        assertEquals(10, ModuleBaritone.setting("ResumeDelay").get())
    }

    @Test
    fun `Baritone defaults to Fly navigation with stable coordinator settings`() {
        val navigationMode = ModuleBaritone.setting("NavigationMode")

        assertInstanceOf(ModeValueGroup::class.java, navigationMode)
        assertEquals(ValueType.CHOICE, navigationMode.valueType)
        navigationMode as ModeValueGroup<*>
        assertEquals(listOf("Fly", "Walk"), navigationMode.modes.map { it.name })
        assertEquals("Fly", navigationMode.activeMode.name)

        val fly = navigationMode.modes.single { it.name == "Fly" }
        assertEquals(listOf("ArmTimeout", "MaxRestarts", "RetryDistance"), fly.inner.map { it.name })
        assertEquals(10, fly.setting("ArmTimeout").get())
        assertEquals(3, fly.setting("MaxRestarts").get())
        assertEquals(32, fly.setting("RetryDistance").get())

        assertEquals(BaritoneNavigationMode.FLY, ModuleBaritone.navigationMode)
        assertEquals(
            BaritoneFlyNavigationConfig(
                armTimeoutTicks = 200,
                maxRestarts = 3,
                retryDistanceBlocks = 32,
            ),
            ModuleBaritone.flyNavigationConfig,
        )

        try {
            navigationMode.setByString("Walk")
            assertEquals(BaritoneNavigationMode.WALK, ModuleBaritone.navigationMode)
        } finally {
            navigationMode.restore()
        }
    }

    @Test
    fun `Baritone keeps existing top-level settings in their established order`() {
        assertEquals(
            listOf("NavigationMode", "PauseOnUserInput", "ResumeDelay", "ConflictModules", "OpenDashboard"),
            ModuleBaritone.inner
                .map { it.name }
                .filter { it in establishedSettings },
        )
    }

    @Test
    fun `existing module config restores the selected navigation mode and nested Fly settings`() {
        val original = ConfigSystem.serializeValueGroup(ModuleBaritone, fileGson)
        val navigationMode = ModuleBaritone.setting("NavigationMode") as ModeValueGroup<*>
        val fly = navigationMode.modes.single { it.name == "Fly" }

        try {
            navigationMode.setByString("Walk")
            fly.setting("ArmTimeout").setByString("17")
            fly.setting("MaxRestarts").setByString("4")
            fly.setting("RetryDistance").setByString("41")
            val saved = ConfigSystem.serializeValueGroup(ModuleBaritone, fileGson)

            navigationMode.setByString("Fly")
            fly.setting("ArmTimeout").setByString("10")
            fly.setting("MaxRestarts").setByString("3")
            fly.setting("RetryDistance").setByString("32")
            ConfigSystem.deserializeValueGroup(ModuleBaritone, saved)

            assertEquals("Walk", navigationMode.activeMode.name)
            assertEquals(17, fly.setting("ArmTimeout").get())
            assertEquals(4, fly.setting("MaxRestarts").get())
            assertEquals(41, fly.setting("RetryDistance").get())
        } finally {
            ConfigSystem.deserializeValueGroup(ModuleBaritone, original)
        }
    }

    @Test
    fun `Baritone exposes the complete default movement conflict registry`() {
        val setting = ModuleBaritone.setting("ConflictModules")

        assertInstanceOf(RegistryMutableListValue::class.java, setting)
        assertEquals(ValueType.REGISTRY_MUTABLE_LIST, setting.valueType)
        assertEquals(ValueType.CLIENT_MODULE, (setting as RegistryMutableListValue<*, *>).innerValueType)
        assertEquals(defaultConflictModules, setting.get())
    }

    @Test
    fun `Baritone dashboard is exposed as a stateless ClickGUI action`() {
        val action = ModuleBaritone.setting("OpenDashboard")

        assertEquals(ValueType.ACTION, action.valueType)
        assertFalse(action.get() as Boolean)
        assertTrue(action.isImmutable)
    }

    @Test
    fun `disabling Baritone delegates atomic cancellation and key cleanup to its lifecycle`() {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val facade = Proxy.newProxyInstance(
            BaritoneFacade::class.java.classLoader,
            arrayOf(BaritoneFacade::class.java),
        ) { _, method, arguments ->
            calls += method.name to arguments.orEmpty().toList()
            null
        } as BaritoneFacade

        ModuleBaritone.releaseControl(facade)

        assertEquals(listOf("lifecycle"), calls.map { it.first })
        assertEquals(listOf(BaritoneLifecycleEvent.DISABLE), calls.first().second)
    }

    private companion object {
        val establishedSettings = setOf(
            "NavigationMode",
            "PauseOnUserInput",
            "ResumeDelay",
            "ConflictModules",
            "OpenDashboard",
        )

        val defaultConflictModules = listOf(
            "FightBot",
            "SpearKill",
            "MaceKill",
            "ClickTp",
            "Teleport",
            "Phase",
            "Clip",
            "VClip",
            "Fly",
            "Speed",
            "LongJump",
            "AutoDodge",
            "TargetStrafe",
            "Freeze",
            "ElytraFly",
            "AutoWalk",
            "Blink",
            "FreeCam",
            "Scaffold",
        )

        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }
