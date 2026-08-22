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
package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class DynamicTransformSnapshotTest {

    @Test
    fun `later color writes cannot mutate the cached Block ESP transform`() {
        val modelView = Matrix4f()
        val purple = Color4b(216, 71, 255)

        val blockEspTransform = snapshotDynamicTransform(modelView, purple)
        val handTransform = snapshotDynamicTransform(modelView, Color4b.WHITE)

        assertNotSame(blockEspTransform.colorModulator(), handTransform.colorModulator())
        assertEquals(purple.toVector4f(), blockEspTransform.colorModulator())
        assertEquals(Color4b.WHITE.toVector4f(), handTransform.colorModulator())
    }
}
