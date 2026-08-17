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
package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBetterChatNameHighlightTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `BetterChat exposes enabled name highlight with a configurable color`() {
        val nameHighlight = ModuleBetterChat.inner
            .filterIsInstance<ToggleableValueGroup>()
            .single { it.name == "NameHighlight" }

        assertTrue(nameHighlight.enabled)
        assertEquals(listOf("Enabled", "Color"), nameHighlight.inner.map { it.name })
        assertEquals(ValueType.COLOR, nameHighlight.inner.single { it.name == "Color" }.valueType)
    }
}
