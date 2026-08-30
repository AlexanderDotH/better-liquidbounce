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
package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerModelSettingsBridgeTest {

    @Test
    fun `bridge preserves state part and rotation decisions`() {
        val rotation = Rotation(12f, 34f)
        val settings = object : PlayerModelSettingsHook {
            override fun replacementEnabled() = true
            override fun stateEnabled(state: PlayerModelState) = state == PlayerModelState.ROTATION
            override fun partAllowed(part: PlayerModelPart) = part == PlayerModelPart.HEAD
            override fun interpolatedRotation(partialTicks: Float) = rotation
        }

        PlayerModelSettingsBridge.withProviderForTest(settings) {
            assertTrue(PlayerModelSettingsBridge.replacementEnabled())
            assertTrue(PlayerModelSettingsBridge.stateEnabled(PlayerModelState.ROTATION))
            assertFalse(PlayerModelSettingsBridge.stateEnabled(PlayerModelState.POSITION))
            assertTrue(PlayerModelSettingsBridge.partAllowed(PlayerModelPart.HEAD))
            assertFalse(PlayerModelSettingsBridge.partAllowed(PlayerModelPart.BODY))
            assertEquals(rotation, PlayerModelSettingsBridge.interpolatedRotation(0.5f))
        }
    }
}
