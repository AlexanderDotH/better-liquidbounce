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
package net.ccbluex.liquidbounce.render.playermodel

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmnesiaPlayerModelBridgeTest {

    @Test
    fun `bridge fails closed and evaluates its provider dynamically`() {
        AmnesiaPlayerModelBridge.withProviderForTest(null) {
            assertFalse(AmnesiaPlayerModelBridge.isRunning())
        }

        var running = false
        val provider = object : AmnesiaPlayerModelProvider {
            override fun isRunning() = running
        }
        AmnesiaPlayerModelBridge.withProviderForTest(provider) {
            assertFalse(AmnesiaPlayerModelBridge.isRunning())
            running = true
            assertTrue(AmnesiaPlayerModelBridge.isRunning())
        }
    }

    @Test
    fun `render hooks depend only on the render owned Amnesia bridge`() {
        val hooks = HOOKS.map(::read)

        hooks.forEach { source ->
            assertFalse(source.contains("features.module.modules.`fun`.ModuleAmnesia"))
        }
        assertTrue(hooks.any { it.contains("AmnesiaPlayerModelBridge.actionState(entity)") })
        assertTrue(hooks.any { it.contains("AmnesiaPlayerModelBridge.visualTransform(entity)") })
        assertTrue(hooks.any { it.contains("AmnesiaPlayerModelBridge.spoofedDisplayName(player, original)") })
        assertTrue(hooks.any { it.contains("AmnesiaPlayerModelBridge.findTarget()") })
    }

    @Test
    fun `ModuleAmnesia installs every player model decision without changing its facade`() {
        val module = read(MODULE_AMNESIA)
        val adapter = read(AMNESIA_ADAPTER)

        assertTrue(module.contains("AmnesiaPlayerModelAdapter.install()"))
        assertTrue(adapter.contains("AmnesiaPlayerModelBridge.install(this)"))
        assertTrue(adapter.contains("override fun actionState(entity: LivingEntity) ="))
        assertTrue(adapter.contains("ModuleAmnesia.getActionState(entity)"))
        assertTrue(adapter.contains("override fun visualTransform(entity: LivingEntity) ="))
        assertTrue(adapter.contains("ModuleAmnesia.getVisualTransform(entity)"))
        assertTrue(adapter.contains("override fun spoofedDisplayName(player: Player, original: Component) ="))
        assertTrue(adapter.contains("ModuleAmnesia.getSpoofedDisplayName(player, original)"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        val HOOKS = listOf(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelActionHook.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelAppearanceHook.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelDelayHook.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelNametagHook.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelParticleHook.kt",
        )
        const val MODULE_AMNESIA =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/fun/ModuleAmnesia.kt"
        const val AMNESIA_ADAPTER =
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/fun/amnesia/" +
                    "AmnesiaPlayerModelAdapter.kt"
    }
}
