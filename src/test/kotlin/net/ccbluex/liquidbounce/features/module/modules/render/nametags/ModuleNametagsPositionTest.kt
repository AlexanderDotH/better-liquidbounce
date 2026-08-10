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
package net.ccbluex.liquidbounce.features.module.modules.render.nametags

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModuleNametagsPositionTest {

    @Test
    fun `Y offset is exposed in blocks without changing the default position`() {
        MinecraftBootstrap.ensureInitialized()

        val yOffset = ModuleNametags.inner.single { it.name == "YOffset" } as RangedValue<*>

        assertEquals(0f, ModuleNametags.yOffset)
        assertEquals(-1f, yOffset.range.start)
        assertEquals(1f, yOffset.range.endInclusive)
        assertEquals("blocks", yOffset.suffix)
    }

    @Test
    fun `positive and negative Y offsets move only the vertical anchor`() {
        val anchor = Vec3(2.0, 3.0, 4.0)

        assertEquals(Vec3(2.0, 3.75, 4.0), offsetNametagAnchor(anchor, 0.75f))
        assertEquals(Vec3(2.0, 2.5, 4.0), offsetNametagAnchor(anchor, -0.5f))
    }
}
