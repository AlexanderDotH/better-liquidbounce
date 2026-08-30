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

class PlayerModelNametagStateBridgeTest {

    @Test
    fun `bridge fails closed and reads the provider on every decision`() {
        PlayerModelNametagStateBridge.withProviderForTest(null) {
            assertFalse(PlayerModelNametagStateBridge.isRunning())
        }

        var running = false
        PlayerModelNametagStateBridge.withProviderForTest({ running }) {
            assertFalse(PlayerModelNametagStateBridge.isRunning())
            running = true
            assertTrue(PlayerModelNametagStateBridge.isRunning())
        }
    }

    @Test
    fun `player model hooks preserve the nametag guard through the render owned bridge`() {
        val delay = read(PLAYER_MODEL_DELAY_HOOK)
        val nametag = read(PLAYER_MODEL_NAMETAG_HOOK)

        assertTrue(
            delay.contains(
                "if (PlayerModelNametagStateBridge.isRunning() &&\n" +
                    "                    PlayerModelNametagHook.shouldSuppressVanillaNameDisplay(state))",
            ),
        )
        assertTrue(nametag.contains("if (!PlayerModelNametagStateBridge.isRunning())"))
        assertFalse(delay.contains("ModuleNametags"))
        assertFalse(nametag.contains("ModuleNametags"))
    }

    @Test
    fun `nametag module installs its dynamic running state from its existing owner package`() {
        val module = read(MODULE_NAMETAGS)
        val adapter = read(NAMETAG_PLAYER_MODEL_ADAPTER)

        assertTrue(module.contains("NametagPlayerModelAdapter.install()"))
        assertTrue(adapter.contains("PlayerModelNametagStateBridge.install(this)"))
        assertTrue(adapter.contains("override fun isRunning() = ModuleNametags.running"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val PLAYER_MODEL_DELAY_HOOK =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelDelayHook.kt"
        const val PLAYER_MODEL_NAMETAG_HOOK =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelNametagHook.kt"
        const val MODULE_NAMETAGS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/nametags/ModuleNametags.kt"
        const val NAMETAG_PLAYER_MODEL_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/nametags/" +
                "NametagPlayerModelAdapter.kt"
    }
}
