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
package net.ccbluex.liquidbounce.injection

import com.mojang.blaze3d.platform.NativeImage
import net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinSpriteContentsAccessor
import net.ccbluex.liquidbounce.render.SpriteContentsImageAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpriteContentsImageAccessContractTest {

    @Test
    fun `sprite mixin preserves its getter behind the render-owned contract`() {
        val accessor = MixinSpriteContentsAccessor::class.java

        assertTrue(SpriteContentsImageAccess::class.java.isAssignableFrom(accessor))
        assertEquals(NativeImage::class.java, accessor.getMethod("getOriginalImage").returnType)
    }
}
