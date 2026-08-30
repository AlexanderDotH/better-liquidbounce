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
package net.ccbluex.liquidbounce.common

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GhostHandHookTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `uninstalled hook fails closed`() {
        GhostHandHook.withProvidersForTest(null, null) {
            assertFalse(GhostHandHook.isRunning())
            assertFalse(GhostHandHook.isTargeted(Blocks.CHEST))
        }
    }

    @Test
    fun `installed providers preserve running and targeted decisions`() {
        GhostHandHook.withProvidersForTest({ true }, { it === Blocks.CHEST }) {
            assertTrue(GhostHandHook.isRunning())
            assertTrue(GhostHandHook.isTargeted(Blocks.CHEST))
            assertFalse(GhostHandHook.isTargeted(Blocks.STONE))
        }
    }
}
