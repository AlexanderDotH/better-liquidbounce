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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VClipBedrockPathTest {

    private val playerBox = AABB(0.2, 64.0, 0.2, 0.8, 65.8, 0.8)

    @Test
    fun `enabled safety blocks a five block upward clip through bedrock`() {
        assertTrue(
            VClipBedrockPath.isBlocked(
                enabled = true,
                boundingBox = playerBox,
                verticalOffset = 5.0,
                isBedrockAt = bedrockAt(y = 67),
            ),
        )
    }

    @Test
    fun `disabled safety permits the same upward clip through bedrock`() {
        assertFalse(
            VClipBedrockPath.isBlocked(
                enabled = false,
                boundingBox = playerBox,
                verticalOffset = 5.0,
                isBedrockAt = bedrockAt(y = 67),
            ),
        )
    }

    @Test
    fun `enabled safety also blocks a downward clip through bedrock`() {
        assertTrue(
            VClipBedrockPath.isBlocked(
                enabled = true,
                boundingBox = playerBox,
                verticalOffset = -5.0,
                isBedrockAt = bedrockAt(y = 61),
            ),
        )
    }

    @Test
    fun `bedrock supporting the current box is not considered crossed`() {
        assertFalse(
            VClipBedrockPath.isBlocked(
                enabled = true,
                boundingBox = playerBox,
                verticalOffset = 5.0,
                isBedrockAt = bedrockAt(y = 63),
            ),
        )
    }

    private fun bedrockAt(y: Int): (BlockPos) -> Boolean = { position ->
        position.x == 0 && position.y == y && position.z == 0
    }
}
