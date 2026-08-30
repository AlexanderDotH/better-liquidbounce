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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker

import net.ccbluex.liquidbounce.render.engine.CustomFogInteractionBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NukerFogInteractionProviderTest {

    @Test
    fun `custom fog interaction follows the exact Nuker running state`() {
        val expected = ModuleNuker.running

        assertEquals(expected, CustomFogInteractionBridge.active())
    }
}
