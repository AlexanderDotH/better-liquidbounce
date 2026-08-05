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
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleBlockOutlineGlowTest {

    @Test
    fun `block overlay exposes enabled customizable shader glow`() {
        MinecraftBootstrap.ensureInitialized()

        assertEquals("BlockOutline", ModuleBlockOutline.name)
        assertTrue("BlockOverlay" in ModuleBlockOutline.aliases)

        val glow = ModuleBlockOutline.inner.single { it.name == "Glow" } as ToggleableValueGroup
        assertTrue(glow.enabled)
        assertEquals(
            listOf("Enabled", "Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            glow.inner.map { it.name },
        )

        assertRange(glow, "Radius", 4f, 24f, "px")
        assertRange(glow, "Softness", 0.5f, 1.5f, "")
        assertRange(glow, "Intensity", 0f, 2f, "")
        assertRange(glow, "CoreSize", 0f, 3f, "px")
    }

    private fun assertRange(
        values: ToggleableValueGroup,
        name: String,
        from: Float,
        to: Float,
        suffix: String,
    ) {
        val value = values.inner.single { it.name == name } as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }
}
